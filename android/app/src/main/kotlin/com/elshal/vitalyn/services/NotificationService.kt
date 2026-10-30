package com.elshal.vitalyn.services

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.elshal.vitalyn.AppGraph
import com.elshal.vitalyn.MainActivity
import com.elshal.vitalyn.OpenTarget
import com.elshal.vitalyn.R
import com.elshal.vitalyn.core.*
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.VitalynStore
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.IsoFields

/** Daily reminders, a week of motivational messages, and achievement alerts (all bilingual, all local). */
object NotificationService {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_MOTIVATION = "motivation"
    const val CHANNEL_ACHIEVEMENTS = "achievements"
    const val CHANNEL_RESULTS = "ai-results"
    const val CHANNEL_WORK = "ai-work"

    const val MOTIVATION_ON_KEY = "motivation-on"
    const val MOTIVATION_HOUR_KEY = "motivation-hour"
    const val MOTIVATION_MINUTE_KEY = "motivation-minute"
    const val REMINDERS_ON_KEY = "reminders-on"
    private const val EARNED_BADGES_KEY = "earned-badges"
    private const val AI_WEEK_KEY = "motivation-ai-week"
    private const val AI_MESSAGES_KEY = "motivation-ai-messages"
    private const val SCHEDULED_MOTIVATION_KEY = "motivation-scheduled"

    const val EXTRA_OPEN = "open"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CHANNEL_REMINDERS, "تذكير · Reminders", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_MOTIVATION, "تحفيز · Motivation", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_ACHIEVEMENTS, "إنجازات · Achievements", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_RESULTS, "ردود Claude · Claude replies", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_WORK, "شغل في الخلفية · Background work", NotificationManager.IMPORTANCE_LOW),
        ).forEach(manager::createNotificationChannel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts a notification now. Tapping it opens the app (on `open`'s screen when given). */
    fun post(context: Context, id: String, channel: String, title: String, body: String, open: OpenTarget? = null) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { open?.let { putExtra(EXTRA_OPEN, it.raw) } }
        val pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0E7A55.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, 0, notification)
        } catch (_: SecurityException) {
        }
    }

    // Daily reminders

    fun scheduleDefaults(context: Context) {
        AppGraph.get(context).prefs.setBool(REMINDERS_ON_KEY, true)
        scheduleDaily(context, "morning", 8, 30, "صباح الخير · Good morning", "شوف هدف النهارده واقتراح الفطار · See today’s target and a breakfast idea")
        scheduleDaily(context, "evening", 21, 0, "سجّلت أكلك؟ · Logged your meals?", "صوّر أو اكتب اللي أكلته عشان المتابعة تبقى دقيقة · Snap or type what you ate to keep tracking accurate")
    }

    fun cancelAll(context: Context) {
        AppGraph.get(context).prefs.setBool(REMINDERS_ON_KEY, false)
        listOf("morning", "evening").forEach { cancel(context, it) }
    }

    private fun scheduleDaily(context: Context, id: String, hour: Int, minute: Int, title: String, body: String) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        var fire = LocalDate.now().atTime(hour, minute)
        if (!fire.isAfter(LocalDateTime.now())) fire = fire.plusDays(1)
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            fire.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            reminderIntent(context, id, CHANNEL_REMINDERS, title, body),
        )
    }

    private fun scheduleOnce(context: Context, id: String, at: LocalDateTime, title: String, body: String) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            reminderIntent(context, id, CHANNEL_MOTIVATION, title, body),
        )
    }

    private fun cancel(context: Context, id: String) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(reminderIntent(context, id, "", "", ""))
    }

    private fun reminderIntent(context: Context, id: String, channel: String, title: String, body: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.elshal.vitalyn.reminder.$id")
            .putExtra("id", id)
            .putExtra("channel", channel)
            .putExtra("title", title)
            .putExtra("body", body)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Motivation

    fun motivationEnabled(graph: AppGraph): Boolean = graph.prefs.bool(MOTIVATION_ON_KEY, true)

    fun motivationTime(graph: AppGraph): Pair<Int, Int> =
        graph.prefs.int(MOTIVATION_HOUR_KEY, 13) to graph.prefs.int(MOTIVATION_MINUTE_KEY, 0)

    /**
     * Replaces the next 7 days of motivational notifications. Uses this week's Claude-written messages when
     * available (fetched at most once a week), otherwise the built-in ones personalised with progress.
     */
    suspend fun refreshMotivation(graph: AppGraph) {
        val context = graph.context
        (0 until 7).forEach { cancel(context, "motivation-$it") }
        graph.prefs.setString(SCHEDULED_MOTIVATION_KEY, null)
        val state = graph.store.value
        val profile = state.profile ?: return
        if (!motivationEnabled(graph)) return

        val scans = state.sortedScans
        val current = AppData.currentWeight(profile, scans, graph.health)
        val start = scans.firstOrNull()?.weightKg ?: profile.startWeightKg
        val streak = ProgressMath.streak(state.loggedDays)
        val targets = AppData.targets(profile, scans, graph.health)
        val input = MotivationInput(
            name = profile.name,
            streakDays = streak,
            lostKg = maxOf(0.0, start - current),
            remainingKg = maxOf(0.0, current - profile.goalWeightKg),
            proteinTarget = targets.proteinGrams,
            stepsGoal = profile.stepsGoal,
        )
        val day = LocalDate.now().toEpochDay().toInt()
        var messages = Motivation.messages(input, count = 7, seed = day)

        val userContext = AppData.userContext(state, graph.health)
        if (graph.ai.hasKey && userContext != null) {
            val week = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val listSerializer = ListSerializer(MotivationMessage.serializer())
            val cached = graph.prefs.string(AI_MESSAGES_KEY)?.let { runCatching { VitalynStore.json.decodeFromString(listSerializer, it) }.getOrNull() }
            if (graph.prefs.int(AI_WEEK_KEY) == week && cached != null && cached.size >= 7) {
                messages = cached
            } else {
                val batch = runCatching {
                    graph.ai.run(MotivationBatch.serializer(), AITasks.motivationWeek(userContext, streak, input.lostKg))
                }.getOrNull()
                if (batch != null && batch.messages.size >= 7) {
                    messages = batch.messages.take(7)
                    graph.prefs.setInt(AI_WEEK_KEY, week)
                    graph.prefs.setString(AI_MESSAGES_KEY, VitalynStore.json.encodeToString(listSerializer, messages))
                }
            }
        }

        val (hour, minute) = motivationTime(graph)
        val now = LocalDateTime.now()
        val scheduled = mutableListOf<String>()
        messages.take(7).forEachIndexed { index, message ->
            val fire = LocalDate.now().plusDays(index.toLong()).atTime(LocalTime.of(hour, minute))
            if (!fire.isAfter(now)) return@forEachIndexed
            val title = "${message.titleAr} · ${message.titleEn}"
            val body = "${message.bodyAr}\n${message.bodyEn}"
            scheduleOnce(context, "motivation-$index", fire, title, body)
            scheduled += listOf("motivation-$index", fire.toString(), title, body).joinToString("\u001F")
        }
        graph.prefs.setString(SCHEDULED_MOTIVATION_KEY, scheduled.joinToString("\u001E"))
    }

    /** Alarms are cleared on reboot; put the reminders and this week's messages back. */
    fun rescheduleAfterBoot(context: Context) {
        val graph = AppGraph.get(context)
        if (graph.prefs.bool(REMINDERS_ON_KEY)) scheduleDefaults(context)
        val saved = graph.prefs.string(SCHEDULED_MOTIVATION_KEY).orEmpty()
        saved.split("\u001E").filter { it.isNotEmpty() }.forEach { line ->
            val parts = line.split("\u001F")
            if (parts.size == 4) {
                val fire = runCatching { LocalDateTime.parse(parts[1]) }.getOrNull() ?: return@forEach
                if (fire.isAfter(LocalDateTime.now())) scheduleOnce(context, parts[0], fire, parts[2], parts[3])
            }
        }
    }

    // Background results

    /** Tells the user a background request finished. Skipped while they're in the app, where the screen updates itself. */
    fun announce(context: Context, id: String, title: String, body: String, open: OpenTarget) {
        if (BackgroundAI.isForeground()) return
        post(context, id, CHANNEL_RESULTS, title, body, open)
    }

    // Achievements

    /** Sends a notification for each badge earned since the last check (the first check only records the baseline). */
    fun announceNewBadges(graph: AppGraph) {
        val input = AppData.achievementInput(graph.store.value, graph.health) ?: return
        val badges = Achievements.badges(input)
        val earned = badges.filter { it.achieved }.map { it.id }.toSet()
        val known = graph.prefs.stringSet(EARNED_BADGES_KEY)
        if (known == null) {
            graph.prefs.setStringSet(EARNED_BADGES_KEY, earned)
            return
        }
        val fresh = badges.filter { it.achieved && it.id !in known }
        graph.prefs.setStringSet(EARNED_BADGES_KEY, earned + known)
        fresh.forEach { badge ->
            post(graph.context, "badge-${badge.id}", CHANNEL_ACHIEVEMENTS, "🏆 إنجاز جديد · New achievement", "${badge.ar} · ${badge.en}\nبرافو عليك! · Well done!")
        }
    }
}

/** Shows a scheduled reminder or motivation message. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val channel = intent.getStringExtra("channel")?.takeIf { it.isNotEmpty() } ?: NotificationService.CHANNEL_REMINDERS
        NotificationService.post(context, id, channel, intent.getStringExtra("title").orEmpty(), intent.getStringExtra("body").orEmpty())
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) NotificationService.rescheduleAfterBoot(context)
    }
}
