package com.elshal.vitalyn.core

import kotlin.math.ceil
import kotlin.math.min

/** A weekly weight-loss pace expressed as a share of current body weight. */
data class PaceOption(
    /** Percent of current body weight lost per week (e.g. 0.75). */
    val percent: Double,
    /** The same pace in kg per week for this user, rounded to 0.05 kg. */
    val kgPerWeek: Double,
    val ar: String,
    val en: String,
    val noteAr: String,
    val noteEn: String,
    val recommended: Boolean
) {
    val id: Double get() = percent
}

/**
 * Paces offered to the user. Evidence-based guidance: losing about 0.5–1% of body weight per week
 * preserves lean mass and is sustainable; faster loss raises muscle loss, gallstone and rebound risk.
 */
val NutritionCalculator.pacePercents: List<Double>
    get() = listOf(0.25, 0.5, 0.75, 1.0, 1.25)

/**
 * The recommended percent per week: higher BMI tolerates faster loss; older adults and people
 * close to a healthy weight should go slower to protect muscle.
 */
fun NutritionCalculator.recommendedPacePercent(weightKg: Double, heightCm: Double, ageYears: Int): Double {
    val meters = heightCm / 100
    val bmi = if (meters > 0) weightKg / (meters * meters) else 25.0
    var percent = when {
        bmi >= 35 -> 1.0
        bmi >= 30 && bmi < 35 -> 0.75
        bmi >= 25 && bmi < 30 -> 0.75
        else -> 0.5
    }
    if (ageYears >= 60) percent = min(percent, 0.5)
    return percent
}

fun NutritionCalculator.paceOptions(weightKg: Double, heightCm: Double, ageYears: Int): List<PaceOption> {
    val recommended = recommendedPacePercent(weightKg, heightCm, ageYears)
    return pacePercents.map { percent ->
        val (labels, notes) = when (percent) {
            0.25 -> ("هادي جداً" to "Very gentle") to ("أسهل التزام، نتيجة بطيئة" to "Easiest to stick to, slow results")
            0.5 -> ("هادي" to "Gentle") to ("بيحافظ على العضلات كويس" to "Protects muscle well")
            0.75 -> ("متوازن" to "Balanced") to ("توازن بين السرعة والعضلات" to "Balance of speed and muscle")
            1.0 -> ("سريع" to "Fast") to ("أقصى حد آمن لأغلب الناس" to "Upper safe limit for most people")
            else -> ("سريع جداً" to "Very fast") to ("محتاج متابعة — خطر فقد عضلات" to "Needs supervision — muscle-loss risk")
        }
        PaceOption(
            percent = percent,
            kgPerWeek = roundToStep(weightKg * percent / 100, step = 0.05),
            ar = labels.first, en = labels.second, noteAr = notes.first, noteEn = notes.second,
            recommended = percent == recommended
        )
    }
}

/** Weeks to go from current to goal at a fixed kg/week (null when there is nothing to lose or no pace). */
fun NutritionCalculator.weeksToGoal(currentKg: Double, goalKg: Double, kgPerWeek: Double): Int? {
    if (!(kgPerWeek > 0 && currentKg > goalKg)) return null
    return ceil((currentKg - goalKg) / kgPerWeek).toInt()
}

internal fun NutritionCalculator.roundToStep(value: Double, step: Double): Double =
    ((value / step).swiftRounded() * step * 100).swiftRounded() / 100
