import Foundation

/// Barcode lookup in the free Open Food Facts database, used when a product is not in the user's library yet.
public enum OpenFoodFacts {
    public static func url(for barcode: String) -> URL? {
        let digits = barcode.filter(\.isNumber)
        guard !digits.isEmpty else { return nil }
        var components = URLComponents(string: "https://world.openfoodfacts.org/api/v2/product/\(digits).json")
        components?.queryItems = [URLQueryItem(name: "fields", value: "product_name,product_name_ar,product_name_en,brands,serving_size,serving_quantity,nutriments")]
        return components?.url
    }

    public static func lookup(barcode: String, session: URLSession = .shared) async throws -> ProductExtraction? {
        guard let url = url(for: barcode) else { return nil }
        var request = URLRequest(url: url)
        request.setValue("Vitalyn/0.1 (personal health app)", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 20
        let (data, _) = try await session.data(for: request)
        return parse(data, barcode: barcode)
    }

    /// Maps an Open Food Facts response to a product, or nil when it is missing or has no calories.
    public static func parse(_ data: Data, barcode: String) -> ProductExtraction? {
        guard let json = try? JSONValue.parse(data),
              json["status"].flatMap(number) == 1,
              let product = json["product"],
              let nutriments = product["nutriments"],
              let kcal = nutriments["energy-kcal_100g"].flatMap(number)
        else { return nil }

        let english = product["product_name_en"]?.stringValue.nonEmpty ?? product["product_name"]?.stringValue.nonEmpty ?? barcode
        let arabic = product["product_name_ar"]?.stringValue.nonEmpty ?? english
        let brand = product["brands"]?.stringValue?.split(separator: ",").first.map { $0.trimmingCharacters(in: .whitespaces) }
        let servingDesc = product["serving_size"]?.stringValue ?? ""
        let sodiumGrams = nutriments["sodium_100g"].flatMap(number)

        return ProductExtraction(
            nameAr: arabic,
            nameEn: english,
            brand: brand,
            barcode: barcode,
            servingSizeG: product["serving_quantity"].flatMap(number),
            servingDescAr: servingDesc,
            servingDescEn: servingDesc,
            per100g: NutrientsPer100(
                kcal: kcal,
                proteinG: nutriments["proteins_100g"].flatMap(number) ?? 0,
                carbsG: nutriments["carbohydrates_100g"].flatMap(number) ?? 0,
                fatG: nutriments["fat_100g"].flatMap(number) ?? 0,
                fiberG: nutriments["fiber_100g"].flatMap(number),
                sugarG: nutriments["sugars_100g"].flatMap(number),
                sodiumMg: sodiumGrams.map { $0 * 1000 }
            )
        )
    }

    /// Open Food Facts sometimes sends numbers as strings.
    static func number(_ value: JSONValue) -> Double? {
        switch value {
        case let .number(number): return number
        case let .string(text): return Double(text)
        default: return nil
        }
    }
}

extension Optional where Wrapped == String {
    var nonEmpty: String? {
        guard let self, !self.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        return self
    }
}
