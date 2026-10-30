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

    public init(titleAr: String, titleEn: String, items: [AnalyzedFood]) {
        self.titleAr = titleAr
        self.titleEn = titleEn
        self.items = items
    }
}

public struct VideoSuggestion: Codable, Sendable, Equatable, Identifiable {
    public var id: String { query }
    public var titleAr: String
    public var titleEn: String
    public var query: String

    public init(titleAr: String, titleEn: String, query: String) {
        self.titleAr = titleAr
        self.titleEn = titleEn
        self.query = query
    }

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
    public var ingredients: [PlanIngredient]
    public var recipeAr: String
    public var recipeEn: String
}

extension PlannedMeal {
    /// Plans saved before meals listed their ingredients decode with none.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        slot = try container.decode(MealSlot.self, forKey: .slot)
        nameAr = try container.decode(String.self, forKey: .nameAr)
        nameEn = try container.decode(String.self, forKey: .nameEn)
        kcal = try container.decode(Double.self, forKey: .kcal)
        proteinG = try container.decode(Double.self, forKey: .proteinG)
        carbsG = try container.decode(Double.self, forKey: .carbsG)
        fatG = try container.decode(Double.self, forKey: .fatG)
        ingredients = try container.decodeIfPresent([PlanIngredient].self, forKey: .ingredients) ?? []
        recipeAr = try container.decode(String.self, forKey: .recipeAr)
        recipeEn = try container.decode(String.self, forKey: .recipeEn)
    }
}

public struct PlanDay: Codable, Sendable, Equatable {
    public var dayIndex: Int
    public var meals: [PlannedMeal]

    public init(dayIndex: Int, meals: [PlannedMeal]) {
        self.dayIndex = dayIndex
        self.meals = meals
    }

    public var totals: MacroTargets {
        MacroTargets(
            calories: Int(meals.reduce(0) { $0 + $1.kcal }.rounded()),
            proteinGrams: Int(meals.reduce(0) { $0 + $1.proteinG }.rounded()),
            carbsGrams: Int(meals.reduce(0) { $0 + $1.carbsG }.rounded()),
            fatGrams: Int(meals.reduce(0) { $0 + $1.fatG }.rounded())
        )
    }
}

/// Grocery list as older plans stored it; newer plans build it from meal ingredients.
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
    /// What to cook ahead and when.
    public var prepAr: String
    public var prepEn: String

    public init(days: [PlanDay], groceries: [GroceryItem], notesAr: String, notesEn: String, prepAr: String, prepEn: String) {
        self.days = days
        self.groceries = groceries
        self.notesAr = notesAr
        self.notesEn = notesEn
        self.prepAr = prepAr
        self.prepEn = prepEn
    }
}

extension WeekPlan {
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        days = try container.decode([PlanDay].self, forKey: .days)
        groceries = try container.decodeIfPresent([GroceryItem].self, forKey: .groceries) ?? []
        notesAr = try container.decode(String.self, forKey: .notesAr)
        notesEn = try container.decode(String.self, forKey: .notesEn)
        prepAr = try container.decodeIfPresent(String.self, forKey: .prepAr) ?? ""
        prepEn = try container.decodeIfPresent(String.self, forKey: .prepEn) ?? ""
    }

    /// Every ingredient name already in the plan, for keeping swaps consistent with the grocery list.
    public var ingredientNames: [MenuIngredient] {
        var seen = Set<String>()
        return days.flatMap { $0.meals.flatMap(\.ingredients) }.compactMap { ingredient in
            seen.insert(ingredient.nameEn.lowercased()).inserted
                ? MenuIngredient(nameAr: ingredient.nameAr, nameEn: ingredient.nameEn, category: ingredient.category)
                : nil
        }
    }
}

public struct DailyTip: Codable, Sendable, Equatable {
    public var tipAr: String
    public var tipEn: String

    public init(tipAr: String, tipEn: String) {
        self.tipAr = tipAr
        self.tipEn = tipEn
    }
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

public enum HealthItemKind: String, Codable, Sendable, CaseIterable {
    case condition, medication, allergy
}

/// What Claude understood from something the user typed in their health profile ("WPW", "Concor", …).
public struct HealthItemInsight: Codable, Sendable, Equatable, Identifiable {
    public var id: String { Self.key(kind, input) }
    public var kind: HealthItemKind
    public var input: String
    public var recognized: Bool
    public var nameAr: String
    public var nameEn: String
    public var whatItIsAr: String
    public var whatItIsEn: String
    public var notesAr: String
    public var notesEn: String

    public static func key(_ kind: HealthItemKind, _ input: String) -> String {
        "\(kind.rawValue):\(input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())"
    }
}

public struct HealthItemInsights: Codable, Sendable, Equatable {
    public var items: [HealthItemInsight]
}
