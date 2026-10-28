import Foundation

/// Placeholder data shown until real profile, InBody and Health data exist (phase 1).
public enum SampleData {
    public static let body = BodyMetrics(
        weightKg: 96.4,
        heightCm: 178,
        ageYears: 34,
        sex: .male,
        bodyFatPercent: 31.2,
        measuredBMR: 1880
    )

    public static let targets = MacroTargets(calories: 2050, proteinGrams: 150, carbsGrams: 215, fatGrams: 65)

    public static let today = TodaySnapshot(
        date: Date(),
        targets: targets,
        eatenCalories: 1240,
        activeCalories: 310,
        proteinGrams: 92,
        carbsGrams: 110,
        fatGrams: 41,
        steps: 6420,
        stepsGoal: 8000,
        waterGlasses: 5,
        waterGoal: 8,
        streakDays: 21,
        weightKg: 96.4,
        weightChangeKg: -4.2
    )

    public struct Meal: Sendable, Identifiable {
        public let id: Int
        public let slotAr: String
        public let slotEn: String
        public let time: String
        public let itemsAr: String
        public let itemsEn: String
        public let calories: Int
    }

    public static let meals: [Meal] = [
        Meal(id: 1, slotAr: "فطار", slotEn: "Breakfast", time: "8:30", itemsAr: "2 بيض مسلوق + جبنة قريش + نص رغيف بلدي", itemsEn: "2 boiled eggs, cottage cheese, half baladi bread", calories: 410),
        Meal(id: 2, slotAr: "سناك", slotEn: "Snack", time: "11:45", itemsAr: "زبادي يوناني بالشوفان", itemsEn: "Greek yogurt with oats", calories: 230),
        Meal(id: 3, slotAr: "غدا", slotEn: "Lunch", time: "3:10", itemsAr: "فراخ مشوية + رز + سلطة", itemsEn: "Grilled chicken, rice, salad", calories: 600)
    ]
}
