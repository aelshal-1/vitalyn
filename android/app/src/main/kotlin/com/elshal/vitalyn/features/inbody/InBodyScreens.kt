package com.elshal.vitalyn.features.inbody

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.AITasks
import com.elshal.vitalyn.core.ClaudeContent
import com.elshal.vitalyn.core.InBodyExtraction
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.InBodyScan
import com.elshal.vitalyn.data.nowMillis
import com.elshal.vitalyn.data.startMillis
import com.elshal.vitalyn.data.toLocalDateTime
import com.elshal.vitalyn.data.toMillis
import com.elshal.vitalyn.features.progress.ChartPoint
import com.elshal.vitalyn.features.progress.TrendLineChart
import com.elshal.vitalyn.services.ImageTools
import com.elshal.vitalyn.services.userMessage
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Capsule
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.ErrorBanner
import com.elshal.vitalyn.ui.FullSheet
import com.elshal.vitalyn.ui.ImageSourceButtons
import com.elshal.vitalyn.ui.LocalNavigator
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.NumberField
import com.elshal.vitalyn.ui.PrimaryButton
import com.elshal.vitalyn.ui.PushedScreen
import com.elshal.vitalyn.ui.SecondaryButton
import com.elshal.vitalyn.ui.SectionTitle
import com.elshal.vitalyn.ui.SheetScaffold
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import com.elshal.vitalyn.ui.WorkingView
import com.elshal.vitalyn.ui.rememberFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** "Oct 30, 2026" (the iOS `.abbreviated` date in the app's en_US locale). */
private val abbreviated: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

private fun Long.abbreviatedDate(): String = abbreviated.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private val reviewYellow = Color(0xFFFFF3D6)

/** List of scans with the weight trend and an "add scan" entry point. */
@Composable
fun InBodyListScreen() {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    val navigator = LocalNavigator.current
    var showCapture by remember { mutableStateOf(false) }
    val scans = state.sortedScans.reversed() // newest first

    PushedScreen("InBody", "Body scans") {
        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrimaryButton(onClick = { showCapture = true }) {
                BiLabel("ضيف InBody جديد", "Add a new InBody", icon = Icons.Rounded.CenterFocusStrong)
            }

            if (scans.size > 1) {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("الوزن عبر القياسات", "Weight across scans")
                        TrendLineChart(
                            points = scans.reversed().map { ChartPoint(it.date, it.weightKg, marked = true) },
                            height = 160.dp,
                        )
                    }
                }
            }

            if (scans.isEmpty()) {
                BiText(
                    "لسه مفيش قياسات. صوّر ورقة الـ InBody أو ارفع الـ PDF.",
                    "No scans yet. Snap your InBody sheet or upload the PDF.",
                    size = 14f,
                    weight = 400,
                    color = VColor.inkSecondary,
                )
            }

            scans.forEachIndexed { index, scan ->
                val previous = scans.getOrNull(index + 1)
                ScanRow(scan, previous) {
                    navigator.push { InBodyDetailScreen(scan, previous) }
                }
            }
        }
    }

    if (showCapture) InBodyCaptureSheet(onDismiss = { showCapture = false })
}

@Composable
private fun ScanRow(scan: InBodyScan, previous: InBodyScan?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VColor.surface, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(VColor.primarySoft, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.FindInPage, contentDescription = null, tint = VColor.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(scan.date.abbreviatedDate(), style = VFont.style(14f, 600, VColor.ink))
            Ltr {
                Text(
                    "${VFormat.decimal(scan.weightKg)} kg · ${scan.effectiveBodyFatPercent?.let { VFormat.decimal(it) + "%" } ?: "—"}",
                    style = VFont.style(12f, 400, VColor.inkSecondary),
                )
            }
        }
        if (previous != null) {
            val delta = scan.weightKg - previous.weightKg
            Ltr {
                Text(
                    (if (delta > 0) "+" else "") + VFormat.decimal(delta) + " kg",
                    style = VFont.style(13f, 600, if (delta <= 0) VColor.primaryDark else VColor.energy),
                )
            }
        }
    }
}

/** One scan's metrics (with the change from the scan before it), segmental lean mass and Claude's summary. */
@Composable
fun InBodyDetailScreen(scan: InBodyScan, previous: InBodyScan?) {
    PushedScreen(scan.date.abbreviatedDate(), "InBody") {
        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetricsGrid(InBodyMetric.list(scan, previous))
            SegmentalCard(scan)
            if (scan.summaryAr.isNotEmpty()) {
                Card(background = VColor.night, padding = 20.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(scan.summaryAr, style = VFont.style(14f, 400, VColor.nightText))
                        Text(scan.summaryEn, style = VFont.style(12f, 400, VColor.nightMuted))
                    }
                }
            }
        }
    }
}

data class InBodyMetric(
    val ar: String,
    val en: String,
    val value: String,
    val unit: String,
    val delta: String?,
    val deltaGood: Boolean,
    val lowConfidence: Boolean,
) {
    val id: String get() = en

    companion object {
        fun list(scan: InBodyScan, previous: InBodyScan?, lowConfidence: Set<String> = emptySet()): List<InBodyMetric> {
            fun item(ar: String, en: String, key: String, value: Double?, old: Double?, unit: String, lowerIsBetter: Boolean): InBodyMetric? {
                if (value == null) return null
                var deltaText: String? = null
                var good = true
                if (old != null) {
                    val delta = value - old
                    deltaText = (if (delta > 0) "▲ " else "▼ ") + VFormat.decimal(abs(delta))
                    good = if (lowerIsBetter) delta <= 0 else delta >= 0
                }
                return InBodyMetric(ar, en, VFormat.decimal(value), unit, deltaText, good, key in lowConfidence)
            }
            return listOfNotNull(
                item("الوزن", "Weight", "weightKg", scan.weightKg, previous?.weightKg, "kg", lowerIsBetter = true),
                item("الكتلة العضلية", "Skeletal muscle", "skeletalMuscleKg", scan.skeletalMuscleKg, previous?.skeletalMuscleKg, "kg", lowerIsBetter = false),
                item("نسبة الدهون", "Body fat %", "bodyFatPercent", scan.effectiveBodyFatPercent, previous?.effectiveBodyFatPercent, "%", lowerIsBetter = true),
                item("كتلة الدهون", "Fat mass", "bodyFatKg", scan.bodyFatKg, previous?.bodyFatKg, "kg", lowerIsBetter = true),
                item("دهون حشوية", "Visceral fat", "visceralFatLevel", scan.visceralFatLevel, previous?.visceralFatLevel, "level", lowerIsBetter = true),
                item("معدل الحرق", "BMR", "bmrKcal", scan.bmrKcal, previous?.bmrKcal, "kcal", lowerIsBetter = false),
                item("مية الجسم", "Body water", "totalBodyWaterL", scan.totalBodyWaterL, previous?.totalBodyWaterL, "L", lowerIsBetter = false),
                item("InBody Score", "InBody score", "inbodyScore", scan.inbodyScore, previous?.inbodyScore, "/100", lowerIsBetter = false),
            )
        }
    }
}

/** Two-column grid of metric tiles; low-confidence values get a yellow outline and a "check this" tag. */
@Composable
fun MetricsGrid(metrics: List<InBodyMetric>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { metric -> MetricTile(metric, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(metric: InBodyMetric, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .background(VColor.surface, shape)
            .border(1.5.dp, if (metric.lowConfidence) Color(0xFFF2C14E) else Color.Transparent, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BiText(metric.ar, metric.en, size = 12f, weight = 400, color = VColor.inkSecondary, enSize = 11f)
        Ltr {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(metric.value, style = VFont.style(22f, 600, VColor.ink), modifier = Modifier.alignByBaseline())
                Text(metric.unit, style = VFont.style(12f, 400, VColor.inkSecondary), modifier = Modifier.alignByBaseline())
            }
        }
        metric.delta?.let {
            Text(it, style = VFont.style(12f, 500, if (metric.deltaGood) VColor.primaryDark else VColor.energy))
        }
        if (metric.lowConfidence) {
            BiCaption(
                "ثقة متوسطة — اتأكد",
                "Check this",
                size = 10f,
                color = Color(0xFF8A5A00),
                modifier = Modifier
                    .background(reviewYellow, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** Lean mass per arm, leg and trunk as bars (hidden when the sheet had no segmental values). */
@Composable
fun SegmentalCard(scan: InBodyScan) {
    val rows = listOf(
        Triple("ذراع يمين", "Right arm", scan.rightArmKg),
        Triple("ذراع شمال", "Left arm", scan.leftArmKg),
        Triple("الجذع", "Trunk", scan.trunkKg),
        Triple("رجل يمين", "Right leg", scan.rightLegKg),
        Triple("رجل شمال", "Left leg", scan.leftLegKg),
    )
    if (rows.none { it.third != null }) return
    val maxValue = rows.mapNotNull { it.third }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("العضلات حسب المنطقة", "Segmental lean mass")
            rows.forEach { (ar, en, value) ->
                if (value != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BiText(ar, en, size = 13f, weight = 400, enSize = 10f, modifier = Modifier.width(100.dp))
                        Capsule((value / maxValue).toFloat(), VColor.primary, Modifier.weight(1f).height(8.dp))
                        Text(
                            "${VFormat.decimal(value)} kg",
                            style = VFont.style(12f, 400, VColor.inkSecondary),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(60.dp),
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Capture and review

private fun isPdfBytes(bytes: ByteArray): Boolean =
    bytes.size > 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()

/**
 * Photo or PDF of an InBody sheet → Claude reads the values → [InBodyReviewSheet] → saved scan.
 * Saving also updates the height, writes weight/fat/lean mass to Health Connect and refreshes the widgets.
 */
@Composable
fun InBodyCaptureSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    val scope = rememberCoroutineScope()

    var fileData by remember { mutableStateOf<ByteArray?>(null) }
    var isPDF by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<Bitmap?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var extraction by remember { mutableStateOf<InBodyExtraction?>(null) }

    fun useImage(image: Bitmap) {
        previewImage = image
        isPDF = false
        fileData = null
        scope.launch {
            val jpeg = withContext(Dispatchers.Default) { ImageTools.jpeg(image, maxDimension = 2400, quality = 85) }
            if (previewImage === image) fileData = jpeg
        }
    }

    val pickPdf = rememberFilePicker(arrayOf("application/pdf")) { file ->
        if (file.isPDF || isPdfBytes(file.bytes)) {
            fileData = file.bytes
            isPDF = true
            previewImage = null
        } else {
            scope.launch {
                withContext(Dispatchers.Default) { ImageTools.decode(file.bytes, maxDimension = 2400) }?.let { useImage(it) }
            }
        }
    }

    fun analyze() {
        val data = fileData ?: return
        working = true
        error = null
        val content: ClaudeContent = if (isPDF) ClaudeContent.Pdf(data) else ClaudeContent.Image(data, "image/jpeg")
        val request = AITasks.inBody(content, AppData.userContext(state, app.health))
        scope.launch {
            try {
                extraction = app.ai.run(InBodyExtraction.serializer(), request)
            } catch (e: Exception) {
                error = e.userMessage()
            }
            working = false
        }
    }

    fun save(draft: InBodyExtraction, date: Long) {
        val source = fileData
        val pdf = isPDF
        app.scope.launch {
            val blob = source?.let { bytes -> withContext(Dispatchers.IO) { app.store.saveBlob(bytes, if (pdf) "pdf" else "jpg") } }
            val scan = InBodyScan(
                date = date,
                weightKg = draft.weightKg,
                skeletalMuscleKg = draft.skeletalMuscleKg,
                bodyFatKg = draft.bodyFatKg,
                bodyFatPercent = draft.bodyFatPercent,
                leanMassKg = draft.leanMassKg,
                visceralFatLevel = draft.visceralFatLevel,
                bmrKcal = draft.bmrKcal,
                totalBodyWaterL = draft.totalBodyWaterL,
                inbodyScore = draft.inbodyScore,
                rightArmKg = draft.segmentalLean.rightArmKg,
                leftArmKg = draft.segmentalLean.leftArmKg,
                trunkKg = draft.segmentalLean.trunkKg,
                rightLegKg = draft.segmentalLean.rightLegKg,
                leftLegKg = draft.segmentalLean.leftLegKg,
                summaryAr = draft.summaryAr,
                summaryEn = draft.summaryEn,
                sourceFile = blob,
                sourceIsPDF = pdf,
            )
            val height = draft.heightCm
            app.store.update { s ->
                val next = s.upsertScan(scan)
                if (height != null && height > 100) next.withProfile { it.copy(heightCm = height) } else next
            }
            app.health.saveBody(
                weightKg = scan.weightKg,
                bodyFatPercent = scan.effectiveBodyFatPercent,
                leanMassKg = scan.effectiveLeanMassKg,
                date = Instant.ofEpochMilli(date),
            )
            app.publishSnapshot()
        }
        onDismiss()
    }

    FullSheet(onDismiss) {
        SheetScaffold("InBody جديد", "New InBody", onClose = onDismiss) {
            Column(
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val current = extraction
                if (current != null) {
                    InBodyReviewSheet(current, onSave = { draft, date -> save(draft, date) })
                } else {
                    BiText("صوّر ورقة الـ InBody أو ارفع الـ PDF", "Snap your InBody sheet or upload the PDF", size = 18f)
                    BiText(
                        "Claude هيقرا كل الأرقام وانت تراجعها قبل الحفظ.",
                        "Claude reads every value; you review before saving.",
                        size = 13f,
                        weight = 400,
                        color = VColor.inkSecondary,
                    )
                    val preview = previewImage
                    if (preview != null) {
                        val bitmap = remember(preview) { preview.asImageBitmap() }
                        Image(
                            bitmap,
                            contentDescription = "InBody",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    } else if (isPDF) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Description, contentDescription = null, tint = VColor.ink)
                            Text("PDF", style = VFont.style(16f, 600, VColor.ink))
                        }
                    }
                    ImageSourceButtons(onImage = { useImage(it) })
                    SecondaryButton(onClick = pickPdf, modifier = Modifier.fillMaxWidth()) {
                        BiLabel("ارفع PDF", "Upload PDF", icon = Icons.Rounded.FileUpload)
                    }

                    if (working) {
                        WorkingView("بقرا الأرقام…", "Reading the values…")
                    } else if (fileData != null) {
                        PrimaryButton(onClick = { analyze() }, color = VColor.night) {
                            BiLabel("حلّل", "Analyze", icon = Icons.Rounded.AutoAwesome)
                        }
                    }
                }
                error?.let { ErrorBanner(it) }
            }
        }
    }
}

/** Editable review of what Claude read from the sheet; values Claude was unsure about are highlighted yellow. */
@Composable
fun InBodyReviewSheet(extraction: InBodyExtraction, onSave: (InBodyExtraction, Long) -> Unit) {
    val context = LocalContext.current
    var draft by remember(extraction) { mutableStateOf(extraction) }
    var date by remember { mutableLongStateOf(nowMillis()) }
    val low = draft.lowConfidenceFields.toSet()

    LaunchedEffect(extraction) {
        extraction.scanDate?.let { text ->
            runCatching { LocalDate.parse(text.take(10)) }.getOrNull()?.let { date = it.startMillis() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = VColor.primary)
            BiText(
                "راجع الأرقام وصلّح أي حاجة قبل الحفظ",
                "Review the values and fix anything before saving",
                size = 14f,
                weight = 500,
                color = VColor.primaryDark,
            )
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BiText("تاريخ القياس", "Scan date", size = 14f, weight = 500, modifier = Modifier.weight(1f))
                    SecondaryButton(onClick = {
                        val current = date.toLocalDateTime()
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
                        Ltr { Text(date.abbreviatedDate(), style = VFont.style(13f, 500, VColor.ink)) }
                    }
                }
                NumberField("الوزن", "Weight", draft.weightKg, { draft = draft.copy(weightKg = it) }, unit = "kg")
                OptionalField("الكتلة العضلية", "Skeletal muscle", draft.skeletalMuscleKg, "kg", "skeletalMuscleKg" in low) {
                    draft = draft.copy(skeletalMuscleKg = it)
                }
                OptionalField("كتلة الدهون", "Fat mass", draft.bodyFatKg, "kg", "bodyFatKg" in low) {
                    draft = draft.copy(bodyFatKg = it)
                }
                OptionalField("نسبة الدهون", "Body fat", draft.bodyFatPercent, "%", "bodyFatPercent" in low) {
                    draft = draft.copy(bodyFatPercent = it)
                }
                OptionalField("دهون حشوية", "Visceral fat", draft.visceralFatLevel, "", "visceralFatLevel" in low) {
                    draft = draft.copy(visceralFatLevel = it)
                }
                OptionalField("معدل الحرق BMR", "BMR", draft.bmrKcal, "kcal", "bmrKcal" in low) {
                    draft = draft.copy(bmrKcal = it)
                }
                OptionalField("مية الجسم", "Body water", draft.totalBodyWaterL, "L", "totalBodyWaterL" in low) {
                    draft = draft.copy(totalBodyWaterL = it)
                }
            }
        }
        Card(background = VColor.night, padding = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(draft.summaryAr, style = VFont.style(14f, 400, VColor.nightText))
                Text(draft.summaryEn, style = VFont.style(12f, 400, VColor.nightMuted))
            }
        }
        PrimaryButton(onClick = { onSave(draft, date) }) {
            BiLabel("احفظ وحدّث أهدافي", "Save & update my targets", icon = Icons.Rounded.Check)
        }
    }
}

/** A number field for an optional value: 0 / empty means "not on the sheet" (null). */
@Composable
private fun OptionalField(ar: String, en: String, value: Double?, unit: String, lowConfidence: Boolean, onChange: (Double?) -> Unit) {
    NumberField(
        ar,
        en,
        value ?: 0.0,
        { onChange(if (it == 0.0) null else it) },
        modifier = Modifier
            .background(if (lowConfidence) reviewYellow else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(6.dp),
        unit = unit,
    )
}
