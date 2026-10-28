import Foundation

/// Everything Claude should know about the user for a request, rendered as plain text.
public struct UserContext: Sendable, Equatable {
    public var name: String
    public var ageYears: Int
    public var sex: Sex
    public var heightCm: Double
    public var weightKg: Double
    public var goalWeightKg: Double
    public var weeklyLossKg: Double
    public var activity: ActivityLevel
    public var conditions: [ChronicCondition]
    public var otherConditions: String
    public var medications: String
    public var allergies: String
    public var preferences: [String]
    public var targets: MacroTargets
    public var eatenToday: MacroTargets
    public var mealsToday: [String]
    public var stepsToday: Int
    public var activeCaloriesToday: Int
    public var latestInBody: String?
    public var weightTrend: String?

    public init(
        name: String = "",
        ageYears: Int,
        sex: Sex,
        heightCm: Double,
        weightKg: Double,
        goalWeightKg: Double,
        weeklyLossKg: Double,
        activity: ActivityLevel,
        conditions: [ChronicCondition] = [],
        otherConditions: String = "",
        medications: String = "",
        allergies: String = "",
        preferences: [String] = [],
        targets: MacroTargets,
        eatenToday: MacroTargets = MacroTargets(calories: 0, proteinGrams: 0, carbsGrams: 0, fatGrams: 0),
        mealsToday: [String] = [],
        stepsToday: Int = 0,
        activeCaloriesToday: Int = 0,
        latestInBody: String? = nil,
        weightTrend: String? = nil
    ) {
        self.name = name
        self.ageYears = ageYears
        self.sex = sex
        self.heightCm = heightCm
        self.weightKg = weightKg
        self.goalWeightKg = goalWeightKg
        self.weeklyLossKg = weeklyLossKg
        self.activity = activity
        self.conditions = conditions
        self.otherConditions = otherConditions
        self.medications = medications
        self.allergies = allergies
        self.preferences = preferences
        self.targets = targets
        self.eatenToday = eatenToday
        self.mealsToday = mealsToday
        self.stepsToday = stepsToday
        self.activeCaloriesToday = activeCaloriesToday
        self.latestInBody = latestInBody
        self.weightTrend = weightTrend
    }

    public func promptText(today: Date = Date()) -> String {
        var lines: [String] = []
        lines.append("## User profile")
        if !name.isEmpty { lines.append("- Name: \(name)") }
        lines.append("- \(sex.rawValue), \(ageYears) years, \(Int(heightCm)) cm, \(Self.format(weightKg)) kg")
        lines.append("- Goal: \(Self.format(goalWeightKg)) kg at about \(Self.format(weeklyLossKg)) kg/week")
        lines.append("- Everyday activity: \(activity.rawValue)")

        lines.append("## Health")
        if conditions.isEmpty && otherConditions.isEmpty {
            lines.append("- No chronic conditions reported")
        }
        for condition in conditions {
            lines.append("- \(condition.en): \(condition.guidance)")
        }
        if !otherConditions.isEmpty { lines.append("- Other conditions: \(otherConditions)") }
        if !medications.isEmpty { lines.append("- Medications: \(medications)") }
        if !allergies.isEmpty { lines.append("- Food allergies / intolerances: \(allergies) — never suggest these") }
        if !preferences.isEmpty { lines.append("- Food preferences: \(preferences.joined(separator: "; "))") }

        lines.append("## Daily targets (calculated by the app — use these, do not invent new targets)")
        lines.append("- \(targets.calories) kcal, protein \(targets.proteinGrams) g, carbs \(targets.carbsGrams) g, fat \(targets.fatGrams) g")

        let date = today.formatted(.iso8601.year().month().day())
        lines.append("## Today (\(date))")
        lines.append("- Eaten: \(eatenToday.calories) kcal, protein \(eatenToday.proteinGrams) g, carbs \(eatenToday.carbsGrams) g, fat \(eatenToday.fatGrams) g")
        lines.append("- Remaining: \(max(0, targets.calories - eatenToday.calories)) kcal, protein \(max(0, targets.proteinGrams - eatenToday.proteinGrams)) g")
        for meal in mealsToday { lines.append("- Meal: \(meal)") }
        lines.append("- Steps: \(stepsToday), active energy: \(activeCaloriesToday) kcal")

        if let latestInBody { lines.append("## Latest InBody\n\(latestInBody)") }
        if let weightTrend { lines.append("## Weight trend\n\(weightTrend)") }
        return lines.joined(separator: "\n")
    }

    static func format(_ value: Double) -> String {
        value == value.rounded() ? String(Int(value)) : String(format: "%.1f", value)
    }
}
