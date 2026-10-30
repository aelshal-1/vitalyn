package com.elshal.vitalyn.features.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.AchievementInput
import com.elshal.vitalyn.core.Achievements
import com.elshal.vitalyn.core.Badge
import com.elshal.vitalyn.core.ProgressMath
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.UserProfile
import com.elshal.vitalyn.data.nowMillis
import com.elshal.vitalyn.ui.BiChip
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.PrimaryButton
import com.elshal.vitalyn.ui.PushedScreen
import com.elshal.vitalyn.ui.SectionTitle
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import com.elshal.vitalyn.ui.shareImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ShareCardStyle(val ar: String, val en: String) {
    KILOS("الكيلوهات", "Kilos"),
    CHART("المنحنى", "Chart"),
    STREAK("الالتزام", "Streak"),
}

data class ShareCardData(
    val start: Double,
    val current: Double,
    val fatChange: Double?,
    val muscleChange: Double?,
    val streak: Int,
    val series: List<Double>,
    /** Epoch millis. */
    val since: Long,
) {
    val lost: Double get() = start - current
}

@Composable
fun AchievementsScreen() {
    val app = LocalApp.current
    val health = app.health
    val state by app.store.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(ShareCardStyle.KILOS) }
    val cardLayer = rememberGraphicsLayer()

    val scans = state.sortedScans
    val profile = state.profile
    val streak = ProgressMath.streak(state.loggedDays)
    val currentWeight = AppData.currentWeight(profile ?: UserProfile(), scans, health)

    val data = run {
        val start = scans.firstOrNull()?.weightKg ?: health.weights.firstOrNull()?.value ?: profile?.startWeightKg ?: 0.0
        var fatChange: Double? = null
        var muscleChange: Double? = null
        val first = scans.firstOrNull()
        val last = scans.lastOrNull()
        if (first != null && last != null && scans.size > 1) {
            if (first.bodyFatKg != null && last.bodyFatKg != null) fatChange = last.bodyFatKg - first.bodyFatKg
            if (first.skeletalMuscleKg != null && last.skeletalMuscleKg != null) muscleChange = last.skeletalMuscleKg - first.skeletalMuscleKg
        }
        var series = scans.map { it.weightKg }
        if (series.size < 2) series = health.weights.map { it.value }
        ShareCardData(
            start = start,
            current = currentWeight,
            fatChange = fatChange,
            muscleChange = muscleChange,
            streak = streak,
            series = series,
            since = scans.firstOrNull()?.date ?: profile?.createdAt ?: nowMillis(),
        )
    }

    val badges = Achievements.badges(
        AchievementInput(
            scanCount = scans.size,
            startWeightKg = scans.firstOrNull()?.weightKg ?: profile?.startWeightKg,
            currentWeightKg = currentWeight,
            streakDays = streak,
            bestStepsDay = health.bestStepsDay,
            loggedMealCount = state.meals.size,
            goalWeightKg = profile?.goalWeightKg,
        ),
    )

    PushedScreen("الإنجازات", "Achievements") {
        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareCardStyle.entries.forEach { item ->
                    BiChip(item.ar, item.en, selected = style == item, dark = true) { style = item }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ShareCard(
                    style = style,
                    data = data,
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .drawWithContent {
                            // Record the card into a layer so it can be shared as an image.
                            cardLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(cardLayer)
                        },
                )
            }

            PrimaryButton(onClick = {
                scope.launch {
                    val image = runCatching { cardLayer.toImageBitmap() }.getOrNull() ?: return@launch
                    val bitmap = withContext(Dispatchers.Default) {
                        image.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                    } ?: return@launch
                    shareImage(context, bitmap)
                }
            }) {
                BiLabel("شارك مع صحابك", "Share with friends", icon = Icons.Rounded.Share)
            }

            SectionTitle("الشارات", "Badges")
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                badges.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { badge -> BadgeCell(badge, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeCell(badge: Badge, modifier: Modifier = Modifier) {
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = "${badge.ar} — ${badge.en}${if (badge.achieved) "" else " (لسه · not yet)"}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(60.dp)
                .background(if (badge.achieved) VColor.primarySoft else Color(0xFFE6EBE6), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                badgeIcon(badge.symbol),
                contentDescription = null,
                tint = if (badge.achieved) VColor.primary else Color(0xFF9AA8A2),
                modifier = Modifier.size(26.dp),
            )
        }
        BiText(
            badge.ar,
            badge.en,
            size = 11f,
            weight = 500,
            color = if (badge.achieved) VColor.ink else VColor.muted,
            enSize = 10f,
            alignment = Alignment.CenterHorizontally,
        )
    }
}

/** Material icon for each badge's SF Symbol (see `Achievements.badges`). */
private fun badgeIcon(symbol: String): ImageVector = when (symbol) {
    "fork.knife" -> Icons.Rounded.Restaurant
    "doc.text.magnifyingglass" -> Icons.Rounded.FindInPage
    "flame" -> Icons.Rounded.LocalFireDepartment
    "flame.fill" -> Icons.Rounded.Whatshot
    "figure.walk" -> Icons.AutoMirrored.Rounded.DirectionsWalk
    "arrow.down.circle" -> Icons.Rounded.ArrowDownward
    "arrow.down.circle.fill" -> Icons.Rounded.ArrowCircleDown
    "star.circle" -> Icons.Rounded.Stars
    "trophy" -> Icons.Rounded.EmojiEvents
    else -> Icons.Rounded.Star
}

private val streakGold = Color(0xFFF7C46C)

/** The shareable progress card (dark, square). */
@Composable
fun ShareCard(style: ShareCardStyle, data: ShareCardData, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val monthLabel = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("ar")).format(today) +
        " · " + DateTimeFormatter.ofPattern("MMM yyyy", Locale.US).format(today)
    Column(
        modifier
            .background(VColor.night, RoundedCornerShape(28.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Vitalyn", style = VFont.style(15f, 700, VColor.nightText))
            Spacer(Modifier.weight(1f))
            Text(monthLabel, style = VFont.style(11f, 400, VColor.nightMuted))
        }
        Spacer(Modifier.weight(1f))
        when (style) {
            ShareCardStyle.KILOS -> {
                val since = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
                    .format(Instant.ofEpochMilli(data.since).atZone(ZoneId.systemDefault()))
                Text("نزلت من $since · Lost since", style = VFont.style(13f, 400, VColor.nightMuted))
                Ltr {
                    Text(
                        "${if (data.lost >= 0) "−" else "+"}${VFormat.decimal(abs(data.lost))} kg",
                        style = VFont.style(64f, 700, VColor.mint),
                    )
                }
                val fat = data.fatChange
                if (fat != null) {
                    val muscle = data.muscleChange?.let { (if (it >= 0) "+" else "") + VFormat.decimal(it) } ?: "—"
                    Text("دهون ${VFormat.decimal(fat)} · عضلات $muscle", style = VFont.style(13f, 400, VColor.nightText))
                    Text("Fat ${VFormat.decimal(fat)} kg · Muscle $muscle kg", style = VFont.style(12f, 400, VColor.nightMuted))
                }
            }
            ShareCardStyle.CHART -> {
                Text(
                    "الوزن · Weight ${VFormat.decimal(data.start)} → ${VFormat.decimal(data.current)}",
                    style = VFont.style(13f, 400, VColor.nightMuted),
                )
                Sparkline(
                    values = if (data.series.isEmpty()) listOf(data.start, data.current) else data.series,
                    color = VColor.mint,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
            }
            ShareCardStyle.STREAK -> {
                Text("ملتزم بالأكل · On-plan streak", style = VFont.style(13f, 400, VColor.nightMuted))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${data.streak}", style = VFont.style(72f, 700, streakGold), modifier = Modifier.alignByBaseline())
                    Text("يوم · days", style = VFont.style(18f, 400, VColor.nightText), modifier = Modifier.alignByBaseline())
                }
                val cells = min(28, max(7, data.streak))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    (0 until cells).chunked(7).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            row.forEach { index ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(12.dp)
                                        .background(if (index < data.streak) streakGold else Color(0xFF2A3C36), RoundedCornerShape(3.dp)),
                                )
                            }
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("اتعمل بـ Vitalyn · Made with Vitalyn", style = VFont.style(10f, 400, Color(0xFF8FA79C)))
    }
}

/** A line through `values`, scaled to fill the box (drawn left-to-right). */
@Composable
private fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val low = values.min()
        val high = values.max()
        val span = max(0.1, high - low)
        val stroke = 4.dp.toPx()
        val inset = stroke / 2
        val width = size.width - stroke
        val height = size.height - stroke
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = inset + width * index / (values.size - 1)
            val y = inset + height * ((high - value) / span).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

