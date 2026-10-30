package com.elshal.vitalyn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** A single progress ring. */
@Composable
fun ProgressRing(
    progress: Double,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = VColor.track,
    lineWidth: Dp = 14.dp,
) {
    Canvas(modifier.padding(lineWidth / 2)) {
        val stroke = lineWidth.toPx()
        val diameter = min(size.width, size.height)
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        val sweep = 360f * progress.coerceIn(0.001, 1.0).toFloat()
        drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Three nested rings for protein, carbs and fat. */
@Composable
fun MacroRings(
    protein: Double,
    carbs: Double,
    fat: Double,
    modifier: Modifier = Modifier,
    lineWidth: Dp = 12.dp,
    colors: Triple<Color, Color, Color> = Triple(VColor.lime, VColor.amber, VColor.sky),
) {
    Box(
        modifier.semantics {
            contentDescription = "بروتين ${(protein * 100).toInt()}٪، كارب ${(carbs * 100).toInt()}٪، دهون ${(fat * 100).toInt()}٪"
        },
    ) {
        val step = lineWidth + 2.dp
        ProgressRing(protein, colors.first, Modifier.fillMaxSize(), colors.first.copy(alpha = 0.22f), lineWidth)
        ProgressRing(carbs, colors.second, Modifier.fillMaxSize().padding(step), colors.second.copy(alpha = 0.22f), lineWidth)
        ProgressRing(fat, colors.third, Modifier.fillMaxSize().padding(step * 2), colors.third.copy(alpha = 0.22f), lineWidth)
    }
}

/** Labelled horizontal macro bar. */
@Composable
fun MacroBar(ar: String, en: String, value: Int, goal: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = if (goal > 0) min(1f, value.toFloat() / goal) else 0f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ar, style = VFont.style(13f, 500, VColor.ink))
            Text(en, style = VFont.style(11f, 400, VColor.muted))
            Spacer(Modifier.weight(1f))
            Ltr { Text("$value / $goal g", style = VFont.style(13f, 400, VColor.inkSecondary)) }
        }
        Capsule(progress, color, Modifier.fillMaxWidth().height(8.dp))
    }
}

/** Rounded track with a filled part (reads from the leading edge). */
@Composable
fun Capsule(progress: Float, color: Color, modifier: Modifier = Modifier, track: Color = VColor.track) {
    Box(modifier.background(track, CircleShape)) {
        if (progress > 0f) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(color, CircleShape))
        }
    }
}

/** Two-line bilingual label used inside buttons. */
@Composable
fun BiLabel(ar: String, en: String, icon: ImageVector? = null, size: Float = 15f, color: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size((size + 5).dp), tint = if (color == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else color)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ar, style = VFont.style(size, 600, color), textAlign = TextAlign.Center)
            Text(en, style = VFont.style(max(9f, size * 0.68f), 400, color), modifier = Modifier.alpha(0.85f), textAlign = TextAlign.Center)
        }
    }
}

/** Full-width green capsule button (iOS `PrimaryButtonStyle`). */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = VColor.primary,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .background(color.copy(alpha = if (pressed) 0.85f else 1f), CircleShape)
                .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** White outlined capsule button (iOS `SecondaryButtonStyle`). */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides VColor.ink) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = 48.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .background(VColor.surface.copy(alpha = if (pressed) 0.7f else 1f), CircleShape)
                .border(1.dp, VColor.line, CircleShape)
                .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Selectable bilingual chip. */
@Composable
fun BiChip(ar: String, en: String, selected: Boolean, modifier: Modifier = Modifier, dark: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val fg = if (selected) Color.White else VColor.ink
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 46.dp)
            .background(if (selected) (if (dark) VColor.ink else VColor.primary) else VColor.surface, shape)
            .border(BorderStroke(1.dp, if (selected) Color.Transparent else VColor.line), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(ar, style = VFont.style(13f, 400, fg), textAlign = TextAlign.Center)
        Text(en, style = VFont.style(10f, 400, fg), modifier = Modifier.alpha(0.85f), textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(VColor.errorBackground, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = VColor.energy)
        Text(message, style = VFont.style(13f, 400, VColor.ink))
    }
}

@Composable
fun WorkingView(ar: String, en: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(VColor.surface, RoundedCornerShape(24.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(color = VColor.primary)
        BiText(ar, en, size = 15f, weight = 500, alignment = Alignment.CenterHorizontally)
    }
}

@Composable
fun SectionTitle(ar: String, en: String, modifier: Modifier = Modifier) {
    BiText(ar, en, modifier = modifier.fillMaxWidth(), size = 16f)
}

/** Labelled number field with a unit. Empty when the value is 0; accepts Arabic digits and decimal commas. */
@Composable
fun NumberField(
    ar: String,
    en: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "",
    digits: Int = 1,
) {
    var text by remember { mutableStateOf(NumberFieldFormat.display(value, digits)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!focused && NumberFieldFormat.parse(text) != value) text = NumberFieldFormat.display(value, digits)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BiText(ar, en, size = 14f, weight = 500)
        Spacer(Modifier.weight(1f))
        Ltr {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onValueChange(NumberFieldFormat.parse(it) ?: 0.0)
                    },
                    singleLine = true,
                    textStyle = VFont.style(16f, 600, VColor.ink).copy(textAlign = TextAlign.End),
                    cursorBrush = SolidColor(VColor.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .width(90.dp)
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (text.isEmpty()) Text("0", style = VFont.style(16f, 600, VColor.muted.copy(alpha = 0.5f)))
                            inner()
                        }
                    },
                )
                if (unit.isNotEmpty()) Text(unit, style = VFont.style(12f, 400, VColor.muted))
            }
        }
    }
}

object NumberFieldFormat {
    fun display(value: Double, digits: Int): String = if (value == 0.0) "" else VFormat.plain(value, digits)

    private val arabic = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4', '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '٫' to '.', ',' to '.', '،' to '.',
    )

    fun parse(text: String): Double? = text.map { arabic[it] ?: it }.joinToString("").trim().toDoubleOrNull()
}

/** Text field styled like the iOS rounded fields (white, line border). */
@Composable
fun VTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    ltr: Boolean = false,
) {
    val field = @Composable {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = VFont.style(15f, 400, VColor.ink),
            cursorBrush = SolidColor(VColor.primary),
            keyboardOptions = keyboardOptions,
            modifier = modifier
                .fillMaxWidth()
                .background(VColor.surface, RoundedCornerShape(16.dp))
                .border(1.dp, VColor.line, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = VFont.style(15f, 400, VColor.muted))
                    inner()
                }
            },
        )
    }
    if (ltr) Ltr(field) else field()
}

/** Round icon button (mic, send, close…). */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = VColor.primary,
    tint: Color = Color.White,
    size: Dp = 44.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.45f)
            .background(background, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}
