package com.elshal.vitalyn.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A ready-to-send Claude request: prompt, content, schema and effort. */
data class AIRequest(
    val system: String,
    val messages: List<ClaudeMessage>,
    val schema: JsonElement,
    val effort: ClaudeEffort,
    val maxTokens: Int
)

/** A saved product offered to Claude so it can recognise it in photos and text. */
data class LibraryProduct(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val brand: String,
    val servingSizeG: Double?,
    val per100g: NutrientsPer100
) {
    val promptLine: String
        get() {
            val serving = servingSizeG?.let { ", serving ${it.toInt()} g" } ?: ""
            val brandText = if (brand.isEmpty()) "" else " [$brand]"
            return "- id=$id: $nameEn / $nameAr$brandText$serving; per 100 g: ${per100g.kcal.toInt()} kcal, " +
                "P ${swiftDescription(per100g.proteinG)}, C ${swiftDescription(per100g.carbsG)}, F ${swiftDescription(per100g.fatG)}"
        }
}

@Serializable
enum class PlanPreset(val raw: String) {
    @SerialName("egyptianHome") EGYPTIAN_HOME("egyptianHome"),
    @SerialName("highProtein") HIGH_PROTEIN("highProtein"),
    @SerialName("budget") BUDGET("budget"),
    @SerialName("intermittentFasting") INTERMITTENT_FASTING("intermittentFasting"),
    @SerialName("lowCarb") LOW_CARB("lowCarb"),
    @SerialName("ramadan") RAMADAN("ramadan");

    val id: String get() = raw

    val ar: String
        get() = when (this) {
            EGYPTIAN_HOME -> "أكل بيتي مصري"
            HIGH_PROTEIN -> "بروتين عالي"
            BUDGET -> "اقتصادي"
            INTERMITTENT_FASTING -> "صيام متقطع 16:8"
            LOW_CARB -> "كارب قليل"
            RAMADAN -> "رمضان"
        }

    val en: String
        get() = when (this) {
            EGYPTIAN_HOME -> "Egyptian home food"
            HIGH_PROTEIN -> "High protein"
            BUDGET -> "Budget"
            INTERMITTENT_FASTING -> "Fasting 16:8"
            LOW_CARB -> "Low carb"
            RAMADAN -> "Ramadan"
        }

    val instruction: String
        get() = when (this) {
            EGYPTIAN_HOME -> "Base meals on everyday Egyptian home cooking (ful, eggs, baladi bread, grilled chicken, kofta, fish, rice, molokhia, salads, lentil soup) in healthy portions."
            HIGH_PROTEIN -> "Push protein toward the top of the target in every meal using lean, affordable sources."
            BUDGET -> "Keep ingredients cheap and widely available in Egyptian supermarkets; reuse ingredients across days."
            INTERMITTENT_FASTING -> "Fit all meals in an 8-hour eating window (about 12:00–20:00); the first meal of the day breaks the fast around noon, whatever its slot."
            LOW_CARB -> "Keep carbs low (about 25% of calories) while hitting protein; favor vegetables, legumes in small portions."
            RAMADAN -> "Plan iftar (the dinner slot), one snack between, and suhoor (the breakfast slot); hydrating foods, dates at iftar."
        }

    companion object {
        fun fromRaw(raw: String): PlanPreset? = entries.firstOrNull { it.raw == raw }
    }
}

object AITasks {
    internal val persona: String =
        "You are Vitalyn, a friendly nutrition and fitness coach inside a personal health app for an Egyptian user.\n" +
            "Every user-facing text field has an Arabic and an English version: write the Arabic in natural Egyptian Arabic " +
            "(the way Egyptians talk) and the English as a faithful, natural translation of the same content.\n" +
            "Be accurate with numbers. Use the calorie and macro targets the app gives you; never invent different targets.\n" +
            "Respect the user's chronic conditions, medications and allergies in every suggestion. Never tell the user to change, " +
            "stop or dose medication; for anything medical, suggest checking with their doctor."

    // MARK: InBody

    val inBodySchema: JsonObject = JSONSchema.obj(
        "scanDate" to JSONSchema.nullable(JSONSchema.string("Scan date as YYYY-MM-DD if printed")),
        "weightKg" to JSONSchema.number("Body weight in kg"),
        "skeletalMuscleKg" to JSONSchema.nullable(JSONSchema.number("Skeletal Muscle Mass (SMM) in kg")),
        "bodyFatKg" to JSONSchema.nullable(JSONSchema.number("Body Fat Mass in kg")),
        "bodyFatPercent" to JSONSchema.nullable(JSONSchema.number("Percent Body Fat (PBF)")),
        "leanMassKg" to JSONSchema.nullable(JSONSchema.number("Fat Free Mass / lean body mass in kg")),
        "visceralFatLevel" to JSONSchema.nullable(JSONSchema.number("Visceral Fat Level")),
        "bmrKcal" to JSONSchema.nullable(JSONSchema.number("Basal Metabolic Rate in kcal")),
        "totalBodyWaterL" to JSONSchema.nullable(JSONSchema.number("Total Body Water in L")),
        "inbodyScore" to JSONSchema.nullable(JSONSchema.number("InBody Score out of 100")),
        "heightCm" to JSONSchema.nullable(JSONSchema.number("Height in cm if printed")),
        "ageYears" to JSONSchema.nullable(JSONSchema.integer("Age if printed")),
        "segmentalLean" to JSONSchema.obj(
            "rightArmKg" to JSONSchema.nullable(JSONSchema.number()),
            "leftArmKg" to JSONSchema.nullable(JSONSchema.number()),
            "trunkKg" to JSONSchema.nullable(JSONSchema.number()),
            "rightLegKg" to JSONSchema.nullable(JSONSchema.number()),
            "leftLegKg" to JSONSchema.nullable(JSONSchema.number()),
            description = "Segmental lean analysis in kg"
        ),
        "lowConfidenceFields" to JSONSchema.array(JSONSchema.string(), description = "Names of fields above that were hard to read"),
        "summaryAr" to JSONSchema.string("2–3 sentence summary of what the numbers mean for the user's goal, Egyptian Arabic"),
        "summaryEn" to JSONSchema.string("Same summary in English")
    )

    fun inBody(file: ClaudeContent, context: UserContext?): AIRequest {
        var system = persona + "\nYou read InBody body-composition result sheets (photos or PDFs) and extract the printed values exactly."
        if (context != null) system += "\n\n" + context.promptText()
        val instruction =
            "Extract the values from this InBody result sheet. Copy numbers exactly as printed (kg, %, kcal). " +
                "Use null for anything not on the sheet. List any value you are unsure about in lowConfidenceFields. " +
                "If an earlier scan is shown in the history section, use only the current (most recent) scan."
        return AIRequest(system, listOf(ClaudeMessage.user(listOf(file, ClaudeContent.Text(instruction)))), inBodySchema, ClaudeEffort.MEDIUM, 8000)
    }

    // MARK: Meals

    internal val foodSchema: JsonObject = JSONSchema.obj(
        "nameAr" to JSONSchema.string(),
        "nameEn" to JSONSchema.string(),
        "grams" to JSONSchema.number("Estimated eaten weight in grams"),
        "kcal" to JSONSchema.number(),
        "proteinG" to JSONSchema.number(),
        "carbsG" to JSONSchema.number(),
        "fatG" to JSONSchema.number(),
        "confidence" to JSONSchema.enumeration(listOf("high", "medium", "low")),
        "libraryProductId" to JSONSchema.nullable(JSONSchema.string("id of the matching saved product, if any"))
    )

    val mealSchema: JsonObject = JSONSchema.obj(
        "titleAr" to JSONSchema.string("Short meal title, Egyptian Arabic"),
        "titleEn" to JSONSchema.string("Short meal title, English"),
        "items" to JSONSchema.array(foodSchema),
        "questions" to JSONSchema.array(
            JSONSchema.obj(
                "questionAr" to JSONSchema.string(),
                "questionEn" to JSONSchema.string(),
                "itemIndex" to JSONSchema.nullable(JSONSchema.integer("Index in items this question is about")),
                "options" to JSONSchema.array(
                    JSONSchema.obj(
                        "labelAr" to JSONSchema.string(),
                        "labelEn" to JSONSchema.string(),
                        "libraryProductId" to JSONSchema.nullable(JSONSchema.string("Choosing this option sets the item to this saved product")),
                        "grams" to JSONSchema.nullable(JSONSchema.number("Choosing this option sets the item weight to this many grams"))
                    )
                )
            ),
            description = "At most 3 short questions, only when an answer would change the numbers meaningfully"
        ),
        "notesAr" to JSONSchema.string("One short note on the meal vs today's targets, Egyptian Arabic"),
        "notesEn" to JSONSchema.string("Same note in English")
    )

    internal fun libraryText(library: List<LibraryProduct>): String {
        if (library.isEmpty()) return "The user has no saved products yet."
        return "Saved products (use their exact nutrition when you recognise one; set libraryProductId):\n" +
            library.joinToString("\n") { it.promptLine }
    }

    fun mealFromPhoto(image: ByteArray, note: String, library: List<LibraryProduct>, context: UserContext?): AIRequest {
        var system = persona + "\nYou estimate food, portions, calories and macros from meal photos, with Egyptian dishes and portion sizes in mind."
        if (context != null) system += "\n\n" + context.promptText()
        system += "\n\n" + libraryText(library)
        var instruction =
            "Identify each food in this photo, estimate the eaten weight in grams, and give kcal and macros for that weight. " +
                "If a packaged product matches a saved product, use it. If you are unsure which saved product it is, or the portion " +
                "is ambiguous in a way that changes the numbers a lot, ask a short multiple-choice question."
        if (note.isNotEmpty()) instruction += "\nThe user added: $note"
        val content = listOf(ClaudeContent.Image(image, mediaType = "image/jpeg"), ClaudeContent.Text(instruction))
        return AIRequest(system, listOf(ClaudeMessage.user(content)), mealSchema, ClaudeEffort.MEDIUM, 8000)
    }

    fun mealFromText(text: String, library: List<LibraryProduct>, context: UserContext?): AIRequest {
        var system = persona + "\nYou turn a spoken or written description of a meal into foods with portions, calories and macros."
        if (context != null) system += "\n\n" + context.promptText()
        system += "\n\n" + libraryText(library)
        val instruction =
            "The user ate: $text\n" +
                "Break it into foods with estimated grams, kcal and macros (typical Egyptian portions when unspecified, e.g. a baladi " +
                "loaf is about 90 g). Use saved products when they match. Ask a short question only if the answer changes the numbers a lot."
        return AIRequest(system, listOf(ClaudeMessage.user(listOf(ClaudeContent.Text(instruction)))), mealSchema, ClaudeEffort.MEDIUM, 8000)
    }

    // MARK: Products

    val productSchema: JsonObject = JSONSchema.obj(
        "nameAr" to JSONSchema.string("Product name in Arabic (translate if only English is printed)"),
        "nameEn" to JSONSchema.string("Product name in English (translate if only Arabic is printed)"),
        "brand" to JSONSchema.nullable(JSONSchema.string()),
        "barcode" to JSONSchema.nullable(JSONSchema.string("Digits of the barcode if visible")),
        "servingSizeG" to JSONSchema.nullable(JSONSchema.number("Serving or package size in grams (or ml)")),
        "servingDescAr" to JSONSchema.string("e.g. علبة 170 جم"),
        "servingDescEn" to JSONSchema.string("e.g. 170 g cup"),
        "per100g" to JSONSchema.obj(
            "kcal" to JSONSchema.number(),
            "proteinG" to JSONSchema.number(),
            "carbsG" to JSONSchema.number(),
            "fatG" to JSONSchema.number(),
            "fiberG" to JSONSchema.nullable(JSONSchema.number()),
            "sugarG" to JSONSchema.nullable(JSONSchema.number()),
            "sodiumMg" to JSONSchema.nullable(JSONSchema.number()),
            description = "Values per 100 g (or 100 ml); convert from per-serving values if that is all the label shows"
        )
    )

    fun productFromLabel(images: List<ByteArray>): AIRequest {
        val system = persona + "\nYou read packaged-food labels and nutrition-facts tables (Arabic and/or English) accurately."
        val content = mutableListOf<ClaudeContent>()
        images.forEach { content += ClaudeContent.Image(it, mediaType = "image/jpeg") }
        content += ClaudeContent.Text("Read this product's name, brand, barcode and nutrition table. Return values per 100 g. If kJ only, convert to kcal (÷ 4.184).")
        return AIRequest(system, listOf(ClaudeMessage.user(content)), productSchema, ClaudeEffort.MEDIUM, 4000)
    }

    // MARK: Coach

    val coachSchema: JsonObject = JSONSchema.obj(
        "replyAr" to JSONSchema.string("The reply, Egyptian Arabic, conversational and concise"),
        "replyEn" to JSONSchema.string("The same reply in English"),
        "actions" to JSONSchema.array(
            JSONSchema.obj(
                "type" to JSONSchema.enumeration(CoachActionType.allValues),
                "workout" to JSONSchema.nullable(
                    JSONSchema.obj(
                        "nameAr" to JSONSchema.string(),
                        "nameEn" to JSONSchema.string(),
                        "kind" to JSONSchema.enumeration(WorkoutKind.entries.map { it.raw }),
                        "minutes" to JSONSchema.number(),
                        "kcal" to JSONSchema.number("Estimated active kcal for this user")
                    )
                ),
                "waterGlasses" to JSONSchema.nullable(JSONSchema.integer()),
                "meal" to JSONSchema.nullable(
                    JSONSchema.obj(
                        "titleAr" to JSONSchema.string(),
                        "titleEn" to JSONSchema.string(),
                        "items" to JSONSchema.array(foodSchema)
                    )
                ),
                "videos" to JSONSchema.array(
                    JSONSchema.obj(
                        "titleAr" to JSONSchema.string(),
                        "titleEn" to JSONSchema.string(),
                        "query" to JSONSchema.string("YouTube search query that finds a good matching workout video")
                    )
                )
            ),
            description = "Things the app should do. log_workout / log_water / log_meal only when the user says they did or ate something; suggest_videos when recommending workouts. Empty when nothing to do."
        )
    )

    fun coach(history: List<ClaudeMessage>, userMessage: String, context: UserContext?): AIRequest {
        var system = persona + "\n" +
            "You are chatting with the user day to day: answer questions, suggest meals that fit what's left today, suggest workouts " +
            "that fit their conditions and level, and keep them motivated. Keep replies short (2–5 sentences) unless asked for detail.\n" +
            "When the user reports exercise (\"I played football for an hour\"), estimate active kcal from their weight and log it. " +
            "When they report water or food, log it. When recommending workouts, add suggest_videos with good YouTube search queries."
        if (context != null) system += "\n\n" + context.promptText()
        val messages = history + ClaudeMessage.user(listOf(ClaudeContent.Text(userMessage)))
        return AIRequest(system, messages, coachSchema, ClaudeEffort.LOW, 6000)
    }

    val tipSchema: JsonObject = JSONSchema.obj(
        "tipAr" to JSONSchema.string("One practical suggestion for the rest of today, Egyptian Arabic, 1–2 sentences"),
        "tipEn" to JSONSchema.string("Same in English")
    )

    fun dailyTip(context: UserContext): AIRequest {
        val system = persona + "\n\n" + context.promptText()
        val instruction = "Give one concrete suggestion for the rest of today based on what's left of the targets (for example a dinner that closes the protein gap), mentioning grams and kcal."
        return AIRequest(system, listOf(ClaudeMessage.user(listOf(ClaudeContent.Text(instruction)))), tipSchema, ClaudeEffort.LOW, 2000)
    }

    // MARK: Plans

    internal val ingredientSchema: JsonObject = JSONSchema.obj(
        "nameAr" to JSONSchema.string(),
        "nameEn" to JSONSchema.string(),
        "amount" to JSONSchema.number("Amount to buy for this meal"),
        "unit" to JSONSchema.enumeration(IngredientUnit.entries.map { it.raw }, description = "g = raw/uncooked weight, ml for liquids, piece for countable items (eggs, loaves, fruit)"),
        "category" to JSONSchema.enumeration(GroceryCategory.entries.map { it.raw })
    )

    val plannedMealSchema: JsonObject = JSONSchema.obj(
        "slot" to JSONSchema.enumeration(MealSlot.entries.map { it.raw }),
        "nameAr" to JSONSchema.string(),
        "nameEn" to JSONSchema.string(),
        "kcal" to JSONSchema.number(),
        "proteinG" to JSONSchema.number(),
        "carbsG" to JSONSchema.number(),
        "fatG" to JSONSchema.number(),
        "ingredients" to JSONSchema.array(ingredientSchema, description = "Every ingredient except water, salt and pinches of spice"),
        "recipeAr" to JSONSchema.string("Method only, 1–2 short sentences, Egyptian Arabic (ingredients are listed separately)"),
        "recipeEn" to JSONSchema.string("Same in English")
    )

    val weekPlanSchema: JsonObject = JSONSchema.obj(
        "ingredients" to JSONSchema.array(
            JSONSchema.obj(
                "nameAr" to JSONSchema.string(),
                "nameEn" to JSONSchema.string("Short name, e.g. Chicken breast"),
                "category" to JSONSchema.enumeration(GroceryCategory.entries.map { it.raw })
            ),
            description = "Every ingredient the week needs, each once (water, salt and pinches of spice excluded)"
        ),
        "days" to JSONSchema.array(
            JSONSchema.obj(
                "dayIndex" to JSONSchema.integer("0 = first day of the plan … 6"),
                "meals" to JSONSchema.array(
                    JSONSchema.obj(
                        "slot" to JSONSchema.enumeration(MealSlot.entries.map { it.raw }),
                        "nameAr" to JSONSchema.string("Short dish name"),
                        "nameEn" to JSONSchema.string("Short dish name"),
                        "kcal" to JSONSchema.number(),
                        "proteinG" to JSONSchema.number(),
                        "carbsG" to JSONSchema.number(),
                        "fatG" to JSONSchema.number(),
                        "items" to JSONSchema.array(
                            JSONSchema.obj(
                                "ingredient" to JSONSchema.integer("Position in the ingredients list, from 0"),
                                "amount" to JSONSchema.number("Amount to buy for this meal"),
                                "unit" to JSONSchema.enumeration(IngredientUnit.entries.map { it.raw }, description = "g = raw/uncooked weight, ml for liquids, piece for eggs, loaves, fruit")
                            )
                        ),
                        "recipeAr" to JSONSchema.string("One short sentence of method, Egyptian Arabic"),
                        "recipeEn" to JSONSchema.string("Same in English")
                    ),
                    description = "The day's meals in the given order"
                )
            ),
            description = "Exactly 7 days"
        ),
        "prepAr" to JSONSchema.string("Short cooking plan for the week: what to cook ahead and on which day, Egyptian Arabic"),
        "prepEn" to JSONSchema.string("Same in English"),
        "notesAr" to JSONSchema.string("One short note about the plan, Egyptian Arabic"),
        "notesEn" to JSONSchema.string("Same in English")
    )

    private val dayLabelFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

    /** "Saturday, October 31" for the plan's day [index] counted from [start]. */
    internal fun dayLabel(index: Int, start: LocalDate): String = start.plusDays(index.toLong()).format(dayLabelFormat)

    internal fun ingredientList(ingredients: List<MenuIngredient>): String =
        ingredients.joinToString("\n") { "- ${it.nameEn} / ${it.nameAr} (${it.category.raw})" }

    /**
     * A full week: every meal with portions, macros, ingredients to buy and method.
     * Long, so it is streamed and can run as a background request. [start] is the plan's first day.
     */
    fun weekPlan(context: UserContext, preset: PlanPreset, mealsPerDay: MealsPerDay, start: LocalDate, request: String): AIRequest {
        val system = persona + "\nYou design weekly meal plans that people actually cook and shop for.\n\n" + context.promptText()
        val slots = mealsPerDay.slots(preset).joinToString(", ") { it.raw }
        val days = (0 until 7).joinToString("\n") { "- Day $it: ${dayLabel(it, start)}" }
        var instruction =
            "Create a 7-day meal plan:\n" +
                "$days\n" +
                "Every day has exactly these meals, in this order: $slots.\n" +
                "Style: ${preset.instruction}\n" +
                "Each day should land within about 5% of the calorie target and reach the protein target. Vary the dishes across " +
                "the week, but reuse ingredients so little goes to waste, and plan batch cooking and leftovers where it saves time.\n" +
                "First list every ingredient the week needs, once each. Then, for each meal, point to its ingredients by their " +
                "position in that list with the amount to buy: raw/uncooked grams, ml for liquids, pieces for eggs, bread loaves " +
                "and fruit; count cooking oil. Keep dish names short and each method to one short sentence."
        if (request.isNotEmpty()) instruction += "\nThe user also asked: $request"
        return AIRequest(system, listOf(ClaudeMessage.user(listOf(ClaudeContent.Text(instruction)))), weekPlanSchema, ClaudeEffort.LOW, 32000)
    }

    fun swapMeal(context: UserContext, meal: PlannedMeal, ingredients: List<MenuIngredient>, request: String): AIRequest {
        val system = persona + "\n\n" + context.promptText()
        var instruction =
            "Suggest one different ${meal.slot.raw} to replace \"${meal.nameEn}\" in the user's weekly plan, with similar kcal " +
                "(${meal.kcal.toInt()}) and protein (${meal.proteinG.toInt()} g). List its ingredients in the amount to buy (raw grams, ml, " +
                "or pieces). Prefer ingredients already on this week's grocery list and use their exact names:\n" +
                ingredientList(ingredients)
        if (request.isNotEmpty()) instruction += "\nThe user wants: $request"
        return AIRequest(system, listOf(ClaudeMessage.user(listOf(ClaudeContent.Text(instruction)))), plannedMealSchema, ClaudeEffort.LOW, 4000)
    }

    // MARK: Workouts

    val workoutsSchema: JsonObject = JSONSchema.obj(
        "items" to JSONSchema.array(
            JSONSchema.obj(
                "titleAr" to JSONSchema.string(),
                "titleEn" to JSONSchema.string(),
                "minutes" to JSONSchema.number(),
                "intensity" to JSONSchema.enumeration(listOf("low", "moderate", "high")),
                "whyAr" to JSONSchema.string("Why this fits the user, Egyptian Arabic, one sentence"),
                "whyEn" to JSONSchema.string("Same in English"),
                "youtubeQuery" to JSONSchema.string("YouTube search query for a good follow-along video")
            ),
            description = "3 to 5 workouts"
        )
    )

    fun workoutSuggestions(context: UserContext, request: String): AIRequest {
        val system = persona + "\nYou recommend safe, effective workouts for weight loss.\n\n" + context.promptText()
        var instruction = "Suggest workouts for the next few days that fit the user's level, conditions and recent activity, mixing cardio and strength."
        if (request.isNotEmpty()) instruction += " The user asked: $request"
        return AIRequest(system, listOf(ClaudeMessage.user(listOf(ClaudeContent.Text(instruction)))), workoutsSchema, ClaudeEffort.LOW, 4000)
    }

    // MARK: Health profile items

    val healthItemsSchema: JsonObject = JSONSchema.obj(
        "items" to JSONSchema.array(
            JSONSchema.obj(
                "kind" to JSONSchema.enumeration(HealthItemKind.entries.map { it.raw }),
                "input" to JSONSchema.string("Exactly what the user typed"),
                "recognized" to JSONSchema.boolean("True only if you are confident what this is"),
                "nameAr" to JSONSchema.string("Proper name in Arabic; for medications the generic name and drug class"),
                "nameEn" to JSONSchema.string("Proper name in English; for medications 'Brand (generic) — class'"),
                "whatItIsAr" to JSONSchema.string("One plain sentence on what it is, Egyptian Arabic"),
                "whatItIsEn" to JSONSchema.string("Same in English"),
                "notesAr" to JSONSchema.string("The 1–3 most important diet/exercise considerations for a weight-loss program, Egyptian Arabic. If not recognized, ask the user to write the full name."),
                "notesEn" to JSONSchema.string("Same in English")
            )
        )
    )

    /**
     * Identifies conditions, medications and allergies the user typed, including abbreviations,
     * Egyptian brand names and misspellings, and notes what matters for diet and exercise.
     */
    fun identifyHealthItems(items: List<Pair<HealthItemKind, String>>, context: UserContext?): AIRequest {
        var system = persona + "\n" +
            "You help the user record their health profile accurately. Users type abbreviations, brand names common in Egypt " +
            "and misspellings (e.g. \"WPW\" = Wolff-Parkinson-White syndrome; \"Concor\" = bisoprolol, a beta-blocker). " +
            "Identify each item. If you are not confident, set recognized=false, give your best guess in the names, and ask for " +
            "the full name in the notes. Point out interactions that matter for training and eating together with the user's " +
            "other items (for example, beta-blockers blunt heart rate, so effort should be judged by perceived exertion). " +
            "Never give dosing advice."
        if (context != null) system += "\n\n" + context.promptText()
        val list = items.joinToString("\n") { "- ${it.first.raw}: ${it.second}" }
        val instruction = "Identify each of these items from the user's health profile and return one entry per item, in order:\n$list"
        return AIRequest(system, listOf(ClaudeMessage.user(listOf(ClaudeContent.Text(instruction)))), healthItemsSchema, ClaudeEffort.MEDIUM, 6000)
    }

    // MARK: Motivation

    val motivationSchema: JsonObject = JSONSchema.obj(
        "messages" to JSONSchema.array(
            JSONSchema.obj(
                "titleAr" to JSONSchema.string("Short punchy title with one emoji, Egyptian Arabic, under 40 characters"),
                "titleEn" to JSONSchema.string("Same title in English"),
                "bodyAr" to JSONSchema.string("One or two warm sentences, Egyptian Arabic, under 110 characters"),
                "bodyEn" to JSONSchema.string("Same in English")
            ),
            description = "Exactly 7 messages, one per day"
        )
    )

    /** A week of personal motivational notifications based on the user's real progress. */
    fun motivationWeek(context: UserContext, streakDays: Int, lostKg: Double): AIRequest {
        val system = persona + "\nYou write short, warm, specific motivational push notifications.\n\n" + context.promptText()
        val instruction =
            "Write 7 daily notifications for the coming week. Use the user's real progress (current streak $streakDays days, " +
                "${formatOneDecimal(lostKg)} kg lost so far, goal and targets above). Mix: celebrating progress, a concrete tip for " +
                "the day (water, protein, steps, sleep, an Egyptian food swap), and encouragement after hard days. Respect their " +
                "conditions. No guilt, no medical advice, no repeated messages."
        return AIRequest(system, listOf(ClaudeMessage.user(listOf(ClaudeContent.Text(instruction)))), motivationSchema, ClaudeEffort.LOW, 4000)
    }

    /** Every schema the app sends, for validation in tests. */
    val allSchemas: Map<String, JsonObject>
        get() = linkedMapOf(
            "inBody" to inBodySchema,
            "meal" to mealSchema,
            "product" to productSchema,
            "coach" to coachSchema,
            "tip" to tipSchema,
            "weekPlan" to weekPlanSchema,
            "plannedMeal" to plannedMealSchema,
            "workouts" to workoutsSchema,
            "healthItems" to healthItemsSchema,
            "motivation" to motivationSchema
        )
}
