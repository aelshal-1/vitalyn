import Foundation

/// Streaks, achievements and the body "journey" between scans.
public enum ProgressMath {
    /// Consecutive days with at least one logged meal, ending today (or yesterday if today has nothing yet).
    public static func streak(loggedDays: Set<Date>, today: Date = Date(), calendar: Calendar = .current) -> Int {
        let days = Set(loggedDays.map { calendar.startOfDay(for: $0) })
        var cursor = calendar.startOfDay(for: today)
        if !days.contains(cursor) {
            guard let yesterday = calendar.date(byAdding: .day, value: -1, to: cursor), days.contains(yesterday) else { return 0 }
            cursor = yesterday
        }
        var count = 0
        while days.contains(cursor) {
            count += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = previous
        }
        return count
    }

    /// Linear interpolation from first scan → now → goal, `t` in 0...1 where 0.5 is now.
    public static func journey(first: Double, now: Double, goal: Double, t: Double) -> Double {
        let t = min(1, max(0, t))
        if t <= 0.5 { return first + (now - first) * (t / 0.5) }
        return now + (goal - now) * ((t - 0.5) / 0.5)
    }
}

public struct Badge: Sendable, Equatable, Identifiable {
    public var id: String
    public var ar: String
    public var en: String
    public var symbol: String
    public var achieved: Bool
}

public struct AchievementInput: Sendable {
    public var scanCount: Int
    public var startWeightKg: Double?
    public var currentWeightKg: Double?
    public var streakDays: Int
    public var bestStepsDay: Int
    public var loggedMealCount: Int
    public var goalWeightKg: Double?

    public init(scanCount: Int, startWeightKg: Double?, currentWeightKg: Double?, streakDays: Int, bestStepsDay: Int, loggedMealCount: Int, goalWeightKg: Double?) {
        self.scanCount = scanCount
        self.startWeightKg = startWeightKg
        self.currentWeightKg = currentWeightKg
        self.streakDays = streakDays
        self.bestStepsDay = bestStepsDay
        self.loggedMealCount = loggedMealCount
        self.goalWeightKg = goalWeightKg
    }

    public var lostKg: Double {
        guard let startWeightKg, let currentWeightKg else { return 0 }
        return max(0, startWeightKg - currentWeightKg)
    }
}

public enum Achievements {
    public static func badges(for input: AchievementInput) -> [Badge] {
        let reachedGoal: Bool = {
            guard let goal = input.goalWeightKg, let current = input.currentWeightKg else { return false }
            return current <= goal
        }()
        return [
            Badge(id: "first-meal", ar: "أول وجبة", en: "First meal", symbol: "fork.knife", achieved: input.loggedMealCount >= 1),
            Badge(id: "first-inbody", ar: "أول InBody", en: "First InBody", symbol: "doc.text.magnifyingglass", achieved: input.scanCount >= 1),
            Badge(id: "streak-7", ar: "7 أيام التزام", en: "7-day streak", symbol: "flame", achieved: input.streakDays >= 7),
            Badge(id: "streak-21", ar: "21 يوم", en: "21-day streak", symbol: "flame.fill", achieved: input.streakDays >= 21),
            Badge(id: "steps-10k", ar: "10,000 خطوة", en: "10k steps", symbol: "figure.walk", achieved: input.bestStepsDay >= 10000),
            Badge(id: "lost-3", ar: "أول 3 كيلو", en: "First 3 kg", symbol: "arrow.down.circle", achieved: input.lostKg >= 3),
            Badge(id: "lost-5", ar: "أول 5 كيلو", en: "First 5 kg", symbol: "arrow.down.circle.fill", achieved: input.lostKg >= 5),
            Badge(id: "lost-10", ar: "10 كيلو", en: "10 kg down", symbol: "star.circle", achieved: input.lostKg >= 10),
            Badge(id: "goal", ar: "الهدف", en: "Goal reached", symbol: "trophy", achieved: reachedGoal)
        ]
    }
}
