import Foundation

/// How many meals and snacks each day of a weekly plan has.
public enum MealsPerDay: String, Codable, Sendable, CaseIterable, Identifiable {
    case threeMealsTwoSnacks, threeMealsOneSnack, threeMeals, twoMealsOneSnack

    public var id: String { rawValue }

    public var ar: String {
        switch self {
        case .threeMealsTwoSnacks: "3 وجبات + 2 سناك"
        case .threeMealsOneSnack: "3 وجبات + سناك"
        case .threeMeals: "3 وجبات"
        case .twoMealsOneSnack: "وجبتين + سناك"
        }
    }

    public var en: String {
        switch self {
        case .threeMealsTwoSnacks: "3 meals + 2 snacks"
        case .threeMealsOneSnack: "3 meals + 1 snack"
        case .threeMeals: "3 meals"
        case .twoMealsOneSnack: "2 meals + 1 snack"
        }
    }

    /// The day's slots in eating order. Ramadan is always iftar, a snack, then suhoor.
    public func slots(for preset: PlanPreset) -> [MealSlot] {
        if preset == .ramadan { return [.dinner, .snack, .breakfast] }
        switch self {
        case .threeMealsTwoSnacks: return [.breakfast, .snack, .lunch, .snack, .dinner]
        case .threeMealsOneSnack: return [.breakfast, .lunch, .snack, .dinner]
        case .threeMeals: return [.breakfast, .lunch, .dinner]
        case .twoMealsOneSnack: return [.lunch, .snack, .dinner]
        }
    }
}

/// Supermarket section an ingredient is bought from.
public enum GroceryCategory: String, Codable, Sendable, CaseIterable {
    case produce, protein, dairy, bakery, grains, pantry, other

    public var ar: String {
        switch self {
        case .produce: "خضار وفاكهة"
        case .protein: "لحوم وفراخ وسمك وبيض"
        case .dairy: "ألبان وجبن"
        case .bakery: "عيش ومخبوزات"
        case .grains: "رز ومكرونة وبقوليات"
        case .pantry: "بقالة وزيوت وتوابل"
        case .other: "حاجات تانية"
        }
    }

    public var en: String {
        switch self {
        case .produce: "Vegetables & fruit"
        case .protein: "Meat, poultry, fish & eggs"
        case .dairy: "Dairy & cheese"
        case .bakery: "Bread & bakery"
        case .grains: "Rice, pasta & legumes"
        case .pantry: "Pantry, oils & spices"
        case .other: "Other"
        }
    }
}

public enum IngredientUnit: String, Codable, Sendable, CaseIterable {
    case g, ml, piece
}

/// One ingredient of a planned meal, in the amount bought (raw weight, ml, or pieces).
public struct PlanIngredient: Codable, Sendable, Equatable {
    public var nameAr: String
    public var nameEn: String
    public var amount: Double
    public var unit: IngredientUnit
    public var category: GroceryCategory

    public init(nameAr: String, nameEn: String, amount: Double, unit: IngredientUnit, category: GroceryCategory) {
        self.nameAr = nameAr
        self.nameEn = nameEn
        self.amount = amount
        self.unit = unit
        self.category = category
    }

    /// The amount for one meal: 150 g, 10 ml, 2 pcs, 0.5 pcs.
    public var amountText: String {
        switch unit {
        case .g: "\(Int(amount.rounded())) g"
        case .ml: "\(Int(amount.rounded())) ml"
        case .piece: "\(UserContext.format((amount * 2).rounded() / 2)) pcs"
        }
    }
}

// MARK: Menu (first step of building a plan)

public struct MenuMeal: Codable, Sendable, Equatable {
    public var slot: MealSlot
    public var nameAr: String
    public var nameEn: String
}

public struct MenuDay: Codable, Sendable, Equatable {
    public var dayIndex: Int
    public var meals: [MenuMeal]
}

/// An ingredient name shared by every day of the plan, so the grocery list can add amounts up.
public struct MenuIngredient: Codable, Sendable, Equatable {
    public var nameAr: String
    public var nameEn: String
    public var category: GroceryCategory
}

/// The week's dishes and shared ingredients; each day's portions and recipes are written separately.
public struct WeekMenu: Codable, Sendable, Equatable {
    public var days: [MenuDay]
    public var ingredients: [MenuIngredient]
    public var prepAr: String
    public var prepEn: String
    public var notesAr: String
    public var notesEn: String
}

/// One day's meals with portions, macros, ingredients and method.
public struct DayMeals: Codable, Sendable, Equatable {
    public var meals: [PlannedMeal]
}

extension WeekMenu {
    /// Joins the menu with each day's detailed meals into the stored plan.
    public func assemble(_ details: [Int: [PlannedMeal]]) -> WeekPlan {
        WeekPlan(
            days: days.sorted { $0.dayIndex < $1.dayIndex }.map { PlanDay(dayIndex: $0.dayIndex, meals: details[$0.dayIndex] ?? []) },
            groceries: [],
            notesAr: notesAr,
            notesEn: notesEn,
            prepAr: prepAr,
            prepEn: prepEn
        )
    }
}

// MARK: Grocery list

public struct GroceryLine: Sendable, Equatable, Identifiable {
    public var id: String
    public var nameAr: String
    public var nameEn: String
    public var category: GroceryCategory
    public var quantity: String
}

public enum GroceryList {
    /// Adds up every meal's ingredients by name and unit, grouped by category.
    /// Plans saved before meals had ingredients fall back to their stored list.
    public static func build(_ plan: WeekPlan) -> [GroceryLine] {
        let ingredients = plan.days.flatMap { $0.meals.flatMap(\.ingredients) }
        guard !ingredients.isEmpty else {
            return plan.groceries.map {
                GroceryLine(id: key($0.nameEn, nil), nameAr: $0.nameAr, nameEn: $0.nameEn, category: .other, quantity: $0.quantity)
            }
        }
        var order: [String] = []
        var totals: [String: (ingredient: PlanIngredient, amount: Double)] = [:]
        for ingredient in ingredients where ingredient.amount > 0 {
            let id = key(ingredient.nameEn, ingredient.unit)
            if let existing = totals[id] {
                totals[id] = (existing.ingredient, existing.amount + ingredient.amount)
            } else {
                order.append(id)
                totals[id] = (ingredient, ingredient.amount)
            }
        }
        let categories = GroceryCategory.allCases
        return order.compactMap { id -> GroceryLine? in
            guard let total = totals[id] else { return nil }
            let ingredient = total.ingredient
            return GroceryLine(id: id, nameAr: ingredient.nameAr, nameEn: ingredient.nameEn, category: ingredient.category, quantity: format(total.amount, ingredient.unit))
        }
        .sorted {
            let left = categories.firstIndex(of: $0.category) ?? 0
            let right = categories.firstIndex(of: $1.category) ?? 0
            return left != right ? left < right : $0.nameEn.localizedCaseInsensitiveCompare($1.nameEn) == .orderedAscending
        }
    }

    static func key(_ nameEn: String, _ unit: IngredientUnit?) -> String {
        let name = nameEn.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return unit.map { "\(name)|\($0.rawValue)" } ?? name
    }

    /// Shop-friendly amounts, rounded up: 340 g, 1.3 kg, 750 ml, 1.5 L, 3 pcs.
    static func format(_ amount: Double, _ unit: IngredientUnit) -> String {
        switch unit {
        case .g: return metric(amount, small: "g", large: "kg")
        case .ml: return metric(amount, small: "ml", large: "L")
        case .piece: return "\(Int(amount.rounded(.up))) pcs"
        }
    }

    private static func metric(_ amount: Double, small: String, large: String) -> String {
        if amount < 1000 {
            return "\(Int((amount / 10).rounded(.up) * 10)) \(small)"
        }
        let value = (amount / 100).rounded(.up) / 10
        let text = value == value.rounded() ? String(Int(value)) : String(format: "%.1f", value)
        return "\(text) \(large)"
    }
}
