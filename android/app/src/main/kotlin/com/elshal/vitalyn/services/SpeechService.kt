package com.elshal.vitalyn.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.elshal.vitalyn.ui.CircleIconButton
import com.elshal.vitalyn.ui.VColor
import java.util.Locale

/** Dictation (Egyptian Arabic by default) and spoken replies. Create one per screen with [rememberSpeech]. */
class SpeechService(private val context: Context) {
    var transcript by mutableStateOf("")
        private set
    var isRecording by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    var localeIdentifier = "ar-EG"

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: Pair<String, String>? = null

    val hasMicPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun toggle() = if (isRecording) stop() else start()

    /** Starts listening. Needs the microphone permission (the dictation button asks for it). */
    fun start() {
        errorMessage = null
        transcript = ""
        if (!hasMicPermission) {
            errorMessage = "اسمح بالمايك من الإعدادات · Allow the microphone in Settings"
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            errorMessage = "التعرف على الكلام مش متاح دلوقتي · Speech recognition is unavailable"
            return
        }
        val recognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) transcript = it }
            }

            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) transcript = it }
                isRecording = false
            }

            override fun onError(error: Int) {
                isRecording = false
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT && error != SpeechRecognizer.ERROR_CLIENT) {
                    errorMessage = "مقدرتش أسمعك — جرّب تاني · Couldn't hear you — try again"
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeIdentifier)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        recognizer.startListening(intent)
        isRecording = true
    }

    fun stop() {
        recognizer?.stopListening()
        isRecording = false
    }

    fun speak(text: String, language: String = "ar") {
        val engine = tts
        if (engine == null) {
            pendingSpeech = text to language
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                pendingSpeech?.let { (t, l) -> if (ttsReady) say(t, l) }
                pendingSpeech = null
            }
            return
        }
        if (ttsReady) say(text, language) else pendingSpeech = text to language
    }

    private fun say(text: String, language: String) {
        val engine = tts ?: return
        engine.language = Locale.forLanguageTag(language)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vitalyn-reply")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }
}

/** A [SpeechService] tied to the calling screen's lifetime. */
@Composable
fun rememberSpeech(): SpeechService {
    val context = LocalContext.current
    val speech = remember { SpeechService(context) }
    DisposableEffect(speech) { onDispose { speech.release() } }
    return speech
}

/** Mic button bound to a [SpeechService], writing the transcript into `onText`. Asks for the microphone first. */
@Composable
fun DictationButton(speech: SpeechService, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start() else speech.errorMessage = "اسمح بالمايك من الإعدادات · Allow the microphone in Settings"
    }
    LaunchedEffect(speech.transcript) {
        if (speech.transcript.isNotEmpty()) onText(speech.transcript)
    }
    CircleIconButton(
        icon = if (speech.isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
        contentDescription = if (speech.isRecording) "وقّف — Stop" else "اتكلم — Dictate",
        onClick = {
            when {
                speech.isRecording -> speech.stop()
                speech.hasMicPermission -> speech.start()
                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = modifier,
        background = if (speech.isRecording) VColor.energy else VColor.primary,
    )
}
