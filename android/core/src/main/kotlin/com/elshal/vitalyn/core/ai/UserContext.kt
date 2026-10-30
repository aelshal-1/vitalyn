package com.elshal.vitalyn.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max

/** Everything Claude should know about the user for a request, rendered as plain text. */
data class UserContext(
    val name: String = "",
    val ageYears: Int,
    val sex: Sex,
    val heightCm: Double,
    val weightKg: Double,
    val goalWeightKg: Double,
    val weeklyLossKg: Double,
    val activity: ActivityLevel,
    val conditions: List<ChronicCondition> = emptyList(),
    val otherConditions: String = "",
    val medications: String = "",
    val allergies: String = "",
    val preferences: List<String> = emptyList(),
    val targets: MacroTargets,
    val eatenToday: MacroTargets = MacroTargets(calories = 0, proteinGrams = 0, carbsGrams = 0, fatGrams = 0),
    val mealsToday: List<String> = emptyList(),
    val stepsToday: Int = 0,
    val activeCaloriesToday: Int = 0,
    val latestInBody: String? = null,
    val weightTrend: String? = null,
    /** Lines describing identified conditions/medications/allergies and what they mean for diet and exercise. */
    val healthInsights: List<String> = emptyList()
) {
    /**
     * The prompt text. Like the iOS app (`Date.ISO8601FormatStyle`, which defaults to GMT),
     * the "Today" heading shows the UTC calendar date of [today].
     */
    fun promptText(today: Instant = Instant.now()): String = promptText(today.atOffset(ZoneOffset.UTC).toLocalDate())

    /** The prompt text with an explicit date for the "Today" heading. */
    fun promptText(today: LocalDate): String {
        val lines = mutableListOf<String>()
        lines += "## User profile"
        if (name.isNotEmpty()) lines += "- Name: $name"
        lines += "- ${sex.raw}, $ageYears years, ${heightCm.toInt()} cm, ${format(weightKg)} kg"
        lines += "- Goal: ${format(goalWeightKg)} kg at about ${format(weeklyLossKg)} kg/week"
        lines += "- Everyday activity: ${activity.raw}"

        lines += "## Health"
        if (conditions.isEmpty() && otherConditions.isEmpty()) {
            lines += "- No chronic conditions reported"
        }
        for (condition in conditions) {
            lines += "- ${condition.en}: ${condition.guidance}"
        }
        if (otherConditions.isNotEmpty()) lines += "- Other conditions: $otherConditions"
        if (medications.isNotEmpty()) lines += "- Medications: $medications"
        if (allergies.isNotEmpty()) lines += "- Food allergies / intolerances: $allergies — never suggest these"
        if (preferences.isNotEmpty()) lines += "- Food preferences: ${preferences.joinToString("; ")}"
        for (insight in healthInsights) lines += "- $insight"

        lines += "## Daily targets (calculated by the app — use these, do not invent new targets)"
        lines += "- ${targets.calories} kcal, protein ${targets.proteinGrams} g, carbs ${targets.carbsGrams} g, fat ${targets.fatGrams} g"

        lines += "## Today ($today)"
        lines += "- Eaten: ${eatenToday.calories} kcal, protein ${eatenToday.proteinGrams} g, carbs ${eatenToday.carbsGrams} g, fat ${eatenToday.fatGrams} g"
        lines += "- Remaining: ${max(0, targets.calories - eatenToday.calories)} kcal, protein ${max(0, targets.proteinGrams - eatenToday.proteinGrams)} g"
        for (meal in mealsToday) lines += "- Meal: $meal"
        lines += "- Steps: $stepsToday, active energy: $activeCaloriesToday kcal"

        latestInBody?.let { lines += "## Latest InBody\n$it" }
        weightTrend?.let { lines += "## Weight trend\n$it" }
        return lines.joinToString("\n")
    }

    companion object {
        /** `96` for whole numbers, otherwise one decimal (`96.4`). */
        fun format(value: Double): String =
            if (value == value.swiftRounded()) value.toLong().toString() else formatOneDecimal(value)
    }
}
