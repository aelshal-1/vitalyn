package com.elshal.vitalyn.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.truncate

// Helpers that reproduce Swift/Foundation number and text behaviour exactly, so outputs match the iOS app.

/** Swift `Double.rounded()`: to nearest, ties away from zero (unlike Kotlin's `round`, which ties to even). */
internal fun Double.swiftRounded(): Double {
    if (isNaN() || isInfinite()) return this
    val t = truncate(this)
    return if (abs(this - t) >= 0.5) t + sign(this) else t
}

/** `String(format: "%.1f", value)`: rounds the exact binary value, ties to even (like C printf). */
internal fun formatOneDecimal(value: Double): String {
    if (value.isNaN()) return "nan"
    if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
    val text = BigDecimal(value).setScale(1, RoundingMode.HALF_EVEN).toPlainString()
    val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
    return if (negative && !text.startsWith("-")) "-$text" else text
}

/** Swift's `"\(double)"` / `String(double)`: shortest round-trip digits, `1.0`, `0.5`, `1e-05`, `1e+16`. */
internal fun swiftDescription(value: Double): String {
    if (value.isNaN()) return "nan"
    if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
    if (value == 0.0) return if (1.0 / value < 0) "-0.0" else "0.0"
    val decimal = BigDecimal(value.toString()).stripTrailingZeros()
    val magnitude = abs(value)
    if (magnitude < 1e-4 || magnitude >= 9007199254740992.0) {
        // Exponential: mantissa digits, then e+XX / e-XX with at least two exponent digits.
        val unscaled = decimal.unscaledValue().abs().toString()
        val exponent = unscaled.length - 1 - decimal.scale()
        val mantissa = if (unscaled.length > 1) unscaled[0] + "." + unscaled.substring(1) else unscaled
        val sign = if (value < 0) "-" else ""
        val expSign = if (exponent < 0) "-" else "+"
        val expDigits = abs(exponent).toString().padStart(2, '0')
        return "$sign${mantissa}e$expSign$expDigits"
    }
    val plain = decimal.toPlainString()
    return if (plain.contains('.')) plain else "$plain.0"
}

/** Characters Swift's `CharacterSet.whitespaces` holds: tab and Unicode space separators. */
internal fun Char.isSwiftWhitespace(): Boolean = this == '\t' || Character.getType(this) == Character.SPACE_SEPARATOR.toInt()

/** `trimmingCharacters(in: .whitespaces)`. */
internal fun String.trimSwiftWhitespace(): String = trim { it.isSwiftWhitespace() }

/** `trimmingCharacters(in: .whitespacesAndNewlines)`. */
internal fun String.trimSwiftWhitespaceAndNewlines(): String =
    trim { it.isSwiftWhitespace() || it in "\n\u000B\u000C\r\u0085  " }

/** Splits on Swift `Character.isNewline` characters, dropping empty pieces (`split(whereSeparator: \.isNewline)`). */
internal fun String.splitSwiftLines(): List<String> =
    split('\n', '\r', '\u000B', '\u000C', '\u0085', ' ', ' ').filter { it.isNotEmpty() }

/** Percent-encodes everything outside `allowed` as UTF-8 bytes (uppercase hex), like Foundation. */
internal fun percentEncode(text: String, allowed: (Char) -> Boolean): String {
    val out = StringBuilder()
    text.codePoints().forEach { codePoint ->
        if (codePoint < 0x80 && allowed(codePoint.toChar())) {
            out.append(codePoint.toChar())
        } else {
            val bytes = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
            for (byte in bytes) out.append('%').append(String.format("%02X", byte.toInt() and 0xFF))
        }
    }
    return out.toString()
}

private const val UNRESERVED = "-._~"
private const val SUB_DELIMS = "!$&'()*+,;="

/** URLComponents' allowed set for a query item name or value: the query set minus `&` and `=`. */
internal fun isQueryItemAllowed(char: Char): Boolean =
    char.isAsciiLetterOrDigit() || char in UNRESERVED || (char in SUB_DELIMS && char != '&' && char != '=') || char in ":@/?"

/** URL path characters. */
internal fun isPathAllowed(char: Char): Boolean =
    char.isAsciiLetterOrDigit() || char in UNRESERVED || char in SUB_DELIMS || char in ":@/"

private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
