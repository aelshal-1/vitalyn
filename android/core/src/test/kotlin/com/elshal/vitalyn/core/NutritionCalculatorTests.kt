package com.elshal.vitalyn.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NutritionCalculatorTests {
    private val body = BodyMetrics(weightKg = 96.4, heightCm = 178.0, ageYears = 34, sex = Sex.MALE, bodyFatPercent = 31.2)

    @Test
    fun leanMassFromBodyFat() {
        val lean = assertNotNull(body.leanMassKg)
        assertTrue(abs(lean - 66.32) < 0.01)
    }

    @Test
    fun katchMcArdle() {
        assertTrue(abs(NutritionCalculator.bmrKatchMcArdle(leanMassKg = 66.32) - 1802.5) < 0.1)
    }

    @Test
    fun mifflinStJeor() {
        val male = NutritionCalculator.bmrMifflinStJeor(weightKg = 96.4, heightCm = 178.0, ageYears = 34, sex = Sex.MALE)
        val female = NutritionCalculator.bmrMifflinStJeor(weightKg = 96.4, heightCm = 178.0, ageYears = 34, sex = Sex.FEMALE)
        assertTrue(abs(male - 1911.5) < 0.001)
        assertTrue(abs((male - female) - 166) < 0.001)
    }

    @Test
    fun bmrPrefersMeasuredThenLeanMass() {
        val measured = body.copy(measuredBMR = 1880.0)
        assertEquals(1880.0, NutritionCalculator.bmr(measured))
        assertTrue(abs(NutritionCalculator.bmr(body) - NutritionCalculator.bmrKatchMcArdle(body.leanMassKg!!)) < 0.001)

        val noScan = body.copy(bodyFatPercent = null)
        assertTrue(abs(NutritionCalculator.bmr(noScan) - 1911.5) < 0.001)
    }

    @Test
    fun deficitFollowsWeeklyGoal() {
        assertTrue(abs(NutritionCalculator.dailyDeficit(tdee = 2700.0, weeklyLossKg = 0.6) - 660) < 0.001)
        assertEquals(0.0, NutritionCalculator.dailyDeficit(tdee = 2700.0, weeklyLossKg = -1.0))
    }

    @Test
    fun deficitIsCapped() {
        assertEquals(500.0, NutritionCalculator.dailyDeficit(tdee = 2000.0, weeklyLossKg = 2.0))
    }

    @Test
    fun calorieTargetMatchesDesignExample() {
        val scanned = body.copy(measuredBMR = 1880.0)
        val target = NutritionCalculator.calorieTarget(scanned, activityFactor = 1.44, weeklyLossKg = 0.6)
        assertEquals(2050, target)
    }

    @Test
    fun calorieTargetNeverBelowBMR() {
        val scanned = body.copy(measuredBMR = 1880.0)
        val target = NutritionCalculator.calorieTarget(scanned, activityFactor = 1.2, weeklyLossKg = 1.5)
        assertTrue(target >= 1880)
    }

    @Test
    fun macrosAddUpToCalories() {
        val macros = NutritionCalculator.macros(calories = 2050, body = body)
        val total = macros.proteinGrams * 4 + macros.carbsGrams * 4 + macros.fatGrams * 9
        assertTrue(abs(total - 2050) <= 15)
        assertEquals(145, macros.proteinGrams)
        assertEquals(65, macros.fatGrams)
    }

    @Test
    fun activityFactors() {
        assertEquals(1.2, ActivityLevel.SEDENTARY.factor)
        assertEquals(1.55, ActivityLevel.MODERATE.factor)
    }
}

class TodaySnapshotTests {
    @Test
    fun caloriesLeftNeverNegative() {
        var snapshot = SampleData.today
        assertEquals(810, snapshot.caloriesLeft)
        snapshot = snapshot.copy(eatenCalories = 3000)
        assertEquals(0, snapshot.caloriesLeft)
        assertEquals(1.0, snapshot.calorieProgress)
    }

    /** iOS saves through SnapshotStore (UserDefaults); on Android storage is the app's job, so this checks the JSON round trip. */
    @Test
    fun roundTripThroughStore() {
        val text = VitalynJson.encodeToString(TodaySnapshot.serializer(), SampleData.today)
        val loaded = assertNotNull(VitalynJson.decodeFromString(TodaySnapshot.serializer(), text))
        assertEquals(SampleData.today.steps, loaded.steps)
        assertEquals(SampleData.today.targets, loaded.targets)
        assertEquals(SampleData.today.date, loaded.date)
    }
}

class IdealWeightTests {
    @Test
    fun healthyRangeForHeight() {
        val ideal = NutritionCalculator.idealWeight(heightCm = 178.0, ageYears = 34, sex = Sex.MALE, currentWeightKg = 96.4)
        assertEquals(58.5, ideal.healthyLowKg)
        assertEquals(79.0, ideal.healthyHighKg)
        // BMI 22.5 × 1.78² = 71.3 → 71.5
        assertEquals(71.5, ideal.goalKg)
        assertFalse(ideal.alreadyThere)
    }

    @Test
    fun targetRisesWithAgeAndDropsForWomen() {
        val young = NutritionCalculator.idealWeight(heightCm = 170.0, ageYears = 25, sex = Sex.MALE, currentWeightKg = 90.0)
        val older = NutritionCalculator.idealWeight(heightCm = 170.0, ageYears = 60, sex = Sex.MALE, currentWeightKg = 90.0)
        val woman = NutritionCalculator.idealWeight(heightCm = 170.0, ageYears = 25, sex = Sex.FEMALE, currentWeightKg = 90.0)
        assertTrue(older.goalKg > young.goalKg)
        assertTrue(woman.goalKg < young.goalKg)
    }

    @Test
    fun bodyFatKeepsLeanMass() {
        // 96.4 kg at 31.2% fat → lean 66.3 kg; at 18% fat → 80.9 kg, capped at BMI 24.9 (79 kg).
        val ideal = NutritionCalculator.idealWeight(heightCm = 178.0, ageYears = 34, sex = Sex.MALE, currentWeightKg = 96.4, bodyFatPercent = 31.2)
        assertEquals(79.0, ideal.goalKg)
    }

    @Test
    fun neverAboveCurrentWeight() {
        val ideal = NutritionCalculator.idealWeight(heightCm = 178.0, ageYears = 34, sex = Sex.MALE, currentWeightKg = 68.0)
        assertTrue(ideal.alreadyThere)
        assertEquals(68.0, ideal.goalKg)
    }
}

class PaceTests {
    @Test
    fun optionsScaleWithWeight() {
        val options = NutritionCalculator.paceOptions(weightKg = 96.4, heightCm = 178.0, ageYears = 34)
        assertEquals(listOf(0.25, 0.5, 0.75, 1.0, 1.25), options.map { it.percent })
        // 0.75% of 96.4 = 0.723 → 0.70 kg/week; 1% = 0.964 → 0.95
        assertEquals(0.7, options[2].kgPerWeek)
        assertEquals(0.95, options[3].kgPerWeek)
    }

    @Test
    fun recommendationFollowsBMIAndAge() {
        // BMI 30.4 → 0.75%
        assertEquals(0.75, NutritionCalculator.recommendedPacePercent(weightKg = 96.4, heightCm = 178.0, ageYears = 34))
        // BMI 37.9 → 1%
        assertEquals(1.0, NutritionCalculator.recommendedPacePercent(weightKg = 120.0, heightCm = 178.0, ageYears = 34))
        // BMI 23.7 → 0.5%
        assertEquals(0.5, NutritionCalculator.recommendedPacePercent(weightKg = 75.0, heightCm = 178.0, ageYears = 34))
        // 60+ capped at 0.5%
        assertEquals(0.5, NutritionCalculator.recommendedPacePercent(weightKg = 120.0, heightCm = 178.0, ageYears = 62))
        val options = NutritionCalculator.paceOptions(weightKg = 96.4, heightCm = 178.0, ageYears = 34)
        assertEquals(listOf(0.75), options.filter { it.recommended }.map { it.percent })
    }

    @Test
    fun weeksToGoal() {
        assertEquals(25, NutritionCalculator.weeksToGoal(currentKg = 96.4, goalKg = 79.0, kgPerWeek = 0.7))
        assertEquals(null, NutritionCalculator.weeksToGoal(currentKg = 79.0, goalKg = 79.0, kgPerWeek = 0.7))
    }
}
