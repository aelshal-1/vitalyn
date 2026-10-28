import Foundation

/// Chronic conditions the user can pick. Each carries guidance passed to Claude and may adjust macro targets.
public enum ChronicCondition: String, Codable, Sendable, CaseIterable, Identifiable {
    case type2Diabetes
    case insulinResistance
    case hypertension
    case highCholesterol
    case thyroid
    case ibs
    case gout
    case fattyLiver
    case kidneyDisease

    public var id: String { rawValue }

    public var ar: String {
        switch self {
        case .type2Diabetes: "سكر النوع التاني"
        case .insulinResistance: "مقاومة إنسولين"
        case .hypertension: "ضغط عالي"
        case .highCholesterol: "كوليسترول"
        case .thyroid: "غدة درقية"
        case .ibs: "قولون عصبي"
        case .gout: "نقرس"
        case .fattyLiver: "كبد دهني"
        case .kidneyDisease: "مشاكل كلى"
        }
    }

    public var en: String {
        switch self {
        case .type2Diabetes: "Type 2 diabetes"
        case .insulinResistance: "Insulin resistance"
        case .hypertension: "Hypertension"
        case .highCholesterol: "High cholesterol"
        case .thyroid: "Thyroid condition"
        case .ibs: "IBS"
        case .gout: "Gout"
        case .fattyLiver: "Fatty liver"
        case .kidneyDisease: "Kidney disease"
        }
    }

    /// Short guidance included in every prompt.
    public var guidance: String {
        switch self {
        case .type2Diabetes: "Prefer low-GI carbs, spread carbs across meals, pair carbs with protein/fiber, avoid sugary drinks; watch hypoglycemia risk with exercise if on medication."
        case .insulinResistance: "Prefer low-GI, high-fiber carbs and higher protein; limit refined carbs and sugar."
        case .hypertension: "Keep sodium low (pickles, cheeses, processed meats, salty bread are common Egyptian sources); favor potassium-rich vegetables."
        case .highCholesterol: "Limit saturated and trans fat (ghee, fatty meats, fried food); favor fish, olive oil, oats, legumes."
        case .thyroid: "Keep meals regular; note thyroid medication is usually taken on an empty stomach away from calcium/iron-rich food."
        case .ibs: "Watch common triggers (high-FODMAP foods like onion, garlic, beans) and suggest gentler alternatives."
        case .gout: "Limit organ meats, red meat, sardines/anchovies, and sugary drinks; encourage water."
        case .fattyLiver: "Avoid added sugar and fructose drinks, limit refined carbs, favor gradual weight loss and regular activity."
        case .kidneyDisease: "Do not push high protein; keep sodium and potassium moderate; defer to the user's nephrologist."
        }
    }
}

extension NutritionCalculator {
    /// Calorie and macro targets, adjusted for chronic conditions.
    public static func targets(
        body: BodyMetrics,
        activityFactor: Double,
        weeklyLossKg: Double,
        conditions: [ChronicCondition]
    ) -> MacroTargets {
        let calories = calorieTarget(body: body, activityFactor: activityFactor, weeklyLossKg: weeklyLossKg)
        let carbSensitive = conditions.contains(.type2Diabetes) || conditions.contains(.insulinResistance)
        let protein = conditions.contains(.kidneyDisease) ? 1.2 : 2.2
        return macros(calories: calories, body: body, proteinPerKgLean: protein, fatShare: carbSensitive ? 0.33 : 0.28)
    }
}
