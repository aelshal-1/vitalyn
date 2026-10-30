package com.elshal.vitalyn.features.food

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.LocalDining
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.AITasks
import com.elshal.vitalyn.core.AnalyzedFood
import com.elshal.vitalyn.core.Confidence
import com.elshal.vitalyn.core.MealLog
import com.elshal.vitalyn.core.MealsPerDay
import com.elshal.vitalyn.core.PlanDay
import com.elshal.vitalyn.core.PlanPreset
import com.elshal.vitalyn.core.PlannedMeal
import com.elshal.vitalyn.core.WeekPlan
import com.elshal.vitalyn.core.GroceryList
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.MealPlanRecord
import com.elshal.vitalyn.data.PlanStatus
import com.elshal.vitalyn.data.startMillis
import com.elshal.vitalyn.data.toLocalDate
import com.elshal.vitalyn.services.AIJob
import com.elshal.vitalyn.services.DictationButton
import com.elshal.vitalyn.services.rememberSpeech
import com.elshal.vitalyn.services.userMessage
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiChip
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.ErrorBanner
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.PrimaryButton
import com.elshal.vitalyn.ui.SecondaryButton
import com.elshal.vitalyn.ui.SectionTitle
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import com.elshal.vitalyn.ui.WorkingView
import com.elshal.vitalyn.ui.shareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** When a new weekly plan begins. */
enum class PlanStart(val ar: String, val en: String) {
    TODAY("النهارده", "Today"),
    TOMORROW("بكرة", "Tomorrow"),
    SATURDAY("السبت الجاي", "Next Saturday");

    /** The plan's first day, as start-of-day epoch millis. */
    fun date(now: LocalDate = LocalDate.now()): Long = when (this) {
        TODAY -> now
        TOMORROW -> now.plusDays(1)
        SATURDAY -> now.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
    }.startMillis()
}

/** The planned meal as a single item, for logging it as eaten. */
val PlannedMeal.mealLog: MealLog
    get() = MealLog(
        titleAr = nameAr,
        titleEn = nameEn,
        items = listOf(
            AnalyzedFood(nameAr = nameAr, nameEn = nameEn, grams = 0.0, kcal = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG, confidence = Confidence.MEDIUM),
        ),
    )

private enum class PlanMode { DAY, WEEK }

private val arabicLocale: Locale = Locale.forLanguageTag("ar")
private val englishLocale: Locale = Locale.ENGLISH

/** iOS formats these with the plain "ar" locale, which writes Arabic-Indic digits. */
private val arabicDigits: DecimalStyle = DecimalStyle.of(arabicLocale)
private val arabicWeekdayDay = DateTimeFormatter.ofPattern("EEEE d", arabicLocale).withDecimalStyle(arabicDigits)
private val arabicWeekdayShort = DateTimeFormatter.ofPattern("EEE", arabicLocale)
private val englishWeekdayShort = DateTimeFormatter.ofPattern("EEE", englishLocale)
private val englishDay = DateTimeFormatter.ofPattern("d", englishLocale)

private fun rangeText(record: MealPlanRecord): String = PlanShareText.range(record)

private fun groceriesSubtitle(record: MealPlanRecord, plan: WeekPlan): String {
    val lines = GroceryList.build(plan)
    val left = lines.count { it.id !in record.checkedGroceries }
    return if (left == 0) "Grocery list · all bought" else "Grocery list · $left of ${lines.size} to buy"
}

/** The weekly meal plan on the Food screen. Lives inside the Food screen's scrolling column. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanSection() {
    val app = LocalApp.current
    val context = LocalContext.current
    val state by app.store.state.collectAsState()
    val speech = rememberSpeech()

    var preset by rememberSaveable { mutableStateOf(PlanPreset.EGYPTIAN_HOME) }
    var mealsPerDay by rememberSaveable { mutableStateOf(MealsPerDay.THREE_MEALS_TWO_SNACKS) }
    var start by rememberSaveable { mutableStateOf(PlanStart.TODAY) }
    var request by rememberSaveable { mutableStateOf("") }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(PlanMode.WEEK) }
    var dayIndex by rememberSaveable { mutableIntStateOf(0) }
    /** A past plan the user opened from history; null shows the latest. */
    var selectedID by rememberSaveable { mutableStateOf<String?>(null) }
    var swapping by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGroceries by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf<MealInput?>(null) }

    val plans = state.plans.sortedByDescending { it.createdAt }
    val readyPlans = plans.filter { it.status == PlanStatus.READY }
    val building = plans.firstOrNull { it.status == PlanStatus.BUILDING }
    /** A failure newer than the latest finished plan. */
    val failed = plans.firstOrNull { it.status == PlanStatus.FAILED }
        ?.takeIf { it.createdAt > (readyPlans.firstOrNull()?.createdAt ?: Long.MIN_VALUE) }
    val record = readyPlans.firstOrNull { it.id == selectedID } ?: readyPlans.firstOrNull()

    LaunchedEffect(record?.id) {
        record?.let { dayIndex = it.dayIndex(LocalDate.now()) ?: 0 }
    }

    fun removePlan(id: String) {
        app.store.update { s -> s.copy(plans = s.plans.filter { it.id != id }) }
    }

    /** Saves the plan as "building" and hands the request to the background job. */
    fun submit(newRecord: MealPlanRecord) {
        val userContext = AppData.userContext(app.store.value, app.health) ?: return
        error = null
        val saved = newRecord.withStatus(PlanStatus.BUILDING)
        app.store.update { it.upsertPlan(saved) }
        try {
            val aiRequest = AITasks.weekPlan(
                context = userContext,
                preset = saved.preset,
                mealsPerDay = saved.mealsPerDay,
                start = saved.startDate.toLocalDate(),
                request = saved.requestText,
            )
            app.jobs.submit(AIJob(kind = AIJob.Kind.weekPlan, target = saved.id), aiRequest, app.ai.model)
            showSetup = false
            selectedID = null
            mode = PlanMode.WEEK
        } catch (e: Exception) {
            removePlan(saved.id)
            error = e.userMessage()
        }
    }

    fun generate() {
        speech.stop()
        submit(
            MealPlanRecord(
                startDate = start.date(),
                presetRaw = preset.raw,
                mealsPerDayRaw = mealsPerDay.raw,
                requestText = request,
            ),
        )
    }

    fun retry(failedRecord: MealPlanRecord) {
        // Keep the original start date unless it has already passed.
        val newRecord = MealPlanRecord(
            startDate = maxOf(failedRecord.startDate, LocalDate.now().startMillis()),
            presetRaw = failedRecord.presetRaw,
            mealsPerDayRaw = failedRecord.mealsPerDayRaw,
            requestText = failedRecord.requestText,
        )
        removePlan(failedRecord.id)
        submit(newRecord)
    }

    /** Copies a past plan onto new dates, with a fresh grocery list. */
    fun reuse(past: MealPlanRecord, option: PlanStart) {
        val copy = MealPlanRecord(
            startDate = option.date(),
            presetRaw = past.presetRaw,
            mealsPerDayRaw = past.mealsPerDayRaw,
            requestText = past.requestText,
            plan = past.plan,
        )
        app.store.update { it.upsertPlan(copy) }
        selectedID = null
        mode = PlanMode.WEEK
    }

    fun swap(target: MealPlanRecord, day: PlanDay, mealIndex: Int) {
        val userContext = AppData.userContext(app.store.value, app.health) ?: return
        val plan = target.plan ?: return
        swapping = "${day.dayIndex}-$mealIndex"
        error = null
        val aiRequest = AITasks.swapMeal(context = userContext, meal = day.meals[mealIndex], ingredients = plan.ingredientNames, request = "")
        // Like the iOS Task: finishes even if the user leaves the screen.
        app.scope.launch {
            try {
                val replacement = app.ai.run(PlannedMeal.serializer(), aiRequest)
                app.store.update { s ->
                    s.updatePlan(target.id) { r ->
                        val current = r.plan
                        val position = current?.days?.indexOfFirst { it.dayIndex == day.dayIndex } ?: -1
                        if (current == null || position < 0 || mealIndex !in current.days[position].meals.indices) {
                            r
                        } else {
                            val days = current.days.mapIndexed { i, d ->
                                if (i == position) d.copy(meals = d.meals.mapIndexed { m, meal -> if (m == mealIndex) replacement else meal }) else d
                            }
                            r.copy(plan = current.copy(days = days))
                        }
                    }
                }
            } catch (e: Exception) {
                error = e.userMessage()
            }
            swapping = null
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (building != null) {
            BuildingCard(building)
        } else if ((record == null && failed == null) || showSetup) {
            // Setup
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle(ar = "جدول أكل الأسبوع", en = "This week’s meal plan")
                    BiText(
                        ar = "7 أيام أكل على قد احتياجك، وقائمة مشتريات واحدة متجمعة للأسبوع كله.",
                        en = "Seven days of meals sized to your targets, with one combined grocery list for the week.",
                        size = 13f, weight = 400, color = VColor.inkSecondary,
                    )

                    BiCaption(ar = "نوع الأكل", en = "Food style")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanPreset.entries.forEach { item ->
                            BiChip(ar = item.ar, en = item.en, selected = preset == item, dark = true) {
                                if (preset != item && item == PlanPreset.INTERMITTENT_FASTING) mealsPerDay = MealsPerDay.TWO_MEALS_ONE_SNACK
                                preset = item
                            }
                        }
                    }
                    if (preset != PlanPreset.RAMADAN) {
                        BiCaption(ar = "الوجبات في اليوم", en = "Meals a day")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MealsPerDay.entries.forEach { item ->
                                BiChip(ar = item.ar, en = item.en, selected = mealsPerDay == item, dark = true) { mealsPerDay = item }
                            }
                        }
                    }
                    BiCaption(ar = "يبدأ من", en = "Starts")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanStart.entries.forEach { item ->
                            BiChip(ar = item.ar, en = item.en, selected = start == item, dark = true) { start = item }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicTextField(
                            value = request,
                            onValueChange = { request = it },
                            minLines = 1,
                            maxLines = 4,
                            textStyle = VFont.style(14f, 400, VColor.ink),
                            cursorBrush = SolidColor(VColor.primary),
                            modifier = Modifier
                                .weight(1f)
                                .background(VColor.ground, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (request.isEmpty()) {
                                        Text(
                                            "طلب إضافي (اختياري) · Extra request — e.g. fish on Friday, cook twice a week",
                                            style = VFont.style(14f, 400, VColor.muted),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        DictationButton(speech = speech, onText = { request = it })
                    }
                    PrimaryButton(onClick = { generate() }) {
                        BiLabel(
                            ar = if (record == null) "اعمل جدول الأسبوع" else "اعمل جدول جديد",
                            en = if (record == null) "Create this week’s plan" else "Create a new plan",
                            icon = Icons.Rounded.AutoAwesome,
                        )
                    }
                    if (record != null) {
                        SecondaryButton(onClick = { showSetup = false }, modifier = Modifier.fillMaxWidth()) {
                            BiLabel(ar = "إلغاء", en = "Cancel", size = 14f)
                        }
                    }
                }
            }
        }
        if (failed != null && building == null) {
            // Failure + retry
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ErrorBanner(message = failed.errorMessage.ifEmpty { "معرفناش نعمل الجدول · Couldn't make the plan" })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(onClick = { retry(failed) }, modifier = Modifier.weight(1f)) {
                        BiLabel(ar = "جرّب تاني", en = "Try again", icon = Icons.Rounded.Refresh, size = 14f)
                    }
                    SecondaryButton(onClick = { removePlan(failed.id) }, modifier = Modifier.weight(1f)) {
                        BiLabel(ar = "شيله", en = "Dismiss", size = 14f)
                    }
                }
            }
        }
        error?.let { ErrorBanner(message = it) }

        val plan = record?.plan
        if (record != null && plan != null) {
            if (record.id != readyPlans.firstOrNull()?.id) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(VColor.primarySoft, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BiText(
                        ar = "ده جدول قديم", en = "You're viewing a past plan", size = 13f, weight = 500, color = VColor.inkSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    PlanLinkButton("ارجع للحالي · Back to current", onClick = { selectedID = null })
                }
            }

            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BiText(ar = "جدول الأسبوع", en = "Weekly plan", size = 18f)
                    Ltr { Text(rangeText(record), style = VFont.style(12f, 400, VColor.muted)) }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShareMenu(record, plan, if (mode == PlanMode.DAY) dayIndex else (record.dayIndex(LocalDate.now()) ?: dayIndex))
                    if (!showSetup && building == null) {
                        PlanLinkButton("جدول جديد · New plan", icon = Icons.Rounded.Refresh, onClick = { showSetup = true })
                    }
                }
            }

            PrimaryButton(onClick = { showGroceries = true }) {
                BiLabel(ar = "قائمة المشتريات", en = groceriesSubtitle(record, plan), icon = Icons.Rounded.ShoppingCart)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BiChip(ar = "الأسبوع كله", en = "Whole week", selected = mode == PlanMode.WEEK, dark = true) { mode = PlanMode.WEEK }
                BiChip(ar = "يوم بيوم", en = "Day by day", selected = mode == PlanMode.DAY, dark = true) { mode = PlanMode.DAY }
            }

            when (mode) {
                PlanMode.WEEK -> WeekOverview(record, plan) { index ->
                    dayIndex = index
                    mode = PlanMode.DAY
                }
                PlanMode.DAY -> {
                    DayPicker(record, dayIndex) { dayIndex = it }
                    val day = plan.days.firstOrNull { it.dayIndex == dayIndex } ?: plan.days.firstOrNull()
                    if (day != null) {
                        val totals = day.totals
                        Ltr {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(VColor.surface, RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${VFormat.number(totals.calories)} kcal", style = VFont.style(13f, 500, VColor.ink))
                                Spacer(Modifier.weight(1f))
                                Text("P ${totals.proteinGrams} g", style = VFont.style(13f, 500, VColor.primaryDark))
                                Spacer(Modifier.weight(1f))
                                Text("C ${totals.carbsGrams} g", style = VFont.style(13f, 500, VColor.inkSecondary))
                                Spacer(Modifier.weight(1f))
                                Text("F ${totals.fatGrams} g", style = VFont.style(13f, 500, VColor.inkSecondary))
                            }
                        }
                        day.meals.forEachIndexed { index, meal ->
                            val key = "${day.dayIndex}-$index"
                            PlannedMealCard(
                                meal = meal,
                                swapping = swapping == key,
                                onSwap = { swap(record, day, index) },
                                onLog = { logging = MealInput.Prefilled(meal.mealLog, meal.slot) },
                            )
                        }
                    }
                }
            }

            if (plan.prepAr.isNotEmpty()) {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.LocalDining, contentDescription = null, tint = VColor.primary, modifier = Modifier.size(18.dp))
                            BiCaption(ar = "خطة الطبخ", en = "Cooking plan", size = 13f, color = VColor.primaryDark)
                        }
                        BiText(ar = plan.prepAr, en = plan.prepEn, size = 13f, weight = 400, color = VColor.inkSecondary)
                    }
                }
            }
            if (plan.notesAr.isNotEmpty()) {
                BiText(ar = plan.notesAr, en = plan.notesEn, size = 13f, weight = 400, color = VColor.inkSecondary)
            }
        }

        if (readyPlans.size > 1) {
            // History: earlier plans, to look back at or cook again on new dates without asking Claude.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BiText(ar = "الجداول اللي فاتت", en = "Past plans", size = 16f)
                readyPlans.filter { it.id != record?.id }.forEach { past ->
                    HistoryRow(
                        past = past,
                        onView = { selectedID = past.id },
                        onReuse = { option -> reuse(past, option) },
                        onDelete = { removePlan(past.id) },
                    )
                }
            }
        }
    }

    logging?.let { input -> MealCaptureSheet(input = input, onDismiss = { logging = null }) }
    if (showGroceries && record != null) {
        GroceriesSheet(recordId = record.id, onDismiss = { showGroceries = false })
    }
}

@Composable
private fun BuildingCard(record: MealPlanRecord) {
    val app = LocalApp.current
    val written = minOf(app.jobs.status.planMeals[record.id] ?: 0, record.mealCount)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(record.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = maxOf(0L, (now - record.createdAt) / 1000).toInt()
    val clock = String.format(Locale.US, "%d:%02d", elapsed / 60, elapsed % 60)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (written > 0) {
            WorkingView(
                ar = "بكتب جدول الأسبوع… $written من ${record.mealCount} وجبة · $clock",
                en = "Writing your weekly plan… $written of ${record.mealCount} meals · $clock",
            )
        } else {
            WorkingView(
                ar = "بكتب جدول الأسبوع… $clock · ممكن ياخد كام دقيقة",
                en = "Writing your weekly plan… $clock · this can take a few minutes",
            )
        }
        BiText(
            ar = "تقدر تقفل التطبيق أو تستخدم حاجة تانية — هبعتلك إشعار لما يجهز. بس متقفلوش من قائمة التطبيقات.",
            en = "You can leave the app or use something else — you’ll get a notification when it’s ready. Just don’t swipe it away in the app switcher.",
            size = 12f, weight = 400, color = VColor.muted,
        )
    }
}

/** Sends the plan as text (WhatsApp, Messages, Notes…): the week's menu, one day with recipes, or what's left to buy. */
@Composable
private fun ShareMenu(record: MealPlanRecord, plan: WeekPlan, shownDay: Int) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val day = plan.days.firstOrNull { it.dayIndex == shownDay } ?: plan.days.firstOrNull()
    Box {
        PlanLinkButton("شارك · Share", icon = Icons.Rounded.Share, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("الأسبوع كله · Whole week", style = VFont.style(14f)) },
                leadingIcon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null) },
                onClick = {
                    expanded = false
                    shareText(context, PlanShareText.week(record, plan))
                },
            )
            if (day != null) {
                DropdownMenuItem(
                    text = { Text("${PlanShareText.dayTitle(record, day.dayIndex)} بالوصفات · with recipes", style = VFont.style(14f)) },
                    leadingIcon = { Icon(Icons.Rounded.Restaurant, contentDescription = null) },
                    onClick = {
                        expanded = false
                        shareText(context, PlanShareText.day(record, day))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("قائمة المشتريات · Grocery list", style = VFont.style(14f)) },
                leadingIcon = { Icon(Icons.Rounded.ShoppingCart, contentDescription = null) },
                onClick = {
                    expanded = false
                    shareText(context, PlanShareText.groceries(GroceryList.build(plan), record.checkedGroceries.toSet()))
                },
            )
        }
    }
}

@Composable
private fun HistoryRow(past: MealPlanRecord, onView: () -> Unit, onReuse: (PlanStart) -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(VColor.surface, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Ltr { Text(rangeText(past), style = VFont.style(14f, 600, VColor.ink)) }
            Text("${past.preset.ar} · ${past.preset.en}", style = VFont.style(11f, 400, VColor.muted))
        }
        PlanLinkButton("اعرضه · View", onClick = onView)
        Box {
            PlanLinkButton("استخدمه تاني · Reuse", onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                PlanStart.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("ابدأه ${option.ar} · Start ${option.en.lowercase()}", style = VFont.style(14f)) },
                        onClick = {
                            menu = false
                            onReuse(option)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("امسحه · Delete", style = VFont.style(14f, 400, VColor.energy)) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekOverview(record: MealPlanRecord, plan: WeekPlan, onOpenDay: (Int) -> Unit) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        plan.days.sortedBy { it.dayIndex }.forEach { day ->
            val date = record.date(day.dayIndex)
            val totals = day.totals
            val isToday = date == today
            val shape = RoundedCornerShape(20.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(VColor.surface, shape)
                    .border(1.5.dp, if (isToday) VColor.primary else Color.Transparent, shape)
                    .clickable(role = Role.Button) { onOpenDay(day.dayIndex) }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(date.format(arabicWeekdayDay), style = VFont.style(15f, 600, VColor.ink), modifier = Modifier.alignByBaseline())
                    Text(date.format(englishWeekdayShort), style = VFont.style(11f, 400, VColor.muted), modifier = Modifier.alignByBaseline())
                    if (isToday) {
                        Text(
                            "النهارده · Today",
                            style = VFont.style(10f, 600, VColor.primaryDark),
                            modifier = Modifier
                                .alignByBaseline()
                                .background(VColor.primarySoft, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val baseline = Modifier.alignByBaseline()
                    Ltr {
                        Text(
                            "${VFormat.number(totals.calories)} kcal · P ${totals.proteinGrams} g",
                            style = VFont.style(11f, 500, VColor.inkSecondary),
                            modifier = baseline,
                        )
                    }
                }
                day.meals.forEach { meal ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(meal.slot.ar, style = VFont.style(11f, 400, VColor.muted), modifier = Modifier.alignByBaseline().width(40.dp))
                        Column(Modifier.alignByBaseline().weight(1f)) {
                            Text(meal.nameAr, style = VFont.style(13f, 400, VColor.ink))
                            Text(meal.nameEn, style = VFont.style(11f, 400, VColor.muted))
                        }
                        Text("${meal.kcal.toInt()}", style = VFont.style(11f, 400, VColor.muted), modifier = Modifier.alignByBaseline())
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPicker(record: MealPlanRecord, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (0 until 7).forEach { index ->
            val date = record.date(index)
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(16.dp)
            val fg = if (selected) Color.White else VColor.ink
            Column(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 66.dp)
                    .clip(shape)
                    .background(if (selected) VColor.primary else VColor.surface, shape)
                    .border(1.dp, if (date == today && !selected) VColor.primary else Color.Transparent, shape)
                    .clickable(role = Role.Button) { onSelect(index) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            ) {
                Text(date.format(arabicWeekdayShort), style = VFont.style(10f, 400, fg), maxLines = 1, textAlign = TextAlign.Center)
                Text(date.format(englishWeekdayShort), style = VFont.style(9f, 400, fg), modifier = Modifier.alpha(0.85f), maxLines = 1)
                Text(date.format(englishDay), style = VFont.style(16f, 600, fg))
            }
        }
    }
}

/** One meal of the plan's day view: swap it for another, open its ingredients and method, or log it. */
@Composable
fun PlannedMealCard(meal: PlannedMeal, swapping: Boolean, onSwap: () -> Unit, onLog: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(VColor.surface, RoundedCornerShape(20.dp))
            .animateContentSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${meal.slot.ar} · ${meal.slot.en} · ${meal.kcal.toInt()} kcal · P ${meal.proteinG.toInt()} g",
                    style = VFont.style(11f, 400, VColor.muted),
                )
                Text(meal.nameAr, style = VFont.style(14f, 500, VColor.ink))
                Text(meal.nameEn, style = VFont.style(12f, 400, VColor.inkSecondary))
            }
            if (swapping) {
                CircularProgressIndicator(color = VColor.primary, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
            } else {
                Column(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VColor.ground)
                        .clickable(role = Role.Button, onClick = onSwap)
                        .semantics { contentDescription = "بدّل — Swap" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                ) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = VColor.primary, modifier = Modifier.size(18.dp))
                    Text("بدّل", style = VFont.style(11f, 400, VColor.ink))
                    Text("Swap", style = VFont.style(9f, 400, VColor.muted))
                }
            }
        }
        if (expanded) {
            if (meal.ingredients.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(VColor.ground, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    meal.ingredients.forEach { ingredient ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${ingredient.nameAr} · ${ingredient.nameEn}",
                                style = VFont.style(12f, 400, VColor.inkSecondary),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Ltr { Text(ingredient.amountText, style = VFont.style(12f, 600, VColor.ink)) }
                        }
                    }
                }
            }
            Text(meal.recipeAr, style = VFont.style(12f, 400, VColor.inkSecondary))
            Text(meal.recipeEn, style = VFont.style(11f, 400, VColor.muted))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlanLinkButton(
                if (expanded) "اخفي · Hide" else "المكونات والطريقة · Ingredients & method",
                onClick = { expanded = !expanded },
            )
            Spacer(Modifier.weight(1f))
            PlanLinkButton("سجّلها · Log it", onClick = onLog)
        }
    }
}

/** Today's meals from the weekly plan on the Today screen, or a way into planning when there are none. */
@Composable
fun TodayPlanCard(openPlan: () -> Unit) {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    var logging by remember { mutableStateOf<MealInput?>(null) }

    val plans = state.plans.sortedByDescending { it.createdAt }
    val today = LocalDate.now()
    /** The newest finished plan that covers today. */
    val current = plans.firstOrNull { it.status == PlanStatus.READY && it.dayIndex(today) != null }
    val todayIndex = current?.dayIndex(today)
    val day = current?.plan?.days?.firstOrNull { it.dayIndex == todayIndex }

    if (day != null && day.meals.isNotEmpty()) {
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BiText(ar = "أكل النهارده من الجدول", en = "Today’s planned meals", size = 15f, modifier = Modifier.weight(1f))
                    PlanLinkButton("الأسبوع · Week", onClick = openPlan)
                }
                day.meals.forEach { meal ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("${meal.slot.ar} · ${meal.slot.en} · ${meal.kcal.toInt()} kcal", style = VFont.style(11f, 400, VColor.muted))
                            Text(meal.nameAr, style = VFont.style(13f, 500, VColor.ink))
                            Text(meal.nameEn, style = VFont.style(11f, 400, VColor.inkSecondary))
                        }
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { logging = MealInput.Prefilled(meal.mealLog, meal.slot) }
                                .semantics { contentDescription = "سجّلها — Log it" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.AddCircle, contentDescription = null, tint = VColor.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    } else {
        val prompt = when {
            plans.any { it.status == PlanStatus.BUILDING } ->
                "جدول الأكل بيتعمل… هنبعتلك إشعار لما يجهز" to "Your meal plan is being made… we’ll notify you"
            plans.any { it.status == PlanStatus.READY && it.startDate > System.currentTimeMillis() } ->
                "جدول الأكل بيبدأ قريب" to "Your meal plan starts soon"
            else -> "خطط أكل الأسبوع وقائمة المشتريات" to "Plan this week’s meals and grocery list"
        }
        val shape = RoundedCornerShape(20.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(VColor.surface, shape)
                .clickable(role = Role.Button, onClick = openPlan)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = VColor.primary, modifier = Modifier.size(22.dp))
            BiText(ar = prompt.first, en = prompt.second, size = 14f, weight = 500, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = VColor.muted)
        }
    }

    logging?.let { input -> MealCaptureSheet(input = input, onDismiss = { logging = null }) }
}

/** A text button in the accent color (SwiftUI's plain `Button("…")`), optionally with an icon. */
@Composable
private fun PlanLinkButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    size: Float = 12f,
    weight: Int = 600,
    color: Color = VColor.primary,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size((size + 4).dp))
        Text(text, style = VFont.style(size, weight, color))
    }
}
