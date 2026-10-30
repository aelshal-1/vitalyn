package com.elshal.vitalyn.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

// JSON values are kotlinx.serialization `JsonElement`s. `JsonObject` keeps keys in insertion order,
// which matters for structured-output schemas: Claude fills fields in schema order.

/** Parsing and the shared `Json` configuration. */
object JSONValue {
    /** Parses JSON text (any fragment). Throws `SerializationException` on invalid input. */
    fun parse(data: ByteArray): JsonElement = parse(String(data, Charsets.UTF_8))

    fun parse(text: String): JsonElement = Json.parseToJsonElement(text)
}

/**
 * The `Json` used to decode Claude replies and app data: ignores unknown keys, leaves out nulls when encoding
 * (like Swift's `encodeIfPresent`), and falls back to defaults for null/unknown values where a default exists.
 */
val VitalynJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** Compact JSON text, written exactly like the Swift `JSONValue.serialized()` (integral numbers without `.0`). */
fun JsonElement.serialized(): String {
    val out = StringBuilder()
    writeJson(this, out)
    return out.toString()
}

private fun writeJson(value: JsonElement, out: StringBuilder) {
    when (value) {
        is JsonNull -> out.append("null")
        is JsonPrimitive -> when {
            value.isString -> writeJsonString(value.content, out)
            value.content == "true" || value.content == "false" -> out.append(value.content)
            else -> {
                val number = value.content.toDoubleOrNull()
                when {
                    number == null -> out.append(value.content)
                    number.isFinite() && number == Math.rint(number) && abs(number) < 1e15 -> out.append(number.toLong())
                    number.isFinite() -> out.append(swiftDescription(number))
                    else -> out.append("null")
                }
            }
        }
        is JsonArray -> {
            out.append('[')
            value.forEachIndexed { index, item ->
                if (index > 0) out.append(',')
                writeJson(item, out)
            }
            out.append(']')
        }
        is JsonObject -> {
            out.append('{')
            var first = true
            for ((key, item) in value) {
                if (!first) out.append(',')
                first = false
                writeJsonString(key, out)
                out.append(':')
                writeJson(item, out)
            }
            out.append('}')
        }
    }
}

private fun writeJsonString(text: String, out: StringBuilder) {
    out.append('"')
    for (char in text) {
        when (char) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (char.code < 0x20) out.append(String.format("\\u%04x", char.code)) else out.append(char)
        }
    }
    out.append('"')
}

/** `value["key"]`: the member of an object, or null (also for non-objects and null receivers). */
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

/** The text of a JSON string (null for every other kind). */
val JsonElement?.stringValue: String?
    get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** The items of a JSON array. */
val JsonElement?.arrayValue: List<JsonElement>?
    get() = this as? JsonArray

/** The value of a JSON number (null for strings, booleans and null). */
val JsonElement?.numberValue: Double?
    get() {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString || primitive is JsonNull) return null
        if (primitive.content == "true" || primitive.content == "false") return null
        return primitive.content.toDoubleOrNull()
    }

/** The value of a JSON boolean. */
val JsonElement?.boolValue: Boolean?
    get() {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return when (primitive.content) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

/** Converts Kotlin values to JSON: String, Number, Boolean, null, JsonElement, Pair, Map, List/Array. */
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Pair<*, *> -> jsonObjectOf(this.first as String to this.second)
    is Map<*, *> -> JsonObject(LinkedHashMap<String, JsonElement>().also { map -> forEach { (k, v) -> map[k as String] = v.toJsonElement() } })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    else -> throw IllegalArgumentException("Not a JSON value: $this")
}

/** An ordered JSON object: `jsonObjectOf("type" to "object", "properties" to jsonObjectOf(...))`. */
fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject = jsonObjectOf(pairs.asList())

/** An ordered JSON object from a list of pairs. Later duplicates replace earlier values, keeping the first position. */
fun jsonObjectOf(pairs: List<Pair<String, Any?>>): JsonObject {
    val map = LinkedHashMap<String, JsonElement>()
    for ((key, value) in pairs) map[key] = value.toJsonElement()
    return JsonObject(map)
}

/** A JSON array: `jsonArrayOf(true, null, 2.5)`. */
fun jsonArrayOf(vararg items: Any?): JsonArray = JsonArray(items.map { it.toJsonElement() })
