package com.elshal.vitalyn.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.AITasks
import com.elshal.vitalyn.core.ActivityLevel
import com.elshal.vitalyn.core.ChronicCondition
import com.elshal.vitalyn.core.HealthItemInsight
import com.elshal.vitalyn.core.HealthItemInsights
import com.elshal.vitalyn.core.IdealWeight
import com.elshal.vitalyn.core.NutritionCalculator
import com.elshal.vitalyn.core.ProfileList
import com.elshal.vitalyn.core.Sex
import com.elshal.vitalyn.core.idealWeight
import com.elshal.vitalyn.core.paceOptions
import com.elshal.vitalyn.core.weeksToGoal
import com.elshal.vitalyn.data.UserProfile
import com.elshal.vitalyn.services.userMessage
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiChip
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.CircleIconButton
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.NumberField
import com.elshal.vitalyn.ui.SecondaryButton
import com.elshal.vitalyn.ui.SectionTitle
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

// Profile sections shared by onboarding (editing a local draft) and the profile screen (editing the stored profile).
// Each takes the profile and reports a changed copy through `onChange`.

@Composable
fun BasicsSection(profile: UserProfile, onChange: (UserProfile) -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle(ar = "بياناتك", en = "About you")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BiCaption(ar = "الاسم", en = "Name")
                PlainTextField(
                    value = profile.name,
                    onValueChange = { onChange(profile.copy(name = it)) },
                    placeholder = "الاسم · Name",
                    fontSize = 16f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VColor.ground, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BiChip(ar = "راجل", en = "Male", selected = profile.sex == Sex.MALE) { onChange(profile.withSex(Sex.MALE)) }
                BiChip(ar = "ست", en = "Female", selected = profile.sex == Sex.FEMALE) { onChange(profile.withSex(Sex.FEMALE)) }
            }
            BirthYearField(year = profile.birthYear, onYearChange = { onChange(profile.copy(birthYear = it)) })
            NumberField(ar = "الطول", en = "Height", value = profile.heightCm, onValueChange = { onChange(profile.copy(heightCm = it)) }, unit = "cm", digits = 0)
            NumberField(ar = "الوزن دلوقتي", en = "Current weight", value = profile.startWeightKg, onValueChange = { onChange(profile.copy(startWeightKg = it)) }, unit = "kg")
        }
    }
}

@Composable
fun BirthYearField(year: Int, onYearChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BiText(ar = "سنة الميلاد", en = "Birth year", size = 14f, weight = 500, modifier = Modifier.weight(1f))
        Box {
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .background(VColor.ground, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { expanded = true }
                    .semantics { contentDescription = "سنة الميلاد — Birth year" }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (year >= 1930) year.toString() else "اختار · Choose", style = VFont.style(16f, 600, VColor.ink))
                Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = VColor.ink, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                (2012 downTo 1930).forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.toString(),
                                style = VFont.style(16f, if (option == year) 600 else 400, if (option == year) VColor.primary else VColor.ink),
                            )
                        },
                        onClick = {
                            onYearChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun GoalSection(
    profile: UserProfile,
    onChange: (UserProfile) -> Unit,
    currentWeightKg: Double? = null,
    bodyFatPercent: Double? = null,
) {
    val weight = currentWeightKg ?: profile.startWeightKg
    val ideal: IdealWeight? =
        if (profile.heightCm >= 120 && weight >= 30 && profile.birthYear >= 1930) {
            NutritionCalculator.idealWeight(
                heightCm = profile.heightCm,
                ageYears = profile.ageYears,
                sex = profile.sex,
                currentWeightKg = weight,
                bodyFatPercent = bodyFatPercent,
            )
        } else {
            null
        }

    Card {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle(ar = "هدفك", en = "Your goal")
            if (ideal != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(VColor.primarySoft, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BiText(ar = "وزنك المثالي", en = "Your ideal weight", size = 14f, weight = 500, color = VColor.primaryDark, modifier = Modifier.weight(1f))
                        Ltr {
                            Text("${VFormat.decimal(ideal.goalKg)} kg", style = VFont.style(22f, 700, VColor.primaryDark))
                        }
                    }
                    val low = VFormat.decimal(ideal.healthyLowKg)
                    val high = VFormat.decimal(ideal.healthyHighKg)
                    Text(
                        "المدى الصحي لطولك $low–$high كجم · Healthy range for your height $low–$high kg",
                        style = VFont.style(11f, 400, VColor.inkSecondary),
                    )
                    if (ideal.alreadyThere) {
                        BiText(
                            ar = "وزنك دلوقتي في المدى الصحي — ممكن تركز على الدهون والعضلات.",
                            en = "You're already in the healthy range — focus on fat and muscle.",
                            size = 12f, weight = 400, color = VColor.inkSecondary,
                        )
                    }
                    if (profile.goalWeightKg != ideal.goalKg) {
                        SecondaryButton(onClick = { onChange(profile.copy(goalWeightKg = ideal.goalKg)) }) {
                            BiLabel(ar = "استخدمه كهدف", en = "Use as my goal", icon = Icons.Rounded.TrackChanges, size = 13f)
                        }
                    }
                }
            }
            NumberField(ar = "الوزن المستهدف", en = "Goal weight", value = profile.goalWeightKg, onValueChange = { onChange(profile.copy(goalWeightKg = it)) }, unit = "kg")
            PacePicker(profile = profile, onChange = onChange, currentWeightKg = weight)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BiCaption(ar = "نشاطك اليومي (من غير التمرين)", en = "Daily activity (outside workouts)")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActivityLevel.entries.forEach { level ->
                        BiChip(ar = level.ar, en = level.en, selected = profile.activity == level) { onChange(profile.withActivity(level)) }
                    }
                }
            }
            StepperRow(
                ar = "هدف الخطوات ${VFormat.number(profile.stepsGoal)}",
                en = "Steps goal",
                value = profile.stepsGoal,
                range = 2000..25000,
                step = 500,
                onValueChange = { onChange(profile.copy(stepsGoal = it)) },
            )
            StepperRow(
                ar = "هدف المية ${profile.waterGoal} كوباية",
                en = "Water goal (glasses)",
                value = profile.waterGoal,
                range = 4..16,
                step = 1,
                onValueChange = { onChange(profile.copy(waterGoal = it)) },
            )
        }
    }
}

/** The SwiftUI `Stepper`: a label with − and + buttons, clamped to `range`. */
@Composable
private fun StepperRow(ar: String, en: String, value: Int, range: IntRange, step: Int, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BiText(ar = ar, en = en, size = 13f, weight = 500, modifier = Modifier.weight(1f))
        Row(
            Modifier.background(VColor.ground, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "قلّل — Decrease",
                onClick = { onValueChange((value - step).coerceIn(range)) },
                background = Color.Transparent,
                tint = VColor.ink,
                size = 40.dp,
                enabled = value > range.first,
            )
            Spacer(Modifier.width(1.dp).heightIn(min = 20.dp).background(VColor.line))
            CircleIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "زوّد — Increase",
                onClick = { onValueChange((value + step).coerceIn(range)) },
                background = Color.Transparent,
                tint = VColor.ink,
                size = 40.dp,
                enabled = value < range.last,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthSection(profile: UserProfile, onChange: (UserProfile) -> Unit) {
    val app = LocalApp.current
    val ai = app.ai
    val scope = rememberCoroutineScope()
    var identifying by remember { mutableStateOf(false) }
    var identifyError by remember { mutableStateOf<String?>(null) }
    // Claude's reply arrives later: merge it into the profile as it is then, not as it was when asked.
    val latestProfile by rememberUpdatedState(profile)
    val latestOnChange by rememberUpdatedState(onChange)

    /** Asks Claude about items that have no insight yet (all current items, so it can spot interactions). */
    fun identifyMissing(base: UserProfile) {
        if (!ai.hasKey || identifying) return
        val known = base.healthInsights.map { it.id }.toSet()
        val items = base.healthItems
        if (items.none { HealthItemInsight.key(it.first, it.second) !in known }) return
        identifying = true
        identifyError = null
        scope.launch {
            try {
                val result = ai.run(HealthItemInsights.serializer(), AITasks.identifyHealthItems(items, context = null))
                val current = latestProfile
                val merged = LinkedHashMap<String, HealthItemInsight>()
                current.healthInsights.forEach { merged[it.id] = it }
                result.items.forEach { merged[it.id] = it }
                latestOnChange(current.copy(healthInsights = merged.values.toList()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                identifyError = error.userMessage()
            }
            identifying = false
        }
    }

    Card {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle(ar = "الأمراض المزمنة", en = "Chronic conditions")
            BiText(
                ar = "اختار اللي عندك — Claude هياخدها في اعتباره في كل اقتراح.",
                en = "Pick yours — Claude considers them in every suggestion.",
                size = 12f, weight = 400, color = VColor.inkSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChronicCondition.entries.forEach { condition ->
                    val selected = condition in profile.conditions
                    BiChip(ar = condition.ar, en = condition.en, selected = selected) {
                        val updated = if (selected) profile.conditions.filter { it != condition } else profile.conditions + condition
                        onChange(profile.withConditions(updated))
                    }
                }
            }
            TagListEditor(
                ar = "أمراض تانية", en = "Other conditions", placeholder = "مثلاً: WPW · e.g. asthma",
                items = ProfileList.items(profile.otherConditions),
                onItemsChange = { onChange(profile.copy(otherConditions = ProfileList.text(it))) },
                onAdd = { value -> identifyMissing(profile.copy(otherConditions = ProfileList.text(ProfileList.items(profile.otherConditions) + value))) },
            )
            TagListEditor(
                ar = "الأدوية", en = "Medications", placeholder = "مثلاً: Concor 5 · e.g. Metformin",
                items = ProfileList.items(profile.medications),
                onItemsChange = { onChange(profile.copy(medications = ProfileList.text(it))) },
                onAdd = { value -> identifyMissing(profile.copy(medications = ProfileList.text(ProfileList.items(profile.medications) + value))) },
            )
            TagListEditor(
                ar = "حساسية من أكل", en = "Food allergies", placeholder = "مثلاً: لاكتوز · e.g. peanuts",
                items = ProfileList.items(profile.allergies),
                onItemsChange = { onChange(profile.copy(allergies = ProfileList.text(it))) },
                onAdd = { value -> identifyMissing(profile.copy(allergies = ProfileList.text(ProfileList.items(profile.allergies) + value))) },
            )

            // What Claude understood
            val known = profile.currentHealthInsights
            val pending = profile.healthItems.filter { item -> known.none { it.id == HealthItemInsight.key(item.first, item.second) } }
            if (known.isNotEmpty() || pending.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(VColor.primarySoft, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = VColor.primary, modifier = Modifier.size(16.dp))
                        BiCaption(ar = "Claude فهم كده", en = "What Claude understood", size = 12f, color = VColor.primaryDark, modifier = Modifier.weight(1f))
                        if (identifying) CircularProgressIndicator(color = VColor.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                    known.forEach { insight ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(VColor.surface, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    if (insight.recognized) Icons.Rounded.Verified else Icons.Rounded.Error,
                                    contentDescription = null,
                                    tint = if (insight.recognized) VColor.primary else Color(0xFFC2410C),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text("${insight.input} ← ${insight.nameAr}", style = VFont.style(13f, 600, VColor.ink))
                            }
                            Text(insight.nameEn, style = VFont.style(12f, 500, VColor.inkSecondary))
                            Text(insight.whatItIsAr, style = VFont.style(12f, 400, VColor.ink))
                            Text(insight.whatItIsEn, style = VFont.style(11f, 400, VColor.inkSecondary))
                            Text(insight.notesAr, style = VFont.style(12f, 400, VColor.ink))
                            Text(insight.notesEn, style = VFont.style(11f, 400, VColor.inkSecondary))
                        }
                    }
                    if (pending.isNotEmpty()) {
                        if (ai.hasKey) {
                            SecondaryButton(onClick = { identifyMissing(profile) }, enabled = !identifying) {
                                BiLabel(ar = "خلّي Claude يتعرف عليهم", en = "Let Claude identify them", icon = Icons.Rounded.AutoAwesome, size = 13f)
                            }
                        } else {
                            BiText(
                                ar = "ضيف مفتاح Claude عشان يتعرف عليهم ويتأكد من الأسماء.",
                                en = "Add your Claude key so it can identify them and check the names.",
                                size = 11f, weight = 400, color = VColor.muted,
                            )
                        }
                    }
                    identifyError?.let { Text(it, style = VFont.style(11f, 400, VColor.energy)) }
                }
            }

            TagListEditor(
                ar = "ذوقك في الأكل", en = "Food preferences", placeholder = "مثلاً: بحب الفراخ · e.g. I like chicken",
                items = profile.preferences,
                onItemsChange = { onChange(profile.copy(preferences = it)) },
            )
        }
    }
}

/** Chips with an add field: one item per entry, tap a chip to remove it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagListEditor(
    ar: String,
    en: String,
    placeholder: String,
    items: List<String>,
    onItemsChange: (List<String>) -> Unit,
    onAdd: (String) -> Unit = {},
) {
    var draft by remember { mutableStateOf("") }

    fun add() {
        val value = draft.trim()
        if (value.isEmpty() || value in items) return
        onItemsChange(items + value)
        draft = ""
        onAdd(value)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BiCaption(ar = ar, en = en)
        if (items.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Row(
                        Modifier
                            .heightIn(min = 36.dp)
                            .background(VColor.primarySoft, CircleShape)
                            .clickable(role = Role.Button) { onItemsChange(items.filter { it != item }) }
                            .semantics { contentDescription = "$item — امسح · remove" }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(item, style = VFont.style(13f, 400, VColor.primaryDark))
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = VColor.primaryDark, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(VColor.ground, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlainTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = placeholder,
                fontSize = 14f,
                modifier = Modifier.weight(1f),
                onDone = { add() },
            )
            val canAdd = draft.isNotBlank()
            Icon(
                Icons.Rounded.AddCircle,
                contentDescription = "ضيف — Add",
                tint = VColor.primary,
                modifier = Modifier
                    .size(30.dp)
                    .alpha(if (canAdd) 1f else 0.35f)
                    .clickable(enabled = canAdd, role = Role.Button) { add() },
            )
        }
    }
}

/** Plain single-line text field with a placeholder (the iOS `TextField` on a tinted background). */
@Composable
internal fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fontSize: Float = 16f,
    onDone: (() -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = VFont.style(fontSize, 400, VColor.ink),
        cursorBrush = SolidColor(VColor.primary),
        keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = VFont.style(fontSize, 400, VColor.muted))
                inner()
            }
        },
    )
}

val ActivityLevel.ar: String
    get() = when (this) {
        ActivityLevel.SEDENTARY -> "قاعد أغلب اليوم"
        ActivityLevel.LIGHT -> "حركة خفيفة"
        ActivityLevel.MODERATE -> "حركة متوسطة"
        ActivityLevel.ACTIVE -> "نشيط"
        ActivityLevel.VERY_ACTIVE -> "نشيط جداً"
    }

val ActivityLevel.en: String
    get() = when (this) {
        ActivityLevel.SEDENTARY -> "Sedentary"
        ActivityLevel.LIGHT -> "Light"
        ActivityLevel.MODERATE -> "Moderate"
        ActivityLevel.ACTIVE -> "Active"
        ActivityLevel.VERY_ACTIVE -> "Very active"
    }

/** Weekly pace as a share of body weight (0.5–1% per week is the evidence-based range), with time to goal. */
@Composable
fun PacePicker(profile: UserProfile, onChange: (UserProfile) -> Unit, currentWeightKg: Double) {
    val options = NutritionCalculator.paceOptions(weightKg = currentWeightKg, heightCm = profile.heightCm, ageYears = profile.ageYears)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BiCaption(ar = "معدل النزول في الأسبوع", en = "Weekly pace")
        BiText(
            ar = "العلم بيقول إن نزول 0.5–1% من وزنك في الأسبوع بيحافظ على العضلات وبيستمر. اخترنالك الأنسب لوزنك وسنك.",
            en = "Research shows losing 0.5–1% of body weight per week protects muscle and lasts. We picked the best fit for your weight and age.",
            size = 12f, weight = 400, color = VColor.inkSecondary,
        )
        options.forEach { option ->
            val selected = abs(profile.weeklyLossKg - option.kgPerWeek) < 0.001
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (selected) VColor.primarySoft else VColor.ground, shape)
                    .border(1.5.dp, if (selected) VColor.primary else Color.Transparent, shape)
                    .clickable(role = Role.RadioButton) { onChange(profile.copy(weeklyLossKg = option.kgPerWeek)) }
                    .semantics { this.selected = selected }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) VColor.primary else VColor.line,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${option.ar} · ${option.en}", style = VFont.style(14f, 600, VColor.ink))
                        if (option.recommended) {
                            Text(
                                "موصى بيه · Recommended",
                                style = VFont.style(10f, 600, Color.White),
                                modifier = Modifier
                                    .background(VColor.primary, CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text("${option.noteAr} · ${option.noteEn}", style = VFont.style(11f, 400, VColor.inkSecondary))
                    NutritionCalculator.weeksToGoal(currentKg = currentWeightKg, goalKg = profile.goalWeightKg, kgPerWeek = option.kgPerWeek)?.let { weeks ->
                        Text(paceTimeText(weeks), style = VFont.style(11f, 400, VColor.muted))
                    }
                }
                Ltr {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("${VFormat.decimal(option.kgPerWeek, digits = 2)} kg", style = VFont.style(15f, 700, VColor.ink))
                        Text("${VFormat.decimal(option.percent, digits = 2)}% / week", style = VFont.style(10f, 400, VColor.muted))
                    }
                }
            }
        }
    }
}

private fun paceTimeText(weeks: Int): String {
    val date = LocalDate.now().plusWeeks(weeks.toLong())
    val ar = date.format(DateTimeFormatter.ofPattern("MMMM y", Locale.forLanguageTag("ar")))
    val en = date.format(DateTimeFormatter.ofPattern("MMM y", Locale.US))
    return "توصل للهدف في حوالي $weeks أسبوع ($ar) · Goal in about $weeks weeks ($en)"
}
