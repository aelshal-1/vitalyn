package com.elshal.vitalyn.core

import java.time.Instant

/** Placeholder data shown until real profile, InBody and Health data exist (phase 1). */
object SampleData {
    val body: BodyMetrics = BodyMetrics(
        weightKg = 96.4,
        heightCm = 178.0,
        ageYears = 34,
        sex = Sex.MALE,
        bodyFatPercent = 31.2,
        measuredBMR = 1880.0
    )

    val targets: MacroTargets = MacroTargets(calories = 2050, proteinGrams = 150, carbsGrams = 215, fatGrams = 65)

    val today: TodaySnapshot = TodaySnapshot(
        date = Instant.now(),
        targets = targets,
        eatenCalories = 1240,
        activeCalories = 310,
        proteinGrams = 92,
        carbsGrams = 110,
        fatGrams = 41,
        steps = 6420,
        stepsGoal = 8000,
        waterGlasses = 5,
        waterGoal = 8,
        streakDays = 21,
        weightKg = 96.4,
        weightChangeKg = -4.2
    )

    data class Meal(
        val id: Int,
        val slotAr: String,
        val slotEn: String,
        val time: String,
        val itemsAr: String,
        val itemsEn: String,
        val calories: Int
    )

    val meals: List<Meal> = listOf(
        Meal(id = 1, slotAr = "فطار", slotEn = "Breakfast", time = "8:30", itemsAr = "2 بيض مسلوق + جبنة قريش + نص رغيف بلدي", itemsEn = "2 boiled eggs, cottage cheese, half baladi bread", calories = 410),
        Meal(id = 2, slotAr = "سناك", slotEn = "Snack", time = "11:45", itemsAr = "زبادي يوناني بالشوفان", itemsEn = "Greek yogurt with oats", calories = 230),
        Meal(id = 3, slotAr = "غدا", slotEn = "Lunch", time = "3:10", itemsAr = "فراخ مشوية + رز + سلطة", itemsEn = "Grilled chicken, rice, salad", calories = 600)
    )
}
