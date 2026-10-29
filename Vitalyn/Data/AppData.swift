import Foundation
import SwiftData
import WidgetKit
import VitalynCore

/// Read-side helpers that combine SwiftData, Health and the calculator.
@MainActor
enum AppData {
    static func profile(_ context: ModelContext) -> UserProfile? {
        try? context.fetch(FetchDescriptor<UserProfile>(sortBy: [SortDescriptor(\.createdAt)])).first
    }

    static func scans(_ context: ModelContext) -> [InBodyScan] {
        (try? context.fetch(FetchDescriptor<InBodyScan>(sortBy: [SortDescriptor(\.date)]))) ?? []
    }

    static func meals(on day: Date, _ context: ModelContext) -> [MealEntry] {
        let start = Calendar.current.startOfDay(for: day)
        let end = Calendar.current.date(byAdding: .day, value: 1, to: start) ?? start
        let descriptor = FetchDescriptor<MealEntry>(
            predicate: #Predicate { $0.date >= start && $0.date < end },
            sortBy: [SortDescriptor(\.date)]
        )
        return (try? context.fetch(descriptor)) ?? []
    }

    static func loggedDays(_ context: ModelContext) -> Set<Date> {
        let since = Calendar.current.date(byAdding: .day, value: -120, to: Date()) ?? Date()
        let descriptor = FetchDescriptor<MealEntry>(predicate: #Predicate { $0.date >= since })
        let meals = (try? context.fetch(descriptor)) ?? []
        return Set(meals.map { Calendar.current.startOfDay(for: $0.date) })
    }

    static func products(_ context: ModelContext) -> [FoodProduct] {
        (try? context.fetch(FetchDescriptor<FoodProduct>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)]))) ?? []
    }

    static func currentWeight(profile: UserProfile, scans: [InBodyScan], health: HealthService) -> Double {
        let scan = scans.last
        let healthLatest = health.weights.last
        switch (scan, healthLatest) {
        case let (scan?, weight?): return weight.date > scan.date ? weight.value : scan.weightKg
        case let (scan?, nil): return scan.weightKg
        case let (nil, weight?): return weight.value
        default: return profile.startWeightKg
        }
    }

    static func bodyMetrics(profile: UserProfile, scans: [InBodyScan], health: HealthService) -> BodyMetrics {
        let latest = scans.last
        return BodyMetrics(
            weightKg: currentWeight(profile: profile, scans: scans, health: health),
            heightCm: profile.heightCm,
            ageYears: profile.ageYears,
            sex: profile.sex,
            bodyFatPercent: latest?.effectiveBodyFatPercent,
            measuredBMR: latest?.bmrKcal
        )
    }

    static func targets(profile: UserProfile, scans: [InBodyScan], health: HealthService) -> MacroTargets {
        NutritionCalculator.targets(
            body: bodyMetrics(profile: profile, scans: scans, health: health),
            activityFactor: profile.activity.factor,
            weeklyLossKg: profile.weeklyLossKg,
            conditions: profile.conditions
        )
    }

    static func userContext(_ context: ModelContext, health: HealthService) -> UserContext? {
        guard let profile = profile(context) else { return nil }
        let scans = scans(context)
        let meals = meals(on: Date(), context)
        let eaten = Portions.total(meals.flatMap { $0.sortedItems.map(\.analyzed) })
        let weight = currentWeight(profile: profile, scans: scans, health: health)
        var trend: String?
        if let first = scans.first, scans.count > 1 {
            trend = "First scan \(first.weightKg) kg on \(first.date.formatted(.iso8601.year().month().day())); now \(weight) kg."
        } else if let first = health.weights.first {
            trend = "Health weight \(first.value) kg on \(first.date.formatted(.iso8601.year().month().day())); now \(weight) kg."
        }
        return UserContext(
            name: profile.name,
            ageYears: profile.ageYears,
            sex: profile.sex,
            heightCm: profile.heightCm,
            weightKg: weight,
            goalWeightKg: profile.goalWeightKg,
            weeklyLossKg: profile.weeklyLossKg,
            activity: profile.activity,
            conditions: profile.conditions,
            otherConditions: profile.otherConditions,
            medications: profile.medications,
            allergies: profile.allergies,
            preferences: profile.preferences,
            targets: targets(profile: profile, scans: scans, health: health),
            eatenToday: eaten,
            mealsToday: meals.map(\.promptLine),
            stepsToday: health.stepsToday,
            activeCaloriesToday: health.activeKcalToday,
            latestInBody: scans.last?.promptSummary,
            weightTrend: trend,
            healthInsights: profile.currentHealthInsights.map {
                "\($0.nameEn) (typed \"\($0.input)\", \($0.kind.rawValue)): \($0.whatItIsEn) \($0.notesEn)"
            }
        )
    }

    /// Recomputes today's summary for the widgets and reloads them.
    static func publishSnapshot(_ context: ModelContext, health: HealthService) {
        guard let profile = profile(context) else { return }
        let scans = scans(context)
        let meals = meals(on: Date(), context)
        let eaten = Portions.total(meals.flatMap { $0.sortedItems.map(\.analyzed) })
        let weight = currentWeight(profile: profile, scans: scans, health: health)
        let start = scans.first?.weightKg ?? profile.startWeightKg
        let snapshot = TodaySnapshot(
            date: Date(),
            targets: targets(profile: profile, scans: scans, health: health),
            eatenCalories: eaten.calories,
            activeCalories: health.activeKcalToday,
            proteinGrams: eaten.proteinGrams,
            carbsGrams: eaten.carbsGrams,
            fatGrams: eaten.fatGrams,
            steps: health.stepsToday,
            stepsGoal: profile.stepsGoal,
            waterGlasses: WaterStore.glasses(),
            waterGoal: profile.waterGoal,
            streakDays: ProgressMath.streak(loggedDays: loggedDays(context)),
            weightKg: weight,
            weightChangeKg: weight - start
        )
        SnapshotStore.save(snapshot)
        WidgetCenter.shared.reloadAllTimelines()
    }
}
