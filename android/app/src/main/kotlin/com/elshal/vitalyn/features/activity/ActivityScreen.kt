package com.elshal.vitalyn.features.activity

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.AITasks
import com.elshal.vitalyn.core.VideoSuggestion
import com.elshal.vitalyn.core.WorkoutKind
import com.elshal.vitalyn.core.WorkoutSuggestion
import com.elshal.vitalyn.core.WorkoutSuggestions
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.WorkoutEntry
import com.elshal.vitalyn.data.nowMillis
import com.elshal.vitalyn.data.toLocalDateTime
import com.elshal.vitalyn.data.toMillis
import com.elshal.vitalyn.services.DayValue
import com.elshal.vitalyn.services.ar
import com.elshal.vitalyn.services.en
import com.elshal.vitalyn.services.icon
import com.elshal.vitalyn.services.userMessage
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiChip
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.CircleIconButton
import com.elshal.vitalyn.ui.ErrorBanner
import com.elshal.vitalyn.ui.FullSheet
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.PrimaryButton
import com.elshal.vitalyn.ui.PushedScreen
import com.elshal.vitalyn.ui.SecondaryButton
import com.elshal.vitalyn.ui.SectionTitle
import com.elshal.vitalyn.ui.SheetScaffold
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import com.elshal.vitalyn.ui.WorkingView
import com.elshal.vitalyn.ui.openUrl
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private data class WorkoutRow(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val kind: WorkoutKind,
    /** Epoch millis. */
    val date: Long,
    val minutes: Double,
    val kcal: Double,
    val source: String,
)

private val rowDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen() {
    val app = LocalApp.current
    val health = app.health
    val state by app.store.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var month by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<WorkoutSuggestion>>(emptyList()) }
    var loadingSuggestions by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    val stepsGoal = state.profile?.stepsGoal ?: 8000

    // Health workouts plus manual ones that were not saved to Health Connect (to avoid duplicates).
    val workoutRows = run {
        val fromHealth = health.workouts.map {
            WorkoutRow(it.id, it.kind.ar, it.kind.en, it.kind, it.start.toEpochMilli(), it.minutes, it.kcal, "Health")
        }
        val cutoff = LocalDateTime.now().minusDays(14).toMillis()
        val fromApp = state.workouts.filter { !it.savedToHealth && it.date >= cutoff }.map {
            WorkoutRow(it.id, it.nameAr, it.nameEn, it.kind, it.date, it.minutes, it.kcal, "يدوي · Manual")
        }
        (fromHealth + fromApp).sortedByDescending { it.date }
    }

    fun loadSuggestions() {
        val userContext = AppData.userContext(state, health) ?: return
        loadingSuggestions = true
        error = null
        scope.launch {
            try {
                suggestions = app.ai.run(WorkoutSuggestions.serializer(), AITasks.workoutSuggestions(context = userContext, request = "")).items
            } catch (e: Exception) {
                error = e.userMessage()
            }
            loadingSuggestions = false
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                health.refresh()
                refreshing = false
            }
        },
    ) {
        PushedScreen("النشاط", "Activity") {
            Column(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Steps
                val data = if (month) health.monthSteps else health.weekSteps
                val average = if (data.isEmpty()) 0 else (data.sumOf { it.value } / data.size).toInt()
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                BiCaption(if (month) "متوسط الشهر" else "متوسط الأسبوع", if (month) "Monthly average" else "Weekly average")
                                Text("${VFormat.number(average)} خطوة · steps", style = VFont.style(22f, 600, VColor.ink))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                BiChip("أسبوع", "Week", selected = !month, dark = true) { month = false }
                                BiChip("شهر", "Month", selected = month, dark = true) { month = true }
                            }
                        }
                        if (data.isEmpty()) {
                            BiText(
                                "اربط Health Connect عشان تشوف خطواتك.",
                                "Connect Health Connect to see your steps.",
                                size = 13f,
                                weight = 400,
                                color = VColor.inkSecondary,
                            )
                        } else {
                            StepsChart(data, stepsGoal, month)
                        }
                    }
                }

                // Today's stats
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("سعرات نشاط", "Active kcal", "${health.activeKcalToday}", VColor.energy, Modifier.weight(1f))
                        Stat("مسافة", "Distance", "${VFormat.decimal(health.distanceKmToday)} km", VColor.ink, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("نبض الراحة", "Resting HR", health.restingHeartRate?.let { "$it bpm" } ?: "—", VColor.ink, Modifier.weight(1f))
                        Stat("النوم", "Sleep", health.sleepHoursLastNight?.let { "${VFormat.decimal(it)} h" } ?: "—", VColor.ink, Modifier.weight(1f))
                    }
                }

                // Workouts
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("التمارين", "Workouts", Modifier.weight(1f))
                        SecondaryButton(onClick = { showLog = true }) {
                            BiLabel("سجّل تمرين", "Log workout", icon = Icons.Rounded.Add, size = 13f)
                        }
                    }
                    if (workoutRows.isEmpty()) {
                        BiText(
                            "مفيش تمارين في آخر أسبوعين. سجّل واحد أو قول للمدرب عملت إيه.",
                            "No workouts in the last two weeks. Log one or tell the coach.",
                            size = 13f,
                            weight = 400,
                            color = VColor.inkSecondary,
                        )
                    } else {
                        Column(Modifier.fillMaxWidth().background(VColor.surface, RoundedCornerShape(20.dp))) {
                            workoutRows.forEachIndexed { index, row ->
                                WorkoutRowView(row)
                                if (index < workoutRows.lastIndex) HorizontalDivider(color = VColor.line)
                            }
                        }
                    }
                }

                // Suggestions
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("مقترح ليك", "Recommended for you", Modifier.weight(1f))
                        SecondaryButton(onClick = { loadSuggestions() }, enabled = !loadingSuggestions) {
                            BiLabel(
                                if (suggestions.isEmpty()) "اقترح" else "اقترح غيرهم",
                                if (suggestions.isEmpty()) "Suggest" else "New ideas",
                                icon = Icons.Rounded.AutoAwesome,
                                size = 13f,
                            )
                        }
                    }
                    if (loadingSuggestions) WorkingView("بختار تمارين مناسبة ليك…", "Picking workouts that fit you…")
                    error?.let { ErrorBanner(it) }
                    suggestions.forEach { item ->
                        SuggestionRow(item) {
                            openUrl(context, VideoSuggestion(titleAr = item.titleAr, titleEn = item.titleEn, query = item.youtubeQuery).youtubeURL)
                        }
                    }
                }
            }
        }
    }

    if (showLog) LogWorkoutSheet(onDismiss = { showLog = false })
}

@Composable
private fun StepsChart(data: List<DayValue>, goal: Int, month: Boolean) {
    val highest = max(data.maxOf { it.value }, goal.toDouble()) * 1.08
    val goalColor = VColor.primary
    val reached = VColor.primary
    val below = Color(0xFF9AC9B2)
    Ltr {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                val slot = size.width / data.size
                val gap = if (month) 2.dp.toPx() else 8.dp.toPx()
                val barWidth = max(1f, slot - gap)
                val radius = min(6.dp.toPx(), barWidth / 2)
                for (i in 0..3) {
                    val y = size.height * i / 4f
                    drawLine(VColor.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                data.forEachIndexed { index, day ->
                    val barHeight = (day.value / highest).toFloat() * size.height
                    if (barHeight > 0f) {
                        drawRoundRect(
                            color = if (day.value >= goal) reached else below,
                            topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    }
                }
                val goalY = size.height - (goal / highest).toFloat() * size.height
                drawLine(
                    goalColor.copy(alpha = 0.6f),
                    Offset(0f, goalY),
                    Offset(size.width, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                val weekday = DateTimeFormatter.ofPattern("EEE", Locale.US)
                data.forEachIndexed { index, day ->
                    val label = when {
                        !month -> weekday.format(day.date)
                        index % 7 == 0 -> day.date.dayOfMonth.toString()
                        else -> ""
                    }
                    Text(
                        label,
                        style = VFont.style(10f, 400, VColor.muted),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(ar: String, en: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(VColor.surface, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BiCaption(ar, en)
        Ltr { Text(value, style = VFont.style(20f, 600, color)) }
    }
}

@Composable
private fun WorkoutRowView(row: WorkoutRow) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(VColor.primarySoft, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(row.kind.icon, contentDescription = null, tint = VColor.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${row.nameAr} · ${row.nameEn}", style = VFont.style(14f, 500, VColor.ink))
            val date = rowDate.format(Instant.ofEpochMilli(row.date).atZone(ZoneId.systemDefault()))
            Text(
                "$date · ${row.minutes.toInt()} min · ${row.kcal.toInt()} kcal",
                style = VFont.style(11f, 400, VColor.muted),
            )
        }
        Text(
            row.source,
            style = VFont.style(10f, 400, VColor.inkSecondary),
            modifier = Modifier
                .background(VColor.ground, CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SuggestionRow(item: WorkoutSuggestion, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(VColor.surface, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.OndemandVideo, contentDescription = null, tint = Color(0xFFE02B2B), modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.titleAr, style = VFont.style(14f, 600, VColor.ink))
            Text(item.titleEn, style = VFont.style(12f, 400, VColor.inkSecondary))
            Text("${item.minutes.toInt()} min · ${item.intensity}", style = VFont.style(11f, 400, VColor.muted))
            Text(item.whyAr, style = VFont.style(12f, 400, VColor.inkSecondary))
            Text(item.whyEn, style = VFont.style(11f, 400, VColor.muted))
        }
    }
}

/** kcal ≈ (MET − 1) × kg × hours (active energy, excluding resting). */
private fun estimatedKcal(kind: WorkoutKind, weightKg: Double, minutes: Double): Double {
    val met = when (kind) {
        WorkoutKind.WALKING -> 3.8
        WorkoutKind.RUNNING -> 9.0
        WorkoutKind.CYCLING -> 7.0
        WorkoutKind.SWIMMING -> 7.0
        WorkoutKind.FOOTBALL -> 7.0
        WorkoutKind.STRENGTH -> 5.0
        WorkoutKind.HIIT -> 8.0
        WorkoutKind.YOGA -> 2.8
        WorkoutKind.OTHER -> 5.0
    }
    return Math.round((met - 1) * weightKg * minutes / 60).toDouble()
}

/** Manual workout entry; calories from MET values and current weight. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogWorkoutSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    val context = LocalContext.current

    var kind by remember { mutableStateOf(WorkoutKind.WALKING) }
    var minutes by remember { mutableDoubleStateOf(30.0) }
    var date by remember { mutableLongStateOf(nowMillis()) }

    val weightKg = app.health.weights.lastOrNull()?.value ?: state.profile?.startWeightKg ?: 80.0
    val kcal = estimatedKcal(kind, weightKg, minutes)

    fun save() {
        val entry = WorkoutEntry(
            date = date,
            nameAr = kind.ar,
            nameEn = kind.en,
            kindRaw = kind.raw,
            minutes = minutes,
            kcal = kcal,
        )
        app.store.update { it.upsertWorkout(entry) }
        val savedKind = kind
        app.scope.launch {
            val saved = app.health.saveWorkout(savedKind, Instant.ofEpochMilli(entry.date), entry.minutes, entry.kcal)
            if (saved) {
                app.store.update { s ->
                    s.copy(workouts = s.workouts.map { if (it.id == entry.id) it.copy(savedToHealth = true) else it })
                }
            }
            app.publishSnapshot()
        }
        onDismiss()
    }

    FullSheet(onDismiss) {
        SheetScaffold("سجّل تمرين", "Log workout", onClose = onDismiss) {
            Column(
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BiCaption("نوع التمرين", "Workout type")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WorkoutKind.entries.forEach { item ->
                                BiChip(item.ar, item.en, selected = kind == item) { kind = item }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BiText(
                                "المدة: ${minutes.toInt()} دقيقة",
                                "Duration: ${minutes.toInt()} min",
                                size = 14f,
                                weight = 500,
                                modifier = Modifier.weight(1f),
                            )
                            CircleIconButton(
                                Icons.Rounded.Remove,
                                "أقل — Less",
                                onClick = { minutes = max(5.0, minutes - 5) },
                                background = VColor.track,
                                tint = VColor.ink,
                                size = 36.dp,
                                enabled = minutes > 5,
                            )
                            Spacer(Modifier.size(8.dp))
                            CircleIconButton(
                                Icons.Rounded.Add,
                                "أكتر — More",
                                onClick = { minutes = min(300.0, minutes + 5) },
                                background = VColor.track,
                                tint = VColor.ink,
                                size = 36.dp,
                                enabled = minutes < 300,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BiCaption("بدأ", "Started", Modifier.weight(1f))
                            val current = date.toLocalDateTime()
                            SecondaryButton(onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, monthIndex, day ->
                                        date = LocalDateTime.of(LocalDate.of(year, monthIndex + 1, day), date.toLocalDateTime().toLocalTime()).toMillis()
                                    },
                                    current.year,
                                    current.monthValue - 1,
                                    current.dayOfMonth,
                                ).show()
                            }) {
                                Text(dayFormat.format(current), style = VFont.style(13f, 500, VColor.ink))
                            }
                            Spacer(Modifier.size(6.dp))
                            SecondaryButton(onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        date = LocalDateTime.of(date.toLocalDateTime().toLocalDate(), LocalTime.of(hour, minute)).toMillis()
                                    },
                                    current.hour,
                                    current.minute,
                                    false,
                                ).show()
                            }) {
                                Ltr { Text(timeFormat.format(current), style = VFont.style(13f, 500, VColor.ink)) }
                            }
                        }
                    }
                }
                Card(background = VColor.night) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BiText(
                            "سعرات تقديرية",
                            "Estimated kcal",
                            size = 14f,
                            color = VColor.nightText,
                            enColor = VColor.nightMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${kcal.toInt()}", style = VFont.style(28f, 700, VColor.mint))
                    }
                }
                PrimaryButton(onClick = { save() }) {
                    BiLabel("سجّل", "Log workout", icon = Icons.Rounded.Check)
                }
            }
        }
    }
}
