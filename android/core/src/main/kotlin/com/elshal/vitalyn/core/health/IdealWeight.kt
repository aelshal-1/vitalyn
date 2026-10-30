package com.elshal.vitalyn.core

import kotlin.math.max
import kotlin.math.min

/** A suggested goal weight and the healthy range around it. */
data class IdealWeight(
    val goalKg: Double,
    val healthyLowKg: Double,
    val healthyHighKg: Double,
    /** True when the current weight is already at or below the suggestion. */
    val alreadyThere: Boolean
)

/**
 * Suggests a goal weight from height, age, sex and current weight (and body fat when an InBody scan exists).
 *
 * - Healthy range: BMI 18.5–24.9 for the height.
 * - Target BMI rises slightly with age (22.5 under 35, 23.5 for 35–54, 24.5 from 55), 0.5 lower for women.
 * - With body fat known, the goal keeps current lean mass at a healthy body-fat level
 *   (men 18% / 20% from 40, women 25% / 28% from 40), kept inside the healthy BMI range.
 * - The goal is never above the current weight, and is rounded to 0.5 kg.
 */
fun NutritionCalculator.idealWeight(
    heightCm: Double,
    ageYears: Int,
    sex: Sex,
    currentWeightKg: Double,
    bodyFatPercent: Double? = null
): IdealWeight {
    val meters = heightCm / 100
    val squared = meters * meters
    val low = 18.5 * squared
    val high = 24.9 * squared

    var targetBMI = if (ageYears < 35) 22.5 else if (ageYears < 55) 23.5 else 24.5
    if (sex == Sex.FEMALE) targetBMI -= 0.5
    var goal = targetBMI * squared

    if (bodyFatPercent != null && bodyFatPercent > 0 && bodyFatPercent < 70) {
        val lean = currentWeightKg * (1 - bodyFatPercent / 100)
        val targetFat = when (sex) {
            Sex.MALE -> if (ageYears < 40) 0.18 else 0.20
            Sex.FEMALE -> if (ageYears < 40) 0.25 else 0.28
        }
        goal = lean / (1 - targetFat)
    }

    goal = min(max(goal, low), high)
    val alreadyThere = currentWeightKg <= goal
    if (alreadyThere) goal = currentWeightKg
    return IdealWeight(
        goalKg = roundHalf(goal),
        healthyLowKg = roundHalf(low),
        healthyHighKg = roundHalf(high),
        alreadyThere = alreadyThere
    )
}

internal fun NutritionCalculator.roundHalf(value: Double): Double = (value * 2).swiftRounded() / 2
