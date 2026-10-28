import Foundation

/// A ready-to-send Claude request: prompt, content, schema and effort.
public struct AIRequest: Sendable {
    public var system: String
    public var messages: [ClaudeMessage]
    public var schema: JSONValue
    public var effort: ClaudeEffort
    public var maxTokens: Int
}

/// A saved product offered to Claude so it can recognise it in photos and text.
public struct LibraryProduct: Sendable, Equatable {
    public var id: String
    public var nameAr: String
    public var nameEn: String
    public var brand: String
    public var servingSizeG: Double?
    public var per100g: NutrientsPer100

    public init(id: String, nameAr: String, nameEn: String, brand: String, servingSizeG: Double?, per100g: NutrientsPer100) {
        self.id = id
        self.nameAr = nameAr
        self.nameEn = nameEn
        self.brand = brand
        self.servingSizeG = servingSizeG
        self.per100g = per100g
    }

    var promptLine: String {
        let serving = servingSizeG.map { ", serving \(Int($0)) g" } ?? ""
        let brandText = brand.isEmpty ? "" : " [\(brand)]"
        return "- id=\(id): \(nameEn) / \(nameAr)\(brandText)\(serving); per 100 g: \(Int(per100g.kcal)) kcal, P \(per100g.proteinG), C \(per100g.carbsG), F \(per100g.fatG)"
    }
}

public enum PlanPreset: String, Codable, Sendable, CaseIterable, Identifiable {
    case egyptianHome, highProtein, budget, intermittentFasting, lowCarb, ramadan

    public var id: String { rawValue }

    public var ar: String {
        switch self {
        case .egyptianHome: "أكل بيتي مصري"
        case .highProtein: "بروتين عالي"
        case .budget: "اقتصادي"
        case .intermittentFasting: "صيام متقطع 16:8"
        case .lowCarb: "كارب قليل"
        case .ramadan: "رمضان"
        }
    }

    public var en: String {
        switch self {
        case .egyptianHome: "Egyptian home food"
        case .highProtein: "High protein"
        case .budget: "Budget"
        case .intermittentFasting: "Fasting 16:8"
        case .lowCarb: "Low carb"
        case .ramadan: "Ramadan"
        }
    }

    var instruction: String {
        switch self {
        case .egyptianHome: "Base meals on everyday Egyptian home cooking (ful, eggs, baladi bread, grilled chicken, kofta, fish, rice, molokhia, salads, lentil soup) in healthy portions."
        case .highProtein: "Push protein toward the top of the target in every meal using lean, affordable sources."
        case .budget: "Keep ingredients cheap and widely available in Egyptian supermarkets; reuse ingredients across days."
        case .intermittentFasting: "Fit all meals in an 8-hour eating window (about 12:00–20:00); no breakfast slot, use lunch, snack and dinner."
        case .lowCarb: "Keep carbs low (about 25% of calories) while hitting protein; favor vegetables, legumes in small portions."
        case .ramadan: "Plan suhoor and iftar (map suhoor to breakfast, iftar to dinner, one snack between); hydrating foods, dates at iftar."
        }
    }
}

public enum AITasks {
    static let persona = """
    You are Vitalyn, a friendly nutrition and fitness coach inside a personal health app for an Egyptian user.
    Every user-facing text field has an Arabic and an English version: write the Arabic in natural Egyptian Arabic \
    (the way Egyptians talk) and the English as a faithful, natural translation of the same content.
    Be accurate with numbers. Use the calorie and macro targets the app gives you; never invent different targets.
    Respect the user's chronic conditions, medications and allergies in every suggestion. Never tell the user to change, \
    stop or dose medication; for anything medical, suggest checking with their doctor.
    """

    // MARK: InBody

    public static let inBodySchema = Schema.object([
        ("scanDate", Schema.nullable(Schema.string("Scan date as YYYY-MM-DD if printed"))),
        ("weightKg", Schema.number("Body weight in kg")),
        ("skeletalMuscleKg", Schema.nullable(Schema.number("Skeletal Muscle Mass (SMM) in kg"))),
        ("bodyFatKg", Schema.nullable(Schema.number("Body Fat Mass in kg"))),
        ("bodyFatPercent", Schema.nullable(Schema.number("Percent Body Fat (PBF)"))),
        ("leanMassKg", Schema.nullable(Schema.number("Fat Free Mass / lean body mass in kg"))),
        ("visceralFatLevel", Schema.nullable(Schema.number("Visceral Fat Level"))),
        ("bmrKcal", Schema.nullable(Schema.number("Basal Metabolic Rate in kcal"))),
        ("totalBodyWaterL", Schema.nullable(Schema.number("Total Body Water in L"))),
        ("inbodyScore", Schema.nullable(Schema.number("InBody Score out of 100"))),
        ("heightCm", Schema.nullable(Schema.number("Height in cm if printed"))),
        ("ageYears", Schema.nullable(Schema.integer("Age if printed"))),
        ("segmentalLean", Schema.object([
            ("rightArmKg", Schema.nullable(Schema.number())),
            ("leftArmKg", Schema.nullable(Schema.number())),
            ("trunkKg", Schema.nullable(Schema.number())),
            ("rightLegKg", Schema.nullable(Schema.number())),
            ("leftLegKg", Schema.nullable(Schema.number()))
        ], description: "Segmental lean analysis in kg")),
        ("lowConfidenceFields", Schema.array(Schema.string(), description: "Names of fields above that were hard to read")),
        ("summaryAr", Schema.string("2–3 sentence summary of what the numbers mean for the user's goal, Egyptian Arabic")),
        ("summaryEn", Schema.string("Same summary in English"))
    ])

    public static func inBody(file: ClaudeContent, context: UserContext?) -> AIRequest {
        var system = persona + "\nYou read InBody body-composition result sheets (photos or PDFs) and extract the printed values exactly."
        if let context { system += "\n\n" + context.promptText() }
        let instruction = """
        Extract the values from this InBody result sheet. Copy numbers exactly as printed (kg, %, kcal). \
        Use null for anything not on the sheet. List any value you are unsure about in lowConfidenceFields. \
        If an earlier scan is shown in the history section, use only the current (most recent) scan.
        """
        return AIRequest(system: system, messages: [.user([file, .text(instruction)])], schema: inBodySchema, effort: .medium, maxTokens: 8000)
    }

    // MARK: Meals

    static let foodSchema = Schema.object([
        ("nameAr", Schema.string()),
        ("nameEn", Schema.string()),
        ("grams", Schema.number("Estimated eaten weight in grams")),
        ("kcal", Schema.number()),
        ("proteinG", Schema.number()),
        ("carbsG", Schema.number()),
        ("fatG", Schema.number()),
        ("confidence", Schema.enumeration(["high", "medium", "low"])),
        ("libraryProductId", Schema.nullable(Schema.string("id of the matching saved product, if any")))
    ])

    public static let mealSchema = Schema.object([
        ("titleAr", Schema.string("Short meal title, Egyptian Arabic")),
        ("titleEn", Schema.string("Short meal title, English")),
        ("items", Schema.array(foodSchema)),
        ("questions", Schema.array(Schema.object([
            ("questionAr", Schema.string()),
            ("questionEn", Schema.string()),
            ("itemIndex", Schema.nullable(Schema.integer("Index in items this question is about"))),
            ("options", Schema.array(Schema.object([
                ("labelAr", Schema.string()),
                ("labelEn", Schema.string()),
                ("libraryProductId", Schema.nullable(Schema.string("Choosing this option sets the item to this saved product"))),
                ("grams", Schema.nullable(Schema.number("Choosing this option sets the item weight to this many grams")))
            ])))
        ]), description: "At most 3 short questions, only when an answer would change the numbers meaningfully")),
        ("notesAr", Schema.string("One short note on the meal vs today's targets, Egyptian Arabic")),
        ("notesEn", Schema.string("Same note in English"))
    ])

    static func libraryText(_ library: [LibraryProduct]) -> String {
        guard !library.isEmpty else { return "The user has no saved products yet." }
        return "Saved products (use their exact nutrition when you recognise one; set libraryProductId):\n" + library.map(\.promptLine).joined(separator: "\n")
    }

    public static func mealFromPhoto(image: Data, note: String, library: [LibraryProduct], context: UserContext?) -> AIRequest {
        var system = persona + "\nYou estimate food, portions, calories and macros from meal photos, with Egyptian dishes and portion sizes in mind."
        if let context { system += "\n\n" + context.promptText() }
        system += "\n\n" + libraryText(library)
        var instruction = """
        Identify each food in this photo, estimate the eaten weight in grams, and give kcal and macros for that weight. \
        If a packaged product matches a saved product, use it. If you are unsure which saved product it is, or the portion \
        is ambiguous in a way that changes the numbers a lot, ask a short multiple-choice question.
        """
        if !note.isEmpty { instruction += "\nThe user added: \(note)" }
        return AIRequest(system: system, messages: [.user([.image(image, mediaType: "image/jpeg"), .text(instruction)])], schema: mealSchema, effort: .medium, maxTokens: 8000)
    }

    public static func mealFromText(_ text: String, library: [LibraryProduct], context: UserContext?) -> AIRequest {
        var system = persona + "\nYou turn a spoken or written description of a meal into foods with portions, calories and macros."
        if let context { system += "\n\n" + context.promptText() }
        system += "\n\n" + libraryText(library)
        let instruction = """
        The user ate: \(text)
        Break it into foods with estimated grams, kcal and macros (typical Egyptian portions when unspecified, e.g. a baladi \
        loaf is about 90 g). Use saved products when they match. Ask a short question only if the answer changes the numbers a lot.
        """
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: mealSchema, effort: .medium, maxTokens: 8000)
    }

    // MARK: Products

    public static let productSchema = Schema.object([
        ("nameAr", Schema.string("Product name in Arabic (translate if only English is printed)")),
        ("nameEn", Schema.string("Product name in English (translate if only Arabic is printed)")),
        ("brand", Schema.nullable(Schema.string())),
        ("barcode", Schema.nullable(Schema.string("Digits of the barcode if visible"))),
        ("servingSizeG", Schema.nullable(Schema.number("Serving or package size in grams (or ml)"))),
        ("servingDescAr", Schema.string("e.g. علبة 170 جم")),
        ("servingDescEn", Schema.string("e.g. 170 g cup")),
        ("per100g", Schema.object([
            ("kcal", Schema.number()),
            ("proteinG", Schema.number()),
            ("carbsG", Schema.number()),
            ("fatG", Schema.number()),
            ("fiberG", Schema.nullable(Schema.number())),
            ("sugarG", Schema.nullable(Schema.number())),
            ("sodiumMg", Schema.nullable(Schema.number()))
        ], description: "Values per 100 g (or 100 ml); convert from per-serving values if that is all the label shows"))
    ])

    public static func productFromLabel(images: [Data]) -> AIRequest {
        let system = persona + "\nYou read packaged-food labels and nutrition-facts tables (Arabic and/or English) accurately."
        var content: [ClaudeContent] = images.map { .image($0, mediaType: "image/jpeg") }
        content.append(.text("Read this product's name, brand, barcode and nutrition table. Return values per 100 g. If kJ only, convert to kcal (÷ 4.184)."))
        return AIRequest(system: system, messages: [.user(content)], schema: productSchema, effort: .medium, maxTokens: 4000)
    }

    // MARK: Coach

    public static let coachSchema = Schema.object([
        ("replyAr", Schema.string("The reply, Egyptian Arabic, conversational and concise")),
        ("replyEn", Schema.string("The same reply in English")),
        ("actions", Schema.array(Schema.object([
            ("type", Schema.enumeration(CoachActionType.allValues)),
            ("workout", Schema.nullable(Schema.object([
                ("nameAr", Schema.string()),
                ("nameEn", Schema.string()),
                ("kind", Schema.enumeration(WorkoutKind.allCases.map(\.rawValue))),
                ("minutes", Schema.number()),
                ("kcal", Schema.number("Estimated active kcal for this user"))
            ]))),
            ("waterGlasses", Schema.nullable(Schema.integer())),
            ("meal", Schema.nullable(Schema.object([
                ("titleAr", Schema.string()),
                ("titleEn", Schema.string()),
                ("items", Schema.array(foodSchema))
            ]))),
            ("videos", Schema.array(Schema.object([
                ("titleAr", Schema.string()),
                ("titleEn", Schema.string()),
                ("query", Schema.string("YouTube search query that finds a good matching workout video"))
            ])))
        ]), description: "Things the app should do. log_workout / log_water / log_meal only when the user says they did or ate something; suggest_videos when recommending workouts. Empty when nothing to do."))
    ])

    public static func coach(history: [ClaudeMessage], userMessage: String, context: UserContext?) -> AIRequest {
        var system = persona + """

        You are chatting with the user day to day: answer questions, suggest meals that fit what's left today, suggest workouts \
        that fit their conditions and level, and keep them motivated. Keep replies short (2–5 sentences) unless asked for detail.
        When the user reports exercise ("I played football for an hour"), estimate active kcal from their weight and log it. \
        When they report water or food, log it. When recommending workouts, add suggest_videos with good YouTube search queries.
        """
        if let context { system += "\n\n" + context.promptText() }
        let messages = history + [.user([.text(userMessage)])]
        return AIRequest(system: system, messages: messages, schema: coachSchema, effort: .low, maxTokens: 6000)
    }

    public static let tipSchema = Schema.object([
        ("tipAr", Schema.string("One practical suggestion for the rest of today, Egyptian Arabic, 1–2 sentences")),
        ("tipEn", Schema.string("Same in English"))
    ])

    public static func dailyTip(context: UserContext) -> AIRequest {
        let system = persona + "\n\n" + context.promptText()
        let instruction = "Give one concrete suggestion for the rest of today based on what's left of the targets (for example a dinner that closes the protein gap), mentioning grams and kcal."
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: tipSchema, effort: .low, maxTokens: 2000)
    }

    // MARK: Plans

    static let plannedMealSchema = Schema.object([
        ("slot", Schema.enumeration(MealSlot.allCases.map(\.rawValue))),
        ("nameAr", Schema.string()),
        ("nameEn", Schema.string()),
        ("kcal", Schema.number()),
        ("proteinG", Schema.number()),
        ("carbsG", Schema.number()),
        ("fatG", Schema.number()),
        ("recipeAr", Schema.string("Ingredients with grams and a one-line method, Egyptian Arabic")),
        ("recipeEn", Schema.string("Same in English"))
    ])

    public static let weekPlanSchema = Schema.object([
        ("days", Schema.array(Schema.object([
            ("dayIndex", Schema.integer("0 = first day of the plan … 6")),
            ("meals", Schema.array(plannedMealSchema))
        ]), description: "Exactly 7 days")),
        ("groceries", Schema.array(Schema.object([
            ("nameAr", Schema.string()),
            ("nameEn", Schema.string()),
            ("quantity", Schema.string("Total for the week, e.g. 1.5 kg"))
        ]))),
        ("notesAr", Schema.string("Short notes about the plan, Egyptian Arabic")),
        ("notesEn", Schema.string("Same in English"))
    ])

    public static func weekPlan(context: UserContext, preset: PlanPreset, request: String) -> AIRequest {
        let system = persona + "\nYou design weekly meal plans.\n\n" + context.promptText()
        var instruction = """
        Create a 7-day meal plan. Each day should land within about 5% of the calorie target and reach the protein target. \
        Style: \(preset.instruction) Vary meals across the week and keep recipes simple. Add a combined grocery list.
        """
        if !request.isEmpty { instruction += "\nThe user also asked: \(request)" }
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: weekPlanSchema, effort: .medium, maxTokens: 16000)
    }

    public static func swapMeal(context: UserContext, meal: PlannedMeal, request: String) -> AIRequest {
        let system = persona + "\n\n" + context.promptText()
        var instruction = "Suggest one different \(meal.slot.rawValue) to replace \"\(meal.nameEn)\" with similar kcal (\(Int(meal.kcal))) and protein (\(Int(meal.proteinG)) g)."
        if !request.isEmpty { instruction += " The user wants: \(request)" }
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: plannedMealSchema, effort: .low, maxTokens: 3000)
    }

    // MARK: Workouts

    public static let workoutsSchema = Schema.object([
        ("items", Schema.array(Schema.object([
            ("titleAr", Schema.string()),
            ("titleEn", Schema.string()),
            ("minutes", Schema.number()),
            ("intensity", Schema.enumeration(["low", "moderate", "high"])),
            ("whyAr", Schema.string("Why this fits the user, Egyptian Arabic, one sentence")),
            ("whyEn", Schema.string("Same in English")),
            ("youtubeQuery", Schema.string("YouTube search query for a good follow-along video"))
        ]), description: "3 to 5 workouts"))
    ])

    public static func workoutSuggestions(context: UserContext, request: String) -> AIRequest {
        let system = persona + "\nYou recommend safe, effective workouts for weight loss.\n\n" + context.promptText()
        var instruction = "Suggest workouts for the next few days that fit the user's level, conditions and recent activity, mixing cardio and strength."
        if !request.isEmpty { instruction += " The user asked: \(request)" }
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: workoutsSchema, effort: .low, maxTokens: 4000)
    }

    /// Every schema the app sends, for validation in tests.
    public static var allSchemas: [String: JSONValue] {
        [
            "inBody": inBodySchema,
            "meal": mealSchema,
            "product": productSchema,
            "coach": coachSchema,
            "tip": tipSchema,
            "weekPlan": weekPlanSchema,
            "plannedMeal": plannedMealSchema,
            "workouts": workoutsSchema
        ]
    }
}

extension CoachActionType {
    static var allValues: [String] { ["log_workout", "log_water", "log_meal", "suggest_videos"] }
}

extension ClaudeClient {
    public func generate<T: Decodable>(_ type: T.Type, _ request: AIRequest) async throws -> T {
        try await generate(type, system: request.system, messages: request.messages, schema: request.schema, effort: request.effort, maxTokens: request.maxTokens)
    }
}
