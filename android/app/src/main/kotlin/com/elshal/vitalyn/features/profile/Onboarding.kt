package com.elshal.vitalyn.features.profile

import android.Manifest
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.NutritionCalculator
import com.elshal.vitalyn.core.Sex
import com.elshal.vitalyn.core.idealWeight
import com.elshal.vitalyn.core.paceOptions
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.InBodyScan
import com.elshal.vitalyn.data.UserProfile
import com.elshal.vitalyn.services.HealthService
import com.elshal.vitalyn.services.NotificationService
import com.elshal.vitalyn.services.rememberHealthPermissionRequest
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiChip
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.NumberField
import com.elshal.vitalyn.ui.PrimaryButton
import com.elshal.vitalyn.ui.SecondaryButton
import com.elshal.vitalyn.ui.SectionTitle
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import com.elshal.vitalyn.ui.openUrl
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private val healthRed = Color(0xFFC2264B)
private const val STEPS = 4

/** First-launch setup: Health Connect + Claude key → about you (prefilled from Health Connect) → goal → health. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalApp.current
    val health = app.health
    val context = LocalContext.current

    // Start empty so nothing is accepted without the user entering it.
    var draft by remember { mutableStateOf(UserProfile(heightCm = 0.0, startWeightKg = 0.0, goalWeightKg = 0.0, birthYear = 0)) }
    var step by remember { mutableIntStateOf(0) }
    var apiKey by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<HealthService.HealthProfile?>(null) }
    var importing by remember { mutableStateOf(false) }
    var editingImported by remember { mutableStateOf(false) }

    /** Fills empty profile fields from Health Connect; the user reviews and edits them on the next step. */
    suspend fun importFromHealth() {
        importing = true
        val data = health.profileFromHealth()
        var updated = draft
        data.heightCm?.let { if (updated.heightCm == 0.0) updated = updated.copy(heightCm = Math.round(it).toDouble()) }
        data.weightKg?.let { if (updated.startWeightKg == 0.0) updated = updated.copy(startWeightKg = Math.round(it * 10) / 10.0) }
        data.birthYear?.let { if (updated.birthYear == 0) updated = updated.copy(birthYear = it) }
        data.sex?.let { updated = updated.withSex(it) }
        draft = updated
        imported = data
        importing = false
    }

    val connectHealth = rememberHealthPermissionRequest(health) { importFromHealth() }

    /** Health's weight as it was put into the profile, to tell whether the user changed it. */
    fun roundedImportedWeight(): Double? = imported?.weightKg?.let { Math.round(it * 10) / 10.0 }

    fun complete() {
        if (apiKey.isNotBlank()) app.ai.setKey(apiKey)
        val profile = draft
        // Only write the weight back when it's new, so an old Health Connect reading isn't re-dated to today.
        val writeWeight = health.requested && profile.startWeightKg != roundedImportedWeight()
        app.store.update { it.copy(profiles = it.profiles + profile) }
        if (writeWeight) {
            app.scope.launch { health.saveBody(weightKg = profile.startWeightKg, bodyFatPercent = null, leanMassKg = null, date = Instant.now()) }
        }
        NotificationService.scheduleDefaults(context)
        app.publishSnapshot()
        onDone()
    }

    // Asked before the profile is saved: once it is, this screen leaves and the result would be lost.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { complete() }

    fun finish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationService.canPost(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            complete()
        }
    }

    LaunchedEffect(Unit) {
        if (health.requested && imported == null) importFromHealth()
    }

    /** What the current step still needs before Next is enabled. */
    val missing: List<Pair<String, String>> = buildList {
        when (step) {
            1 -> {
                if (draft.name.isBlank()) add("اكتب اسمك" to "Enter your name")
                if (draft.birthYear !in 1930..2012) add("اختار سنة ميلادك" to "Choose your birth year")
                if (draft.heightCm !in 120.0..230.0) add("اكتب طولك (120–230 سم)" to "Enter your height (120–230 cm)")
                if (draft.startWeightKg !in 30.0..300.0) add("اكتب وزنك (30–300 كجم)" to "Enter your weight (30–300 kg)")
            }
            2 -> {
                if (draft.goalWeightKg !in 30.0..300.0) {
                    add("اكتب الوزن المستهدف" to "Enter your goal weight")
                } else if (draft.goalWeightKg > draft.startWeightKg) {
                    add("الهدف لازم يكون أقل من وزنك دلوقتي أو قده" to "Goal can't be above your current weight")
                }
            }
            else -> Unit
        }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(VColor.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / STEPS },
            color = VColor.primary,
            trackColor = VColor.track,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp),
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    0 -> {
                        Welcome()
                        // Connections
                        Card {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle(ar = "Health Connect", en = "Health Connect")
                                BiText(
                                    ar = "عشان ناخد خطواتك ونشاطك ووزنك، ونسجل وجباتك وتمارينك.",
                                    en = "To read steps, activity and weight, and save your meals and workouts.",
                                    size = 13f, weight = 400, color = VColor.inkSecondary,
                                )
                                PrimaryButton(
                                    onClick = connectHealth,
                                    color = if (health.requested) VColor.primaryDark else healthRed,
                                    enabled = health.isAvailable || health.needsInstall,
                                ) {
                                    BiLabel(
                                        ar = if (health.requested) "متصل" else "اربط Health Connect",
                                        en = if (health.requested) "Connected" else "Connect Health Connect",
                                        icon = if (health.requested) Icons.Rounded.CheckCircle else Icons.Rounded.Favorite,
                                    )
                                }
                                if (importing) CircularProgressIndicator(color = VColor.primary, modifier = Modifier.size(24.dp))
                                imported?.let { data ->
                                    if (data.isEmpty) {
                                        BiText(
                                            ar = "Health Connect مفيهوش بيانات عنك لسه — هتكتبها في الخطوة الجاية.",
                                            en = "Health Connect has no profile data yet — you'll enter it next.",
                                            size = 12f, weight = 400, color = VColor.inkSecondary,
                                        )
                                    } else {
                                        ImportedSection(
                                            imported = data,
                                            profile = draft,
                                            onChange = { draft = it },
                                            editing = editingImported,
                                            onToggleEditing = { editingImported = !editingImported },
                                            roundedImportedWeight = roundedImportedWeight(),
                                            onBodyFatChange = { value ->
                                                // Body fat lives on the import (it feeds the goal step); 0 clears it.
                                                val newValue = if (value > 0) value else null
                                                if (newValue != data.bodyFatPercent) {
                                                    imported = data.copy(bodyFatPercent = newValue, bodyFatDate = null)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Card {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle(ar = "مفتاح Claude API", en = "Claude API key")
                                BiText(
                                    ar = "ده اللي بيخلّي Vitalyn يحلل الصور ويقترح. هيتخزن متشفّر في الـ Keystore على جهازك بس. تقدر تضيفه بعدين من الإعدادات.",
                                    en = "Powers photo analysis and coaching. Stored encrypted only in this device's Keystore. You can add it later in Settings.",
                                    size = 13f, weight = 400, color = VColor.inkSecondary,
                                )
                                ApiKeyField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(VColor.ground, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                )
                                ConsoleLink()
                            }
                        }
                    }
                    1 -> {
                        val data = imported
                        if (data != null && !data.isEmpty) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(VColor.primarySoft, RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = healthRed, modifier = Modifier.size(20.dp))
                                BiText(
                                    ar = "جبنا البيانات دي من Health Connect — راجعها وعدّل أي حاجة.",
                                    en = "Filled in from Health Connect — review and edit anything.",
                                    size = 12f, weight = 500, color = VColor.primaryDark,
                                )
                            }
                        }
                        BasicsSection(profile = draft, onChange = { draft = it })
                    }
                    2 -> {
                        LaunchedEffect(Unit) {
                            if (draft.goalWeightKg == 0.0) {
                                val p = draft
                                var updated = p.copy(
                                    goalWeightKg = NutritionCalculator.idealWeight(
                                        heightCm = p.heightCm, ageYears = p.ageYears, sex = p.sex, currentWeightKg = p.startWeightKg,
                                    ).goalKg,
                                )
                                NutritionCalculator.paceOptions(weightKg = p.startWeightKg, heightCm = p.heightCm, ageYears = p.ageYears)
                                    .firstOrNull { it.recommended }
                                    ?.let { updated = updated.copy(weeklyLossKg = it.kgPerWeek) }
                                draft = updated
                            }
                        }
                        GoalSection(profile = draft, onChange = { draft = it }, bodyFatPercent = imported?.bodyFatPercent)
                    }
                    else -> {
                        HealthSection(profile = draft, onChange = { draft = it })
                        MetabolismSection(profile = draft, onChange = { draft = it })
                        TargetsPreview(profile = draft)
                    }
                }
            }
        }

        if (missing.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                missing.forEach { (ar, en) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = VColor.energy, modifier = Modifier.size(16.dp))
                        Text("$ar · $en", style = VFont.style(12f, 400, VColor.energy))
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                SecondaryButton(onClick = { step -= 1 }, modifier = Modifier.weight(1f)) {
                    BiLabel(ar = "رجوع", en = "Back")
                }
            }
            val last = step >= STEPS - 1
            PrimaryButton(
                onClick = {
                    if (!last) {
                        if (step == 0 && apiKey.isNotBlank()) app.ai.setKey(apiKey)
                        step += 1
                    } else {
                        finish()
                    }
                },
                modifier = Modifier.weight(1f),
                color = if (missing.isEmpty()) VColor.primary else VColor.muted,
                enabled = missing.isEmpty(),
            ) {
                BiLabel(ar = if (!last) "التالي" else "يلا نبدأ", en = if (!last) "Next" else "Let’s start")
            }
        }
    }
}

@Composable
private fun Welcome() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.Eco, contentDescription = null, tint = VColor.primary, modifier = Modifier.size(48.dp))
        BiText(ar = "أهلاً بيك في Vitalyn", en = "Welcome to Vitalyn", size = 26f, enSize = 16f)
        BiText(
            ar = "هنعرف عنك شوية حاجات عشان نحسب احتياجك ونتابعك يوم بيوم.",
            en = "A few questions so we can calculate your needs and coach you day by day.",
            size = 14f, weight = 400, color = VColor.inkSecondary,
        )
    }
}

/** What came from Health Connect, shown as the profile's current values so edits show up here too. */
@Composable
private fun ImportedSection(
    imported: HealthService.HealthProfile,
    profile: UserProfile,
    onChange: (UserProfile) -> Unit,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    roundedImportedWeight: Double?,
    onBodyFatChange: (Double) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BiCaption(ar = "جبنا من Health Connect", en = "Imported from Health Connect", color = VColor.primaryDark, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .clickable(role = Role.Button, onClick = onToggleEditing)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, contentDescription = null, tint = VColor.primary, modifier = Modifier.size(16.dp))
                Text(if (editing) "تمام · Done" else "عدّل · Edit", style = VFont.style(12f, 600, VColor.primary))
            }
        }
        if (editing) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BiChip(ar = "راجل", en = "Male", selected = profile.sex == Sex.MALE) { onChange(profile.withSex(Sex.MALE)) }
                    BiChip(ar = "ست", en = "Female", selected = profile.sex == Sex.FEMALE) { onChange(profile.withSex(Sex.FEMALE)) }
                }
                BirthYearField(year = profile.birthYear, onYearChange = { onChange(profile.copy(birthYear = it)) })
                NumberField(ar = "الطول", en = "Height", value = profile.heightCm, onValueChange = { onChange(profile.copy(heightCm = it)) }, unit = "cm", digits = 0)
                NumberField(ar = "الوزن دلوقتي", en = "Current weight", value = profile.startWeightKg, onValueChange = { onChange(profile.copy(startWeightKg = it)) }, unit = "kg")
                NumberField(ar = "نسبة الدهون", en = "Body fat", value = imported.bodyFatPercent ?: 0.0, onValueChange = onBodyFatChange, unit = "%")
            }
        } else {
            if (imported.heightCm != null && profile.heightCm > 0) {
                ImportedRow("الطول", "Height", "${VFormat.number(Math.round(profile.heightCm).toInt())} cm")
            }
            if (imported.weightKg != null && profile.startWeightKg > 0) {
                ImportedRow(
                    "الوزن", "Weight", "${VFormat.decimal(profile.startWeightKg)} kg",
                    measured = if (profile.startWeightKg == roundedImportedWeight) imported.weightDate else null,
                )
            }
            imported.bodyFatPercent?.let { value ->
                ImportedRow("نسبة الدهون", "Body fat", "${VFormat.decimal(value)}%", measured = imported.bodyFatDate)
            }
            if (imported.birthYear != null && profile.birthYear > 0) {
                ImportedRow("سنة الميلاد", "Birth year", profile.birthYear.toString())
            }
            if (imported.sex != null) {
                ImportedRow("النوع", "Sex", if (profile.sex == Sex.MALE) "راجل · Male" else "ست · Female")
            }
        }
    }
}

/** A row; `measured` flags a reading old enough that it may no longer be right. */
@Composable
private fun ImportedRow(ar: String, en: String, value: String, measured: Instant? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$ar · $en", style = VFont.style(12f, 400, VColor.inkSecondary), modifier = Modifier.weight(1f))
            Ltr { Text(value, style = VFont.style(13f, 600, VColor.ink)) }
        }
        if (measured != null && measured.isBefore(Instant.now().minus(Duration.ofDays(30)))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = VColor.energy, modifier = Modifier.size(14.dp))
                Text(
                    "آخر قياس ${ago(measured, ULocale("ar@numbers=latn"))} · Last measured ${ago(measured, ULocale.ENGLISH)}",
                    style = VFont.style(11f, 400, VColor.energy),
                )
            }
        }
    }
}

/** "3 months ago" in the given locale (the iOS RelativeDateTimeFormatter with full units). */
private fun ago(date: Instant, locale: ULocale): String {
    val days = Duration.between(date, Instant.now()).toDays().coerceAtLeast(0)
    val (quantity, unit) = when {
        days >= 365 -> days / 365 to RelativeDateTimeFormatter.RelativeUnit.YEARS
        days >= 30 -> days / 30 to RelativeDateTimeFormatter.RelativeUnit.MONTHS
        days >= 7 -> days / 7 to RelativeDateTimeFormatter.RelativeUnit.WEEKS
        else -> days to RelativeDateTimeFormatter.RelativeUnit.DAYS
    }
    return RelativeDateTimeFormatter.getInstance(locale).format(quantity.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
}

/** Link to the Anthropic console, where the user creates an API key. */
@Composable
internal fun ConsoleLink() {
    val context = LocalContext.current
    Text(
        "اعمل مفتاح من console.anthropic.com · Get a key at console.anthropic.com",
        style = VFont.style(12f, 600, VColor.primary),
        modifier = Modifier
            .clickable(role = Role.Button) { openUrl(context, "https://console.anthropic.com/settings/keys") }
            .padding(vertical = 4.dp),
    )
}

/** Shows the calculated daily targets for a profile. */
@Composable
fun TargetsPreview(profile: UserProfile, scans: List<InBodyScan> = emptyList()) {
    val app = LocalApp.current
    val targets = AppData.targets(profile, scans, app.health)
    Card(background = VColor.night, padding = 20.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BiText(ar = "احتياجك اليومي", en = "Your daily target", size = 16f, color = VColor.nightText, enColor = VColor.nightMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(VFormat.number(targets.calories), style = VFont.style(36f, 700, VColor.mint), modifier = Modifier.alignByBaseline())
                Text("سعرة · kcal", style = VFont.style(13f, 400, VColor.nightMuted), modifier = Modifier.alignByBaseline())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetMacro(targets.proteinGrams, "بروتين", "Protein", VColor.mint, Modifier.weight(1f))
                TargetMacro(targets.carbsGrams, "كارب", "Carbs", Color(0xFFF7C46C), Modifier.weight(1f))
                TargetMacro(targets.fatGrams, "دهون", "Fat", Color(0xFF9DB1FF), Modifier.weight(1f))
            }
            BiText(
                ar = "الأرقام دي محسوبة من وزنك ونشاطك وهدفك، وبتتحدث مع كل InBody.",
                en = "Calculated from your weight, activity and goal; updated with every InBody scan.",
                size = 11f, weight = 400, color = VColor.nightMuted, enColor = VColor.nightMuted,
            )
        }
    }
}

@Composable
private fun TargetMacro(value: Int, ar: String, en: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .background(Color(0xFF1C2F29), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$value g", style = VFont.style(18f, 600, color))
        Text(ar, style = VFont.style(11f, 400, VColor.nightText))
        Text(en, style = VFont.style(10f, 400, VColor.nightMuted))
    }
}

/** Shows the BMR in use and lets the user enter their own measured value. */
@Composable
fun MetabolismSection(profile: UserProfile, onChange: (UserProfile) -> Unit, scans: List<InBodyScan> = emptyList()) {
    val app = LocalApp.current
    val calculated = AppData.calculatedBMR(profile, scans, app.health)
    Card {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(ar = "معدل الحرق الأساسي (BMR)", en = "Basal metabolic rate (BMR)")
            BiText(
                ar = "السعرات اللي جسمك بيحرقها وانت مرتاح. لو تعرف رقمك الحقيقي (من InBody أو دكتور) اكتبه هنا وهنحسب عليه.",
                en = "Calories your body burns at rest. If you know your real number (InBody or a doctor), enter it and we'll use it.",
                size = 12f, weight = 400, color = VColor.inkSecondary,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${calculated.sourceAr} · ${calculated.sourceEn}", style = VFont.style(11f, 400, VColor.muted), modifier = Modifier.weight(1f))
                Ltr { Text("${VFormat.number(calculated.kcal)} kcal", style = VFont.style(14f, 600, VColor.inkSecondary)) }
            }
            NumberField(ar = "الـ BMR بتاعي", en = "My BMR", value = profile.manualBMR, onValueChange = { onChange(profile.copy(manualBMR = it)) }, unit = "kcal", digits = 0)
            if (profile.manualBMR > 0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = VColor.primary, modifier = Modifier.size(16.dp))
                    BiCaption(ar = "بنستخدم رقمك", en = "Using your number", size = 11f, color = VColor.primaryDark, modifier = Modifier.weight(1f))
                    Text(
                        "رجّع المحسوب · Use calculated",
                        style = VFont.style(12f, 600, VColor.primary),
                        modifier = Modifier
                            .clickable(role = Role.Button) { onChange(profile.copy(manualBMR = 0.0)) }
                            .padding(vertical = 6.dp),
                    )
                }
                if (profile.manualBMR < 900 || profile.manualBMR > 3500) {
                    BiText(
                        ar = "الرقم ده غريب — اتأكد منه.",
                        en = "That number looks unusual — please double-check.",
                        size = 11f, weight = 500, color = VColor.energy,
                    )
                }
            }
        }
    }
}
