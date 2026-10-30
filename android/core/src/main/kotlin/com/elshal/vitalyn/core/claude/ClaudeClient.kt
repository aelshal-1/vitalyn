package com.elshal.vitalyn.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

@Serializable
enum class ClaudeModel(val raw: String) {
    @SerialName("claude-opus-5-5") OPUS("claude-opus-5-5"),
    @SerialName("claude-sonnet-5-5") SONNET("claude-sonnet-5-5"),
    @SerialName("claude-haiku-5-5") HAIKU("claude-haiku-5-5");

    val id: String get() = raw

    val displayName: String
        get() = when (this) {
            OPUS -> "Claude Opus 5.5"
            SONNET -> "Claude Sonnet 5.5"
            HAIKU -> "Claude Haiku 5.5"
        }

    val noteAr: String
        get() = when (this) {
            OPUS -> "الأدق — الافتراضي"
            SONNET -> "أسرع وأرخص"
            HAIKU -> "الأرخص — للمهام البسيطة"
        }

    val noteEn: String
        get() = when (this) {
            OPUS -> "Most accurate — default"
            SONNET -> "Faster and cheaper"
            HAIKU -> "Cheapest — simple tasks"
        }

    /** Server-side refusal fallback is available on Opus and Sonnet (not Haiku). */
    val supportsFallback: Boolean get() = this != HAIKU

    companion object {
        fun fromRaw(raw: String): ClaudeModel? = entries.firstOrNull { it.raw == raw }
    }
}

enum class ClaudeEffort(val raw: String) {
    LOW("low"), MEDIUM("medium"), HIGH("high");

    companion object {
        fun fromRaw(raw: String): ClaudeEffort? = entries.firstOrNull { it.raw == raw }
    }
}

/** One piece of a user message. */
sealed class ClaudeContent {
    data class Text(val text: String) : ClaudeContent()

    class Image(val bytes: ByteArray, val mediaType: String) : ClaudeContent() {
        override fun equals(other: Any?): Boolean =
            other is Image && mediaType == other.mediaType && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mediaType.hashCode()
        override fun toString(): String = "Image(${bytes.size} bytes, mediaType=$mediaType)"
    }

    class Pdf(val bytes: ByteArray) : ClaudeContent() {
        override fun equals(other: Any?): Boolean = other is Pdf && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
        override fun toString(): String = "Pdf(${bytes.size} bytes)"
    }

    /** The Messages API content block. */
    val json: JsonObject
        get() = when (this) {
            is Text -> jsonObjectOf("type" to "text", "text" to text)
            is Image -> jsonObjectOf(
                "type" to "image",
                "source" to jsonObjectOf("type" to "base64", "media_type" to mediaType, "data" to base64(bytes))
            )
            is Pdf -> jsonObjectOf(
                "type" to "document",
                "source" to jsonObjectOf("type" to "base64", "media_type" to "application/pdf", "data" to base64(bytes))
            )
        }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}

data class ClaudeMessage(val role: Role, val content: List<ClaudeContent>) {
    enum class Role(val raw: String) {
        USER("user"), ASSISTANT("assistant");

        companion object {
            fun fromRaw(raw: String): Role? = entries.firstOrNull { it.raw == raw }
        }
    }

    companion object {
        fun user(content: List<ClaudeContent>): ClaudeMessage = ClaudeMessage(Role.USER, content)
        fun assistant(text: String): ClaudeMessage = ClaudeMessage(Role.ASSISTANT, listOf(ClaudeContent.Text(text)))
    }
}

/** Errors from Claude; `message` is the bilingual text shown to the user. */
sealed class ClaudeError(message: String) : Exception(message) {
    class MissingAPIKey : ClaudeError("ضيف مفتاح Claude API من الإعدادات · Add your Claude API key in Settings") {
        override fun equals(other: Any?): Boolean = other is MissingAPIKey
        override fun hashCode(): Int = 1
    }

    /** `errorMessage` is the API's own message (Swift: `http(status:message:)`). */
    class Http(val status: Int, val errorMessage: String) :
        ClaudeError("خطأ من Claude ($status) · Claude error ($status): $errorMessage") {
        override fun equals(other: Any?): Boolean = other is Http && status == other.status && errorMessage == other.errorMessage
        override fun hashCode(): Int = 31 * status + errorMessage.hashCode()
        override fun toString(): String = "ClaudeError.Http(status=$status, errorMessage=$errorMessage)"
    }

    class Refusal : ClaudeError("Claude رفض الطلب ده · Claude declined this request") {
        override fun equals(other: Any?): Boolean = other is Refusal
        override fun hashCode(): Int = 3
    }

    class Truncated : ClaudeError("الرد اتقطع — جرّب تاني · The reply was cut off — try again") {
        override fun equals(other: Any?): Boolean = other is Truncated
        override fun hashCode(): Int = 4
    }

    class EmptyResponse : ClaudeError("مفيش رد من Claude · No reply from Claude") {
        override fun equals(other: Any?): Boolean = other is EmptyResponse
        override fun hashCode(): Int = 5
    }

    class InvalidOutput(val detail: String) : ClaudeError("الرد مش مفهوم · Couldn't read the reply: $detail") {
        override fun equals(other: Any?): Boolean = other is InvalidOutput && detail == other.detail
        override fun hashCode(): Int = detail.hashCode()
    }
}

/** Minimal Messages API client over OkHttp, streaming replies as server-sent events. */
class ClaudeClient(
    val apiKey: String,
    val model: ClaudeModel = ClaudeModel.OPUS,
    private val httpClient: OkHttpClient = defaultHttpClient
) {
    companion object {
        const val endpoint: String = "https://api.anthropic.com/v1/messages"
        const val apiVersion: String = "2026-10-21"
        const val fallbackBeta: String = "server-side-fallback-2026-07-01"

        /** Decodes Claude replies: unknown keys ignored, missing optionals null, nulls coerced to defaults. */
        val json: Json = VitalynJson

        /** Long read/write timeouts: the reply streams, but big uploads and slow first tokens take a while. */
        val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(240, TimeUnit.SECONDS)
                .writeTimeout(240, TimeUnit.SECONDS)
                .build()
        }

        /** The reply text from a whole streamed response body (as saved by a background download). */
        fun parseStream(data: ByteArray, status: Int): String {
            if (status !in 200..299) return parse(data, status)
            val stream = StreamAccumulator()
            for (line in String(data, Charsets.UTF_8).splitSwiftLines()) stream.consume(line)
            return stream.finish()
        }

        /** The reply text so far in a partial streamed body, for progress while it downloads. */
        fun partialStreamText(data: ByteArray): String {
            val stream = StreamAccumulator()
            for (line in String(data, Charsets.UTF_8).splitSwiftLines()) {
                try {
                    stream.consume(line)
                } catch (_: ClaudeError) {
                }
            }
            return stream.text
        }

        /** Extracts the reply text from a (non-streamed) Messages API response, turning API failures into [ClaudeError]. */
        fun parse(data: ByteArray, status: Int): String {
            val json: JsonElement? = try {
                JSONValue.parse(data)
            } catch (_: Exception) {
                null
            }
            if (status !in 200..299) {
                val message = json["error"]["message"].stringValue
                    ?: String(data.copyOf(minOf(300, data.size)), Charsets.UTF_8)
                throw ClaudeError.Http(status, message)
            }
            val stopReason = json["stop_reason"].stringValue
            if (stopReason == "refusal") throw ClaudeError.Refusal()
            val text = (json["content"].arrayValue ?: emptyList())
                .filter { it["type"].stringValue == "text" }
                .mapNotNull { it["text"].stringValue }
                .joinToString("")
            if (stopReason == "max_tokens") throw ClaudeError.Truncated()
            if (text.isEmpty()) throw ClaudeError.EmptyResponse()
            return text
        }
    }

    /** Sends a request constrained to `schema` and decodes the JSON reply with [serializer]. */
    suspend fun <T> generate(
        serializer: KSerializer<T>,
        system: String,
        messages: List<ClaudeMessage>,
        schema: JsonElement,
        effort: ClaudeEffort = ClaudeEffort.MEDIUM,
        maxTokens: Int = 16000,
        onText: ((String) -> Unit)? = null
    ): T {
        val text = send(system, messages, schema, effort, maxTokens, onText)
        return decode(serializer, text)
    }

    /** Sends an [AIRequest] and decodes the reply with [serializer]. */
    suspend fun <T> generate(serializer: KSerializer<T>, request: AIRequest, onText: ((String) -> Unit)? = null): T =
        generate(serializer, request.system, request.messages, request.schema, request.effort, request.maxTokens, onText)

    /** `client.generate<DailyTip>(system, messages, schema)`. */
    suspend inline fun <reified T> generate(
        system: String,
        messages: List<ClaudeMessage>,
        schema: JsonElement,
        effort: ClaudeEffort = ClaudeEffort.MEDIUM,
        maxTokens: Int = 16000,
        noinline onText: ((String) -> Unit)? = null
    ): T = generate(serializer<T>(), system, messages, schema, effort, maxTokens, onText)

    /** `client.generate<MealAnalysis>(AITasks.mealFromText(...))`. */
    suspend inline fun <reified T> generate(request: AIRequest, noinline onText: ((String) -> Unit)? = null): T =
        generate(serializer<T>(), request, onText)

    /** Decodes a reply text, turning decoding failures into [ClaudeError.InvalidOutput]. */
    fun <T> decode(serializer: KSerializer<T>, text: String): T =
        try {
            json.decodeFromString(serializer, text)
        } catch (e: IllegalArgumentException) {
            throw ClaudeError.InvalidOutput(e.message ?: e.toString())
        }

    /**
     * Streams the reply (server-sent events), so long replies keep the connection alive instead of sitting
     * silent until they finish, and returns its text. [onText] gets the accumulated text each time it grows
     * (called on an IO thread).
     */
    suspend fun send(
        system: String,
        messages: List<ClaudeMessage>,
        schema: JsonElement?,
        effort: ClaudeEffort = ClaudeEffort.MEDIUM,
        maxTokens: Int = 16000,
        onText: ((String) -> Unit)? = null
    ): String {
        if (apiKey.trimSwiftWhitespaceAndNewlines().isEmpty()) throw ClaudeError.MissingAPIKey()
        val call = httpClient.newCall(makeRequest(system, messages, schema, effort, maxTokens))
        return coroutineScope {
            // Cancelling the coroutine cancels the HTTP call, which unblocks the read below.
            val watcher = launch {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                withContext(Dispatchers.IO) {
                    call.execute().use { response ->
                        val status = response.code
                        val body = response.body
                        if (status !in 200..299) return@withContext parse(body?.bytes() ?: ByteArray(0), status)
                        val stream = StreamAccumulator()
                        val source = body?.source()
                        while (source != null) {
                            ensureActive()
                            val line = source.readUtf8Line() ?: break
                            val before = stream.textLength
                            stream.consume(line)
                            if (onText != null && stream.textLength != before) onText(stream.text)
                        }
                        stream.finish()
                    }
                }
            } finally {
                watcher.cancel()
            }
        }
    }

    /** The HTTP request for an [AIRequest], e.g. to run as a background job. */
    fun makeRequest(request: AIRequest): Request =
        makeRequest(request.system, request.messages, request.schema, request.effort, request.maxTokens)

    fun makeRequest(
        system: String,
        messages: List<ClaudeMessage>,
        schema: JsonElement?,
        effort: ClaudeEffort,
        maxTokens: Int
    ): Request {
        val builder = Request.Builder()
            .url(endpoint)
            .header("content-type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", apiVersion)
        if (model.supportsFallback) builder.header("anthropic-beta", fallbackBeta)
        val bytes = body(system, messages, schema, effort, maxTokens).serialized().toByteArray(Charsets.UTF_8)
        return builder.post(bytes.toRequestBody("application/json".toMediaType())).build()
    }

    /** The JSON request body. */
    fun body(system: String, messages: List<ClaudeMessage>, schema: JsonElement?, effort: ClaudeEffort, maxTokens: Int): JsonObject {
        val outputConfig = mutableListOf<Pair<String, Any?>>("effort" to effort.raw)
        if (schema != null) outputConfig += "format" to jsonObjectOf("type" to "json_schema", "schema" to schema)
        val pairs = mutableListOf<Pair<String, Any?>>(
            "model" to model.raw,
            "max_tokens" to maxTokens,
            "system" to jsonArrayOf(
                jsonObjectOf("type" to "text", "text" to system, "cache_control" to jsonObjectOf("type" to "ephemeral"))
            ),
            "messages" to messages.map { message ->
                jsonObjectOf("role" to message.role.raw, "content" to message.content.map { it.json })
            },
            "output_config" to jsonObjectOf(outputConfig),
            "stream" to true
        )
        if (model.supportsFallback) pairs += "fallbacks" to "default"
        return jsonObjectOf(pairs)
    }
}

/** Collects the reply text from a Messages API event stream. */
class StreamAccumulator {
    private val builder = StringBuilder()
    val text: String get() = builder.toString()
    /** Length of [text], without building it. */
    val textLength: Int get() = builder.length
    var stopReason: String? = null
        private set

    fun consume(line: String) {
        if (!line.startsWith("data:")) return
        val payload = line.substring(5).trimSwiftWhitespace()
        val event = try {
            JSONValue.parse(payload)
        } catch (_: Exception) {
            return
        }
        when (event["type"].stringValue) {
            "content_block_delta" -> {
                val piece = event["delta"]["text"].stringValue
                if (event["delta"]["type"].stringValue == "text_delta" && piece != null) builder.append(piece)
            }
            "message_delta" -> {
                val reason = event["delta"]["stop_reason"].stringValue
                if (reason != null) stopReason = reason
            }
            "error" -> {
                val kind = event["error"]["type"].stringValue
                val message = event["error"]["message"].stringValue ?: "Stream error"
                throw ClaudeError.Http(if (kind == "overloaded_error") 529 else 500, message)
            }
            else -> {}
        }
    }

    fun finish(): String {
        if (stopReason == "refusal") throw ClaudeError.Refusal()
        if (stopReason == "max_tokens") throw ClaudeError.Truncated()
        if (builder.isEmpty()) throw ClaudeError.EmptyResponse()
        return text
    }
}
