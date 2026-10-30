package com.elshal.vitalyn.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Barcode lookup in the free Open Food Facts database, used when a product is not in the user's library yet. */
object OpenFoodFacts {
    /** The API URL for a barcode (digits only), or null when it has no digits. */
    fun url(barcode: String): String? {
        val digits = barcode.filter { it.isSwiftNumber() }
        if (digits.isEmpty()) return null
        return "https://world.openfoodfacts.org/api/v2/product/${percentEncode(digits, ::isPathAllowed)}.json" +
            "?fields=product_name,product_name_ar,product_name_en,brands,serving_size,serving_quantity,nutriments"
    }

    private val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Looks a barcode up (on [Dispatchers.IO]); null when it is unknown or has no calories. Network errors throw. */
    suspend fun lookup(barcode: String, client: OkHttpClient = defaultHttpClient): ProductExtraction? {
        val url = url(barcode) ?: return null
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Vitalyn/0.1 (personal health app)")
            .build()
        val data = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { it.body?.bytes() ?: ByteArray(0) }
        }
        return parse(data, barcode)
    }

    /** Maps an Open Food Facts response to a product, or null when it is missing or has no calories. */
    fun parse(data: ByteArray, barcode: String): ProductExtraction? {
        val json = try {
            JSONValue.parse(data)
        } catch (_: Exception) {
            return null
        }
        if (number(json["status"]) != 1.0) return null
        val product = json["product"] ?: return null
        val nutriments = product["nutriments"] ?: return null
        val kcal = number(nutriments["energy-kcal_100g"]) ?: return null

        val english = product["product_name_en"].stringValue.nonEmpty() ?: product["product_name"].stringValue.nonEmpty() ?: barcode
        val arabic = product["product_name_ar"].stringValue.nonEmpty() ?: english
        val brand = product["brands"].stringValue?.split(',')?.firstOrNull { it.isNotEmpty() }?.trimSwiftWhitespace()
        val servingDesc = product["serving_size"].stringValue ?: ""
        val sodiumGrams = number(nutriments["sodium_100g"])

        return ProductExtraction(
            nameAr = arabic,
            nameEn = english,
            brand = brand,
            barcode = barcode,
            servingSizeG = number(product["serving_quantity"]),
            servingDescAr = servingDesc,
            servingDescEn = servingDesc,
            per100g = NutrientsPer100(
                kcal = kcal,
                proteinG = number(nutriments["proteins_100g"]) ?: 0.0,
                carbsG = number(nutriments["carbohydrates_100g"]) ?: 0.0,
                fatG = number(nutriments["fat_100g"]) ?: 0.0,
                fiberG = number(nutriments["fiber_100g"]),
                sugarG = number(nutriments["sugars_100g"]),
                sodiumMg = sodiumGrams?.let { it * 1000 }
            )
        )
    }

    /** Open Food Facts sometimes sends numbers as strings. */
    internal fun number(value: JsonElement?): Double? {
        val primitive = value as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content.toSwiftDoubleOrNull() else value.numberValue
    }

    /** Swift `Double(text)`: no surrounding spaces, no Java-only suffixes like `d`/`f`. */
    private fun String.toSwiftDoubleOrNull(): Double? {
        if (isEmpty() || first().isWhitespace() || last().isWhitespace()) return null
        if (last().lowercaseChar() == 'd' || last().lowercaseChar() == 'f') return null
        return toDoubleOrNull()
    }

    private fun String?.nonEmpty(): String? = if (this == null || trimSwiftWhitespace().isEmpty()) null else this

    private fun Char.isSwiftNumber(): Boolean = when (Character.getType(this).toByte()) {
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }
}
