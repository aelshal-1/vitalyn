package com.elshal.vitalyn.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.elshal.vitalyn.AppGraph
import com.elshal.vitalyn.MainActivity
import com.elshal.vitalyn.core.SampleData
import com.elshal.vitalyn.core.TodaySnapshot
import com.elshal.vitalyn.data.SnapshotStore
import com.elshal.vitalyn.data.WaterStore
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

// Home-screen widgets (the iOS WidgetKit bundle), drawn with Glance from the snapshot the app publishes.

object VitalynWidgets {
    suspend fun updateAll(context: Context) {
        runCatching {
            FuelWidget().updateAll(context)
            StreakWidget().updateAll(context)
            StepsWidget().updateAll(context)
            WaterWidget().updateAll(context)
            JourneyWidget().updateAll(context)
        }
    }

    fun snapshot(context: Context): TodaySnapshot = SnapshotStore.load(AppGraph.get(context).prefs) ?: SampleData.today
}

private fun color(value: Color) = ColorProvider(value)

private fun style(size: Int, color: Color, bold: Boolean = false) =
    TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

private val openApp get() = actionStartActivity<MainActivity>()

/** Progress rings drawn into a bitmap (Glance has no arcs). */
private fun ringsBitmap(values: List<Pair<Double, Color>>, track: Color? = null, sizePx: Int = 360, line: Float = 32f): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = line
        strokeCap = Paint.Cap.ROUND
    }
    values.forEachIndexed { index, (progress, tint) ->
        val inset = line / 2 + index * (line + 6f)
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        paint.color = (track ?: tint.copy(alpha = 0.22f)).toArgb()
        canvas.drawArc(rect, 0f, 360f, false, paint)
        paint.color = tint.toArgb()
        canvas.drawArc(rect, -90f, 360f * progress.coerceIn(0.001, 1.0).toFloat(), false, paint)
    }
    return bitmap
}

// Fuel: calories left + macro rings

class FuelWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = VitalynWidgets.snapshot(context)
        val rings = ringsBitmap(
            listOf(snapshot.proteinProgress to VColor.lime, snapshot.carbsProgress to VColor.amber, snapshot.fatProgress to VColor.sky),
        )
        provideContent { FuelContent(snapshot, rings) }
    }

    @Composable
    private fun FuelContent(snapshot: TodaySnapshot, rings: Bitmap) {
        val wide = LocalSize.current.width >= 220.dp
        Box(
            GlanceModifier.fillMaxSize().background(Color(0xFF0A0F0D)).cornerRadius(22.dp).padding(14.dp).clickable(openApp),
            contentAlignment = Alignment.Center,
        ) {
            if (wide) {
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(rings), contentDescription = null, modifier = GlanceModifier.size(112.dp))
                    Spacer(GlanceModifier.width(14.dp))
                    Column {
                        CaloriesLabel(snapshot, 36)
                        Spacer(GlanceModifier.height(6.dp))
                        Row {
                            Legend(snapshot.proteinGrams, "P", "بروتين", VColor.lime)
                            Spacer(GlanceModifier.width(10.dp))
                            Legend(snapshot.carbsGrams, "C", "كارب", VColor.amber)
                            Spacer(GlanceModifier.width(10.dp))
                            Legend(snapshot.fatGrams, "F", "دهون", VColor.sky)
                        }
                    }
                }
            } else {
                Image(ImageProvider(rings), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
                CaloriesLabel(snapshot, 22)
            }
        }
    }

    @Composable
    private fun CaloriesLabel(snapshot: TodaySnapshot, size: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(VFormat.number(snapshot.caloriesLeft), style = style(size, Color.White, bold = true))
            Text("سعرة باقية · kcal left", style = style(10, Color(0xFFC9D4CF)))
        }
    }

    @Composable
    private fun Legend(value: Int, letter: String, ar: String, tint: Color) {
        Column {
            Text("$letter $value", style = style(12, tint, bold = true))
            Text(ar, style = style(10, tint))
        }
    }
}

// Streak

class StreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = VitalynWidgets.snapshot(context)
        provideContent {
            val ink = Color(0xFF2A0C04)
            Column(GlanceModifier.fillMaxSize().background(VColor.coral).cornerRadius(22.dp).padding(14.dp).clickable(openApp)) {
                Text("🔥 التزام", style = style(12, ink, bold = true))
                Text("Streak", style = style(11, ink))
                Spacer(GlanceModifier.defaultWeight())
                Text("${snapshot.streakDays}", style = style(48, ink, bold = true))
                Text("يوم ورا بعض · days", style = style(11, ink))
            }
        }
    }
}

// Steps

class StepsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = VitalynWidgets.snapshot(context)
        val ring = ringsBitmap(listOf(snapshot.stepsProgress to Color.White), track = Color(0xFF5A7DFF), sizePx = 160, line = 18f)
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(VColor.electric).cornerRadius(22.dp).padding(14.dp).clickable(openApp)) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text("خطوات", style = style(12, Color.White, bold = true))
                        Text("Steps", style = style(11, Color.White))
                    }
                    Image(ImageProvider(ring), contentDescription = null, modifier = GlanceModifier.size(44.dp))
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(VFormat.number(snapshot.steps), style = style(32, Color.White, bold = true))
                Text("الهدف · Goal ${VFormat.number(snapshot.stepsGoal)}", style = style(11, Color.White))
            }
        }
    }
}

// Water (interactive)

class WaterWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = VitalynWidgets.snapshot(context)
        val today = snapshot.date.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
        // The widget's own button writes straight to WaterStore, so read it rather than the (maybe older) snapshot.
        val glasses = if (today) WaterStore.glasses(AppGraph.get(context).prefs) else 0
        val fraction = if (snapshot.waterGoal > 0) min(1.0, glasses.toDouble() / snapshot.waterGoal) else 0.0
        provideContent {
            val ink = Color(0xFF0D2A66)
            val height = LocalSize.current.height
            Box(GlanceModifier.fillMaxSize().background(Color(0xFFE6F0FF)).cornerRadius(22.dp), contentAlignment = Alignment.BottomStart) {
                Box(GlanceModifier.fillMaxWidth().height(height * fraction.toFloat()).background(Color(0xFFBFD6FF))) {}
                Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                    Text("مية", style = style(12, ink, bold = true))
                    Text("Water", style = style(11, ink))
                    Spacer(GlanceModifier.defaultWeight())
                    Text("$glasses/${snapshot.waterGoal}", style = style(30, ink, bold = true))
                    Spacer(GlanceModifier.height(6.dp))
                    Box(
                        GlanceModifier.fillMaxWidth().height(34.dp).background(VColor.electric).cornerRadius(17.dp)
                            .clickable(actionRunCallback<AddWaterAction>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ كوباية · glass", style = style(12, Color.White, bold = true))
                    }
                }
            }
        }
    }
}

/** "+1 glass" from the water widget (runs without opening the app). */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WaterStore.add(AppGraph.get(context).prefs, 1)
        VitalynWidgets.updateAll(context)
    }
}

// Journey

class JourneyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = VitalynWidgets.snapshot(context)
        provideContent {
            val small = LocalSize.current.width < 220.dp
            val change = snapshot.weightChangeKg
            Column(GlanceModifier.fillMaxSize().background(Color(0xFF0D3B2B)).cornerRadius(22.dp).padding(14.dp).clickable(openApp)) {
                Text("رحلتي", style = style(12, Color(0xFF9FF0C6), bold = true))
                Text("My journey", style = style(11, Color(0xFF9FF0C6)))
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    (if (change <= 0) "−" else "+") + String.format(Locale.US, "%.1f", abs(change)) + " kg",
                    style = style(if (small) 32 else 42, VColor.lime, bold = true),
                )
                Text("دلوقتي · now " + String.format(Locale.US, "%.1f", snapshot.weightKg) + " kg", style = style(11, Color(0xFFCFE5DA)))
            }
        }
    }
}

class FuelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FuelWidget()
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}

class WaterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaterWidget()
}

class JourneyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JourneyWidget()
}
