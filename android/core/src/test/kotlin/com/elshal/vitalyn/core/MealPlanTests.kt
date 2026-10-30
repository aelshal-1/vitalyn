package com.elshal.vitalyn.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MealPlanTests {
    private fun meal(slot: MealSlot, ingredients: List<PlanIngredient>): PlannedMeal =
        PlannedMeal(slot = slot, nameAr = "أكلة", nameEn = "Dish", kcal = 400.0, proteinG = 30.0, carbsG = 40.0, fatG = 10.0, ingredients = ingredients, recipeAr = "", recipeEn = "")

    private fun ingredient(name: String, amount: Double, unit: IngredientUnit, category: GroceryCategory): PlanIngredient =
        PlanIngredient(nameAr = name, nameEn = name, amount = amount, unit = unit, category = category)

    private val context = UserContext(
        ageYears = 36, sex = Sex.MALE, heightCm = 166.0, weightKg = 91.0, goalWeightKg = 75.0, weeklyLossKg = 0.5, activity = ActivityLevel.LIGHT,
        targets = MacroTargets(calories = 1800, proteinGrams = 140, carbsGrams = 170, fatGrams = 60)
    )

    @Test
    fun groceriesAddUpAcrossTheWeek() {
        val plan = WeekPlan(
            days = listOf(
                PlanDay(dayIndex = 0, meals = listOf(meal(MealSlot.LUNCH, listOf(ingredient("Chicken breast", 200.0, IngredientUnit.G, GroceryCategory.PROTEIN), ingredient("Eggs", 2.0, IngredientUnit.PIECE, GroceryCategory.PROTEIN))))),
                PlanDay(dayIndex = 1, meals = listOf(meal(MealSlot.DINNER, listOf(ingredient("chicken breast ", 150.0, IngredientUnit.G, GroceryCategory.PROTEIN), ingredient("Tomatoes", 900.0, IngredientUnit.G, GroceryCategory.PRODUCE), ingredient("Tomatoes", 350.0, IngredientUnit.G, GroceryCategory.PRODUCE)))))
            ),
            groceries = emptyList(), notesAr = "", notesEn = "", prepAr = "", prepEn = ""
        )
        val lines = GroceryList.build(plan)
        assertEquals(listOf("Tomatoes", "Chicken breast", "Eggs"), lines.map { it.nameEn })
        assertEquals(listOf("1.3 kg", "350 g", "2 pcs"), lines.map { it.quantity })
    }

    @Test
    fun quantitiesRoundUpForShopping() {
        assertEquals("350 g", GroceryList.format(342.0, IngredientUnit.G))
        assertEquals("1 kg", GroceryList.format(1000.0, IngredientUnit.G))
        assertEquals("1.5 L", GroceryList.format(1500.0, IngredientUnit.ML))
        assertEquals("3 pcs", GroceryList.format(2.5, IngredientUnit.PIECE))
    }

    @Test
    fun mealAmountsStayExact() {
        assertEquals("5 ml", ingredient("Oil", 5.0, IngredientUnit.ML, GroceryCategory.PANTRY).amountText)
        assertEquals("0.5 pcs", ingredient("Bread", 0.5, IngredientUnit.PIECE, GroceryCategory.BAKERY).amountText)
    }

    @Test
    fun oldPlansStillDecode() {
        val json = """
            {"days":[{"dayIndex":0,"meals":[{"slot":"lunch","nameAr":"كفتة","nameEn":"Kofta","kcal":500,"proteinG":35,"carbsG":30,"fatG":20,"recipeAr":"","recipeEn":""}]}],
             "groceries":[{"nameAr":"لحمة","nameEn":"Minced beef","quantity":"1 kg"}],"notesAr":"","notesEn":""}
        """.trimIndent()
        val plan = VitalynJson.decodeFromString<WeekPlan>(json)
        assertTrue(plan.days[0].meals[0].ingredients.isEmpty())
        assertTrue(plan.prepAr.isEmpty())
        assertEquals(listOf("1 kg"), GroceryList.build(plan).map { it.quantity })
    }

    @Test
    fun slotsFollowTheChoice() {
        assertEquals(
            listOf(MealSlot.BREAKFAST, MealSlot.SNACK, MealSlot.LUNCH, MealSlot.SNACK, MealSlot.DINNER),
            MealsPerDay.THREE_MEALS_TWO_SNACKS.slots(PlanPreset.EGYPTIAN_HOME)
        )
        assertEquals(listOf(MealSlot.DINNER, MealSlot.SNACK, MealSlot.BREAKFAST), MealsPerDay.THREE_MEALS.slots(PlanPreset.RAMADAN))
    }

    @Test
    fun weekRequestNamesDaysAndSlots() {
        val start = LocalDate.of(2026, 10, 31)
        val request = AITasks.weekPlan(context, PlanPreset.EGYPTIAN_HOME, MealsPerDay.THREE_MEALS_TWO_SNACKS, start = start, request = "fish on Friday")
        val text = (request.messages.firstOrNull()?.content?.firstOrNull() as? ClaudeContent.Text)?.text
            ?: fail("Expected a text message")
        assertTrue(text.contains("Day 0: Saturday"))
        assertTrue(text.contains("breakfast, snack, lunch, snack, dinner"))
        assertTrue(text.contains("fish on Friday"))
        assertEquals(32000, request.maxTokens)
    }

    @Test
    fun draftSpellsOutIngredients() {
        val json = """
            {"ingredients":[{"nameAr":"بيض","nameEn":"Eggs","category":"protein"},{"nameAr":"عيش بلدي","nameEn":"Baladi bread","category":"bakery"}],
             "days":[{"dayIndex":1,"meals":[]},{"dayIndex":0,"meals":[{"slot":"breakfast","nameAr":"بيض وعيش","nameEn":"Eggs and bread","kcal":420,"proteinG":22,"carbsG":45,"fatG":14,
               "items":[{"ingredient":0,"amount":2,"unit":"piece"},{"ingredient":1,"amount":0.5,"unit":"piece"},{"ingredient":9,"amount":5,"unit":"g"}],"recipeAr":"","recipeEn":""}]}],
             "prepAr":"","prepEn":"","notesAr":"","notesEn":""}
        """.trimIndent()
        val plan = VitalynJson.decodeFromString<WeekPlanDraft>(json).plan
        assertEquals(listOf(0, 1), plan.days.map { it.dayIndex })
        val ingredients = plan.days[0].meals[0].ingredients
        assertEquals(listOf("Eggs", "Baladi bread"), ingredients.map { it.nameEn })
        assertEquals(GroceryCategory.BAKERY, ingredients[1].category)
        assertEquals(listOf("2 pcs", "1 pcs"), GroceryList.build(plan).map { it.quantity })
    }

    @Test
    fun swapOffersThePlansIngredients() {
        val plan = WeekPlan(
            days = listOf(
                PlanDay(dayIndex = 0, meals = listOf(
                    meal(MealSlot.LUNCH, listOf(ingredient("Chicken breast", 200.0, IngredientUnit.G, GroceryCategory.PROTEIN))),
                    meal(MealSlot.DINNER, listOf(ingredient("chicken breast", 100.0, IngredientUnit.G, GroceryCategory.PROTEIN)))
                ))
            ),
            groceries = emptyList(), notesAr = "", notesEn = "", prepAr = "", prepEn = ""
        )
        assertEquals(1, plan.ingredientNames.size)
        val request = AITasks.swapMeal(context, meal = plan.days[0].meals[0], ingredients = plan.ingredientNames, request = "")
        val text = (request.messages.firstOrNull()?.content?.firstOrNull() as? ClaudeContent.Text)?.text
            ?: fail("Expected a text message")
        assertTrue(text.contains("- Chicken breast / Chicken breast (protein)"))
    }
}
