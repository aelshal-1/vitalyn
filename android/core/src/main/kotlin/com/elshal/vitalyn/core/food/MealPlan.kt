package com.elshal.vitalyn.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Collator
import java.util.Locale
import kotlin.math.ceil

/** How many meals and snacks each day of a weekly plan has. */
@Serializable
enum class MealsPerDay(val raw: String) {
    @SerialName("threeMealsTwoSnacks") THREE_MEALS_TWO_SNACKS("threeMealsTwoSnacks"),
    @SerialName("threeMealsOneSnack") THREE_MEALS_ONE_SNACK("threeMealsOneSnack"),
    @SerialName("threeMeals") THREE_MEALS("threeMeals"),
    @SerialName("twoMealsOneSnack") TWO_MEALS_ONE_SNACK("twoMealsOneSnack");

    val id: String get() = raw

    val ar: String
        get() = when (this) {
            THREE_MEALS_TWO_SNACKS -> "3 وجبات + 2 سناك"
            THREE_MEALS_ONE_SNACK -> "3 وجبات + سناك"
            THREE_MEALS -> "3 وجبات"
            TWO_MEALS_ONE_SNACK -> "وجبتين + سناك"
        }

    val en: String
        get() = when (this) {
            THREE_MEALS_TWO_SNACKS -> "3 meals + 2 snacks"
            THREE_MEALS_ONE_SNACK -> "3 meals + 1 snack"
            THREE_MEALS -> "3 meals"
            TWO_MEALS_ONE_SNACK -> "2 meals + 1 snack"
        }

    /** The day's slots in eating order. Ramadan is always iftar, a snack, then suhoor. */
    fun slots(preset: PlanPreset): List<MealSlot> {
        if (preset == PlanPreset.RAMADAN) return listOf(MealSlot.DINNER, MealSlot.SNACK, MealSlot.BREAKFAST)
        return when (this) {
            THREE_MEALS_TWO_SNACKS -> listOf(MealSlot.BREAKFAST, MealSlot.SNACK, MealSlot.LUNCH, MealSlot.SNACK, MealSlot.DINNER)
            THREE_MEALS_ONE_SNACK -> listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.SNACK, MealSlot.DINNER)
            THREE_MEALS -> listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER)
            TWO_MEALS_ONE_SNACK -> listOf(MealSlot.LUNCH, MealSlot.SNACK, MealSlot.DINNER)
        }
    }

    companion object {
        fun fromRaw(raw: String): MealsPerDay? = entries.firstOrNull { it.raw == raw }
    }
}

/** Supermarket section an ingredient is bought from. */
@Serializable
enum class GroceryCategory(val raw: String) {
    @SerialName("produce") PRODUCE("produce"),
    @SerialName("protein") PROTEIN("protein"),
    @SerialName("dairy") DAIRY("dairy"),
    @SerialName("bakery") BAKERY("bakery"),
    @SerialName("grains") GRAINS("grains"),
    @SerialName("pantry") PANTRY("pantry"),
    @SerialName("other") OTHER("other");

    val ar: String
        get() = when (this) {
            PRODUCE -> "خضار وفاكهة"
            PROTEIN -> "لحوم وفراخ وسمك وبيض"
            DAIRY -> "ألبان وجبن"
            BAKERY -> "عيش ومخبوزات"
            GRAINS -> "رز ومكرونة وبقوليات"
            PANTRY -> "بقالة وزيوت وتوابل"
            OTHER -> "حاجات تانية"
        }

    val en: String
        get() = when (this) {
            PRODUCE -> "Vegetables & fruit"
            PROTEIN -> "Meat, poultry, fish & eggs"
            DAIRY -> "Dairy & cheese"
            BAKERY -> "Bread & bakery"
            GRAINS -> "Rice, pasta & legumes"
            PANTRY -> "Pantry, oils & spices"
            OTHER -> "Other"
        }

    companion object {
        fun fromRaw(raw: String): GroceryCategory? = entries.firstOrNull { it.raw == raw }
    }
}

@Serializable
enum class IngredientUnit(val raw: String) {
    @SerialName("g") G("g"),
    @SerialName("ml") ML("ml"),
    @SerialName("piece") PIECE("piece");

    companion object {
        fun fromRaw(raw: String): IngredientUnit? = entries.firstOrNull { it.raw == raw }
    }
}

/** One ingredient of a planned meal, in the amount bought (raw weight, ml, or pieces). */
@Serializable
data class PlanIngredient(
    val nameAr: String,
    val nameEn: String,
    val amount: Double,
    val unit: IngredientUnit,
    val category: GroceryCategory
) {
    /** The amount for one meal: 150 g, 10 ml, 2 pcs, 0.5 pcs. */
    val amountText: String
        get() = when (unit) {
            IngredientUnit.G -> "${amount.swiftRounded().toInt()} g"
            IngredientUnit.ML -> "${amount.swiftRounded().toInt()} ml"
            IngredientUnit.PIECE -> "${UserContext.format((amount * 2).swiftRounded() / 2)} pcs"
        }
}

/** An ingredient name already used in a plan, offered to swaps so the grocery list stays consistent. */
@Serializable
data class MenuIngredient(
    val nameAr: String,
    val nameEn: String,
    val category: GroceryCategory
)

// Compact plan (as Claude writes it)

/**
 * A meal ingredient as a position in the week's ingredient list, so each name is written once
 * instead of in every meal (Arabic names are long in tokens, and a week has ~35 meals).
 */
@Serializable
data class DraftItem(
    val ingredient: Int,
    val amount: Double,
    val unit: IngredientUnit
)

@Serializable
data class DraftMeal(
    val slot: MealSlot,
    val nameAr: String,
    val nameEn: String,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val items: List<DraftItem>,
    val recipeAr: String,
    val recipeEn: String
)

@Serializable
data class DraftDay(
    val dayIndex: Int,
    val meals: List<DraftMeal>
)

@Serializable
data class WeekPlanDraft(
    val ingredients: List<MenuIngredient>,
    val days: List<DraftDay>,
    val prepAr: String,
    val prepEn: String,
    val notesAr: String,
    val notesEn: String
) {
    /** The stored plan, with every meal's ingredients spelled out. Items pointing outside the list are dropped. */
    val plan: WeekPlan
        get() {
            val days = days.sortedBy { it.dayIndex }.map { day ->
                PlanDay(dayIndex = day.dayIndex, meals = day.meals.map { meal ->
                    PlannedMeal(
                        slot = meal.slot, nameAr = meal.nameAr, nameEn = meal.nameEn,
                        kcal = meal.kcal, proteinG = meal.proteinG, carbsG = meal.carbsG, fatG = meal.fatG,
                        ingredients = meal.items.mapNotNull { item ->
                            val ingredient = ingredients.getOrNull(item.ingredient) ?: return@mapNotNull null
                            PlanIngredient(
                                nameAr = ingredient.nameAr, nameEn = ingredient.nameEn, amount = item.amount,
                                unit = item.unit, category = ingredient.category
                            )
                        },
                        recipeAr = meal.recipeAr, recipeEn = meal.recipeEn
                    )
                })
            }
            return WeekPlan(days = days, groceries = emptyList(), notesAr = notesAr, notesEn = notesEn, prepAr = prepAr, prepEn = prepEn)
        }
}

// Grocery list

data class GroceryLine(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val category: GroceryCategory,
    val quantity: String
)

object GroceryList {
    /**
     * Adds up every meal's ingredients by name and unit, grouped by category.
     * Plans saved before meals had ingredients fall back to their stored list.
     */
    fun build(plan: WeekPlan): List<GroceryLine> {
        val ingredients = plan.days.flatMap { day -> day.meals.flatMap { it.ingredients } }
        if (ingredients.isEmpty()) {
            return plan.groceries.map {
                GroceryLine(id = key(it.nameEn, null), nameAr = it.nameAr, nameEn = it.nameEn, category = GroceryCategory.OTHER, quantity = it.quantity)
            }
        }
        val order = mutableListOf<String>()
        val totals = HashMap<String, Pair<PlanIngredient, Double>>()
        for (ingredient in ingredients) {
            if (!(ingredient.amount > 0)) continue
            val id = key(ingredient.nameEn, ingredient.unit)
            val existing = totals[id]
            if (existing != null) {
                totals[id] = existing.first to (existing.second + ingredient.amount)
            } else {
                order += id
                totals[id] = ingredient to ingredient.amount
            }
        }
        val categories = GroceryCategory.entries
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
        return order.mapNotNull { id ->
            val (ingredient, amount) = totals[id] ?: return@mapNotNull null
            GroceryLine(
                id = id, nameAr = ingredient.nameAr, nameEn = ingredient.nameEn, category = ingredient.category,
                quantity = format(amount, ingredient.unit)
            )
        }.sortedWith { a, b ->
            val left = categories.indexOf(a.category)
            val right = categories.indexOf(b.category)
            if (left != right) left.compareTo(right) else collator.compare(a.nameEn, b.nameEn)
        }
    }

    internal fun key(nameEn: String, unit: IngredientUnit?): String {
        val name = nameEn.trimSwiftWhitespaceAndNewlines().lowercase()
        return if (unit != null) "$name|${unit.raw}" else name
    }

    /** Shop-friendly amounts, rounded up: 340 g, 1.3 kg, 750 ml, 1.5 L, 3 pcs. */
    internal fun format(amount: Double, unit: IngredientUnit): String = when (unit) {
        IngredientUnit.G -> metric(amount, small = "g", large = "kg")
        IngredientUnit.ML -> metric(amount, small = "ml", large = "L")
        IngredientUnit.PIECE -> "${ceil(amount).toInt()} pcs"
    }

    private fun metric(amount: Double, small: String, large: String): String {
        if (amount < 1000) {
            return "${(ceil(amount / 10) * 10).toInt()} $small"
        }
        val value = ceil(amount / 100) / 10
        val text = if (value == value.swiftRounded()) value.toInt().toString() else formatOneDecimal(value)
        return "$text $large"
    }
}
