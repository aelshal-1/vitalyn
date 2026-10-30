package com.elshal.vitalyn.services

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.elshal.vitalyn.core.*
import com.elshal.vitalyn.data.Prefs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/** Stores the Claude API key encrypted with the Android Keystore (never in the code, the repo or plain prefs). */
class KeyStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "vitalyn-secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun read(): String? = prefs.getString(ACCOUNT, null)?.takeIf { it.isNotBlank() }

    fun save(key: String) {
        val trimmed = key.trim()
        prefs.edit { if (trimmed.isEmpty()) remove(ACCOUNT) else putString(ACCOUNT, trimmed) }
    }

    fun delete() = prefs.edit { remove(ACCOUNT) }

    private companion object {
        const val ACCOUNT = "anthropic-api-key"
    }
}

/** Entry point for every Claude call in the app. Its properties are Compose state, so screens update by themselves. */
class AIService(context: Context, private val prefs: Prefs) {
    val keyStore = KeyStore(context)

    var hasKey by mutableStateOf(keyStore.read() != null)
        private set

    private var _model by mutableStateOf(ClaudeModel.fromRaw(prefs.string(MODEL_KEY) ?: "") ?: ClaudeModel.OPUS)
    var model: ClaudeModel
        get() = _model
        set(value) {
            _model = value
            prefs.setString(MODEL_KEY, value.id)
        }

    fun setKey(key: String) {
        keyStore.save(key)
        hasKey = keyStore.read() != null
    }

    fun removeKey() {
        keyStore.delete()
        hasKey = false
    }

    val maskedKey: String
        get() {
            val key = keyStore.read() ?: return ""
            if (key.length <= 12) return ""
            return key.take(10) + "…" + key.takeLast(4)
        }

    fun client(): ClaudeClient {
        val key = keyStore.read() ?: throw ClaudeError.MissingAPIKey()
        return ClaudeClient(apiKey = key, model = model)
    }

    /** Runs a structured request and decodes Claude's JSON reply. Throws [ClaudeError] on failure. */
    suspend fun <T> run(serializer: KSerializer<T>, request: AIRequest): T = client().generate(serializer, request)

    suspend inline fun <reified T> run(request: AIRequest): T = run(serializer<T>(), request)

    private companion object {
        const val MODEL_KEY = "claude-model"
    }
}

/** The bilingual message for any error a Claude call can throw. */
fun Throwable.userMessage(): String = when (this) {
    is ClaudeError -> message ?: "Claude error"
    is kotlinx.serialization.SerializationException -> ClaudeError.InvalidOutput(message ?: "").message ?: "Couldn't read the reply"
    is java.net.UnknownHostException, is java.net.ConnectException ->
        "مفيش إنترنت — اتأكد من الاتصال · No internet connection"
    is java.net.SocketTimeoutException -> "الطلب أخد وقت طويل — جرّب تاني · The request timed out — try again"
    else -> localizedMessage ?: toString()
}
