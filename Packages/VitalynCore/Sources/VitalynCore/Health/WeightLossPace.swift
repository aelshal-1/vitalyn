import Foundation

/// A weekly weight-loss pace expressed as a share of current body weight.
public struct PaceOption: Sendable, Equatable, Identifiable {
    public var id: Double { percent }
    /// Percent of current body weight lost per week (e.g. 0.75).
    public var percent: Double
    /// The same pace in kg per week for this user, rounded to 0.05 kg.
    public var kgPerWeek: Double
    public var ar: String
    public var en: String
    public var noteAr: String
    public var noteEn: String
    public var recommended: Bool
}

extension NutritionCalculator {
    /// Paces offered to the user. Evidence-based guidance: losing about 0.5–1% of body weight per week
    /// preserves lean mass and is sustainable; faster loss raises muscle loss, gallstone and rebound risk.
    public static let pacePercents: [Double] = [0.25, 0.5, 0.75, 1.0, 1.25]

    /// The recommended percent per week: higher BMI tolerates faster loss; older adults and people
    /// close to a healthy weight should go slower to protect muscle.
    public static func recommendedPacePercent(weightKg: Double, heightCm: Double, ageYears: Int) -> Double {
        let meters = heightCm / 100
        let bmi = meters > 0 ? weightKg / (meters * meters) : 25
        var percent: Double
        switch bmi {
        case 35...: percent = 1.0
        case 30..<35: percent = 0.75
        case 25..<30: percent = 0.75
        default: percent = 0.5
        }
        if ageYears >= 60 { percent = min(percent, 0.5) }
        return percent
    }

    public static func paceOptions(weightKg: Double, heightCm: Double, ageYears: Int) -> [PaceOption] {
        let recommended = recommendedPacePercent(weightKg: weightKg, heightCm: heightCm, ageYears: ageYears)
        return pacePercents.map { percent in
            let (ar, en, noteAr, noteEn): (String, String, String, String) = switch percent {
            case 0.25: ("هادي جداً", "Very gentle", "أسهل التزام، نتيجة بطيئة", "Easiest to stick to, slow results")
            case 0.5: ("هادي", "Gentle", "بيحافظ على العضلات كويس", "Protects muscle well")
            case 0.75: ("متوازن", "Balanced", "توازن بين السرعة والعضلات", "Balance of speed and muscle")
            case 1.0: ("سريع", "Fast", "أقصى حد آمن لأغلب الناس", "Upper safe limit for most people")
            default: ("سريع جداً", "Very fast", "محتاج متابعة — خطر فقد عضلات", "Needs supervision — muscle-loss risk")
            }
            return PaceOption(
                percent: percent,
                kgPerWeek: roundToStep(weightKg * percent / 100, step: 0.05),
                ar: ar, en: en, noteAr: noteAr, noteEn: noteEn,
                recommended: percent == recommended
            )
        }
    }

    /// Weeks to go from current to goal at a fixed kg/week (nil when there is nothing to lose or no pace).
    public static func weeksToGoal(currentKg: Double, goalKg: Double, kgPerWeek: Double) -> Int? {
        guard kgPerWeek > 0, currentKg > goalKg else { return nil }
        return Int(((currentKg - goalKg) / kgPerWeek).rounded(.up))
    }

    static func roundToStep(_ value: Double, step: Double) -> Double {
        ((value / step).rounded() * step * 100).rounded() / 100
    }
}
