package com.elshal.vitalyn.features.coach

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.AITasks
import com.elshal.vitalyn.core.ClaudeContent
import com.elshal.vitalyn.core.ClaudeMessage
import com.elshal.vitalyn.core.CoachAction
import com.elshal.vitalyn.core.CoachActionType
import com.elshal.vitalyn.core.serialized
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.CoachMessage
import com.elshal.vitalyn.features.food.MealCaptureSheet
import com.elshal.vitalyn.features.food.MealInput
import com.elshal.vitalyn.services.AIJob
import com.elshal.vitalyn.services.CoachActions
import com.elshal.vitalyn.services.rememberSpeech
import com.elshal.vitalyn.services.userMessage
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.CircleIconButton
import com.elshal.vitalyn.ui.ErrorBanner
import com.elshal.vitalyn.ui.SecondaryButton
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.openUrl
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.max

private val quickPrompts = listOf(
    "آكل إيه دلوقتي؟" to "What should I eat now?",
    "قيّم أسبوعي" to "Review my week",
    "عندي عزومة بكرة" to "I have a dinner party tomorrow",
    "اقترحلي تمرين النهارده" to "Suggest a workout for today",
)

/** The Coach tab: a chat with Claude that can log workouts, water and meals, spoken or typed. */
@Composable
fun CoachScreen() {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    val messages = state.sortedCoachMessages
    // Replies run as background requests, so they arrive even if the app is closed meanwhile.
    val jobs = app.jobs.status
    val sending = jobs.coachWaiting

    val speech = rememberSpeech()
    var draft by remember { mutableStateOf("") }
    var speaking by remember { mutableStateOf(false) }
    var mealToLog by remember { mutableStateOf<MealInput?>(null) }
    // True while a mic-started dictation is running; its text goes to the box and is sent when it ends.
    var voiceActive by remember { mutableStateOf(false) }

    fun send(text: String, speakReply: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || jobs.coachWaiting) return
        voiceActive = false
        speech.stop()
        draft = ""
        jobs.coachError = null

        val history: List<ClaudeMessage> = app.store.value.sortedCoachMessages.takeLast(16).map { message ->
            if (message.isUser) {
                ClaudeMessage.user(listOf(ClaudeContent.Text(message.textAr)))
            } else {
                ClaudeMessage.assistant("""{"replyAr":"${escape(message.textAr)}","replyEn":"${escape(message.textEn)}","actions":[]}""")
            }
        }

        val userMessage = CoachMessage(roleRaw = "user", textAr = trimmed)
        app.store.update { it.copy(coachMessages = it.coachMessages + userMessage) }

        val request = AITasks.coach(history = history, userMessage = trimmed, context = AppData.userContext(app.store.value, app.health))
        try {
            app.jobs.submit(AIJob(kind = AIJob.Kind.coachReply, speak = speakReply), request, app.ai.model)
        } catch (error: Exception) {
            jobs.coachError = error.userMessage()
        }
    }

    fun startVoice() {
        speech.stopSpeaking()
        speaking = false
        draft = ""
        speech.start()
        voiceActive = speech.isRecording
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else speech.errorMessage = "اسمح بالمايك من الإعدادات · Allow the microphone in Settings"
    }

    /** First tap starts dictation into the box; second tap stops and sends, and the reply is read aloud. */
    fun micTapped() {
        if (speech.isRecording) {
            val text = speech.transcript.ifEmpty { draft }
            voiceActive = false
            speech.stop()
            send(text, speakReply = true)
        } else if (speech.hasMicPermission) {
            startVoice()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun perform(action: CoachAction) {
        val meal = action.meal
        if (action.type == CoachActionType.LOG_MEAL && meal != null) {
            mealToLog = MealInput.Prefilled(meal, null)
        } else {
            CoachActions.apply(action, app)
        }
    }

    // Live transcript into the box.
    LaunchedEffect(speech.transcript) {
        if (voiceActive && speech.transcript.isNotEmpty()) draft = speech.transcript
    }
    // Android's recognizer stops by itself after a pause; send what was heard, as the second mic tap would.
    LaunchedEffect(speech.isRecording) {
        if (!speech.isRecording && voiceActive) {
            voiceActive = false
            send(speech.transcript.ifEmpty { draft }, speakReply = true)
        }
    }
    LaunchedEffect(jobs.replyToSpeak) {
        val reply = jobs.replyToSpeak ?: return@LaunchedEffect
        speech.speak(reply)
        speaking = true
        jobs.replyToSpeak = null
    }
    DisposableEffect(Unit) {
        onDispose {
            speech.stop()
            speech.stopSpeaking()
        }
    }

    // Keep the input bar above the keyboard (the tab bar under this screen is covered by it).
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var spaceBelow by remember { mutableIntStateOf(0) }
    val keyboardPadding = with(density) { max(0, imeBottom - spaceBelow).toDp() }

    Column(
        Modifier
            .fillMaxSize()
            .background(VColor.ground)
            .onGloballyPositioned { coordinates ->
                val rootHeight = coordinates.findRootCoordinates().size.height
                val bottom = coordinates.positionInRoot().y + coordinates.size.height
                spaceBelow = max(0, rootHeight - bottom.toInt())
            }
            .padding(bottom = keyboardPadding)
            .statusBarsPadding(),
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).background(VColor.night, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = VColor.mint, modifier = Modifier.size(20.dp))
            }
            BiText(ar = "مدرب Vitalyn", en = "Vitalyn Coach", size = 17f, modifier = Modifier.weight(1f))
            if (speaking) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.VolumeOff,
                    contentDescription = "وقّف الصوت — Stop speaking",
                    onClick = {
                        speech.stopSpeaking()
                        speaking = false
                    },
                    background = VColor.surface,
                    tint = VColor.ink,
                )
            }
        }

        // Chat
        val listState = rememberLazyListState()
        var scrolledOnce by remember { mutableStateOf(false) }
        LaunchedEffect(messages.size) {
            if (messages.isEmpty()) return@LaunchedEffect
            if (scrolledOnce) listState.animateScrollToItem(messages.lastIndex) else listState.scrollToItem(messages.lastIndex)
            scrolledOnce = true
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (messages.isEmpty()) {
                    item(key = "empty") {
                        BiText(
                            ar = "اسألني أي حاجة عن أكلك ورياضتك، أو قولّي عملت إيه النهارده وأنا أسجله.",
                            en = "Ask me anything about food and exercise, or tell me what you did today and I’ll log it.",
                            size = 14f,
                            weight = 400,
                            color = VColor.inkSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message, onAction = { perform(it) })
                }
                if (sending) {
                    item(key = "typing") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(color = VColor.muted, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Text("بفكر… · Thinking…", style = VFont.style(12f, 400, VColor.muted))
                            }
                            Text(
                                "تقدر تقفل التطبيق — هبعتلك إشعار لما أرد · You can leave the app — you’ll get a notification when I reply",
                                style = VFont.style(11f, 400, VColor.muted),
                            )
                        }
                    }
                }
                jobs.coachError?.let { error ->
                    item(key = "error") { ErrorBanner(message = error) }
                }
            }
        }

        // Input bar
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickPrompts.forEach { (ar, en) ->
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .defaultMinSize(minHeight = 46.dp)
                            .background(VColor.surface, shape)
                            .border(1.dp, VColor.line, shape)
                            .clip(shape)
                            .clickable(role = Role.Button) { send(ar) }
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(ar, style = VFont.style(12f, 400, VColor.ink))
                        Text(en, style = VFont.style(10f, 400, VColor.muted))
                    }
                }
            }
            if (speech.isRecording) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = VColor.energy, modifier = Modifier.size(16.dp))
                    Text("بسمعك… دوس المايك تاني عشان تبعت · Listening… tap the mic again to send", style = VFont.style(11f, 400, VColor.energy))
                }
            }
            speech.errorMessage?.let { message ->
                Text(message, style = VFont.style(11f, 400, VColor.energy), modifier = Modifier.padding(horizontal = 16.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val fieldShape = RoundedCornerShape(22.dp)
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    maxLines = 5,
                    textStyle = VFont.style(15f, 400, VColor.ink),
                    cursorBrush = SolidColor(VColor.primary),
                    modifier = Modifier
                        .weight(1f)
                        .background(VColor.surface, fieldShape)
                        .border(1.dp, VColor.line, fieldShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    if (speech.isRecording) "بسمعك… · Listening…" else "اكتب أو اتكلم · Type or talk",
                                    style = VFont.style(15f, 400, VColor.muted),
                                )
                            }
                            inner()
                        }
                    },
                )
                CircleIconButton(
                    icon = if (speech.isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (speech.isRecording) "وقّف وابعت — Stop and send" else "اتكلم — Talk",
                    onClick = { micTapped() },
                    background = if (speech.isRecording) VColor.energy else VColor.primary,
                )
                CircleIconButton(
                    icon = Icons.Rounded.ArrowUpward,
                    contentDescription = "ابعت — Send",
                    onClick = { send(draft) },
                    background = VColor.ink,
                    enabled = draft.isNotBlank() && !sending,
                )
            }
        }
    }

    mealToLog?.let { input -> MealCaptureSheet(input = input, onDismiss = { mealToLog = null }) }
}

/** The text as it appears inside a JSON string literal (without the quotes). */
private fun escape(text: String): String = JsonPrimitive(text).serialized().drop(1).dropLast(1)

@Composable
private fun MessageBubble(message: CoachMessage, onAction: (CoachAction) -> Unit) {
    if (message.isUser) {
        Box(Modifier.fillMaxWidth().padding(end = 40.dp), contentAlignment = Alignment.TopStart) {
            Text(
                message.textAr,
                style = VFont.style(15f, 400, Color.White),
                modifier = Modifier
                    .background(VColor.primary, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    } else {
        Box(Modifier.fillMaxWidth().padding(start = 40.dp), contentAlignment = Alignment.TopEnd) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier
                        .background(VColor.surface, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(message.textAr, style = VFont.style(15f, 400, VColor.ink))
                    Text(message.textEn, style = VFont.style(13f, 400, VColor.inkSecondary))
                }
                message.actions.forEach { action -> ActionView(action, onAction) }
            }
        }
    }
}

@Composable
private fun ActionView(action: CoachAction, onAction: (CoachAction) -> Unit) {
    val context = LocalContext.current
    when (action.type) {
        CoachActionType.LOG_WORKOUT -> action.workout?.let { workout ->
            Confirmation(
                ar = "اتسجل في Health Connect · ${workout.nameAr} ${workout.minutes.toInt()} د",
                en = "Saved to Health Connect · ${workout.nameEn} ${workout.minutes.toInt()} min · ${workout.kcal.toInt()} kcal",
            )
        }
        CoachActionType.LOG_WATER -> Confirmation(
            ar = "اتسجل ${action.waterGlasses ?: 1} كوباية مية",
            en = "Logged ${action.waterGlasses ?: 1} glass(es) of water",
        )
        CoachActionType.LOG_MEAL -> action.meal?.let { meal ->
            SecondaryButton(onClick = { onAction(action) }) {
                BiLabel(ar = "راجع وسجّل: ${meal.titleAr}", en = "Review & log: ${meal.titleEn}", icon = Icons.Rounded.Restaurant, size = 13f)
            }
        }
        CoachActionType.SUGGEST_VIDEOS -> action.videos.forEach { video ->
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(VColor.surface, shape)
                    .clip(shape)
                    .clickable(role = Role.Button) { openUrl(context, video.youtubeURL) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color(0xFFE02B2B), modifier = Modifier.size(28.dp))
                BiText(ar = video.titleAr, en = video.titleEn, size = 13f, weight = 500, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = VColor.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Confirmation(ar: String, en: String) {
    Row(
        Modifier
            .background(VColor.primarySoft, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = VColor.primaryDark, modifier = Modifier.size(18.dp))
        BiText(ar = ar, en = en, size = 12f, weight = 500, color = VColor.primaryDark, enColor = VColor.primaryDark, enSize = 11f)
    }
}
