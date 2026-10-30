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
        case .intermittentFasting: "Fit all meals in an 8-hour eating window (about 12:00–20:00); the first meal of the day breaks the fast around noon, whatever its slot."
        case .lowCarb: "Keep carbs low (about 25% of calories) while hitting protein; favor vegetables, legumes in small portions."
        case .ramadan: "Plan iftar (the dinner slot), one snack between, and suhoor (the breakfast slot); hydrating foods, dates at iftar."
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

    public static let inBodySchema = JSONSchema.object([
        ("scanDate", JSONSchema.nullable(JSONSchema.string("Scan date as YYYY-MM-DD if printed"))),
        ("weightKg", JSONSchema.number("Body weight in kg")),
        ("skeletalMuscleKg", JSONSchema.nullable(JSONSchema.number("Skeletal Muscle Mass (SMM) in kg"))),
        ("bodyFatKg", JSONSchema.nullable(JSONSchema.number("Body Fat Mass in kg"))),
        ("bodyFatPercent", JSONSchema.nullable(JSONSchema.number("Percent Body Fat (PBF)"))),
        ("leanMassKg", JSONSchema.nullable(JSONSchema.number("Fat Free Mass / lean body mass in kg"))),
        ("visceralFatLevel", JSONSchema.nullable(JSONSchema.number("Visceral Fat Level"))),
        ("bmrKcal", JSONSchema.nullable(JSONSchema.number("Basal Metabolic Rate in kcal"))),
        ("totalBodyWaterL", JSONSchema.nullable(JSONSchema.number("Total Body Water in L"))),
        ("inbodyScore", JSONSchema.nullable(JSONSchema.number("InBody Score out of 100"))),
        ("heightCm", JSONSchema.nullable(JSONSchema.number("Height in cm if printed"))),
        ("ageYears", JSONSchema.nullable(JSONSchema.integer("Age if printed"))),
        ("segmentalLean", JSONSchema.object([
            ("rightArmKg", JSONSchema.nullable(JSONSchema.number())),
            ("leftArmKg", JSONSchema.nullable(JSONSchema.number())),
            ("trunkKg", JSONSchema.nullable(JSONSchema.number())),
            ("rightLegKg", JSONSchema.nullable(JSONSchema.number())),
            ("leftLegKg", JSONSchema.nullable(JSONSchema.number()))
        ], description: "Segmental lean analysis in kg")),
        ("lowConfidenceFields", JSONSchema.array(JSONSchema.string(), description: "Names of fields above that were hard to read")),
        ("summaryAr", JSONSchema.string("2–3 sentence summary of what the numbers mean for the user's goal, Egyptian Arabic")),
        ("summaryEn", JSONSchema.string("Same summary in English"))
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

    static let foodSchema = JSONSchema.object([
        ("nameAr", JSONSchema.string()),
        ("nameEn", JSONSchema.string()),
        ("grams", JSONSchema.number("Estimated eaten weight in grams")),
        ("kcal", JSONSchema.number()),
        ("proteinG", JSONSchema.number()),
        ("carbsG", JSONSchema.number()),
        ("fatG", JSONSchema.number()),
        ("confidence", JSONSchema.enumeration(["high", "medium", "low"])),
        ("libraryProductId", JSONSchema.nullable(JSONSchema.string("id of the matching saved product, if any")))
    ])

    public static let mealSchema = JSONSchema.object([
        ("titleAr", JSONSchema.string("Short meal title, Egyptian Arabic")),
        ("titleEn", JSONSchema.string("Short meal title, English")),
        ("items", JSONSchema.array(foodSchema)),
        ("questions", JSONSchema.array(JSONSchema.object([
            ("questionAr", JSONSchema.string()),
            ("questionEn", JSONSchema.string()),
            ("itemIndex", JSONSchema.nullable(JSONSchema.integer("Index in items this question is about"))),
            ("options", JSONSchema.array(JSONSchema.object([
                ("labelAr", JSONSchema.string()),
                ("labelEn", JSONSchema.string()),
                ("libraryProductId", JSONSchema.nullable(JSONSchema.string("Choosing this option sets the item to this saved product"))),
                ("grams", JSONSchema.nullable(JSONSchema.number("Choosing this option sets the item weight to this many grams")))
            ])))
        ]), description: "At most 3 short questions, only when an answer would change the numbers meaningfully")),
        ("notesAr", JSONSchema.string("One short note on the meal vs today's targets, Egyptian Arabic")),
        ("notesEn", JSONSchema.string("Same note in English"))
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

    public static let productSchema = JSONSchema.object([
        ("nameAr", JSONSchema.string("Product name in Arabic (translate if only English is printed)")),
        ("nameEn", JSONSchema.string("Product name in English (translate if only Arabic is printed)")),
        ("brand", JSONSchema.nullable(JSONSchema.string())),
        ("barcode", JSONSchema.nullable(JSONSchema.string("Digits of the barcode if visible"))),
        ("servingSizeG", JSONSchema.nullable(JSONSchema.number("Serving or package size in grams (or ml)"))),
        ("servingDescAr", JSONSchema.string("e.g. علبة 170 جم")),
        ("servingDescEn", JSONSchema.string("e.g. 170 g cup")),
        ("per100g", JSONSchema.object([
            ("kcal", JSONSchema.number()),
            ("proteinG", JSONSchema.number()),
            ("carbsG", JSONSchema.number()),
            ("fatG", JSONSchema.number()),
            ("fiberG", JSONSchema.nullable(JSONSchema.number())),
            ("sugarG", JSONSchema.nullable(JSONSchema.number())),
            ("sodiumMg", JSONSchema.nullable(JSONSchema.number()))
        ], description: "Values per 100 g (or 100 ml); convert from per-serving values if that is all the label shows"))
    ])

    public static func productFromLabel(images: [Data]) -> AIRequest {
        let system = persona + "\nYou read packaged-food labels and nutrition-facts tables (Arabic and/or English) accurately."
        var content: [ClaudeContent] = images.map { .image($0, mediaType: "image/jpeg") }
        content.append(.text("Read this product's name, brand, barcode and nutrition table. Return values per 100 g. If kJ only, convert to kcal (÷ 4.184)."))
        return AIRequest(system: system, messages: [.user(content)], schema: productSchema, effort: .medium, maxTokens: 4000)
    }

    // MARK: Coach

    public static let coachSchema = JSONSchema.object([
        ("replyAr", JSONSchema.string("The reply, Egyptian Arabic, conversational and concise")),
        ("replyEn", JSONSchema.string("The same reply in English")),
        ("actions", JSONSchema.array(JSONSchema.object([
            ("type", JSONSchema.enumeration(CoachActionType.allValues)),
            ("workout", JSONSchema.nullable(JSONSchema.object([
                ("nameAr", JSONSchema.string()),
                ("nameEn", JSONSchema.string()),
                ("kind", JSONSchema.enumeration(WorkoutKind.allCases.map(\.rawValue))),
                ("minutes", JSONSchema.number()),
                ("kcal", JSONSchema.number("Estimated active kcal for this user"))
            ]))),
            ("waterGlasses", JSONSchema.nullable(JSONSchema.integer())),
            ("meal", JSONSchema.nullable(JSONSchema.object([
                ("titleAr", JSONSchema.string()),
                ("titleEn", JSONSchema.string()),
                ("items", JSONSchema.array(foodSchema))
            ]))),
            ("videos", JSONSchema.array(JSONSchema.object([
                ("titleAr", JSONSchema.string()),
                ("titleEn", JSONSchema.string()),
                ("query", JSONSchema.string("YouTube search query that finds a good matching workout video"))
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

    public static let tipSchema = JSONSchema.object([
        ("tipAr", JSONSchema.string("One practical suggestion for the rest of today, Egyptian Arabic, 1–2 sentences")),
        ("tipEn", JSONSchema.string("Same in English"))
    ])

    public static func dailyTip(context: UserContext) -> AIRequest {
        let system = persona + "\n\n" + context.promptText()
        let instruction = "Give one concrete suggestion for the rest of today based on what's left of the targets (for example a dinner that closes the protein gap), mentioning grams and kcal."
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: tipSchema, effort: .low, maxTokens: 2000)
    }

    // MARK: Plans

    static let ingredientSchema = JSONSchema.object([
        ("nameAr", JSONSchema.string()),
        ("nameEn", JSONSchema.string()),
        ("amount", JSONSchema.number("Amount to buy for this meal")),
        ("unit", JSONSchema.enumeration(IngredientUnit.allCases.map(\.rawValue), description: "g = raw/uncooked weight, ml for liquids, piece for countable items (eggs, loaves, fruit)")),
        ("category", JSONSchema.enumeration(GroceryCategory.allCases.map(\.rawValue)))
    ])

    static let plannedMealSchema = JSONSchema.object([
        ("slot", JSONSchema.enumeration(MealSlot.allCases.map(\.rawValue))),
        ("nameAr", JSONSchema.string()),
        ("nameEn", JSONSchema.string()),
        ("kcal", JSONSchema.number()),
        ("proteinG", JSONSchema.number()),
        ("carbsG", JSONSchema.number()),
        ("fatG", JSONSchema.number()),
        ("ingredients", JSONSchema.array(ingredientSchema, description: "Every ingredient except water, salt and pinches of spice")),
        ("recipeAr", JSONSchema.string("Method only, 1–2 short sentences, Egyptian Arabic (ingredients are listed separately)")),
        ("recipeEn", JSONSchema.string("Same in English"))
    ])

    public static let weekPlanSchema = JSONSchema.object([
        ("days", JSONSchema.array(JSONSchema.object([
            ("dayIndex", JSONSchema.integer("0 = first day of the plan … 6")),
            ("meals", JSONSchema.array(plannedMealSchema, description: "The day's meals in the given order"))
        ]), description: "Exactly 7 days")),
        ("prepAr", JSONSchema.string("Short cooking plan for the week: what to cook ahead, on which day, and leftovers to reuse, Egyptian Arabic")),
        ("prepEn", JSONSchema.string("Same in English")),
        ("notesAr", JSONSchema.string("One or two short notes about the plan, Egyptian Arabic")),
        ("notesEn", JSONSchema.string("Same in English"))
    ])

    static func dayLabel(_ index: Int, start: Date) -> String {
        let date = Calendar(identifier: .gregorian).date(byAdding: .day, value: index, to: start) ?? start
        return date.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Locale(identifier: "en_US")))
    }

    static func ingredientList(_ ingredients: [MenuIngredient]) -> String {
        ingredients.map { "- \($0.nameEn) / \($0.nameAr) (\($0.category.rawValue))" }.joined(separator: "\n")
    }

    /// A full week: every meal with portions, macros, ingredients to buy and method.
    /// Long, so it is streamed and can run as a background request.
    public static func weekPlan(context: UserContext, preset: PlanPreset, mealsPerDay: MealsPerDay, start: Date, request: String) -> AIRequest {
        let system = persona + "\nYou design weekly meal plans that people actually cook and shop for.\n\n" + context.promptText()
        let slots = mealsPerDay.slots(for: preset).map(\.rawValue).joined(separator: ", ")
        let days = (0..<7).map { "- Day \($0): \(dayLabel($0, start: start))" }.joined(separator: "\n")
        var instruction = """
        Create a 7-day meal plan:
        \(days)
        Every day has exactly these meals, in this order: \(slots).
        Style: \(preset.instruction)
        Each day should land within about 5% of the calorie target and reach the protein target. Vary the dishes across \
        the week, but reuse ingredients so little goes to waste, and plan batch cooking and leftovers where it saves time.
        List each meal's ingredients in the amount to buy: raw/uncooked grams, ml for liquids, pieces for eggs, bread loaves \
        and fruit; count cooking oil. Use exactly the same names for the same ingredient every time it appears, so the \
        grocery list can add the week up. Keep each method to one or two sentences.
        """
        if !request.isEmpty { instruction += "\nThe user also asked: \(request)" }
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: weekPlanSchema, effort: .low, maxTokens: 64000)
    }

    public static func swapMeal(context: UserContext, meal: PlannedMeal, ingredients: [MenuIngredient], request: String) -> AIRequest {
        let system = persona + "\n\n" + context.promptText()
        var instruction = """
        Suggest one different \(meal.slot.rawValue) to replace "\(meal.nameEn)" in the user's weekly plan, with similar kcal \
        (\(Int(meal.kcal))) and protein (\(Int(meal.proteinG)) g). List its ingredients in the amount to buy (raw grams, ml, \
        or pieces). Prefer ingredients already on this week's grocery list and use their exact names:
        \(ingredientList(ingredients))
        """
        if !request.isEmpty { instruction += "\nThe user wants: \(request)" }
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: plannedMealSchema, effort: .low, maxTokens: 4000)
    }

    // MARK: Workouts

    public static let workoutsSchema = JSONSchema.object([
        ("items", JSONSchema.array(JSONSchema.object([
            ("titleAr", JSONSchema.string()),
            ("titleEn", JSONSchema.string()),
            ("minutes", JSONSchema.number()),
            ("intensity", JSONSchema.enumeration(["low", "moderate", "high"])),
            ("whyAr", JSONSchema.string("Why this fits the user, Egyptian Arabic, one sentence")),
            ("whyEn", JSONSchema.string("Same in English")),
            ("youtubeQuery", JSONSchema.string("YouTube search query for a good follow-along video"))
        ]), description: "3 to 5 workouts"))
    ])

    public static func workoutSuggestions(context: UserContext, request: String) -> AIRequest {
        let system = persona + "\nYou recommend safe, effective workouts for weight loss.\n\n" + context.promptText()
        var instruction = "Suggest workouts for the next few days that fit the user's level, conditions and recent activity, mixing cardio and strength."
        if !request.isEmpty { instruction += " The user asked: \(request)" }
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: workoutsSchema, effort: .low, maxTokens: 4000)
    }

    // MARK: Health profile items

    public static let healthItemsSchema = JSONSchema.object([
        ("items", JSONSchema.array(JSONSchema.object([
            ("kind", JSONSchema.enumeration(HealthItemKind.allCases.map(\.rawValue))),
            ("input", JSONSchema.string("Exactly what the user typed")),
            ("recognized", JSONSchema.boolean("True only if you are confident what this is")),
            ("nameAr", JSONSchema.string("Proper name in Arabic; for medications the generic name and drug class")),
            ("nameEn", JSONSchema.string("Proper name in English; for medications 'Brand (generic) — class'")),
            ("whatItIsAr", JSONSchema.string("One plain sentence on what it is, Egyptian Arabic")),
            ("whatItIsEn", JSONSchema.string("Same in English")),
            ("notesAr", JSONSchema.string("The 1–3 most important diet/exercise considerations for a weight-loss program, Egyptian Arabic. If not recognized, ask the user to write the full name.")),
            ("notesEn", JSONSchema.string("Same in English"))
        ])))
    ])

    /// Identifies conditions, medications and allergies the user typed, including abbreviations,
    /// Egyptian brand names and misspellings, and notes what matters for diet and exercise.
    public static func identifyHealthItems(_ items: [(HealthItemKind, String)], context: UserContext?) -> AIRequest {
        var system = persona + """

        You help the user record their health profile accurately. Users type abbreviations, brand names common in Egypt \
        and misspellings (e.g. "WPW" = Wolff-Parkinson-White syndrome; "Concor" = bisoprolol, a beta-blocker). \
        Identify each item. If you are not confident, set recognized=false, give your best guess in the names, and ask for \
        the full name in the notes. Point out interactions that matter for training and eating together with the user's \
        other items (for example, beta-blockers blunt heart rate, so effort should be judged by perceived exertion). \
        Never give dosing advice.
        """
        if let context { system += "\n\n" + context.promptText() }
        let list = items.map { "- \($0.0.rawValue): \($0.1)" }.joined(separator: "\n")
        let instruction = "Identify each of these items from the user's health profile and return one entry per item, in order:\n\(list)"
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: healthItemsSchema, effort: .medium, maxTokens: 6000)
    }

    // MARK: Motivation

    public static let motivationSchema = JSONSchema.object([
        ("messages", JSONSchema.array(JSONSchema.object([
            ("titleAr", JSONSchema.string("Short punchy title with one emoji, Egyptian Arabic, under 40 characters")),
            ("titleEn", JSONSchema.string("Same title in English")),
            ("bodyAr", JSONSchema.string("One or two warm sentences, Egyptian Arabic, under 110 characters")),
            ("bodyEn", JSONSchema.string("Same in English"))
        ]), description: "Exactly 7 messages, one per day"))
    ])

    /// A week of personal motivational notifications based on the user's real progress.
    public static func motivationWeek(context: UserContext, streakDays: Int, lostKg: Double) -> AIRequest {
        let system = persona + "\nYou write short, warm, specific motivational push notifications.\n\n" + context.promptText()
        let instruction = """
        Write 7 daily notifications for the coming week. Use the user's real progress (current streak \(streakDays) days, \
        \(String(format: "%.1f", lostKg)) kg lost so far, goal and targets above). Mix: celebrating progress, a concrete tip for \
        the day (water, protein, steps, sleep, an Egyptian food swap), and encouragement after hard days. Respect their \
        conditions. No guilt, no medical advice, no repeated messages.
        """
        return AIRequest(system: system, messages: [.user([.text(instruction)])], schema: motivationSchema, effort: .low, maxTokens: 4000)
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
            "workouts": workoutsSchema,
            "healthItems": healthItemsSchema,
            "motivation": motivationSchema
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

    /// The request to send for `request`, e.g. as a background upload.
    public func makeRequest(_ request: AIRequest) -> URLRequest {
        makeRequest(system: request.system, messages: request.messages, schema: request.schema, effort: request.effort, maxTokens: request.maxTokens)
    }
}
