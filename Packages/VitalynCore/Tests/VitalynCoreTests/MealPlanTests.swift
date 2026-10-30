import Foundation
import Testing
@testable import VitalynCore

@Suite struct MealPlanTests {
    func meal(_ slot: MealSlot, _ ingredients: [PlanIngredient]) -> PlannedMeal {
        PlannedMeal(slot: slot, nameAr: "أكلة", nameEn: "Dish", kcal: 400, proteinG: 30, carbsG: 40, fatG: 10, ingredients: ingredients, recipeAr: "", recipeEn: "")
    }

    func ingredient(_ name: String, _ amount: Double, _ unit: IngredientUnit, _ category: GroceryCategory) -> PlanIngredient {
        PlanIngredient(nameAr: name, nameEn: name, amount: amount, unit: unit, category: category)
    }

    @Test func groceriesAddUpAcrossTheWeek() {
        let plan = WeekPlan(days: [
            PlanDay(dayIndex: 0, meals: [meal(.lunch, [ingredient("Chicken breast", 200, .g, .protein), ingredient("Eggs", 2, .piece, .protein)])]),
            PlanDay(dayIndex: 1, meals: [meal(.dinner, [ingredient("chicken breast ", 150, .g, .protein), ingredient("Tomatoes", 900, .g, .produce), ingredient("Tomatoes", 350, .g, .produce)])])
        ], groceries: [], notesAr: "", notesEn: "", prepAr: "", prepEn: "")
        let lines = GroceryList.build(plan)
        #expect(lines.map(\.nameEn) == ["Tomatoes", "Chicken breast", "Eggs"])
        #expect(lines.map(\.quantity) == ["1.3 kg", "350 g", "2 pcs"])
    }

    @Test func quantitiesRoundUpForShopping() {
        #expect(GroceryList.format(342, .g) == "350 g")
        #expect(GroceryList.format(1000, .g) == "1 kg")
        #expect(GroceryList.format(1500, .ml) == "1.5 L")
        #expect(GroceryList.format(2.5, .piece) == "3 pcs")
    }

    @Test func mealAmountsStayExact() {
        #expect(ingredient("Oil", 5, .ml, .pantry).amountText == "5 ml")
        #expect(ingredient("Bread", 0.5, .piece, .bakery).amountText == "0.5 pcs")
    }

    @Test func oldPlansStillDecode() throws {
        let json = """
        {"days":[{"dayIndex":0,"meals":[{"slot":"lunch","nameAr":"كفتة","nameEn":"Kofta","kcal":500,"proteinG":35,"carbsG":30,"fatG":20,"recipeAr":"","recipeEn":""}]}],
         "groceries":[{"nameAr":"لحمة","nameEn":"Minced beef","quantity":"1 kg"}],"notesAr":"","notesEn":""}
        """
        let plan = try JSONDecoder().decode(WeekPlan.self, from: Data(json.utf8))
        #expect(plan.days[0].meals[0].ingredients.isEmpty)
        #expect(plan.prepAr.isEmpty)
        #expect(GroceryList.build(plan).map(\.quantity) == ["1 kg"])
    }

    @Test func menuAssemblesInDayOrder() {
        let menu = WeekMenu(
            days: [MenuDay(dayIndex: 1, meals: []), MenuDay(dayIndex: 0, meals: [])],
            ingredients: [], prepAr: "اطبخ", prepEn: "Cook", notesAr: "", notesEn: ""
        )
        let plan = menu.assemble([0: [meal(.breakfast, [])]])
        #expect(plan.days.map(\.dayIndex) == [0, 1])
        #expect(plan.days[0].meals.count == 1)
        #expect(plan.prepEn == "Cook")
    }

    @Test func slotsFollowTheChoice() {
        #expect(MealsPerDay.threeMealsTwoSnacks.slots(for: .egyptianHome) == [.breakfast, .snack, .lunch, .snack, .dinner])
        #expect(MealsPerDay.threeMeals.slots(for: .ramadan) == [.dinner, .snack, .breakfast])
    }

    @Test func dayRequestUsesSharedIngredientNames() throws {
        let context = UserContext(ageYears: 36, sex: .male, heightCm: 166, weightKg: 91, goalWeightKg: 75, weeklyLossKg: 0.5, activity: .light, targets: MacroTargets(calories: 1800, proteinGrams: 140, carbsGrams: 170, fatGrams: 60))
        let menu = WeekMenu(
            days: [MenuDay(dayIndex: 2, meals: [MenuMeal(slot: .lunch, nameAr: "فراخ مشوية", nameEn: "Grilled chicken")])],
            ingredients: [MenuIngredient(nameAr: "صدور فراخ", nameEn: "Chicken breast", category: .protein)],
            prepAr: "", prepEn: "", notesAr: "", notesEn: ""
        )
        let request = AITasks.planDay(context: context, menu: menu, dayIndex: 2, start: Date(), request: "")
        guard case let .text(text)? = request.messages.first?.content.first else {
            Issue.record("Expected a text message")
            return
        }
        #expect(text.contains("lunch: Grilled chicken"))
        #expect(text.contains("Chicken breast / صدور فراخ (protein)"))
    }
}
