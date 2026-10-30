package com.elshal.vitalyn.features.body

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ThreeDRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.BodyMesh
import com.elshal.vitalyn.core.BodyStats
import com.elshal.vitalyn.core.ProgressMath
import com.elshal.vitalyn.core.Sex
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.InBodyScan
import com.elshal.vitalyn.data.PhotoKind
import com.elshal.vitalyn.data.ProgressPhoto
import com.elshal.vitalyn.data.UserProfile
import com.elshal.vitalyn.data.toLocalDate
import com.elshal.vitalyn.services.HealthService
import com.elshal.vitalyn.services.ImageTools
import com.elshal.vitalyn.services.PhotoCheck
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiChip
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.CircleIconButton
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.PrimaryButton
import com.elshal.vitalyn.ui.PushedScreen
import com.elshal.vitalyn.ui.SecondaryButton
import com.elshal.vitalyn.ui.SectionTitle
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import com.elshal.vitalyn.ui.rememberCameraCapture
import com.elshal.vitalyn.ui.rememberPhotoPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private class Keyframes(val start: BodyStats, val now: BodyStats, val goal: BodyStats)

/** Start, now and goal bodies from the profile and InBody scans (the iOS `keyframes`). */
private fun keyframes(profile: UserProfile?, scans: List<InBodyScan>, health: HealthService): Keyframes? {
    profile ?: return null
    val first = scans.firstOrNull()
    val latest = scans.lastOrNull()
    val startWeight = first?.weightKg ?: profile.startWeightKg
    val nowWeight = AppData.currentWeight(profile, scans, health)
    val lean = latest?.effectiveLeanMassKg
    fun stats(weight: Double, fat: Double?) =
        BodyStats(sex = profile.sex, ageYears = profile.ageYears, heightCm = profile.heightCm, weightKg = weight, bodyFatPercent = fat)
    // With a scan, assume lean mass is kept and weight changes are fat.
    val nowFat = lean?.let { max(5.0, (nowWeight - it) / nowWeight * 100) }
    val goalFat = lean?.let { max(8.0, (profile.goalWeightKg - it) / profile.goalWeightKg * 100) }
    return Keyframes(stats(startWeight, first?.effectiveBodyFatPercent), stats(nowWeight, nowFat), stats(profile.goalWeightKg, goalFat))
}

private class JourneyValues(val weight: Double, val fat: Double?, val muscle: Double?)

/** Weight, body fat and muscle at a point on the journey (0 = first measurement, 0.5 = now, 1 = goal). */
private fun journeyValues(profile: UserProfile?, scans: List<InBodyScan>, health: HealthService, journey: Double): JourneyValues {
    val first = scans.firstOrNull()
    val latest = scans.lastOrNull()
    val startWeight = first?.weightKg ?: profile?.startWeightKg ?: 0.0
    val nowWeight = AppData.currentWeight(profile ?: UserProfile(), scans, health)
    val goalWeight = profile?.goalWeightKg ?: nowWeight
    val weight = ProgressMath.journey(first = startWeight, now = nowWeight, goal = goalWeight, t = journey)

    var fat: Double? = null
    val firstFat = first?.effectiveBodyFatPercent
    val nowFat = latest?.effectiveBodyFatPercent
    val lean = latest?.effectiveLeanMassKg
    if (firstFat != null && nowFat != null && lean != null) {
        // At the goal, assume lean mass is kept and the difference is fat.
        val goalFat = max(8.0, (goalWeight - lean) / goalWeight * 100)
        fat = ProgressMath.journey(first = firstFat, now = nowFat, goal = goalFat, t = journey)
    }
    var muscle: Double? = null
    val firstMuscle = first?.skeletalMuscleKg
    val nowMuscle = latest?.skeletalMuscleKg
    if (firstMuscle != null && nowMuscle != null) {
        muscle = ProgressMath.journey(first = firstMuscle, now = nowMuscle, goal = nowMuscle, t = journey)
    }
    return JourneyValues(weight, fat, muscle)
}

/** "My body": a 3D body shaped from your numbers (start, now, goal), the journey slider and progress photos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen() {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    val health = app.health
    val scope = rememberCoroutineScope()

    val profile = state.profile
    val scans = state.sortedScans

    var skin by remember { mutableIntStateOf(app.prefs.int(AvatarStyle.SKIN_KEY, 2)) }
    var longHairChoice by remember { mutableIntStateOf(app.prefs.int(AvatarStyle.LONG_HAIR_KEY, -1)) } // -1 = by sex

    var journey by remember { mutableDoubleStateOf(0.5) }
    var comparing by remember { mutableStateOf(false) }
    var bodies by remember { mutableStateOf<AvatarBodies?>(null) }
    var showCapture by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var animation by remember { mutableStateOf<Job?>(null) }

    val style = AvatarStyle(
        skin = skin,
        longHair = if (longHairChoice == -1) profile?.sex == Sex.FEMALE else longHairChoice == 1,
    )

    val keyframes = keyframes(profile, scans, health)
    val statsKey = keyframes?.let { k ->
        listOf(k.start, k.now, k.goal).joinToString("|") { "${it.sex}${it.ageYears}-${it.heightCm}-${it.weightKg}-${it.bodyFatPercent ?: -1.0}" }
    } ?: "none"

    LaunchedEffect(statsKey) {
        bodies = keyframes?.let { AvatarBodies.make(it.start, it.now, it.goal) }
    }

    val meshes: List<BodyMesh>? = remember(bodies, journey, comparing) {
        bodies?.let { b -> if (comparing) listOf(b.start, b.goal) else listOf(b.at(journey)) }
    }

    /** Morphs smoothly to a point on the journey. */
    fun show(target: Double) {
        comparing = false
        animation?.cancel()
        val from = journey
        animation = scope.launch {
            val steps = 18
            for (step in 1..steps) {
                delay(22)
                val x = step.toDouble() / steps
                journey = from + (target - from) * (x * x * (3 - 2 * x))
            }
            journey = target
        }
    }

    PushedScreen("جسمي", "My body") {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Stage
            val stageShape = RoundedCornerShape(28.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .clip(stageShape)
                    .background(VColor.ground)
                    .border(1.dp, VColor.line, stageShape),
            ) {
                when {
                    meshes != null -> BodyAvatar(meshes = meshes, style = style, modifier = Modifier.fillMaxSize())
                    profile == null -> BiText(
                        "كمّل ملفك الأول",
                        "Finish your profile first",
                        modifier = Modifier.align(Alignment.Center),
                        size = 15f,
                        alignment = Alignment.CenterHorizontally,
                    )
                    else -> CircularProgressIndicator(color = VColor.primary, modifier = Modifier.align(Alignment.Center))
                }

                CircleIconButton(
                    Icons.Rounded.Palette,
                    "شكل الأفاتار · Avatar style",
                    onClick = { showStyle = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .shadow(6.dp, CircleShape),
                    background = VColor.surface,
                    tint = VColor.ink,
                )

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (comparing) {
                        Ltr {
                            Row(Modifier.fillMaxWidth()) {
                                Text("البداية · Start", Modifier.weight(1f), style = VFont.style(13f, 600, VColor.ink), textAlign = TextAlign.Center)
                                Text("الهدف · Goal", Modifier.weight(1f), style = VFont.style(13f, 600, VColor.ink), textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.ThreeDRotation, contentDescription = null, tint = VColor.muted, modifier = Modifier.size(14.dp))
                        Text("لفّ الموديل بصباعك · Drag to rotate", style = VFont.style(11f, 400, VColor.muted))
                    }
                }
            }

            // Mode picker
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BiChip("البداية", "Start", selected = !comparing && journey == 0.0) { show(0.0) }
                BiChip("دلوقتي", "Now", selected = !comparing && journey == 0.5) { show(0.5) }
                BiChip("الهدف", "Goal", selected = !comparing && journey == 1.0) { show(1.0) }
                BiChip("قارن", "Compare", selected = comparing, dark = true) { comparing = !comparing }
            }

            // Measurement bar
            val values = journeyValues(profile, scans, health, journey)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeasurementPill("الوزن", "Weight", "${VFormat.decimal(values.weight)} kg", highlight = false, modifier = Modifier.weight(1f))
                MeasurementPill("الدهون", "Body fat", values.fat?.let { "${VFormat.decimal(it)}%" } ?: "—", highlight = true, modifier = Modifier.weight(1f))
                MeasurementPill("العضلات", "Muscle", values.muscle?.let { "${VFormat.decimal(it)} kg" } ?: "—", highlight = false, modifier = Modifier.weight(1f))
            }

            // Journey
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BiText("رحلتك: البداية ← دلوقتي ← الهدف", "Your journey: start → now → goal", size = 14f, weight = 500)
                    Slider(
                        value = journey.toFloat(),
                        onValueChange = {
                            animation?.cancel()
                            comparing = false
                            journey = it.toDouble()
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = VColor.primary, activeTrackColor = VColor.primary),
                    )
                }
            }

            PrimaryButton(onClick = { showCapture = true }, color = VColor.night) {
                BiLabel("صور تقدّم جديدة", "New progress photos", icon = Icons.Rounded.PhotoCamera, size = 14f)
            }

            Comparison(state.progressPhotos)

            BiText(
                "الجسم ده متشكّل من طولك ووزنك ونسبة الدهون (من InBody لو موجود) وسنّك. كل ما تسجّل قياس جديد بيتغيّر معاك.",
                "This body is shaped from your height, weight, body fat (from InBody when available) and age. It changes with every new measurement.",
                size = 12f,
                weight = 400,
                color = VColor.inkSecondary,
            )
        }
    }

    if (showCapture) {
        AvatarCaptureSheet(onDismiss = { showCapture = false })
    }

    if (showStyle) {
        ModalBottomSheet(onDismissRequest = { showStyle = false }, containerColor = VColor.ground) {
            AvatarStyleSheet(
                skin = skin,
                longHair = style.longHair,
                onSkin = {
                    skin = it
                    app.prefs.setInt(AvatarStyle.SKIN_KEY, it)
                },
                onLongHair = {
                    val value = if (it) 1 else 0
                    longHairChoice = value
                    app.prefs.setInt(AvatarStyle.LONG_HAIR_KEY, value)
                },
            )
        }
    }
}

@Composable
private fun MeasurementPill(ar: String, en: String, value: String, highlight: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .background(VColor.surface, shape)
            .border(1.dp, VColor.line, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$ar · $en", style = VFont.style(10f, 400, VColor.muted))
        Ltr {
            Text(value, style = VFont.style(15f, 600, if (highlight) VColor.primary else VColor.ink))
        }
    }
}

// Photos

private val photoDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

@Composable
private fun Comparison(photos: List<ProgressPhoto>) {
    val fronts = photos.filter { it.kindRaw == PhotoKind.BODY_FRONT.raw }.sortedBy { it.date }
    if (fronts.size > 1) {
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("قبل وبعد", "Before & after")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhotoColumn(fronts.first(), "البداية", "Start", Modifier.weight(1f))
                    PhotoColumn(fronts.last(), "دلوقتي", "Now", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PhotoColumn(photo: ProgressPhoto, ar: String, en: String, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val image by produceState<Bitmap?>(null, photo.image) {
        value = withContext(Dispatchers.IO) { ImageTools.scaled(app.store.blobBitmap(photo.image, 600) ?: return@withContext null, 600) }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        image?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "$ar · $en",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
            )
        }
        Text(
            "$ar · $en · ${photoDate.format(photo.date.toLocalDate())}",
            style = VFont.style(11f, 400, VColor.muted),
            textAlign = TextAlign.Center,
        )
    }
}

// Style

/** Skin tone and hair for the generated body. */
@Composable
private fun AvatarStyleSheet(skin: Int, longHair: Boolean, onSkin: (Int) -> Unit, onLongHair: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(VColor.ground)
            .padding(24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SectionTitle("شكل الأفاتار", "Avatar style")
        BiCaption("لون البشرة", "Skin tone")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarStyle.skinTones.forEachIndexed { i, tone ->
                val selected = skin == i
                Box(
                    Modifier
                        .size(42.dp)
                        .background(Color(0xFF000000 or tone.toLong()), CircleShape)
                        .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) VColor.primary else VColor.line), CircleShape)
                        .clickable(role = Role.Button) { onSkin(i) }
                        .semantics {
                            contentDescription = "Skin tone ${i + 1}"
                            this.selected = selected
                        },
                )
            }
        }
        BiCaption("الشعر", "Hair")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BiChip("قصير", "Short", selected = !longHair) { onLongHair(false) }
            BiChip("طويل", "Long", selected = longHair) { onLongHair(true) }
        }
    }
}

// Capture

private class CaptureStep(val kind: PhotoKind, val ar: String, val en: String)

private val captureSteps = listOf(
    CaptureStep(PhotoKind.FACE_FRONT, "بص للكاميرا على طول", "Look straight at the camera"),
    CaptureStep(PhotoKind.FACE_RIGHT, "لف وشك شوية لليمين", "Turn your head a little right"),
    CaptureStep(PhotoKind.FACE_LEFT, "لف وشك شوية للشمال", "Turn your head a little left"),
    CaptureStep(PhotoKind.BODY_FRONT, "اقف مفرود ودراعاتك جنبك — جسمك كله في الصورة", "Stand straight, arms by your side — full body in frame"),
    CaptureStep(PhotoKind.BODY_SIDE, "لف جنب", "Turn to the side"),
)

private val captureBackground = Color(0xFF0B1512)

@Composable
private fun StepIcon(kind: PhotoKind) {
    val modifier = Modifier.size(70.dp)
    // Physical directions: keep the arrows from mirroring in the RTL layout.
    Ltr {
        when (kind) {
            PhotoKind.FACE_FRONT -> Icon(Icons.Rounded.Face, contentDescription = null, tint = VColor.mint, modifier = modifier)
            PhotoKind.FACE_RIGHT -> Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = VColor.mint, modifier = modifier)
            PhotoKind.FACE_LEFT -> Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = VColor.mint, modifier = modifier)
            PhotoKind.BODY_FRONT -> Icon(Icons.Rounded.Accessibility, contentDescription = null, tint = VColor.mint, modifier = modifier)
            PhotoKind.BODY_SIDE -> Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = null, tint = VColor.mint, modifier = modifier)
        }
    }
}

/** Guided capture: 3 selfies and 2 body photos, each checked on-device before it's saved. */
@Composable
private fun AvatarCaptureSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var index by rememberSaveable { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    suspend fun store(image: Bitmap, kind: PhotoKind) {
        val bytes = withContext(Dispatchers.Default) { ImageTools.jpeg(image, maxDimension = 1600, quality = 80) }
        val name = withContext(Dispatchers.IO) { app.store.saveBlob(bytes, "jpg") }
        val photo = ProgressPhoto(kindRaw = kind.raw, image = name)
        app.store.update { it.copy(progressPhotos = it.progressPhotos + photo) }
        index += 1
    }

    fun save(image: Bitmap) {
        if (index >= captureSteps.size || checking) return
        val kind = captureSteps[index].kind
        checking = true
        problem = null
        scope.launch {
            val result = PhotoCheck.check(image, kind)
            if (result is PhotoCheck.Result.Problem) {
                checking = false
                problem = "${result.ar}\n${result.en}"
                return@launch
            }
            store(image, kind)
            checking = false
        }
    }

    val openCamera = rememberCameraCapture(front = true) { save(it) }
    val openPhotos = rememberPhotoPicker { save(it) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(captureBackground)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    "إلغاء · Cancel",
                    style = VFont.style(15f, 500, VColor.nightText),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(vertical = 4.dp),
                )
                if (index < captureSteps.size) {
                    val step = captureSteps[index]
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in captureSteps.indices) {
                            val color = when {
                                i < index -> VColor.mint
                                i == index -> VColor.nightText
                                else -> Color(0xFF1F322B)
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .background(color, RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    StepIcon(step.kind)
                    BiText(step.ar, step.en, size = 20f, color = VColor.nightText, enColor = VColor.nightMuted, alignment = Alignment.CenterHorizontally)
                    Ltr {
                        Text("${index + 1} / ${captureSteps.size}", style = VFont.style(13f, 400, VColor.nightMuted))
                    }
                    Spacer(Modifier.weight(1f))
                    PrimaryButton(onClick = openCamera, color = VColor.primary, enabled = !checking) {
                        BiLabel("صوّر", "Capture", icon = Icons.Rounded.PhotoCamera)
                    }
                    SecondaryButton(onClick = openPhotos, modifier = Modifier.fillMaxWidth(), enabled = !checking) {
                        BiLabel("من الصور", "Photos", icon = Icons.Rounded.PhotoLibrary)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = VColor.mint, modifier = Modifier.size(70.dp))
                    BiText("الصور اتحفظت", "Photos saved", size = 22f, color = VColor.nightText, enColor = VColor.nightMuted, alignment = Alignment.CenterHorizontally)
                    BiText(
                        "هتشوف المقارنة قبل وبعد في شاشة جسمي.",
                        "See the before & after on the My body screen.",
                        size = 14f,
                        weight = 400,
                        color = VColor.nightMuted,
                        enColor = VColor.nightMuted,
                        alignment = Alignment.CenterHorizontally,
                    )
                    Spacer(Modifier.weight(1f))
                    PrimaryButton(onClick = onDismiss) {
                        BiLabel("تمام", "Done")
                    }
                }
                if (checking) {
                    CircularProgressIndicator(color = VColor.mint)
                }
                problem?.let {
                    Text(
                        it,
                        style = VFont.style(13f, 500, Color(0xFFFFB4A0)),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2A1A14), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                    )
                }
                BiText(
                    "لبس ضيق وإضاءة كويسة بيدّوا نتيجة أدق. الصور بتفضل على أجهزتك.",
                    "Fitted clothes and good light give better results. Photos stay on your devices.",
                    size = 11f,
                    weight = 400,
                    color = VColor.nightMuted,
                    enColor = VColor.nightMuted,
                    alignment = Alignment.CenterHorizontally,
                )
            }
        }
    }
}
