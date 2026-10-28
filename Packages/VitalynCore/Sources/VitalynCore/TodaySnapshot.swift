import Foundation

/// A small summary of the day that the app writes and the widgets read through the shared App Group.
public struct TodaySnapshot: Codable, Sendable, Equatable {
    public var date: Date
    public var targets: MacroTargets
    public var eatenCalories: Int
    public var activeCalories: Int
    public var proteinGrams: Int
    public var carbsGrams: Int
    public var fatGrams: Int
    public var steps: Int
    public var stepsGoal: Int
    public var waterGlasses: Int
    public var waterGoal: Int
    public var streakDays: Int
    public var weightKg: Double
    public var weightChangeKg: Double

    public init(
        date: Date,
        targets: MacroTargets,
        eatenCalories: Int,
        activeCalories: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        steps: Int,
        stepsGoal: Int,
        waterGlasses: Int,
        waterGoal: Int,
        streakDays: Int,
        weightKg: Double,
        weightChangeKg: Double
    ) {
        self.date = date
        self.targets = targets
        self.eatenCalories = eatenCalories
        self.activeCalories = activeCalories
        self.proteinGrams = proteinGrams
        self.carbsGrams = carbsGrams
        self.fatGrams = fatGrams
        self.steps = steps
        self.stepsGoal = stepsGoal
        self.waterGlasses = waterGlasses
        self.waterGoal = waterGoal
        self.streakDays = streakDays
        self.weightKg = weightKg
        self.weightChangeKg = weightChangeKg
    }

    public var caloriesLeft: Int { max(0, targets.calories - eatenCalories) }

    public var calorieProgress: Double { Self.fraction(eatenCalories, of: targets.calories) }
    public var proteinProgress: Double { Self.fraction(proteinGrams, of: targets.proteinGrams) }
    public var carbsProgress: Double { Self.fraction(carbsGrams, of: targets.carbsGrams) }
    public var fatProgress: Double { Self.fraction(fatGrams, of: targets.fatGrams) }
    public var stepsProgress: Double { Self.fraction(steps, of: stepsGoal) }

    static func fraction(_ value: Int, of goal: Int) -> Double {
        guard goal > 0 else { return 0 }
        return min(1, max(0, Double(value) / Double(goal)))
    }
}

/// Reads and writes the snapshot in the App Group container shared by the app and its widgets.
public enum SnapshotStore {
    public static let appGroup = "group.com.elshal.vitalyn"
    static let key = "today-snapshot"

    public static func save(_ snapshot: TodaySnapshot, suiteName: String = appGroup) {
        guard let defaults = UserDefaults(suiteName: suiteName),
              let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: key)
    }

    public static func load(suiteName: String = appGroup) -> TodaySnapshot? {
        guard let defaults = UserDefaults(suiteName: suiteName),
              let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(TodaySnapshot.self, from: data)
    }
}

/// Today's water glasses, shared by the app and the interactive water widget.
public enum WaterStore {
    static func key(for date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return "water-\(parts.year ?? 0)-\(parts.month ?? 0)-\(parts.day ?? 0)"
    }

    public static func glasses(on date: Date = Date(), suiteName: String = SnapshotStore.appGroup) -> Int {
        UserDefaults(suiteName: suiteName)?.integer(forKey: key(for: date)) ?? 0
    }

    @discardableResult
    public static func add(_ count: Int = 1, on date: Date = Date(), suiteName: String = SnapshotStore.appGroup) -> Int {
        guard let defaults = UserDefaults(suiteName: suiteName) else { return 0 }
        let value = max(0, defaults.integer(forKey: key(for: date)) + count)
        defaults.set(value, forKey: key(for: date))
        if var snapshot = SnapshotStore.load(suiteName: suiteName), Calendar.current.isDate(snapshot.date, inSameDayAs: date) {
            snapshot.waterGlasses = value
            SnapshotStore.save(snapshot, suiteName: suiteName)
        }
        return value
    }
}
