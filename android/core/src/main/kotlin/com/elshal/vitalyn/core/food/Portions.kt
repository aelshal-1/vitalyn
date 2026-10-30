package com.elshal.vitalyn.core

/** Deterministic portion math: macros for a weight of a product, scaling an estimated item. */
object Portions {
    fun food(nameAr: String, nameEn: String, grams: Double, per100g: NutrientsPer100, productId: String?): AnalyzedFood {
        val factor = grams / 100
        return AnalyzedFood(
            nameAr = nameAr,
            nameEn = nameEn,
            grams = grams,
            kcal = (per100g.kcal * factor).swiftRounded(),
            proteinG = round1(per100g.proteinG * factor),
            carbsG = round1(per100g.carbsG * factor),
            fatG = round1(per100g.fatG * factor),
            confidence = Confidence.HIGH,
            libraryProductId = productId
        )
    }

    /** Rescales an item's macros to a new weight, keeping its per-gram density. */
    fun rescale(item: AnalyzedFood, grams: Double): AnalyzedFood {
        if (!(item.grams > 0)) return item.copy(grams = grams)
        val factor = grams / item.grams
        return item.copy(
            grams = grams,
            kcal = (item.kcal * factor).swiftRounded(),
            proteinG = round1(item.proteinG * factor),
            carbsG = round1(item.carbsG * factor),
            fatG = round1(item.fatG * factor)
        )
    }

    /** Applies a chosen answer to a clarifying question. */
    fun apply(option: QuestionOption, item: AnalyzedFood, library: List<LibraryProduct>): AnalyzedFood {
        val id = option.libraryProductId
        val product = if (id != null) library.firstOrNull { it.id == id } else null
        if (product != null) {
            val grams = option.grams ?: (if (item.grams > 0) item.grams else product.servingSizeG ?: 100.0)
            return food(product.nameAr, product.nameEn, grams, product.per100g, product.id)
        }
        val grams = option.grams
        if (grams != null) return rescale(item, grams).copy(confidence = Confidence.HIGH)
        return item
    }

    fun total(items: List<AnalyzedFood>): MacroTargets = MacroTargets(
        calories = items.sumOf { it.kcal }.swiftRounded().toInt(),
        proteinGrams = items.sumOf { it.proteinG }.swiftRounded().toInt(),
        carbsGrams = items.sumOf { it.carbsG }.swiftRounded().toInt(),
        fatGrams = items.sumOf { it.fatG }.swiftRounded().toInt()
    )

    internal fun round1(value: Double): Double = (value * 10).swiftRounded() / 10
}
