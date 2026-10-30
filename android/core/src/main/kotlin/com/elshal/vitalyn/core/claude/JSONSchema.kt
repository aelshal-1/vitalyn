package com.elshal.vitalyn.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builders for structured-output JSON schemas.
 * Every object lists all its properties as required and sets `additionalProperties: false`,
 * as structured outputs require. Optional values are expressed with [nullable].
 */
object JSONSchema {
    /** Swift `JSONSchema.object(_:description:)`. */
    fun obj(properties: List<Pair<String, JsonElement>>, description: String? = null): JsonObject {
        val pairs = mutableListOf<Pair<String, JsonElement>>("type" to JsonPrimitive("object"))
        if (description != null) pairs += "description" to JsonPrimitive(description)
        pairs += "properties" to jsonObjectOf(properties)
        pairs += "required" to JsonArray(properties.map { JsonPrimitive(it.first) })
        pairs += "additionalProperties" to JsonPrimitive(false)
        return jsonObjectOf(pairs)
    }

    fun obj(vararg properties: Pair<String, JsonElement>, description: String? = null): JsonObject =
        obj(properties.asList(), description)

    /** Same as [obj], under the Swift name. */
    fun `object`(properties: List<Pair<String, JsonElement>>, description: String? = null): JsonObject = obj(properties, description)

    fun string(description: String? = null): JsonObject = typed("string", description)

    fun number(description: String? = null): JsonObject = typed("number", description)

    fun integer(description: String? = null): JsonObject = typed("integer", description)

    fun boolean(description: String? = null): JsonObject = typed("boolean", description)

    fun array(items: JsonElement, description: String? = null): JsonObject {
        val pairs = mutableListOf<Pair<String, JsonElement>>("type" to JsonPrimitive("array"), "items" to items)
        if (description != null) pairs += "description" to JsonPrimitive(description)
        return jsonObjectOf(pairs)
    }

    fun enumeration(values: List<String>, description: String? = null): JsonObject {
        val pairs = mutableListOf<Pair<String, JsonElement>>(
            "type" to JsonPrimitive("string"),
            "enum" to JsonArray(values.map { JsonPrimitive(it) })
        )
        if (description != null) pairs += "description" to JsonPrimitive(description)
        return jsonObjectOf(pairs)
    }

    fun nullable(schema: JsonElement): JsonObject =
        jsonObjectOf("anyOf" to JsonArray(listOf(schema, jsonObjectOf("type" to "null"))))

    private fun typed(type: String, description: String?): JsonObject {
        val pairs = mutableListOf<Pair<String, JsonElement>>("type" to JsonPrimitive(type))
        if (description != null) pairs += "description" to JsonPrimitive(description)
        return jsonObjectOf(pairs)
    }

    /** Returns the problems that would make the API reject a schema (empty when valid). */
    fun problems(schema: JsonElement, path: String = "$"): List<String> {
        if (schema !is JsonObject) return emptyList()
        val issues = mutableListOf<String>()
        val keys = schema.keys
        for (banned in listOf("minimum", "maximum", "minLength", "maxLength", "multipleOf", "minItems", "maxItems")) {
            if (banned in keys) issues += "$path: unsupported keyword $banned"
        }
        if (schema["type"].stringValue == "object") {
            if (schema["additionalProperties"].boolValue != false) {
                issues += "$path: additionalProperties must be false"
            }
            val properties = schema["properties"]
            if (properties is JsonObject) {
                val required = schema["required"].arrayValue?.mapNotNull { it.stringValue }?.toSet() ?: emptySet()
                for ((name, child) in properties) {
                    if (name !in required) issues += "$path.$name: not required"
                    issues += problems(child, "$path.$name")
                }
            } else {
                issues += "$path: object without properties"
            }
        }
        val items = schema["items"]
        if (items != null) issues += problems(items, "$path[]")
        schema["anyOf"].arrayValue?.forEachIndexed { index, option ->
            issues += problems(option, "$path|$index")
        }
        return issues
    }
}
