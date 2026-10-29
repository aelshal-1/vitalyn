import Foundation
import SwiftData
import VitalynCore

// SwiftData models. Every property has a default and every relationship is optional with an inverse,
// so the store can sync through CloudKit between iPhone and iPad.

@Model
final class UserProfile {
    var name: String = ""
    var birthYear: Int = 1990
    var sexRaw: String = Sex.male.rawValue
    var heightCm: Double = 175
    var startWeightKg: Double = 90
    var goalWeightKg: Double = 80
    var weeklyLossKg: Double = 0.5
    var activityRaw: String = ActivityLevel.light.rawValue
    var conditionsRaw: [String] = []
    var otherConditions: String = ""
    var medications: String = ""
    var allergies: String = ""
    var preferences: [String] = []
    var stepsGoal: Int = 8000
    var waterGoal: Int = 8
    var createdAt: Date = Date()
    /// JSON array of HealthItemInsight: what Claude understood from conditions, medications and allergies.
    var healthInsightsData: Data = Data()

    init() {}

    var healthInsights: [HealthItemInsight] {
        get { (try? JSONDecoder().decode([HealthItemInsight].self, from: healthInsightsData)) ?? [] }
        set { healthInsightsData = (try? JSONEncoder().encode(newValue)) ?? Data() }
    }

    /// Items currently in the profile, by kind.
    var healthItems: [(HealthItemKind, String)] {
        ProfileList.items(otherConditions).map { (.condition, $0) }
            + ProfileList.items(medications).map { (.medication, $0) }
            + ProfileList.items(allergies).map { (.allergy, $0) }
    }

    /// Insights for items still in the profile.
    var currentHealthInsights: [HealthItemInsight] {
        let keys = Set(healthItems.map { HealthItemInsight.key($0.0, $0.1) })
        return healthInsights.filter { keys.contains($0.id) }
    }

    var sex: Sex {
        get { Sex(rawValue: sexRaw) ?? .male }
        set { sexRaw = newValue.rawValue }
    }

    var activity: ActivityLevel {
        get { ActivityLevel(rawValue: activityRaw) ?? .light }
        set { activityRaw = newValue.rawValue }
    }

    var conditions: [ChronicCondition] {
        get { conditionsRaw.compactMap(ChronicCondition.init(rawValue:)) }
        set { conditionsRaw = newValue.map(\.rawValue) }
    }

    var ageYears: Int {
        max(10, Calendar.current.component(.year, from: Date()) - birthYear)
    }
}

@Model
final class InBodyScan {
    var date: Date = Date()
    var weightKg: Double = 0
    var skeletalMuscleKg: Double?
    var bodyFatKg: Double?
    var bodyFatPercent: Double?
    var leanMassKg: Double?
    var visceralFatLevel: Double?
    var bmrKcal: Double?
    var totalBodyWaterL: Double?
    var inbodyScore: Double?
    var rightArmKg: Double?
    var leftArmKg: Double?
    var trunkKg: Double?
    var rightLegKg: Double?
    var leftLegKg: Double?
    var summaryAr: String = ""
    var summaryEn: String = ""
    @Attribute(.externalStorage) var sourceFile: Data?
    var sourceIsPDF: Bool = false

    init() {}

    var effectiveLeanMassKg: Double? {
        if let leanMassKg { return leanMassKg }
        if let bodyFatKg { return weightKg - bodyFatKg }
        if let bodyFatPercent { return weightKg * (1 - bodyFatPercent / 100) }
        return nil
    }

    var effectiveBodyFatPercent: Double? {
        if let bodyFatPercent { return bodyFatPercent }
        if let bodyFatKg, weightKg > 0 { return bodyFatKg / weightKg * 100 }
        return nil
    }

    var promptSummary: String {
        var parts = ["\(date.formatted(.iso8601.year().month().day())): weight \(weightKg) kg"]
        if let value = skeletalMuscleKg { parts.append("SMM \(value) kg") }
        if let value = effectiveBodyFatPercent { parts.append("body fat \(String(format: "%.1f", value))%") }
        if let value = visceralFatLevel { parts.append("visceral fat \(Int(value))") }
        if let value = bmrKcal { parts.append("BMR \(Int(value)) kcal") }
        return parts.joined(separator: ", ")
    }
}

@Model
final class FoodProduct {
    var nameAr: String = ""
    var nameEn: String = ""
    var brand: String = ""
    var barcode: String = ""
    var servingSizeG: Double?
    var servingDescAr: String = ""
    var servingDescEn: String = ""
    var kcalPer100: Double = 0
    var proteinPer100: Double = 0
    var carbsPer100: Double = 0
    var fatPer100: Double = 0
    var fiberPer100: Double?
    var sugarPer100: Double?
    var sodiumMgPer100: Double?
    var categoryRaw: String = "other"
    var sourceRaw: String = "label"
    @Attribute(.externalStorage) var photo: Data?
    var createdAt: Date = Date()
    var uid: String = UUID().uuidString
    @Relationship(deleteRule: .nullify, inverse: \MealItem.product) var mealItems: [MealItem]? = []

    init() {}

    var per100g: NutrientsPer100 {
        NutrientsPer100(kcal: kcalPer100, proteinG: proteinPer100, carbsG: carbsPer100, fatG: fatPer100, fiberG: fiberPer100, sugarG: sugarPer100, sodiumMg: sodiumMgPer100)
    }

    var library: LibraryProduct {
        LibraryProduct(id: uid, nameAr: nameAr, nameEn: nameEn, brand: brand, servingSizeG: servingSizeG, per100g: per100g)
    }

    func apply(_ extraction: ProductExtraction) {
        nameAr = extraction.nameAr
        nameEn = extraction.nameEn
        brand = extraction.brand ?? ""
        if let code = extraction.barcode, !code.isEmpty { barcode = code }
        servingSizeG = extraction.servingSizeG
        servingDescAr = extraction.servingDescAr
        servingDescEn = extraction.servingDescEn
        kcalPer100 = extraction.per100g.kcal
        proteinPer100 = extraction.per100g.proteinG
        carbsPer100 = extraction.per100g.carbsG
        fatPer100 = extraction.per100g.fatG
        fiberPer100 = extraction.per100g.fiberG
        sugarPer100 = extraction.per100g.sugarG
        sodiumMgPer100 = extraction.per100g.sodiumMg
    }
}

@Model
final class MealEntry {
    var date: Date = Date()
    var slotRaw: String = MealSlot.lunch.rawValue
    var titleAr: String = ""
    var titleEn: String = ""
    var sourceRaw: String = "photo"
    @Attribute(.externalStorage) var photo: Data?
    @Relationship(deleteRule: .cascade, inverse: \MealItem.meal) var items: [MealItem]? = []
    var savedToHealth: Bool = false

    init() {}

    var slot: MealSlot {
        get { MealSlot(rawValue: slotRaw) ?? .lunch }
        set { slotRaw = newValue.rawValue }
    }

    var sortedItems: [MealItem] {
        (items ?? []).sorted { $0.order < $1.order }
    }

    var totals: MacroTargets {
        Portions.total(sortedItems.map(\.analyzed))
    }

    var promptLine: String {
        let total = totals
        let names = sortedItems.map { "\($0.nameEn) \(Int($0.grams)) g" }.joined(separator: ", ")
        return "\(slot.en) at \(date.formatted(date: .omitted, time: .shortened)): \(names) — \(total.calories) kcal, P \(total.proteinGrams) g"
    }
}

@Model
final class MealItem {
    var order: Int = 0
    var nameAr: String = ""
    var nameEn: String = ""
    var grams: Double = 0
    var kcal: Double = 0
    var proteinG: Double = 0
    var carbsG: Double = 0
    var fatG: Double = 0
    var meal: MealEntry?
    var product: FoodProduct?

    init() {}

    convenience init(_ food: AnalyzedFood, order: Int) {
        self.init()
        self.order = order
        nameAr = food.nameAr
        nameEn = food.nameEn
        grams = food.grams
        kcal = food.kcal
        proteinG = food.proteinG
        carbsG = food.carbsG
        fatG = food.fatG
    }

    var analyzed: AnalyzedFood {
        AnalyzedFood(nameAr: nameAr, nameEn: nameEn, grams: grams, kcal: kcal, proteinG: proteinG, carbsG: carbsG, fatG: fatG, confidence: .high, libraryProductId: product?.uid)
    }
}

@Model
final class WorkoutEntry {
    var date: Date = Date()
    var nameAr: String = ""
    var nameEn: String = ""
    var kindRaw: String = WorkoutKind.other.rawValue
    var minutes: Double = 0
    var kcal: Double = 0
    var savedToHealth: Bool = false

    init() {}

    var kind: WorkoutKind {
        get { WorkoutKind(rawValue: kindRaw) ?? .other }
        set { kindRaw = newValue.rawValue }
    }
}

@Model
final class MealPlanRecord {
    var createdAt: Date = Date()
    var startDate: Date = Date()
    var presetRaw: String = PlanPreset.egyptianHome.rawValue
    var planData: Data = Data()

    init() {}

    var plan: WeekPlan? {
        get { try? JSONDecoder().decode(WeekPlan.self, from: planData) }
        set { planData = (try? JSONEncoder().encode(newValue)) ?? Data() }
    }
}

@Model
final class CoachMessage {
    var date: Date = Date()
    var roleRaw: String = "user"
    var textAr: String = ""
    var textEn: String = ""
    var actionsData: Data?

    init() {}

    var isUser: Bool { roleRaw == "user" }

    var actions: [CoachAction] {
        guard let actionsData else { return [] }
        return (try? JSONDecoder().decode([CoachAction].self, from: actionsData)) ?? []
    }
}

enum PhotoKind: String, CaseIterable {
    case faceFront, faceRight, faceLeft, bodyFront, bodySide
}

@Model
final class ProgressPhoto {
    var date: Date = Date()
    var kindRaw: String = PhotoKind.bodyFront.rawValue
    @Attribute(.externalStorage) var image: Data?

    init() {}
}

@Model
final class AvatarAsset {
    var date: Date = Date()
    var fileName: String = "avatar.usdz"
    @Attribute(.externalStorage) var file: Data?

    init() {}
}

enum VitalynSchema {
    static let models: [any PersistentModel.Type] = [
        UserProfile.self, InBodyScan.self, FoodProduct.self, MealEntry.self, MealItem.self,
        WorkoutEntry.self, MealPlanRecord.self, CoachMessage.self, ProgressPhoto.self, AvatarAsset.self
    ]
}
