package com.elshal.vitalyn.features.today

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.AITasks
import com.elshal.vitalyn.core.DailyTip
import com.elshal.vitalyn.core.MacroTargets
import com.elshal.vitalyn.core.Portions
import com.elshal.vitalyn.core.SampleData
import com.elshal.vitalyn.core.VitalynJson
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.MealEntry
import com.elshal.vitalyn.data.WaterStore
import com.elshal.vitalyn.data.shortTime
import com.elshal.vitalyn.data.startMillis
import com.elshal.vitalyn.features.activity.ActivityScreen
import com.elshal.vitalyn.features.food.MealCaptureSheet
import com.elshal.vitalyn.features.food.MealInput
import com.elshal.vitalyn.features.food.TodayPlanCard
import com.elshal.vitalyn.services.userMessage
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.LocalNavigator
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.MacroBar
import com.elshal.vitalyn.ui.PrimaryButton
import com.elshal.vitalyn.ui.ProgressRing
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/** The Today tab: calories, activity, the coach's tip, today's plan and meals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(openProfile: () -> Unit, openPlan: () -> Unit) {
    val app = LocalApp.current
    val health = app.health
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by app.store.state.collectAsState()

    var input by remember { mutableStateOf<MealInput?>(null) }
    var tip by remember { mutableStateOf<DailyTip?>(null) }
    var loadingTip by remember { mutableStateOf(false) }
    var tipError by remember { mutableStateOf<String?>(null) }
    var water by remember { mutableIntStateOf(WaterStore.glasses(app.prefs)) }
    var refreshing by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val start = today.startMillis()
    val profile = state.profile
    val targets: MacroTargets = profile?.let { AppData.targets(it, state.sortedScans, health) } ?: SampleData.targets
    val todayMeals = state.meals.filter { it.date >= start }.sortedBy { it.date }
    val eaten = Portions.total(todayMeals.flatMap { meal -> meal.sortedItems.map { it.analyzed } })
    val workoutMinutes = run {
        val manual = state.workouts.filter { it.date >= start && !it.savedToHealth }.sumOf { it.minutes }
        val fromHealth = health.workouts
            .filter { it.start.atZone(ZoneId.systemDefault()).toLocalDate() == today }
            .sumOf { it.minutes }
        (manual + fromHealth).toInt()
    }
    val tipKey = "tip-${start / 1000}"

    fun fetchTip() {
        val userContext = AppData.userContext(app.store.value, health) ?: return
        loadingTip = true
        tipError = null
        scope.launch {
            try {
                val result = app.ai.run(DailyTip.serializer(), AITasks.dailyTip(userContext))
                tip = result
                app.prefs.setString(tipKey, VitalynJson.encodeToString(DailyTip.serializer(), result))
            } catch (error: Exception) {
                tipError = error.userMessage()
            }
            loadingTip = false
        }
    }

    LaunchedEffect(Unit) {
        water = WaterStore.glasses(app.prefs)
        if (tip == null) {
            tip = app.prefs.string(tipKey)?.let {
                runCatching { VitalynJson.decodeFromString(DailyTip.serializer(), it) }.getOrNull()
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                health.refresh()
                water = WaterStore.glasses(app.prefs)
                app.publishSnapshot()
                refreshing = false
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(VColor.ground),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(profileName = profile?.name.orEmpty(), openProfile = openProfile)
                CaloriesCard(targets = targets, eaten = eaten, activeKcal = health.activeKcalToday)

                // Activity
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(
                        icon = Icons.AutoMirrored.Rounded.DirectionsWalk, tint = VColor.primary,
                        value = VFormat.number(health.stepsToday), ar = "خطوة", en = "Steps",
                        modifier = Modifier.weight(1f),
                    ) { navigator.push { ActivityScreen() } }
                    Tile(
                        icon = Icons.Rounded.LocalFireDepartment, tint = VColor.energy,
                        value = "$workoutMinutes", ar = "دقيقة تمرين", en = "Workout min",
                        modifier = Modifier.weight(1f),
                    ) { navigator.push { ActivityScreen() } }
                    Tile(
                        icon = Icons.Rounded.WaterDrop, tint = VColor.water,
                        value = "$water/${profile?.waterGoal ?: 8}", ar = "مية · دوس +1", en = "Water · tap +1",
                        modifier = Modifier.weight(1f),
                    ) {
                        water = WaterStore.add(app.prefs, 1)
                        app.scope.launch { health.saveWater(1, java.time.Instant.now()) }
                        app.publishSnapshot()
                    }
                }

                CoachCard(
                    tip = tip ?: fallbackTip(targets, eaten),
                    tipError = tipError,
                    loadingTip = loadingTip,
                    logMeal = { input = MealInput.Text },
                    suggest = { fetchTip() },
                )

                TodayPlanCard(openPlan = openPlan)

                // Meals
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BiText(ar = "وجبات النهارده", en = "Today’s meals")
                    if (todayMeals.isEmpty()) {
                        BiText(ar = "لسه مسجلتش أكل النهارده.", en = "No meals logged today yet.", size = 13f, weight = 400, color = VColor.inkSecondary)
                    }
                    todayMeals.forEach { meal -> TodayMealRow(meal) }
                }

                PrimaryButton(onClick = { input = MealInput.Photo }) {
                    BiLabel(ar = "صوّر وجبة", en = "Snap a meal", icon = Icons.Rounded.PhotoCamera, size = 16f)
                }
            }
        }
    }

    input?.let { current -> MealCaptureSheet(input = current, onDismiss = { input = null }) }
}

// Header

@Composable
private fun Header(profileName: String, openProfile: () -> Unit) {
    val now = LocalDate.now()
    val arDate = DateTimeFormatter.ofPattern("EEEE، d MMMM", Locale.forLanguageTag("ar")).format(now)
    val enDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US).format(now)
    val (greetingAr, greetingEn) = when (LocalTime.now().hour) {
        in 5 until 12 -> "صباح الخير" to "Good morning"
        in 12 until 18 -> "مساء الخير" to "Good afternoon"
        else -> "مساء النور" to "Good evening"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BiCaption(ar = arDate, en = enDate)
            BiText(
                ar = greetingAr + if (profileName.isNotEmpty()) "، $profileName" else "",
                en = greetingEn,
                size = 26f,
                enColor = VColor.inkSecondary,
                enSize = 15f,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .background(VColor.surface, CircleShape)
                .border(1.dp, VColor.line, CircleShape)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = openProfile)
                .semantics { contentDescription = "الملف الشخصي — Profile" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = VColor.ink, modifier = Modifier.size(22.dp))
        }
    }
}

// Calories

@Composable
private fun CaloriesCard(targets: MacroTargets, eaten: MacroTargets, activeKcal: Int) {
    val left = max(0, targets.calories - eaten.calories)
    val progress = if (targets.calories > 0) eaten.calories.toDouble() / targets.calories else 0.0
    val over = eaten.calories > targets.calories
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BiText(ar = "سعرات النهارده", en = "Calories today", modifier = Modifier.weight(1f))
                BiCaption(
                    ar = if (over) "عديت الهدف" else "ماشي صح",
                    en = if (over) "Over target" else "On track",
                    color = if (over) VColor.energy else VColor.primaryDark,
                    modifier = Modifier
                        .background(if (over) Color(0xFFFDE4DC) else VColor.primarySoft, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                    ProgressRing(progress = progress, color = if (over) VColor.energy else VColor.primary, modifier = Modifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(VFormat.number(left), style = VFont.style(38f, 600, VColor.ink))
                        BiText(
                            ar = "سعرة باقية", en = "kcal left", size = 12f, weight = 400,
                            color = VColor.inkSecondary, enSize = 11f, alignment = Alignment.CenterHorizontally,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(ar = "الهدف", en = "Target", value = targets.calories, color = VColor.ink)
                    Stat(ar = "أكلت", en = "Eaten", value = eaten.calories, color = VColor.ink)
                    Stat(ar = "نشاط", en = "Active", value = activeKcal, color = VColor.energy)
                }
            }
            HorizontalDivider(color = VColor.track)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MacroBar(ar = "بروتين", en = "Protein", value = eaten.proteinGrams, goal = targets.proteinGrams, color = VColor.protein)
                MacroBar(ar = "كارب", en = "Carbs", value = eaten.carbsGrams, goal = targets.carbsGrams, color = VColor.carbs)
                MacroBar(ar = "دهون", en = "Fat", value = eaten.fatGrams, goal = targets.fatGrams, color = VColor.fat)
            }
        }
    }
}

@Composable
private fun Stat(ar: String, en: String, value: Int, color: Color) {
    Column(
        Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        BiCaption(ar = ar, en = en)
        Text(VFormat.number(value), style = VFont.style(18f, 600, color))
    }
}

// Activity

@Composable
private fun Tile(
    icon: ImageVector,
    tint: Color,
    value: String,
    ar: String,
    en: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxHeight()
            .background(VColor.surface, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Ltr { Text(value, style = VFont.style(20f, 600, VColor.ink), maxLines = 1) }
        BiText(ar = ar, en = en, size = 12f, weight = 400, color = VColor.inkSecondary, enSize = 11f)
    }
}

// Coach tip

private fun fallbackTip(targets: MacroTargets, eaten: MacroTargets): DailyTip {
    val protein = max(0, targets.proteinGrams - eaten.proteinGrams)
    val kcal = max(0, targets.calories - eaten.calories)
    if (eaten.calories == 0) {
        return DailyTip(
            tipAr = "ابدأ يومك بفطار فيه بروتين: 2 بيض + جبنة قريش + نص رغيف بلدي (حوالي 400 سعرة).",
            tipEn = "Start with a protein breakfast: 2 eggs, cottage cheese and half a baladi loaf (about 400 kcal).",
        )
    }
    return DailyTip(
        tipAr = "فاضلك $protein جم بروتين و$kcal سعرة. وجبة زي 200 جم فراخ مشوية + سلطة بتقفل أغلب البروتين.",
        tipEn = "$protein g protein and $kcal kcal left. Something like 200 g grilled chicken with salad closes most of the protein gap.",
    )
}

@Composable
private fun CoachCard(
    tip: DailyTip,
    tipError: String?,
    loadingTip: Boolean,
    logMeal: () -> Unit,
    suggest: () -> Unit,
) {
    Card(background = VColor.night, padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = VColor.mint, modifier = Modifier.size(18.dp))
                BiCaption(ar = "مدرب Vitalyn", en = "Vitalyn Coach", size = 13f, color = VColor.mint)
            }
            Text(tip.tipAr, style = VFont.style(15f, 400, VColor.nightText).copy(lineHeight = 24.sp))
            Text(tip.tipEn, style = VFont.style(13f, 400, VColor.nightMuted).copy(lineHeight = 20.sp))
            if (tipError != null) Text(tipError, style = VFont.style(11f, 400, Color(0xFFFFB4A0)))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .background(VColor.mint, CircleShape)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = logMeal)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BiText(
                        ar = "سجّل أكلة", en = "Log a meal", size = 14f, color = VColor.night,
                        enColor = VColor.night, enSize = 10f, alignment = Alignment.CenterHorizontally,
                    )
                }
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .border(1.dp, VColor.nightLine, CircleShape)
                        .clip(CircleShape)
                        .clickable(enabled = !loadingTip, role = Role.Button, onClick = suggest)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loadingTip) {
                        CircularProgressIndicator(color = VColor.nightText, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        BiText(
                            ar = "اقترح عليا", en = "Suggest for me", size = 14f, weight = 400, color = VColor.nightText,
                            enColor = VColor.nightMuted, enSize = 10f, alignment = Alignment.CenterHorizontally,
                        )
                    }
                }
            }
        }
    }
}

// Meals

/** A logged meal: photo or icon, slot and time, calories and what was in it (the iOS `MealRow`). */
@Composable
private fun TodayMealRow(meal: MealEntry) {
    val app = LocalApp.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, meal.photo) {
        value = withContext(Dispatchers.IO) { runCatching { app.store.blobBitmap(meal.photo, 160) }.getOrNull() }
    }
    val total = meal.totals
    val thumbShape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(VColor.surface, RoundedCornerShape(20.dp))
            .semantics(mergeDescendants = true) {}
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(thumbShape)
                .background(VColor.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            val image = thumbnail
            if (image != null) {
                Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = VColor.ink)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(meal.slot.ar, style = VFont.style(14f, 600, VColor.ink))
                Text("${meal.slot.en} · ${meal.date.shortTime()}", style = VFont.style(11f, 400, VColor.muted))
                Spacer(Modifier.weight(1f))
                Ltr { Text("${total.calories} kcal", style = VFont.style(14f, 600, VColor.ink)) }
            }
            Text(
                meal.titleAr.ifEmpty { meal.sortedItems.joinToString(" + ") { it.nameAr } },
                style = VFont.style(12f, 400, VColor.inkSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meal.titleEn.ifEmpty { meal.sortedItems.joinToString(", ") { it.nameEn } },
                style = VFont.style(11f, 400, VColor.muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
