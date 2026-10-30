package com.elshal.vitalyn.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Results returned by Claude through structured outputs. Property names match the schemas in `AITasks`.

@Serializable
data class SegmentalLean(
    val rightArmKg: Double? = null,
    val leftArmKg: Double? = null,
    val trunkKg: Double? = null,
    val rightLegKg: Double? = null,
    val leftLegKg: Double? = null
)

@Serializable
data class InBodyExtraction(
    val scanDate: String? = null,
    val weightKg: Double,
    val skeletalMuscleKg: Double? = null,
    val bodyFatKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val leanMassKg: Double? = null,
    val visceralFatLevel: Double? = null,
    val bmrKcal: Double? = null,
    val totalBodyWaterL: Double? = null,
    val inbodyScore: Double? = null,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val segmentalLean: SegmentalLean,
    val lowConfidenceFields: List<String>,
    val summaryAr: String,
    val summaryEn: String
)

@Serializable
enum class Confidence(val raw: String) {
    @SerialName("high") HIGH("high"),
    @SerialName("medium") MEDIUM("medium"),
    @SerialName("low") LOW("low");

    companion object {
        fun fromRaw(raw: String): Confidence? = entries.firstOrNull { it.raw == raw }
    }
}

@Serializable
data class AnalyzedFood(
    val nameAr: String,
    val nameEn: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val confidence: Confidence,
    val libraryProductId: String? = null
) {
    /** Swift: `"\(nameEn)-\(grams)"`, e.g. `Rice-150.0`. */
    val id: String get() = "$nameEn-${swiftDescription(grams)}"
}

@Serializable
data class QuestionOption(
    val labelAr: String,
    val labelEn: String,
    val libraryProductId: String? = null,
    val grams: Double? = null
)

@Serializable
data class ClarifyingQuestion(
    val questionAr: String,
    val questionEn: String,
    val itemIndex: Int? = null,
    val options: List<QuestionOption>
)

@Serializable
data class MealAnalysis(
    val titleAr: String,
    val titleEn: String,
    val items: List<AnalyzedFood>,
    val questions: List<ClarifyingQuestion>,
    val notesAr: String,
    val notesEn: String
)

@Serializable
data class NutrientsPer100(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double? = null,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null
)

@Serializable
data class ProductExtraction(
    val nameAr: String,
    val nameEn: String,
    val brand: String? = null,
    val barcode: String? = null,
    val servingSizeG: Double? = null,
    val servingDescAr: String,
    val servingDescEn: String,
    val per100g: NutrientsPer100
)

@Serializable
enum class CoachActionType(val raw: String) {
    @SerialName("log_workout") LOG_WORKOUT("log_workout"),
    @SerialName("log_water") LOG_WATER("log_water"),
    @SerialName("log_meal") LOG_MEAL("log_meal"),
    @SerialName("suggest_videos") SUGGEST_VIDEOS("suggest_videos");

    companion object {
        fun fromRaw(raw: String): CoachActionType? = entries.firstOrNull { it.raw == raw }

        val allValues: List<String> get() = listOf("log_workout", "log_water", "log_meal", "suggest_videos")
    }
}

@Serializable
enum class WorkoutKind(val raw: String) {
    @SerialName("walking") WALKING("walking"),
    @SerialName("running") RUNNING("running"),
    @SerialName("cycling") CYCLING("cycling"),
    @SerialName("swimming") SWIMMING("swimming"),
    @SerialName("football") FOOTBALL("football"),
    @SerialName("strength") STRENGTH("strength"),
    @SerialName("hiit") HIIT("hiit"),
    @SerialName("yoga") YOGA("yoga"),
    @SerialName("other") OTHER("other");

    companion object {
        fun fromRaw(raw: String): WorkoutKind? = entries.firstOrNull { it.raw == raw }
    }
}

@Serializable
data class WorkoutLog(
    val nameAr: String,
    val nameEn: String,
    val kind: WorkoutKind,
    val minutes: Double,
    val kcal: Double
)

@Serializable
data class MealLog(
    val titleAr: String,
    val titleEn: String,
    val items: List<AnalyzedFood>
)

@Serializable
data class VideoSuggestion(
    val titleAr: String,
    val titleEn: String,
    val query: String
) {
    val id: String get() = query

    /** `https://www.youtube.com/results?search_query=...`, encoded like Foundation's URLComponents. */
    val youtubeURL: String
        get() = "https://www.youtube.com/results?search_query=" + percentEncode(query, ::isQueryItemAllowed)
}

@Serializable
data class CoachAction(
    val type: CoachActionType,
    val workout: WorkoutLog? = null,
    val waterGlasses: Int? = null,
    val meal: MealLog? = null,
    val videos: List<VideoSuggestion>
)

@Serializable
data class CoachReply(
    val replyAr: String,
    val replyEn: String,
    val actions: List<CoachAction>
)

@Serializable
enum class MealSlot(val raw: String) {
    @SerialName("breakfast") BREAKFAST("breakfast"),
    @SerialName("snack") SNACK("snack"),
    @SerialName("lunch") LUNCH("lunch"),
    @SerialName("dinner") DINNER("dinner");

    val ar: String
        get() = when (this) {
            BREAKFAST -> "فطار"
            SNACK -> "سناك"
            LUNCH -> "غدا"
            DINNER -> "عشا"
        }

    val en: String
        get() = when (this) {
            BREAKFAST -> "Breakfast"
            SNACK -> "Snack"
            LUNCH -> "Lunch"
            DINNER -> "Dinner"
        }

    companion object {
        fun fromRaw(raw: String): MealSlot? = entries.firstOrNull { it.raw == raw }
    }
}

/** A meal of a weekly plan. Plans saved before meals listed their ingredients decode with none. */
@Serializable
data class PlannedMeal(
    val slot: MealSlot,
    val nameAr: String,
    val nameEn: String,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val ingredients: List<PlanIngredient> = emptyList(),
    val recipeAr: String,
    val recipeEn: String
)

@Serializable
data class PlanDay(
    val dayIndex: Int,
    val meals: List<PlannedMeal>
) {
    val totals: MacroTargets
        get() = MacroTargets(
            calories = meals.sumOf { it.kcal }.swiftRounded().toInt(),
            proteinGrams = meals.sumOf { it.proteinG }.swiftRounded().toInt(),
            carbsGrams = meals.sumOf { it.carbsG }.swiftRounded().toInt(),
            fatGrams = meals.sumOf { it.fatG }.swiftRounded().toInt()
        )
}

/** Grocery list as older plans stored it; newer plans build it from meal ingredients. */
@Serializable
data class GroceryItem(
    val nameAr: String,
    val nameEn: String,
    val quantity: String
)

/** A stored weekly plan. Older plans may lack `groceries`, `prepAr` and `prepEn`. */
@Serializable
data class WeekPlan(
    val days: List<PlanDay>,
    val groceries: List<GroceryItem> = emptyList(),
    val notesAr: String,
    val notesEn: String,
    /** What to cook ahead and when. */
    val prepAr: String = "",
    val prepEn: String = ""
) {
    /** Every ingredient name already in the plan, for keeping swaps consistent with the grocery list. */
    val ingredientNames: List<MenuIngredient>
        get() {
            val seen = HashSet<String>()
            return days.flatMap { day -> day.meals.flatMap { it.ingredients } }.mapNotNull { ingredient ->
                if (seen.add(ingredient.nameEn.lowercase())) {
                    MenuIngredient(nameAr = ingredient.nameAr, nameEn = ingredient.nameEn, category = ingredient.category)
                } else {
                    null
                }
            }
        }
}

@Serializable
data class DailyTip(
    val tipAr: String,
    val tipEn: String
)

@Serializable
data class WorkoutSuggestion(
    val titleAr: String,
    val titleEn: String,
    val minutes: Double,
    val intensity: String,
    val whyAr: String,
    val whyEn: String,
    val youtubeQuery: String
)

@Serializable
data class WorkoutSuggestions(val items: List<WorkoutSuggestion>)

@Serializable
enum class HealthItemKind(val raw: String) {
    @SerialName("condition") CONDITION("condition"),
    @SerialName("medication") MEDICATION("medication"),
    @SerialName("allergy") ALLERGY("allergy");

    companion object {
        fun fromRaw(raw: String): HealthItemKind? = entries.firstOrNull { it.raw == raw }
    }
}

/** What Claude understood from something the user typed in their health profile ("WPW", "Concor", …). */
@Serializable
data class HealthItemInsight(
    val kind: HealthItemKind,
    val input: String,
    val recognized: Boolean,
    val nameAr: String,
    val nameEn: String,
    val whatItIsAr: String,
    val whatItIsEn: String,
    val notesAr: String,
    val notesEn: String
) {
    val id: String get() = key(kind, input)

    companion object {
        fun key(kind: HealthItemKind, input: String): String =
            "${kind.raw}:${input.trimSwiftWhitespaceAndNewlines().lowercase()}"
    }
}

@Serializable
data class HealthItemInsights(val items: List<HealthItemInsight>)
