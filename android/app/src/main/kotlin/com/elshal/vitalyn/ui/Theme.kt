package com.elshal.vitalyn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elshal.vitalyn.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Colors from the Vitalyn design canvas. */
object VColor {
    val ground = Color(0xFFF3F5F1)
    val surface = Color.White
    val ink = Color(0xFF0F1F1A)
    val inkSecondary = Color(0xFF4A5A54)
    val muted = Color(0xFF5F6D67)
    val line = Color(0xFFE2E7E2)
    val track = Color(0xFFEEF1EC)

    val primary = Color(0xFF0E7A55)
    val primaryDark = Color(0xFF0A5C40)
    val primarySoft = Color(0xFFDDF1E7)
    val mint = Color(0xFF7FE0B0)
    val energy = Color(0xFFB83F1C)
    val water = Color(0xFF3E63E0)

    val protein = Color(0xFF0E7A55)
    val carbs = Color(0xFFF2A93B)
    val fat = Color(0xFF5B7CFA)

    val night = Color(0xFF0F1F1A)
    val nightText = Color(0xFFEAF3EE)
    val nightMuted = Color(0xFFB9CCC3)
    val nightLine = Color(0xFF3A4B45)

    // Widget palette (bolder than the app).
    val lime = Color(0xFFC6F432)
    val amber = Color(0xFFFFB020)
    val sky = Color(0xFF4DA3FF)
    val coral = Color(0xFFFF5A36)
    val electric = Color(0xFF2F5BFF)

    val errorBackground = Color(0xFFFDE4DC)
}

/** Readex Pro is a variable font, so each weight is an instance of its `wght` axis. */
object VFont {
    private val weights = listOf(200, 300, 400, 500, 600, 700)

    val readex: FontFamily = FontFamily(
        weights.map { weight ->
            Font(
                R.font.readex_pro,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )

    /** Same idea as the iOS `VFont.readex(size, weight:)`; weight is the CSS-style number (400 = regular). */
    fun style(size: Float, weight: Int = 400, color: Color = Color.Unspecified): TextStyle {
        val snapped = weights.minBy { kotlin.math.abs(it - weight) }
        return TextStyle(fontFamily = readex, fontSize = size.sp, fontWeight = FontWeight(snapped), color = color)
    }
}

/** Number formatting stays in Western digits (1,240) inside the Arabic layout. */
object VFormat {
    private val locale = Locale.US

    fun number(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun decimal(value: Double, digits: Int = 1): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value)

    /** Up to `digits` decimals, no trailing zeros, no grouping (for editable fields). */
    fun plain(value: Double, digits: Int = 1): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = digits
            isGroupingUsed = false
        }.format(value)
}

@Composable
fun VitalynTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VColor.primary,
            onPrimary = Color.White,
            primaryContainer = VColor.primarySoft,
            onPrimaryContainer = VColor.primaryDark,
            secondary = VColor.primaryDark,
            background = VColor.ground,
            onBackground = VColor.ink,
            surface = VColor.surface,
            onSurface = VColor.ink,
            surfaceVariant = VColor.track,
            onSurfaceVariant = VColor.muted,
            outline = VColor.line,
            error = VColor.energy,
        ),
        typography = MaterialTheme.typography.let { t ->
            t.copy(
                bodyLarge = t.bodyLarge.copy(fontFamily = VFont.readex),
                bodyMedium = t.bodyMedium.copy(fontFamily = VFont.readex),
                bodySmall = t.bodySmall.copy(fontFamily = VFont.readex),
                labelLarge = t.labelLarge.copy(fontFamily = VFont.readex),
                labelMedium = t.labelMedium.copy(fontFamily = VFont.readex),
                labelSmall = t.labelSmall.copy(fontFamily = VFont.readex),
                titleLarge = t.titleLarge.copy(fontFamily = VFont.readex),
                titleMedium = t.titleMedium.copy(fontFamily = VFont.readex),
                titleSmall = t.titleSmall.copy(fontFamily = VFont.readex),
                headlineSmall = t.headlineSmall.copy(fontFamily = VFont.readex),
            )
        },
    ) {
        // The app is laid out right-to-left (Arabic first) whatever the phone language is.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

/** Lays out its content left-to-right (numbers with units, English-only rows) inside the RTL app. */
@Composable
fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/** Every label in Vitalyn shows Arabic first and English under it. */
@Composable
fun BiText(
    ar: String,
    en: String,
    modifier: Modifier = Modifier,
    size: Float = 16f,
    weight: Int = 600,
    color: Color = VColor.ink,
    enColor: Color = VColor.muted,
    enSize: Float? = null,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    val textAlign = if (alignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$ar — $en" },
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(ar, style = VFont.style(size, weight, color), textAlign = textAlign)
        Text(en, style = VFont.style(enSize ?: max(9f, (size * 0.75f).roundToInt().toFloat()), 400, enColor), textAlign = textAlign)
    }
}

/** Inline "عربي · English" label for small captions. */
@Composable
fun BiCaption(ar: String, en: String, modifier: Modifier = Modifier, size: Float = 12f, color: Color = VColor.muted) {
    Text("$ar · $en", modifier = modifier, style = VFont.style(size, 400, color))
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    background: Color = VColor.surface,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(24.dp))
            .padding(padding),
        content = content,
    )
}
