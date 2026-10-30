package com.elshal.vitalyn.features.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elshal.vitalyn.BuildConfig
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.ClaudeModel
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.UserProfile
import com.elshal.vitalyn.services.NotificationService
import com.elshal.vitalyn.services.rememberHealthPermissionRequest
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.SectionTitle
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The "Me" tab: the stored profile, edited in place, plus connections and settings. */
@Composable
fun ProfileScreen() {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    val profile = state.profile
    val scans = state.sortedScans
    val edit: (UserProfile) -> Unit = { updated -> app.store.update { s -> s.withProfile { updated } } }

    // Leaving the tab refreshes the widgets with the new targets (the store saves by itself).
    DisposableEffect(Unit) {
        onDispose { app.publishSnapshot() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(VColor.ground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BiText(ar = "ملفي الصحي", en = "My health profile", size = 26f, enSize = 15f)
            if (profile != null) {
                TargetsPreview(profile = profile, scans = scans)
                BasicsSection(profile = profile, onChange = edit)
                GoalSection(
                    profile = profile,
                    onChange = edit,
                    currentWeightKg = AppData.currentWeight(profile, scans, app.health),
                    bodyFatPercent = scans.lastOrNull()?.effectiveBodyFatPercent,
                )
                HealthSection(profile = profile, onChange = edit)
                MetabolismSection(profile = profile, onChange = edit, scans = scans)
            }
            ConnectionsSection()
            BiText(
                ar = "Vitalyn بيساعدك تتابع وتنظم، لكنه مش بديل عن الدكتور. أي تغيير في الأدوية أو الأكل مع مرض مزمن لازم يكون بموافقة دكتورك.",
                en = "Vitalyn helps you track and plan — it doesn’t replace your doctor. Check medication or diet changes with them.",
                size = 12f, weight = 400, color = VColor.inkSecondary,
            )
            Ltr {
                Text(
                    versionText,
                    style = VFont.style(11f, 400, VColor.muted),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val versionText: String
    get() = "Vitalyn ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

/** Health Connect, the Claude key and model, and notification settings. */
@Composable
fun ConnectionsSection() {
    val app = LocalApp.current
    val context = LocalContext.current
    val health = app.health
    val ai = app.ai

    var keyDraft by remember { mutableStateOf("") }
    var editingKey by remember { mutableStateOf(false) }
    var remindersOn by remember { mutableStateOf(app.prefs.bool(NotificationService.REMINDERS_ON_KEY, true)) }
    var motivationOn by remember { mutableStateOf(NotificationService.motivationEnabled(app)) }
    var motivationTime by remember { mutableStateOf(NotificationService.motivationTime(app)) }
    var showTimePicker by remember { mutableStateOf(false) }

    val connectHealth = rememberHealthPermissionRequest(health)

    fun rescheduleMotivation() {
        app.scope.launch { NotificationService.refreshMotivation(app) }
    }

    // Android 13+ asks for notification permission before anything is scheduled.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterPermission?.invoke()
        afterPermission = null
    }
    fun withNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationService.canPost(context)) {
            afterPermission = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    Card {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(ar = "الربط والإعدادات", en = "Connections & settings")

            SettingsRow(icon = Icons.Rounded.Favorite, tint = Color(0xFFC2264B), ar = "Health Connect", en = "Health Connect") {
                if (health.requested) {
                    BiCaption(ar = "متصل", en = "Connected", color = VColor.primaryDark)
                } else {
                    LinkText("اربط · Connect", onClick = connectHealth)
                }
            }

            HorizontalDivider(color = VColor.line)

            SettingsRow(icon = Icons.Rounded.AutoAwesome, tint = VColor.ink, ar = "مفتاح Claude API", en = "Claude API key") {
                if (ai.hasKey && !editingKey) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LinkText("غيّر · Change") { editingKey = true }
                        LinkText("امسح · Remove", color = VColor.energy) {
                            ai.removeKey()
                            keyDraft = ""
                            editingKey = false
                        }
                    }
                }
            }
            if (ai.hasKey && !editingKey) {
                Ltr {
                    Text(
                        ai.maskedKey,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = VColor.muted),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(VColor.ground, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        ApiKeyField(value = keyDraft, onValueChange = { keyDraft = it }, modifier = Modifier.fillMaxWidth())
                    }
                    LinkText("احفظ · Save", enabled = keyDraft.isNotBlank()) {
                        ai.setKey(keyDraft)
                        keyDraft = ""
                        editingKey = false
                    }
                }
                ConsoleLink()
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BiCaption(ar = "الموديل", en = "Model")
                ClaudeModel.entries.forEach { model ->
                    val selected = ai.model == model
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { ai.model = model }
                            .semantics { this.selected = selected }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(model.displayName, style = VFont.style(14f, 600, VColor.ink))
                            Text("${model.noteAr} · ${model.noteEn}", style = VFont.style(11f, 400, VColor.muted))
                        }
                        Icon(
                            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) VColor.primary else VColor.line,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = VColor.line)

            SwitchRow(ar = "تذكير الصبح وبالليل", en = "Morning & evening reminders", checked = remindersOn) { on ->
                remindersOn = on
                if (on) {
                    withNotificationPermission { NotificationService.scheduleDefaults(context) }
                } else {
                    NotificationService.cancelAll(context)
                }
            }

            SwitchRow(ar = "رسايل تحفيزية كل يوم", en = "Daily motivational messages", checked = motivationOn) { on ->
                motivationOn = on
                app.prefs.setBool(NotificationService.MOTIVATION_ON_KEY, on)
                if (on) withNotificationPermission { rescheduleMotivation() } else rescheduleMotivation()
            }
            if (motivationOn) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BiCaption(ar = "الميعاد", en = "Time", modifier = Modifier.weight(1f))
                    Ltr {
                        Text(
                            LocalTime.of(motivationTime.first, motivationTime.second).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)),
                            style = VFont.style(15f, 600, VColor.ink),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .background(VColor.ground, RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button) { showTimePicker = true }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
                BiText(
                    ar = "رسالة كل يوم من مدربك عن تقدمك الحقيقي، وتنبيه لما تحقق إنجاز جديد.",
                    en = "A daily note from your coach about your real progress, plus an alert when you earn an achievement.",
                    size = 11f, weight = 400, color = VColor.muted,
                )
            }
        }
    }

    if (showTimePicker) {
        MotivationTimeDialog(
            hour = motivationTime.first,
            minute = motivationTime.second,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                showTimePicker = false
                motivationTime = hour to minute
                app.prefs.setInt(NotificationService.MOTIVATION_HOUR_KEY, hour)
                app.prefs.setInt(NotificationService.MOTIVATION_MINUTE_KEY, minute)
                rescheduleMotivation()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MotivationTimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("تمام · Done", style = VFont.style(14f, 600, VColor.primary))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء · Cancel", style = VFont.style(14f, 500, VColor.muted))
            }
        },
        title = { BiText(ar = "الميعاد", en = "Time", size = 16f) },
        text = { Ltr { TimePicker(state = state) } },
        containerColor = VColor.surface,
    )
}

@Composable
private fun SettingsRow(icon: ImageVector, tint: Color, ar: String, en: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        BiText(ar = ar, en = en, size = 14f, weight = 500, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SwitchRow(ar: String, en: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BiText(ar = ar, en = en, size = 14f, weight = 500, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = VColor.primary, checkedThumbColor = Color.White),
        )
    }
}

/** A text button ("غيّر · Change"). */
@Composable
private fun LinkText(text: String, enabled: Boolean = true, color: Color = VColor.primary, onClick: () -> Unit) {
    Text(
        text,
        style = VFont.style(13f, 600, if (enabled) color else VColor.muted.copy(alpha = 0.6f)),
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

/** Masked, monospaced field for the API key (the iOS `SecureField`), always left-to-right. */
@Composable
internal fun ApiKeyField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = VColor.ink)
    Ltr {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(VColor.primary),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Password),
            modifier = modifier,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("sk-ant-…", style = style.copy(color = VColor.muted))
                    inner()
                }
            },
        )
    }
}
