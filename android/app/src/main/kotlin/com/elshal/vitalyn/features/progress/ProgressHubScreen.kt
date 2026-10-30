package com.elshal.vitalyn.features.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.features.activity.ActivityScreen
import com.elshal.vitalyn.features.body.BodyScreen
import com.elshal.vitalyn.features.inbody.InBodyListScreen
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.LocalNavigator
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.SectionTitle
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/** Progress tab: weight trend, InBody, body/avatar, activity, achievements. */
@Composable
fun ProgressHubScreen() {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    val navigator = LocalNavigator.current
    val profile = state.profile

    // Weight points from InBody scans and Health Connect, oldest first.
    val fromScans = state.sortedScans.map { ChartPoint(it.date, it.weightKg, marked = true) }
    val fromHealth = app.health.weights.map { ChartPoint(it.instant.toEpochMilli(), it.value, marked = false) }
    val points = (fromScans + fromHealth).sortedBy { it.x }

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
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BiText("التقدم", "Progress", size = 26f, enSize = 15f)
            WeightCard(points, startWeight = profile?.startWeightKg, goal = profile?.goalWeightKg)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("جسمي 3D", "My body", Icons.Rounded.Accessibility, VColor.night, Modifier.weight(1f)) {
                    navigator.push { BodyScreen() }
                }
                Tile("InBody", "Body scans", Icons.Rounded.FindInPage, VColor.primary, Modifier.weight(1f)) {
                    navigator.push { InBodyListScreen() }
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("النشاط", "Activity", Icons.AutoMirrored.Rounded.DirectionsWalk, VColor.electric, Modifier.weight(1f)) {
                    navigator.push { ActivityScreen() }
                }
                Tile("الإنجازات", "Achievements", Icons.Rounded.EmojiEvents, Color(0xFFC2410C), Modifier.weight(1f)) {
                    navigator.push { AchievementsScreen() }
                }
            }
        }
    }
}

@Composable
private fun WeightCard(points: List<ChartPoint>, startWeight: Double?, goal: Double?) {
    val start = points.firstOrNull()?.y ?: startWeight
    val current = points.lastOrNull()?.y ?: startWeight
    val change = (current ?: 0.0) - (start ?: 0.0)
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("الوزن", "Weight", Modifier.weight(1f))
                if (current != null) {
                    Ltr { Text("${VFormat.decimal(current)} kg", style = VFont.style(22f, 600, VColor.ink)) }
                }
            }
            if (points.size > 1) {
                TrendLineChart(
                    points = points,
                    height = 200.dp,
                    goal = goal,
                    goalLabel = goal?.let { "Goal · الهدف ${VFormat.decimal(it)}" },
                    markColor = VColor.night,
                )
                val sign = if (change > 0) "+" else ""
                BiCaption(
                    ar = "التغيير $sign${VFormat.decimal(change)} كجم",
                    en = "Change $sign${VFormat.decimal(change)} kg",
                    size = 13f,
                    color = if (change <= 0) VColor.primaryDark else VColor.energy,
                )
            } else {
                BiText(
                    "ضيف InBody أو سجّل وزنك في Health Connect عشان تشوف المنحنى.",
                    "Add an InBody scan or log weight in Health Connect to see your trend.",
                    size = 13f,
                    weight = 400,
                    color = VColor.inkSecondary,
                )
            }
        }
    }
}

@Composable
private fun Tile(ar: String, en: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxHeight()
            .heightIn(min = 120.dp)
            .clip(shape)
            .background(tint, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        BiText(ar, en, size = 15f, color = Color.White, enColor = Color.White.copy(alpha = 0.85f))
    }
}

/** One point of a [TrendLineChart]: x is epoch millis; marked points get a dot. */
internal data class ChartPoint(val x: Long, val y: Double, val marked: Boolean = false)

private val axisDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

private fun Long.axisLabel(): String = axisDate.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

/**
 * A simple date/value line chart (the Swift Charts LineMark + PointMark + goal RuleMark),
 * laid out left-to-right with min/max on the side and the first/last date under it.
 * The y scale doesn't include zero.
 */
@Composable
internal fun TrendLineChart(
    points: List<ChartPoint>,
    height: Dp,
    modifier: Modifier = Modifier,
    lineColor: Color = VColor.primary,
    markColor: Color = VColor.primary,
    goal: Double? = null,
    goalLabel: String? = null,
) {
    if (points.isEmpty()) return
    val values = points.map { it.y } + listOfNotNull(goal)
    val rawLow = values.min()
    val rawHigh = values.max()
    val pad = max(0.5, (rawHigh - rawLow) * 0.1)
    val low = rawLow - pad
    val high = rawHigh + pad
    val span = high - low
    val minX = points.first().x
    val maxX = points.last().x
    val spanX = max(1L, maxX - minX).toFloat()
    val single = maxX == minX

    Ltr {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f).height(height)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val inset = 6.dp.toPx()
                        val width = size.width - inset * 2
                        fun px(x: Long): Float = if (single) size.width / 2 else inset + (x - minX) / spanX * width
                        fun py(y: Double): Float = ((high - y) / span).toFloat() * size.height

                        // Grid lines.
                        for (i in 0..3) {
                            val y = size.height * i / 3f
                            drawLine(VColor.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        }
                        // Goal rule.
                        if (goal != null) {
                            val y = py(goal)
                            drawLine(
                                lineColor.copy(alpha = 0.5f),
                                Offset(0f, y),
                                Offset(size.width, y),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                            )
                        }
                        // Line.
                        val path = Path()
                        points.forEachIndexed { index, point ->
                            val x = px(point.x)
                            val y = py(point.y)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        // Marks.
                        points.filter { it.marked }.forEach { point ->
                            drawCircle(markColor, radius = 4.dp.toPx(), center = Offset(px(point.x), py(point.y)))
                        }
                    }
                    if (goal != null && goalLabel != null) {
                        val fraction = ((high - goal) / span).toFloat()
                        Text(
                            goalLabel,
                            style = VFont.style(10f, 400, VColor.primaryDark),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 4.dp, y = height * fraction - 16.dp),
                        )
                    }
                }
                Column(
                    Modifier.height(height).padding(start = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(VFormat.decimal(rawHigh), style = VFont.style(10f, 400, VColor.muted))
                    Text(VFormat.decimal(rawLow), style = VFont.style(10f, 400, VColor.muted))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(minX.axisLabel(), style = VFont.style(10f, 400, VColor.muted))
                Spacer(Modifier.weight(1f))
                if (!single) Text(maxX.axisLabel(), style = VFont.style(10f, 400, VColor.muted))
            }
        }
    }
}
