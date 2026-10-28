import Foundation

/// Deterministic portion math: macros for a weight of a product, scaling an estimated item.
public enum Portions {
    public static func food(nameAr: String, nameEn: String, grams: Double, per100g: NutrientsPer100, productId: String?) -> AnalyzedFood {
        let factor = grams / 100
        return AnalyzedFood(
            nameAr: nameAr,
            nameEn: nameEn,
            grams: grams,
            kcal: (per100g.kcal * factor).rounded(),
            proteinG: round1(per100g.proteinG * factor),
            carbsG: round1(per100g.carbsG * factor),
            fatG: round1(per100g.fatG * factor),
            confidence: .high,
            libraryProductId: productId
        )
    }

    /// Rescales an item's macros to a new weight, keeping its per-gram density.
    public static func rescale(_ item: AnalyzedFood, to grams: Double) -> AnalyzedFood {
        guard item.grams > 0 else {
            var copy = item
            copy.grams = grams
            return copy
        }
        let factor = grams / item.grams
        var copy = item
        copy.grams = grams
        copy.kcal = (item.kcal * factor).rounded()
        copy.proteinG = round1(item.proteinG * factor)
        copy.carbsG = round1(item.carbsG * factor)
        copy.fatG = round1(item.fatG * factor)
        return copy
    }

    /// Applies a chosen answer to a clarifying question.
    public static func apply(_ option: QuestionOption, to item: AnalyzedFood, library: [LibraryProduct]) -> AnalyzedFood {
        if let id = option.libraryProductId, let product = library.first(where: { $0.id == id }) {
            let grams = option.grams ?? (item.grams > 0 ? item.grams : product.servingSizeG ?? 100)
            return food(nameAr: product.nameAr, nameEn: product.nameEn, grams: grams, per100g: product.per100g, productId: product.id)
        }
        if let grams = option.grams {
            var updated = rescale(item, to: grams)
            updated.confidence = .high
            return updated
        }
        return item
    }

    public static func total(_ items: [AnalyzedFood]) -> MacroTargets {
        MacroTargets(
            calories: Int(items.reduce(0) { $0 + $1.kcal }.rounded()),
            proteinGrams: Int(items.reduce(0) { $0 + $1.proteinG }.rounded()),
            carbsGrams: Int(items.reduce(0) { $0 + $1.carbsG }.rounded()),
            fatGrams: Int(items.reduce(0) { $0 + $1.fatG }.rounded())
        )
    }

    static func round1(_ value: Double) -> Double {
        (value * 10).rounded() / 10
    }
}
