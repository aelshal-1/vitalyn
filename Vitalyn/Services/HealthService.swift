import Foundation
import HealthKit
import VitalynCore

struct DayValue: Identifiable, Equatable {
    var id: Date { date }
    var date: Date
    var value: Double
}

struct HealthWorkout: Identifiable, Equatable {
    var id: UUID
    var name: String
    var kind: WorkoutKind
    var start: Date
    var minutes: Double
    var kcal: Double
}

/// Reads activity and body data from Apple Health and writes weight, workouts and nutrition back.
@MainActor
@Observable
final class HealthService {
    private let store = HKHealthStore()

    var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }
    private(set) var requested = UserDefaults.standard.bool(forKey: "health-requested")

    var stepsToday = 0
    var activeKcalToday = 0
    var distanceKmToday = 0.0
    var restingHeartRate: Int?
    var sleepHoursLastNight: Double?
    var weekSteps: [DayValue] = []
    var monthSteps: [DayValue] = []
    var weights: [DayValue] = []
    var workouts: [HealthWorkout] = []
    var bestStepsDay = 0
    var lastError: String?

    private var readTypes: Set<HKObjectType> {
        [
            HKQuantityType(.stepCount),
            HKQuantityType(.activeEnergyBurned),
            HKQuantityType(.distanceWalkingRunning),
            HKQuantityType(.restingHeartRate),
            HKQuantityType(.bodyMass),
            HKQuantityType(.bodyFatPercentage),
            HKQuantityType(.leanBodyMass),
            HKQuantityType(.height),
            HKCategoryType(.sleepAnalysis),
            HKObjectType.workoutType(),
            HKCharacteristicType(.dateOfBirth),
            HKCharacteristicType(.biologicalSex)
        ]
    }

    private var shareTypes: Set<HKSampleType> {
        [
            HKQuantityType(.bodyMass),
            HKQuantityType(.bodyFatPercentage),
            HKQuantityType(.leanBodyMass),
            HKQuantityType(.activeEnergyBurned),
            HKQuantityType(.dietaryEnergyConsumed),
            HKQuantityType(.dietaryProtein),
            HKQuantityType(.dietaryCarbohydrates),
            HKQuantityType(.dietaryFatTotal),
            HKQuantityType(.dietaryWater),
            HKObjectType.workoutType()
        ]
    }

    func requestAuthorization() async {
        guard isAvailable else { return }
        do {
            try await store.requestAuthorization(toShare: shareTypes, read: readTypes)
            requested = true
            UserDefaults.standard.set(true, forKey: "health-requested")
            await refresh()
        } catch {
            lastError = error.localizedDescription
        }
    }

    func refresh() async {
        guard isAvailable, requested else { return }
        let calendar = Calendar.current
        let now = Date()
        let startOfDay = calendar.startOfDay(for: now)

        stepsToday = Int(await sum(.stepCount, unit: .count(), from: startOfDay, to: now))
        activeKcalToday = Int(await sum(.activeEnergyBurned, unit: .kilocalorie(), from: startOfDay, to: now))
        distanceKmToday = await sum(.distanceWalkingRunning, unit: .meterUnit(with: .kilo), from: startOfDay, to: now)
        restingHeartRate = await latest(.restingHeartRate, unit: HKUnit.count().unitDivided(by: .minute())).map { Int($0.value) }
        sleepHoursLastNight = await sleepHours(endingAt: now)

        let weekStart = calendar.date(byAdding: .day, value: -6, to: startOfDay) ?? startOfDay
        weekSteps = await daily(.stepCount, unit: .count(), from: weekStart, to: now)
        let monthStart = calendar.date(byAdding: .day, value: -29, to: startOfDay) ?? startOfDay
        monthSteps = await daily(.stepCount, unit: .count(), from: monthStart, to: now)
        bestStepsDay = Int(monthSteps.map(\.value).max() ?? 0)

        let halfYear = calendar.date(byAdding: .day, value: -180, to: now) ?? now
        weights = await samples(.bodyMass, unit: .gramUnit(with: .kilo), from: halfYear)
        workouts = await recentWorkouts(days: 14)
    }

    // MARK: Reading

    private func sum(_ id: HKQuantityTypeIdentifier, unit: HKUnit, from start: Date, to end: Date) async -> Double {
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end)
        let descriptor = HKStatisticsQueryDescriptor(
            predicate: .quantitySample(type: HKQuantityType(id), predicate: predicate),
            options: .cumulativeSum
        )
        let result = try? await descriptor.result(for: store)
        return result?.sumQuantity()?.doubleValue(for: unit) ?? 0
    }

    private func daily(_ id: HKQuantityTypeIdentifier, unit: HKUnit, from start: Date, to end: Date) async -> [DayValue] {
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end)
        let descriptor = HKStatisticsCollectionQueryDescriptor(
            predicate: .quantitySample(type: HKQuantityType(id), predicate: predicate),
            options: .cumulativeSum,
            anchorDate: Calendar.current.startOfDay(for: start),
            intervalComponents: DateComponents(day: 1)
        )
        guard let collection = try? await descriptor.result(for: store) else { return [] }
        var values: [DayValue] = []
        collection.enumerateStatistics(from: start, to: end) { statistics, _ in
            values.append(DayValue(date: statistics.startDate, value: statistics.sumQuantity()?.doubleValue(for: unit) ?? 0))
        }
        return values
    }

    private func latest(_ id: HKQuantityTypeIdentifier, unit: HKUnit) async -> DayValue? {
        let descriptor = HKSampleQueryDescriptor(
            predicates: [.quantitySample(type: HKQuantityType(id))],
            sortDescriptors: [SortDescriptor(\.endDate, order: .reverse)],
            limit: 1
        )
        guard let sample = try? await descriptor.result(for: store).first else { return nil }
        return DayValue(date: sample.endDate, value: sample.quantity.doubleValue(for: unit))
    }

    private func samples(_ id: HKQuantityTypeIdentifier, unit: HKUnit, from start: Date) async -> [DayValue] {
        let descriptor = HKSampleQueryDescriptor(
            predicates: [.quantitySample(type: HKQuantityType(id), predicate: HKQuery.predicateForSamples(withStart: start, end: nil))],
            sortDescriptors: [SortDescriptor(\.endDate, order: .forward)]
        )
        let results = (try? await descriptor.result(for: store)) ?? []
        return results.map { DayValue(date: $0.endDate, value: $0.quantity.doubleValue(for: unit)) }
    }

    private func sleepHours(endingAt end: Date) async -> Double? {
        let start = end.addingTimeInterval(-24 * 3600)
        let descriptor = HKSampleQueryDescriptor(
            predicates: [.categorySample(type: HKCategoryType(.sleepAnalysis), predicate: HKQuery.predicateForSamples(withStart: start, end: end))],
            sortDescriptors: []
        )
        guard let results = try? await descriptor.result(for: store), !results.isEmpty else { return nil }
        let asleepValues = HKCategoryValueSleepAnalysis.allAsleepValues.map(\.rawValue)
        let seconds = results
            .filter { asleepValues.contains($0.value) }
            .reduce(0.0) { $0 + $1.endDate.timeIntervalSince($1.startDate) }
        return seconds > 0 ? seconds / 3600 : nil
    }

    private func recentWorkouts(days: Int) async -> [HealthWorkout] {
        let start = Calendar.current.date(byAdding: .day, value: -days, to: Date()) ?? Date()
        let descriptor = HKSampleQueryDescriptor(
            predicates: [.workout(HKQuery.predicateForSamples(withStart: start, end: nil))],
            sortDescriptors: [SortDescriptor(\.startDate, order: .reverse)]
        )
        let results = (try? await descriptor.result(for: store)) ?? []
        return results.map { workout in
            let kcal = workout.statistics(for: HKQuantityType(.activeEnergyBurned))?.sumQuantity()?.doubleValue(for: .kilocalorie()) ?? 0
            let kind = Self.kind(for: workout.workoutActivityType)
            return HealthWorkout(id: workout.uuid, name: kind.rawValue, kind: kind, start: workout.startDate, minutes: workout.duration / 60, kcal: kcal)
        }
    }

    /// Height, sex and birth year from Health, to prefill the profile.
    func profileHints() -> (heightCm: Double?, sex: Sex?, birthYear: Int?) {
        let sex: Sex? = {
            switch (try? store.biologicalSex())?.biologicalSex {
            case .male: return .male
            case .female: return .female
            default: return nil
            }
        }()
        let birthYear = (try? store.dateOfBirthComponents())?.year
        return (nil, sex, birthYear)
    }

    func latestHeightCm() async -> Double? {
        await latest(.height, unit: .meterUnit(with: .centi))?.value
    }

    // MARK: Writing

    func saveBody(weightKg: Double, bodyFatPercent: Double?, leanMassKg: Double?, date: Date) async {
        guard isAvailable, requested else { return }
        var samples: [HKSample] = [
            HKQuantitySample(type: HKQuantityType(.bodyMass), quantity: HKQuantity(unit: .gramUnit(with: .kilo), doubleValue: weightKg), start: date, end: date)
        ]
        if let bodyFatPercent {
            samples.append(HKQuantitySample(type: HKQuantityType(.bodyFatPercentage), quantity: HKQuantity(unit: .percent(), doubleValue: bodyFatPercent / 100), start: date, end: date))
        }
        if let leanMassKg {
            samples.append(HKQuantitySample(type: HKQuantityType(.leanBodyMass), quantity: HKQuantity(unit: .gramUnit(with: .kilo), doubleValue: leanMassKg), start: date, end: date))
        }
        do { try await store.save(samples) } catch { lastError = error.localizedDescription }
    }

    func saveNutrition(totals: MacroTargets, date: Date) async -> Bool {
        guard isAvailable, requested else { return false }
        let samples: [HKSample] = [
            HKQuantitySample(type: HKQuantityType(.dietaryEnergyConsumed), quantity: HKQuantity(unit: .kilocalorie(), doubleValue: Double(totals.calories)), start: date, end: date),
            HKQuantitySample(type: HKQuantityType(.dietaryProtein), quantity: HKQuantity(unit: .gram(), doubleValue: Double(totals.proteinGrams)), start: date, end: date),
            HKQuantitySample(type: HKQuantityType(.dietaryCarbohydrates), quantity: HKQuantity(unit: .gram(), doubleValue: Double(totals.carbsGrams)), start: date, end: date),
            HKQuantitySample(type: HKQuantityType(.dietaryFatTotal), quantity: HKQuantity(unit: .gram(), doubleValue: Double(totals.fatGrams)), start: date, end: date)
        ]
        do {
            try await store.save(samples)
            return true
        } catch {
            lastError = error.localizedDescription
            return false
        }
    }

    func saveWater(glasses: Int, date: Date) async {
        guard isAvailable, requested, glasses > 0 else { return }
        let sample = HKQuantitySample(type: HKQuantityType(.dietaryWater), quantity: HKQuantity(unit: .literUnit(with: .milli), doubleValue: Double(glasses) * 250), start: date, end: date)
        try? await store.save(sample)
    }

    func saveWorkout(kind: WorkoutKind, start: Date, minutes: Double, kcal: Double) async -> Bool {
        guard isAvailable, requested else { return false }
        let configuration = HKWorkoutConfiguration()
        configuration.activityType = Self.activityType(for: kind)
        let end = start.addingTimeInterval(minutes * 60)
        let builder = HKWorkoutBuilder(healthStore: store, configuration: configuration, device: .local())
        do {
            try await builder.beginCollection(at: start)
            if kcal > 0 {
                let energy = HKQuantitySample(type: HKQuantityType(.activeEnergyBurned), quantity: HKQuantity(unit: .kilocalorie(), doubleValue: kcal), start: start, end: end)
                try await builder.addSamples([energy])
            }
            try await builder.endCollection(at: end)
            _ = try await builder.finishWorkout()
            await refresh()
            return true
        } catch {
            lastError = error.localizedDescription
            return false
        }
    }

    // MARK: Mapping

    static func activityType(for kind: WorkoutKind) -> HKWorkoutActivityType {
        switch kind {
        case .walking: .walking
        case .running: .running
        case .cycling: .cycling
        case .swimming: .swimming
        case .football: .soccer
        case .strength: .traditionalStrengthTraining
        case .hiit: .highIntensityIntervalTraining
        case .yoga: .yoga
        case .other: .other
        }
    }

    static func kind(for type: HKWorkoutActivityType) -> WorkoutKind {
        switch type {
        case .walking, .hiking: .walking
        case .running: .running
        case .cycling: .cycling
        case .swimming: .swimming
        case .soccer: .football
        case .traditionalStrengthTraining, .functionalStrengthTraining: .strength
        case .highIntensityIntervalTraining: .hiit
        case .yoga, .pilates: .yoga
        default: .other
        }
    }
}

extension WorkoutKind {
    var ar: String {
        switch self {
        case .walking: "مشي"
        case .running: "جري"
        case .cycling: "عجلة"
        case .swimming: "سباحة"
        case .football: "كورة"
        case .strength: "أوزان"
        case .hiit: "HIIT"
        case .yoga: "يوجا"
        case .other: "تمرين"
        }
    }

    var en: String {
        switch self {
        case .walking: "Walking"
        case .running: "Running"
        case .cycling: "Cycling"
        case .swimming: "Swimming"
        case .football: "Football"
        case .strength: "Strength"
        case .hiit: "HIIT"
        case .yoga: "Yoga"
        case .other: "Workout"
        }
    }

    var symbol: String {
        switch self {
        case .walking: "figure.walk"
        case .running: "figure.run"
        case .cycling: "figure.outdoor.cycle"
        case .swimming: "figure.pool.swim"
        case .football: "figure.soccer"
        case .strength: "dumbbell"
        case .hiit: "bolt.heart"
        case .yoga: "figure.yoga"
        case .other: "figure.mixed.cardio"
        }
    }
}
