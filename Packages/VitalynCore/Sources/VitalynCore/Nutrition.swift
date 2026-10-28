import Foundation

/// Biological sex as used by BMR formulas.
public enum Sex: String, Codable, Sendable {
    case male
    case female
}

/// Everyday activity level, mapped to the standard TDEE multipliers.
public enum ActivityLevel: String, Codable, Sendable, CaseIterable {
    case sedentary
    case light
    case moderate
    case active
    case veryActive

    public var factor: Double {
        switch self {
        case .sedentary: 1.2
        case .light: 1.375
        case .moderate: 1.55
        case .active: 1.725
        case .veryActive: 1.9
        }
    }
}

/// The body numbers the calculator needs. Lean mass and a measured BMR come from InBody when available.
public struct BodyMetrics: Codable, Sendable, Equatable {
    public var weightKg: Double
    public var heightCm: Double
    public var ageYears: Int
    public var sex: Sex
    public var bodyFatPercent: Double?
    public var measuredBMR: Double?

    public init(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex, bodyFatPercent: Double? = nil, measuredBMR: Double? = nil) {
        self.weightKg = weightKg
        self.heightCm = heightCm
        self.ageYears = ageYears
        self.sex = sex
        self.bodyFatPercent = bodyFatPercent
        self.measuredBMR = measuredBMR
    }

    public var leanMassKg: Double? {
        guard let bodyFatPercent else { return nil }
        return weightKg * (1 - bodyFatPercent / 100)
    }
}

public struct MacroTargets: Codable, Sendable, Equatable {
    public var calories: Int
    public var proteinGrams: Int
    public var carbsGrams: Int
    public var fatGrams: Int

    public init(calories: Int, proteinGrams: Int, carbsGrams: Int, fatGrams: Int) {
        self.calories = calories
        self.proteinGrams = proteinGrams
        self.carbsGrams = carbsGrams
        self.fatGrams = fatGrams
    }
}

/// Deterministic energy and macro math. The AI coach explains and adjusts these numbers; it never invents them.
public enum NutritionCalculator {
    /// Energy in one kilogram of body fat, the usual planning figure.
    public static let kcalPerKgFat = 7700.0
    /// The deficit never exceeds this share of TDEE.
    public static let maxDeficitShare = 0.25

    /// Katch-McArdle: needs lean mass, so it is the most accurate once an InBody scan exists.
    public static func bmrKatchMcArdle(leanMassKg: Double) -> Double {
        370 + 21.6 * leanMassKg
    }

    /// Mifflin-St Jeor: fallback before the first InBody scan.
    public static func bmrMifflinStJeor(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex) -> Double {
        let base = 10 * weightKg + 6.25 * heightCm - 5 * Double(ageYears)
        return sex == .male ? base + 5 : base - 161
    }

    /// Prefers a measured BMR, then Katch-McArdle, then Mifflin-St Jeor.
    public static func bmr(for body: BodyMetrics) -> Double {
        if let measured = body.measuredBMR { return measured }
        if let lean = body.leanMassKg { return bmrKatchMcArdle(leanMassKg: lean) }
        return bmrMifflinStJeor(weightKg: body.weightKg, heightCm: body.heightCm, ageYears: body.ageYears, sex: body.sex)
    }

    public static func tdee(bmr: Double, activityFactor: Double) -> Double {
        bmr * activityFactor
    }

    /// Daily deficit for a weekly loss goal, capped at `maxDeficitShare` of TDEE.
    public static func dailyDeficit(tdee: Double, weeklyLossKg: Double) -> Double {
        let wanted = max(0, weeklyLossKg) * kcalPerKgFat / 7
        return min(wanted, tdee * maxDeficitShare)
    }

    /// Calorie target, rounded to 10 kcal and never below BMR.
    public static func calorieTarget(body: BodyMetrics, activityFactor: Double, weeklyLossKg: Double) -> Int {
        let bmr = bmr(for: body)
        let tdee = tdee(bmr: bmr, activityFactor: activityFactor)
        let target = max(bmr, tdee - dailyDeficit(tdee: tdee, weeklyLossKg: weeklyLossKg))
        return Int((target / 10).rounded()) * 10
    }

    /// Protein from lean mass (or body weight before a scan), fat as a share of calories, carbs fill the rest.
    public static func macros(
        calories: Int,
        body: BodyMetrics,
        proteinPerKgLean: Double = 2.2,
        fatShare: Double = 0.28
    ) -> MacroTargets {
        let proteinBase = body.leanMassKg ?? body.weightKg * 0.75
        let protein = roundTo5(proteinBase * proteinPerKgLean)
        let fat = roundTo5(Double(calories) * fatShare / 9)
        let remaining = Double(calories) - Double(protein * 4) - Double(fat * 9)
        let carbs = max(0, roundTo5(remaining / 4))
        return MacroTargets(calories: calories, proteinGrams: protein, carbsGrams: carbs, fatGrams: fat)
    }

    static func roundTo5(_ value: Double) -> Int {
        Int((value / 5).rounded()) * 5
    }
}
