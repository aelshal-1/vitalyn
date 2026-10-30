package com.elshal.vitalyn.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Chronic conditions the user can pick. Each carries guidance passed to Claude and may adjust macro targets. */
@Serializable
enum class ChronicCondition(val raw: String) {
    @SerialName("type2Diabetes") TYPE2_DIABETES("type2Diabetes"),
    @SerialName("insulinResistance") INSULIN_RESISTANCE("insulinResistance"),
    @SerialName("hypertension") HYPERTENSION("hypertension"),
    @SerialName("highCholesterol") HIGH_CHOLESTEROL("highCholesterol"),
    @SerialName("thyroid") THYROID("thyroid"),
    @SerialName("ibs") IBS("ibs"),
    @SerialName("gout") GOUT("gout"),
    @SerialName("fattyLiver") FATTY_LIVER("fattyLiver"),
    @SerialName("kidneyDisease") KIDNEY_DISEASE("kidneyDisease");

    val id: String get() = raw

    val ar: String
        get() = when (this) {
            TYPE2_DIABETES -> "سكر النوع التاني"
            INSULIN_RESISTANCE -> "مقاومة إنسولين"
            HYPERTENSION -> "ضغط عالي"
            HIGH_CHOLESTEROL -> "كوليسترول"
            THYROID -> "غدة درقية"
            IBS -> "قولون عصبي"
            GOUT -> "نقرس"
            FATTY_LIVER -> "كبد دهني"
            KIDNEY_DISEASE -> "مشاكل كلى"
        }

    val en: String
        get() = when (this) {
            TYPE2_DIABETES -> "Type 2 diabetes"
            INSULIN_RESISTANCE -> "Insulin resistance"
            HYPERTENSION -> "Hypertension"
            HIGH_CHOLESTEROL -> "High cholesterol"
            THYROID -> "Thyroid condition"
            IBS -> "IBS"
            GOUT -> "Gout"
            FATTY_LIVER -> "Fatty liver"
            KIDNEY_DISEASE -> "Kidney disease"
        }

    /** Short guidance included in every prompt. */
    val guidance: String
        get() = when (this) {
            TYPE2_DIABETES -> "Prefer low-GI carbs, spread carbs across meals, pair carbs with protein/fiber, avoid sugary drinks; watch hypoglycemia risk with exercise if on medication."
            INSULIN_RESISTANCE -> "Prefer low-GI, high-fiber carbs and higher protein; limit refined carbs and sugar."
            HYPERTENSION -> "Keep sodium low (pickles, cheeses, processed meats, salty bread are common Egyptian sources); favor potassium-rich vegetables."
            HIGH_CHOLESTEROL -> "Limit saturated and trans fat (ghee, fatty meats, fried food); favor fish, olive oil, oats, legumes."
            THYROID -> "Keep meals regular; note thyroid medication is usually taken on an empty stomach away from calcium/iron-rich food."
            IBS -> "Watch common triggers (high-FODMAP foods like onion, garlic, beans) and suggest gentler alternatives."
            GOUT -> "Limit organ meats, red meat, sardines/anchovies, and sugary drinks; encourage water."
            FATTY_LIVER -> "Avoid added sugar and fructose drinks, limit refined carbs, favor gradual weight loss and regular activity."
            KIDNEY_DISEASE -> "Do not push high protein; keep sodium and potassium moderate; defer to the user's nephrologist."
        }

    companion object {
        fun fromRaw(raw: String): ChronicCondition? = entries.firstOrNull { it.raw == raw }
    }
}

/** Calorie and macro targets, adjusted for chronic conditions. */
fun NutritionCalculator.targets(
    body: BodyMetrics,
    activityFactor: Double,
    weeklyLossKg: Double,
    conditions: List<ChronicCondition>
): MacroTargets {
    val calories = calorieTarget(body, activityFactor, weeklyLossKg)
    val carbSensitive = ChronicCondition.TYPE2_DIABETES in conditions || ChronicCondition.INSULIN_RESISTANCE in conditions
    val protein = if (ChronicCondition.KIDNEY_DISEASE in conditions) 1.2 else 2.2
    return macros(calories, body, proteinPerKgLean = protein, fatShare = if (carbSensitive) 0.33 else 0.28)
}
