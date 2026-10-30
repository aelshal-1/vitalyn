package com.elshal.vitalyn.services

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elshal.vitalyn.AppGraph
import com.elshal.vitalyn.OpenTarget
import com.elshal.vitalyn.R
import com.elshal.vitalyn.core.*
import com.elshal.vitalyn.data.CoachMessage
import com.elshal.vitalyn.data.PlanStatus
import com.elshal.vitalyn.data.VitalynStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A Claude request that keeps going after the user leaves the app. */
@Serializable
data class AIJob(
    val kind: Kind,
    /** For a weekly plan, the plan record's id. */
    val target: String = "",
    /** Read the coach's reply aloud (the question was spoken). */
    val speak: Boolean = false,
    /** Base name of the request body file. */
    val file: String = UUID.randomUUID().toString(),
) {
    @Serializable
    enum class Kind { weekPlan, coachReply }
}

/** What the screens show while jobs run (Compose state). */
class AIJobStatus {
    /** Meals written so far, by plan id. */
    val planMeals = mutableStateMapOf<String, Int>()
    var coachWaiting by mutableStateOf(false)
    var coachError by mutableStateOf<String?>(null)
    /** A coach reply to read aloud, picked up by the coach screen. */
    var replyToSpeak by mutableStateOf<String?>(null)
}

/**
 * Runs Claude requests through WorkManager so they keep going while the app is in the background
 * (and are retried if Android stops the process). The request body is saved to a file first;
 * the reply is saved to the store and announced with a notification.
 */
class BackgroundAI(private val graph: AppGraph) {
    val status = AIJobStatus()
    private val workManager get() = WorkManager.getInstance(graph.context)

    // Starting

    fun submit(job: AIJob, request: AIRequest, model: ClaudeModel) {
        val key = graph.ai.keyStore.read() ?: throw ClaudeError.MissingAPIKey()
        val httpRequest = ClaudeClient(apiKey = key, model = model).makeRequest(request)
        val buffer = Buffer()
        httpRequest.body?.writeTo(buffer)
        bodyFile(graph.context, job).writeBytes(buffer.readByteArray())

        val work = OneTimeWorkRequestBuilder<AIJobWorker>()
            .setInputData(Data.Builder().putString(JOB_KEY, VitalynStore.json.encodeToString(AIJob.serializer(), job)).putString(MODEL_KEY, model.id).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag("target:${job.target}")
            .addTag("kind:${job.kind.name}")
            .build()
        workManager.enqueueUniqueWork(job.file, ExistingWorkPolicy.REPLACE, work)
        markRunning(job)
    }

    private fun markRunning(job: AIJob) {
        when (job.kind) {
            AIJob.Kind.weekPlan -> status.planMeals[job.target] = status.planMeals[job.target] ?: 0
            AIJob.Kind.coachReply -> {
                status.coachWaiting = true
                status.coachError = null
            }
        }
    }

    /**
     * Reconnects to requests still running from before and marks plans whose request was lost as failed.
     * Call when the app comes to the foreground.
     */
    suspend fun restore() {
        val infos = withContext(Dispatchers.IO) { runCatching { workManager.getWorkInfosByTag(TAG).get() }.getOrDefault(emptyList()) }
        val running = infos.filter { !it.state.isFinished }
        val runningTargets = running.flatMap { info -> info.tags.filter { it.startsWith("target:") }.map { it.removePrefix("target:") } }.toSet()
        if (running.none { "kind:${AIJob.Kind.coachReply.name}" in it.tags }) status.coachWaiting = false
        running.forEach { info ->
            info.tags.firstOrNull { it.startsWith("target:") }?.removePrefix("target:")?.takeIf { it.isNotEmpty() }?.let {
                status.planMeals[it] = status.planMeals[it] ?: 0
            }
        }
        graph.store.update { state ->
            state.copy(
                plans = state.plans.map { record ->
                    if (record.status == PlanStatus.BUILDING && record.id !in runningTargets) {
                        status.planMeals.remove(record.id)
                        record.withStatus(PlanStatus.FAILED).copy(errorMessage = "اتوقف قبل ما يخلص — جرّب تاني · Stopped before it finished — try again")
                    } else {
                        record
                    }
                },
            )
        }
    }

    // Progress and results (called by the worker)

    internal fun progress(job: AIJob, partialText: String) {
        if (job.kind == AIJob.Kind.weekPlan) {
            status.planMeals[job.target] = partialText.split("\"slot\"").size - 1
        }
    }

    internal fun finish(job: AIJob, result: Result<String>) {
        bodyFile(graph.context, job).delete()
        when (job.kind) {
            AIJob.Kind.weekPlan -> finishPlan(job, result)
            AIJob.Kind.coachReply -> finishCoach(job, result)
        }
        graph.store.flush()
    }

    private fun finishPlan(job: AIJob, result: Result<String>) {
        status.planMeals.remove(job.target)
        val id = job.target
        if (graph.store.value.plan(id) == null) return
        try {
            val text = result.getOrThrow()
            val plan = ClaudeClient.json.decodeFromString(WeekPlanDraft.serializer(), text).plan
            graph.store.update { it.updatePlan(id) { record -> record.copy(plan = plan, errorMessage = "").withStatus(PlanStatus.READY) } }
            NotificationService.announce(
                graph.context,
                id = "plan-$id",
                title = "جدول الأسبوع جاهز 🍽️ · Your weekly plan is ready",
                body = "الوجبات وقائمة المشتريات مستنياك · Meals and grocery list are waiting",
                open = OpenTarget.PLAN,
            )
        } catch (error: Exception) {
            graph.store.update { it.updatePlan(id) { record -> record.copy(errorMessage = describe(error)).withStatus(PlanStatus.FAILED) } }
            NotificationService.announce(
                graph.context,
                id = "plan-$id",
                title = "معرفناش نعمل الجدول · Couldn't make your plan",
                body = "افتح التطبيق وجرّب تاني · Open the app to try again",
                open = OpenTarget.PLAN,
            )
        }
    }

    private fun finishCoach(job: AIJob, result: Result<String>) {
        status.coachWaiting = false
        try {
            val text = result.getOrThrow()
            val reply = ClaudeClient.json.decodeFromString(CoachReply.serializer(), text)
            val message = CoachMessage(roleRaw = "assistant", textAr = reply.replyAr, textEn = reply.replyEn, actions = reply.actions)
            graph.store.update { it.copy(coachMessages = it.coachMessages + message) }
            reply.actions.filter { it.type == CoachActionType.LOG_WORKOUT || it.type == CoachActionType.LOG_WATER }
                .forEach { CoachActions.apply(it, graph) }
            if (job.speak && isForeground()) status.replyToSpeak = reply.replyAr
            NotificationService.announce(graph.context, id = "coach-${job.file}", title = "رد المدرب · Coach replied", body = "${reply.replyAr}\n${reply.replyEn}", open = OpenTarget.COACH)
        } catch (error: Exception) {
            status.coachError = describe(error)
            NotificationService.announce(graph.context, id = "coach-${job.file}", title = "المدرب مردّش · The coach couldn't reply", body = "افتح التطبيق وابعت تاني · Open the app and send again", open = OpenTarget.COACH)
        }
    }

    companion object {
        const val TAG = "claude-job"
        const val JOB_KEY = "job"
        const val MODEL_KEY = "model"

        private fun folder(context: Context) = File(context.filesDir, "ai-jobs").apply { mkdirs() }

        fun bodyFile(context: Context, job: AIJob) = File(folder(context), "${job.file}.json")

        fun isForeground(): Boolean =
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

        /** Seconds-scale retry is worth it for rate limits, overloads and dropped connections. */
        fun isRetryable(error: Throwable): Boolean = when (error) {
            is ClaudeError.Http -> error.status == 429 || error.status >= 500
            is java.net.SocketTimeoutException -> false
            is IOException -> true
            else -> false
        }

        fun describe(error: Throwable): String = when (error) {
            is kotlinx.serialization.SerializationException -> ClaudeError.InvalidOutput("").message ?: ""
            else -> error.userMessage()
        }
    }
}

/** Sends one saved request body, streaming the reply, and hands the result to [BackgroundAI]. */
class AIJobWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val graph = AppGraph.get(context)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NotificationService.CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Vitalyn")
            .setContentText("Claude بيجهز الرد… · Claude is working…")
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val job = inputData.getString(BackgroundAI.JOB_KEY)?.let {
            runCatching { VitalynStore.json.decodeFromString(AIJob.serializer(), it) }.getOrNull()
        } ?: return Result.failure()
        val model = ClaudeModel.fromRaw(inputData.getString(BackgroundAI.MODEL_KEY) ?: "") ?: ClaudeModel.OPUS
        // Long plans can take minutes; run as a foreground job so Android doesn't stop it.
        runCatching { setForeground(getForegroundInfo()) }

        val result = withContext(Dispatchers.IO) { runCatching { send(job, model) } }
        val error = result.exceptionOrNull()
        if (error != null && runAttemptCount < 2 && BackgroundAI.isRetryable(error)) {
            withContext(Dispatchers.Main) { graph.jobs.progress(job, "") }
            return Result.retry()
        }
        withContext(Dispatchers.Main) { graph.jobs.finish(job, result) }
        return Result.success()
    }

    private suspend fun send(job: AIJob, model: ClaudeModel): String {
        val key = graph.ai.keyStore.read() ?: throw ClaudeError.MissingAPIKey()
        val body = BackgroundAI.bodyFile(applicationContext, job)
        if (!body.exists()) throw ClaudeError.EmptyResponse()
        val builder = Request.Builder()
            .url(ClaudeClient.endpoint)
            .post(body.asRequestBody("application/json".toMediaType()))
            .header("content-type", "application/json")
            .header("x-api-key", key)
            .header("anthropic-version", ClaudeClient.apiVersion)
        if (model.supportsFallback) builder.header("anthropic-beta", ClaudeClient.fallbackBeta)

        ClaudeClient.defaultHttpClient.newCall(builder.build()).execute().use { response ->
            val source = response.body?.source() ?: throw ClaudeError.EmptyResponse()
            val bytes = ByteArrayOutputStream()
            var checkedAt = 0
            val chunk = ByteArray(8192)
            val stream = source.inputStream()
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                bytes.write(chunk, 0, read)
                // Count meals written so far, re-reading the stream only every ~16 KB.
                if (job.kind == AIJob.Kind.weekPlan && bytes.size() - checkedAt > 16_000) {
                    checkedAt = bytes.size()
                    val partial = ClaudeClient.partialStreamText(bytes.toByteArray())
                    withContext(Dispatchers.Main) { graph.jobs.progress(job, partial) }
                }
            }
            return ClaudeClient.parseStream(bytes.toByteArray(), response.code)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 4101
    }
}
