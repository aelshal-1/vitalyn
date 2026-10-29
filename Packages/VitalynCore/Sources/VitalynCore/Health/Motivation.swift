import Foundation

/// A bilingual motivational notification.
public struct MotivationMessage: Codable, Sendable, Equatable {
    public var titleAr: String
    public var titleEn: String
    public var bodyAr: String
    public var bodyEn: String

    public init(titleAr: String, titleEn: String, bodyAr: String, bodyEn: String) {
        self.titleAr = titleAr
        self.titleEn = titleEn
        self.bodyAr = bodyAr
        self.bodyEn = bodyEn
    }
}

public struct MotivationBatch: Codable, Sendable, Equatable {
    public var messages: [MotivationMessage]
}

/// The user's progress, used to personalise messages.
public struct MotivationInput: Sendable, Equatable {
    public var name: String
    public var streakDays: Int
    public var lostKg: Double
    public var remainingKg: Double
    public var proteinTarget: Int
    public var stepsGoal: Int

    public init(name: String, streakDays: Int, lostKg: Double, remainingKg: Double, proteinTarget: Int, stepsGoal: Int) {
        self.name = name
        self.streakDays = streakDays
        self.lostKg = lostKg
        self.remainingKg = remainingKg
        self.proteinTarget = proteinTarget
        self.stepsGoal = stepsGoal
    }
}

/// Built-in messages, used when Claude isn't available. Progress-based ones come first when they apply.
public enum Motivation {
    public static func messages(for input: MotivationInput, count: Int, seed: Int) -> [MotivationMessage] {
        let name = input.name.split(separator: " ").first.map(String.init) ?? ""
        let hi = name.isEmpty ? "" : "\(name)، "
        let hiEn = name.isEmpty ? "" : "\(name), "
        var personal: [MotivationMessage] = []

        if input.lostKg >= 0.5 {
            let kg = format(input.lostKg)
            personal.append(MotivationMessage(
                titleAr: "نزلت \(kg) كجم 💪", titleEn: "\(kg) kg down 💪",
                bodyAr: "\(hi)ده مجهودك انت. كمّل بنفس الهدوء.", bodyEn: "\(hiEn)that's all your work. Keep the same steady pace."
            ))
        }
        if input.streakDays >= 3 {
            personal.append(MotivationMessage(
                titleAr: "\(input.streakDays) يوم ورا بعض 🔥", titleEn: "\(input.streakDays)-day streak 🔥",
                bodyAr: "متقطعش السلسلة النهارده — سجّل أول وجبة.", bodyEn: "Don't break the chain today — log your first meal."
            ))
        }
        if input.remainingKg > 0.5 {
            let kg = format(input.remainingKg)
            personal.append(MotivationMessage(
                titleAr: "فاضل \(kg) كجم على هدفك 🎯", titleEn: "\(kg) kg to your goal 🎯",
                bodyAr: "كل يوم ملتزم فيه بيقرّبك. انت أقرب من امبارح.", bodyEn: "Every on-plan day gets you closer. You're closer than yesterday."
            ))
        }

        let general: [MotivationMessage] = [
            MotivationMessage(titleAr: "اشرب مية 💧", titleEn: "Drink some water 💧",
                              bodyAr: "كوباية مية دلوقتي بتقلل الجوع وبتفوقك.", bodyEn: "A glass of water now curbs hunger and wakes you up."),
            MotivationMessage(titleAr: "البروتين أولاً 🍗", titleEn: "Protein first 🍗",
                              bodyAr: "هدفك \(input.proteinTarget) جم بروتين النهارده — خلّي كل وجبة فيها حاجة منه.", bodyEn: "Your target is \(input.proteinTarget) g protein today — put some in every meal."),
            MotivationMessage(titleAr: "اتحرك شوية 🚶", titleEn: "Move a little 🚶",
                              bodyAr: "عشر دقايق مشي دلوقتي بيقرّبوك من \(input.stepsGoal) خطوة.", bodyEn: "Ten minutes of walking now gets you closer to \(input.stepsGoal) steps."),
            MotivationMessage(titleAr: "مش لازم تكون مثالي ✨", titleEn: "You don't need to be perfect ✨",
                              bodyAr: "يوم مش مظبوط مش بيبوظ الرحلة. المهم ترجع الوجبة الجاية.", bodyEn: "One off day doesn't ruin the journey. Just get back on track at the next meal."),
            MotivationMessage(titleAr: "صوّر قبل ما تاكل 📸", titleEn: "Snap before you eat 📸",
                              bodyAr: "صورة واحدة وVitalyn يحسبلك كل حاجة.", bodyEn: "One photo and Vitalyn counts everything for you."),
            MotivationMessage(titleAr: "النوم جزء من الدايت 😴", titleEn: "Sleep is part of the diet 😴",
                              bodyAr: "نوم 7 ساعات بيقلل الجوع والرغبة في السكريات.", bodyEn: "Seven hours of sleep reduces hunger and sugar cravings."),
            MotivationMessage(titleAr: "انت بتعمل ده لنفسك ❤️", titleEn: "You're doing this for you ❤️",
                              bodyAr: "\(hi)صحتك تستاهل. خطوة صغيرة النهارده تكفي.", bodyEn: "\(hiEn)your health is worth it. One small step today is enough."),
            MotivationMessage(titleAr: "خضار في كل طبق 🥗", titleEn: "Veggies on every plate 🥗",
                              bodyAr: "نص الطبق خضار بيشبعك بسعرات أقل.", bodyEn: "Half a plate of vegetables fills you up for fewer calories.")
        ]

        let start = general.isEmpty ? 0 : ((seed % general.count) + general.count) % general.count
        let rotated = Array(general[start...] + general[..<start])
        return Array((personal + rotated).prefix(max(0, count)))
    }

    static func format(_ value: Double) -> String {
        String(format: "%.1f", value)
    }
}
