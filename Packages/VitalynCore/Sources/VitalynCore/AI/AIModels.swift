import Foundation

// Results returned by Claude through structured outputs. Property names match the schemas in `AITasks`.

public struct SegmentalLean: Codable, Sendable, Equatable {
    public var rightArmKg: Double?
    public var leftArmKg: Double?
    public var trunkKg: Double?
    public var rightLegKg: Double?
    public var leftLegKg: Double?
}

public struct InBodyExtraction: Codable, Sendable, Equatable {
    public var scanDate: String?
    public var weightKg: Double
    public var skeletalMuscleKg: Double?
    public var bodyFatKg: Double?
    public var bodyFatPercent: Double?
    public var leanMassKg: Double?
    public var visceralFatLevel: Double?
    public var bmrKcal: Double?
    public var totalBodyWaterL: Double?
    public var inbodyScore: Double?
    public var heightCm: Double?
    public var ageYears: Int?
    public var segmentalLean: SegmentalLean
    public var lowConfidenceFields: [String]
    public var summaryAr: String
    public var summaryEn: String
}

public enum Confidence: String, Codable, Sendable {
    case high, medium, low
}

public struct AnalyzedFood: Codable, Sendable, Equatable, Identifiable {
    public var id: String { "\(nameEn)-\(grams)" }
    public var nameAr: String
    public var nameEn: String
    public var grams: Double
    public var kcal: Double
    public var proteinG: Double
    public var carbsG: Double
    public var fatG: Double
    public var confidence: Confidence
    public var libraryProductId: String?

    public init(nameAr: String, nameEn: String, grams: Double, kcal: Double, proteinG: Double, carbsG: Double, fatG: Double, confidence: Confidence, libraryProductId: String? = nil) {
        self.nameAr = nameAr
        self.nameEn = nameEn
        self.grams = grams
        self.kcal = kcal
        self.proteinG = proteinG
        self.carbsG = carbsG
        self.fatG = fatG
        self.confidence = confidence
        self.libraryProductId = libraryProductId
    }
}

public struct QuestionOption: Codable, Sendable, Equatable {
    public var labelAr: String
    public var labelEn: String
    public var libraryProductId: String?
    public var grams: Double?
}

public struct ClarifyingQuestion: Codable, Sendable, Equatable {
    public var questionAr: String
    public var questionEn: String
    public var itemIndex: Int?
    public var options: [QuestionOption]
}

public struct MealAnalysis: Codable, Sendable, Equatable {
    public var titleAr: String
    public var titleEn: String
    public var items: [AnalyzedFood]
    public var questions: [ClarifyingQuestion]
    public var notesAr: String
    public var notesEn: String
}

public struct NutrientsPer100: Codable, Sendable, Equatable {
    public var kcal: Double
    public var proteinG: Double
    public var carbsG: Double
    public var fatG: Double
    public var fiberG: Double?
    public var sugarG: Double?
    public var sodiumMg: Double?

    public init(kcal: Double, proteinG: Double, carbsG: Double, fatG: Double, fiberG: Double? = nil, sugarG: Double? = nil, sodiumMg: Double? = nil) {
        self.kcal = kcal
        self.proteinG = proteinG
        self.carbsG = carbsG
        self.fatG = fatG
        self.fiberG = fiberG
        self.sugarG = sugarG
        self.sodiumMg = sodiumMg
    }
}

public struct ProductExtraction: Codable, Sendable, Equatable {
    public var nameAr: String
    public var nameEn: String
    public var brand: String?
    public var barcode: String?
    public var servingSizeG: Double?
    public var servingDescAr: String
    public var servingDescEn: String
    public var per100g: NutrientsPer100

    public init(nameAr: String, nameEn: String, brand: String?, barcode: String?, servingSizeG: Double?, servingDescAr: String, servingDescEn: String, per100g: NutrientsPer100) {
        self.nameAr = nameAr
        self.nameEn = nameEn
        self.brand = brand
        self.barcode = barcode
        self.servingSizeG = servingSizeG
        self.servingDescAr = servingDescAr
        self.servingDescEn = servingDescEn
        self.per100g = per100g
    }
}

public enum CoachActionType: String, Codable, Sendable {
    case logWorkout = "log_workout"
    case logWater = "log_water"
    case logMeal = "log_meal"
    case suggestVideos = "suggest_videos"
}

public enum WorkoutKind: String, Codable, Sendable, CaseIterable {
    case walking, running, cycling, swimming, football, strength, hiit, yoga, other
}

public struct WorkoutLog: Codable, Sendable, Equatable {
    public var nameAr: String
    public var nameEn: String
    public var kind: WorkoutKind
    public var minutes: Double
    public var kcal: Double
}

public struct MealLog: Codable, Sendable, Equatable {
    public var titleAr: String
    public var titleEn: String
    public var items: [AnalyzedFood]
}

public struct VideoSuggestion: Codable, Sendable, Equatable, Identifiable {
    public var id: String { query }
    public var titleAr: String
    public var titleEn: String
    public var query: String

    public var youtubeURL: URL {
        var components = URLComponents(string: "https://www.youtube.com/results")!
        components.queryItems = [URLQueryItem(name: "search_query", value: query)]
        return components.url!
    }
}

public struct CoachAction: Codable, Sendable, Equatable {
    public var type: CoachActionType
    public var workout: WorkoutLog?
    public var waterGlasses: Int?
    public var meal: MealLog?
    public var videos: [VideoSuggestion]
}

public struct CoachReply: Codable, Sendable, Equatable {
    public var replyAr: String
    public var replyEn: String
    public var actions: [CoachAction]
}

public enum MealSlot: String, Codable, Sendable, CaseIterable {
    case breakfast, snack, lunch, dinner

    public var ar: String {
        switch self {
        case .breakfast: "فطار"
        case .snack: "سناك"
        case .lunch: "غدا"
        case .dinner: "عشا"
        }
    }

    public var en: String {
        switch self {
        case .breakfast: "Breakfast"
        case .snack: "Snack"
        case .lunch: "Lunch"
        case .dinner: "Dinner"
        }
    }
}

public struct PlannedMeal: Codable, Sendable, Equatable {
    public var slot: MealSlot
    public var nameAr: String
    public var nameEn: String
    public var kcal: Double
    public var proteinG: Double
    public var carbsG: Double
    public var fatG: Double
    public var recipeAr: String
    public var recipeEn: String
}

public struct PlanDay: Codable, Sendable, Equatable {
    public var dayIndex: Int
    public var meals: [PlannedMeal]
}

public struct GroceryItem: Codable, Sendable, Equatable {
    public var nameAr: String
    public var nameEn: String
    public var quantity: String
}

public struct WeekPlan: Codable, Sendable, Equatable {
    public var days: [PlanDay]
    public var groceries: [GroceryItem]
    public var notesAr: String
    public var notesEn: String
}

public struct DailyTip: Codable, Sendable, Equatable {
    public var tipAr: String
    public var tipEn: String
}

public struct WorkoutSuggestion: Codable, Sendable, Equatable {
    public var titleAr: String
    public var titleEn: String
    public var minutes: Double
    public var intensity: String
    public var whyAr: String
    public var whyEn: String
    public var youtubeQuery: String
}

public struct WorkoutSuggestions: Codable, Sendable, Equatable {
    public var items: [WorkoutSuggestion]
}
