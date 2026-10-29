import Foundation

/// A suggested goal weight and the healthy range around it.
public struct IdealWeight: Sendable, Equatable {
    public var goalKg: Double
    public var healthyLowKg: Double
    public var healthyHighKg: Double
    /// True when the current weight is already at or below the suggestion.
    public var alreadyThere: Bool
}

extension NutritionCalculator {
    /// Suggests a goal weight from height, age, sex and current weight (and body fat when an InBody scan exists).
    ///
    /// - Healthy range: BMI 18.5–24.9 for the height.
    /// - Target BMI rises slightly with age (22.5 under 35, 23.5 for 35–54, 24.5 from 55), 0.5 lower for women.
    /// - With body fat known, the goal keeps current lean mass at a healthy body-fat level
    ///   (men 18% / 20% from 40, women 25% / 28% from 40), kept inside the healthy BMI range.
    /// - The goal is never above the current weight, and is rounded to 0.5 kg.
    public static func idealWeight(heightCm: Double, ageYears: Int, sex: Sex, currentWeightKg: Double, bodyFatPercent: Double? = nil) -> IdealWeight {
        let meters = heightCm / 100
        let squared = meters * meters
        let low = 18.5 * squared
        let high = 24.9 * squared

        var targetBMI: Double = ageYears < 35 ? 22.5 : (ageYears < 55 ? 23.5 : 24.5)
        if sex == .female { targetBMI -= 0.5 }
        var goal = targetBMI * squared

        if let bodyFatPercent, bodyFatPercent > 0, bodyFatPercent < 70 {
            let lean = currentWeightKg * (1 - bodyFatPercent / 100)
            let targetFat: Double = switch sex {
            case .male: ageYears < 40 ? 0.18 : 0.20
            case .female: ageYears < 40 ? 0.25 : 0.28
            }
            goal = lean / (1 - targetFat)
        }

        goal = min(max(goal, low), high)
        let alreadyThere = currentWeightKg <= goal
        if alreadyThere { goal = currentWeightKg }
        return IdealWeight(
            goalKg: roundHalf(goal),
            healthyLowKg: roundHalf(low),
            healthyHighKg: roundHalf(high),
            alreadyThere: alreadyThere
        )
    }

    static func roundHalf(_ value: Double) -> Double {
        (value * 2).rounded() / 2
    }
}
