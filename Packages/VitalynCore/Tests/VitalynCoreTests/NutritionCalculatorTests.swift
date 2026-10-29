import Foundation
import Testing
@testable import VitalynCore

@Suite struct NutritionCalculatorTests {
    let body = BodyMetrics(weightKg: 96.4, heightCm: 178, ageYears: 34, sex: .male, bodyFatPercent: 31.2)

    @Test func leanMassFromBodyFat() throws {
        let lean = try #require(body.leanMassKg)
        #expect(abs(lean - 66.32) < 0.01)
    }

    @Test func katchMcArdle() {
        #expect(abs(NutritionCalculator.bmrKatchMcArdle(leanMassKg: 66.32) - 1802.5) < 0.1)
    }

    @Test func mifflinStJeor() {
        let male = NutritionCalculator.bmrMifflinStJeor(weightKg: 96.4, heightCm: 178, ageYears: 34, sex: .male)
        let female = NutritionCalculator.bmrMifflinStJeor(weightKg: 96.4, heightCm: 178, ageYears: 34, sex: .female)
        #expect(abs(male - 1911.5) < 0.001)
        #expect(abs((male - female) - 166) < 0.001)
    }

    @Test func bmrPrefersMeasuredThenLeanMass() {
        var measured = body
        measured.measuredBMR = 1880
        #expect(NutritionCalculator.bmr(for: measured) == 1880)
        #expect(abs(NutritionCalculator.bmr(for: body) - NutritionCalculator.bmrKatchMcArdle(leanMassKg: body.leanMassKg!)) < 0.001)

        var noScan = body
        noScan.bodyFatPercent = nil
        #expect(abs(NutritionCalculator.bmr(for: noScan) - 1911.5) < 0.001)
    }

    @Test func deficitFollowsWeeklyGoal() {
        #expect(abs(NutritionCalculator.dailyDeficit(tdee: 2700, weeklyLossKg: 0.6) - 660) < 0.001)
        #expect(NutritionCalculator.dailyDeficit(tdee: 2700, weeklyLossKg: -1) == 0)
    }

    @Test func deficitIsCapped() {
        #expect(NutritionCalculator.dailyDeficit(tdee: 2000, weeklyLossKg: 2) == 500)
    }

    @Test func calorieTargetMatchesDesignExample() {
        var scanned = body
        scanned.measuredBMR = 1880
        let target = NutritionCalculator.calorieTarget(body: scanned, activityFactor: 1.44, weeklyLossKg: 0.6)
        #expect(target == 2050)
    }

    @Test func calorieTargetNeverBelowBMR() {
        var scanned = body
        scanned.measuredBMR = 1880
        let target = NutritionCalculator.calorieTarget(body: scanned, activityFactor: 1.2, weeklyLossKg: 1.5)
        #expect(target >= 1880)
    }

    @Test func macrosAddUpToCalories() {
        let macros = NutritionCalculator.macros(calories: 2050, body: body)
        let total = macros.proteinGrams * 4 + macros.carbsGrams * 4 + macros.fatGrams * 9
        #expect(abs(total - 2050) <= 15)
        #expect(macros.proteinGrams == 145)
        #expect(macros.fatGrams == 65)
    }

    @Test func activityFactors() {
        #expect(ActivityLevel.sedentary.factor == 1.2)
        #expect(ActivityLevel.moderate.factor == 1.55)
    }
}

@Suite struct TodaySnapshotTests {
    @Test func caloriesLeftNeverNegative() {
        var snapshot = SampleData.today
        #expect(snapshot.caloriesLeft == 810)
        snapshot.eatenCalories = 3000
        #expect(snapshot.caloriesLeft == 0)
        #expect(snapshot.calorieProgress == 1)
    }

    @Test func roundTripThroughStore() throws {
        let suite = "vitalyn-tests-\(UUID().uuidString)"
        SnapshotStore.save(SampleData.today, suiteName: suite)
        let loaded = try #require(SnapshotStore.load(suiteName: suite))
        #expect(loaded.steps == SampleData.today.steps)
        #expect(loaded.targets == SampleData.today.targets)
        UserDefaults().removePersistentDomain(forName: suite)
    }
}

@Suite struct IdealWeightTests {
    @Test func healthyRangeForHeight() {
        let ideal = NutritionCalculator.idealWeight(heightCm: 178, ageYears: 34, sex: .male, currentWeightKg: 96.4)
        #expect(ideal.healthyLowKg == 58.5)
        #expect(ideal.healthyHighKg == 79)
        // BMI 22.5 × 1.78² = 71.3 → 71.5
        #expect(ideal.goalKg == 71.5)
        #expect(!ideal.alreadyThere)
    }

    @Test func targetRisesWithAgeAndDropsForWomen() {
        let young = NutritionCalculator.idealWeight(heightCm: 170, ageYears: 25, sex: .male, currentWeightKg: 90)
        let older = NutritionCalculator.idealWeight(heightCm: 170, ageYears: 60, sex: .male, currentWeightKg: 90)
        let woman = NutritionCalculator.idealWeight(heightCm: 170, ageYears: 25, sex: .female, currentWeightKg: 90)
        #expect(older.goalKg > young.goalKg)
        #expect(woman.goalKg < young.goalKg)
    }

    @Test func bodyFatKeepsLeanMass() {
        // 96.4 kg at 31.2% fat → lean 66.3 kg; at 18% fat → 80.9 kg, capped at BMI 24.9 (79 kg).
        let ideal = NutritionCalculator.idealWeight(heightCm: 178, ageYears: 34, sex: .male, currentWeightKg: 96.4, bodyFatPercent: 31.2)
        #expect(ideal.goalKg == 79)
    }

    @Test func neverAboveCurrentWeight() {
        let ideal = NutritionCalculator.idealWeight(heightCm: 178, ageYears: 34, sex: .male, currentWeightKg: 68)
        #expect(ideal.alreadyThere)
        #expect(ideal.goalKg == 68)
    }
}
