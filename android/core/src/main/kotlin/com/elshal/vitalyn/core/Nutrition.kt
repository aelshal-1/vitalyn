package com.elshal.vitalyn.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/** Biological sex as used by BMR formulas. */
@Serializable
enum class Sex(val raw: String) {
    @SerialName("male") MALE("male"),
    @SerialName("female") FEMALE("female");

    companion object {
        fun fromRaw(raw: String): Sex? = entries.firstOrNull { it.raw == raw }
    }
}

/** Everyday activity level, mapped to the standard TDEE multipliers. */
@Serializable
enum class ActivityLevel(val raw: String) {
    @SerialName("sedentary") SEDENTARY("sedentary"),
    @SerialName("light") LIGHT("light"),
    @SerialName("moderate") MODERATE("moderate"),
    @SerialName("active") ACTIVE("active"),
    @SerialName("veryActive") VERY_ACTIVE("veryActive");

    val factor: Double
        get() = when (this) {
            SEDENTARY -> 1.2
            LIGHT -> 1.375
            MODERATE -> 1.55
            ACTIVE -> 1.725
            VERY_ACTIVE -> 1.9
        }

    companion object {
        fun fromRaw(raw: String): ActivityLevel? = entries.firstOrNull { it.raw == raw }
    }
}

/** The body numbers the calculator needs. Lean mass and a measured BMR come from InBody when available. */
@Serializable
data class BodyMetrics(
    val weightKg: Double,
    val heightCm: Double,
    val ageYears: Int,
    val sex: Sex,
    val bodyFatPercent: Double? = null,
    val measuredBMR: Double? = null
) {
    val leanMassKg: Double?
        get() {
            val bodyFatPercent = bodyFatPercent ?: return null
            return weightKg * (1 - bodyFatPercent / 100)
        }
}

@Serializable
data class MacroTargets(
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int
)

/** Deterministic energy and macro math. The AI coach explains and adjusts these numbers; it never invents them. */
object NutritionCalculator {
    /** Energy in one kilogram of body fat, the usual planning figure. */
    const val kcalPerKgFat: Double = 7700.0

    /** The deficit never exceeds this share of TDEE. */
    const val maxDeficitShare: Double = 0.25

    /** Katch-McArdle: needs lean mass, so it is the most accurate once an InBody scan exists. */
    fun bmrKatchMcArdle(leanMassKg: Double): Double = 370 + 21.6 * leanMassKg

    /** Mifflin-St Jeor: fallback before the first InBody scan. */
    fun bmrMifflinStJeor(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears.toDouble()
        return if (sex == Sex.MALE) base + 5 else base - 161
    }

    /** Prefers a measured BMR, then Katch-McArdle, then Mifflin-St Jeor. */
    fun bmr(body: BodyMetrics): Double {
        body.measuredBMR?.let { return it }
        body.leanMassKg?.let { return bmrKatchMcArdle(it) }
        return bmrMifflinStJeor(body.weightKg, body.heightCm, body.ageYears, body.sex)
    }

    fun tdee(bmr: Double, activityFactor: Double): Double = bmr * activityFactor

    /** Daily deficit for a weekly loss goal, capped at [maxDeficitShare] of TDEE. */
    fun dailyDeficit(tdee: Double, weeklyLossKg: Double): Double {
        val wanted = max(0.0, weeklyLossKg) * kcalPerKgFat / 7
        return min(wanted, tdee * maxDeficitShare)
    }

    /** Calorie target, rounded to 10 kcal and never below BMR. */
    fun calorieTarget(body: BodyMetrics, activityFactor: Double, weeklyLossKg: Double): Int {
        val bmr = bmr(body)
        val tdee = tdee(bmr, activityFactor)
        val target = max(bmr, tdee - dailyDeficit(tdee, weeklyLossKg))
        return (target / 10).swiftRounded().toInt() * 10
    }

    /** Protein from lean mass (or body weight before a scan), fat as a share of calories, carbs fill the rest. */
    fun macros(
        calories: Int,
        body: BodyMetrics,
        proteinPerKgLean: Double = 2.2,
        fatShare: Double = 0.28
    ): MacroTargets {
        val proteinBase = body.leanMassKg ?: (body.weightKg * 0.75)
        val protein = roundTo5(proteinBase * proteinPerKgLean)
        val fat = roundTo5(calories.toDouble() * fatShare / 9)
        val remaining = calories.toDouble() - (protein * 4).toDouble() - (fat * 9).toDouble()
        val carbs = max(0, roundTo5(remaining / 4))
        return MacroTargets(calories = calories, proteinGrams = protein, carbsGrams = carbs, fatGrams = fat)
    }

    internal fun roundTo5(value: Double): Int = (value / 5).swiftRounded().toInt() * 5
}
