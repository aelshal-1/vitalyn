import Foundation
import Testing
@testable import VitalynCore

@Suite struct JSONValueTests {
    @Test func serializesInOrderWithEscapes() {
        let value: JSONValue = ["b": 1, "a": "x\"y\\z\n", "c": [true, .null, 2.5], "ar": "سعرات"]
        #expect(value.serialized() == #"{"b":1,"a":"x\"y\\z\n","c":[true,null,2.5],"ar":"سعرات"}"#)
    }

    @Test func roundTripsThroughFoundation() throws {
        let value: JSONValue = ["n": 1.25, "s": "تست", "list": [1, 2]]
        let parsed = try JSONValue.parse(Data(value.serialized().utf8))
        #expect(parsed["n"] == .number(1.25))
        #expect(parsed["s"] == .string("تست"))
        #expect(parsed["list"] == .array([1, 2]))
    }

    @Test func controlCharactersAreEscaped() {
        #expect(JSONValue.string("a\u{01}b").serialized() == #""a\u0001b""#)
    }
}

@Suite struct SchemaTests {
    @Test(arguments: Array(AITasks.allSchemas.keys).sorted())
    func schemaIsValidForStructuredOutputs(name: String) throws {
        let schema = try #require(AITasks.allSchemas[name])
        #expect(JSONSchema.problems(in: schema).isEmpty, "\(name): \(JSONSchema.problems(in: schema))")
    }

    @Test func problemsAreDetected() {
        let bad: JSONValue = ["type": "object", "properties": ["a": ["type": "number", "minimum": 0]], "required": []]
        let issues = JSONSchema.problems(in: bad)
        #expect(issues.contains { $0.contains("additionalProperties") })
        #expect(issues.contains { $0.contains("not required") })
        #expect(issues.contains { $0.contains("minimum") })
    }

    @Test func schemaRoundTripsAsJSON() throws {
        for (_, schema) in AITasks.allSchemas {
            _ = try JSONSerialization.jsonObject(with: Data(schema.serialized().utf8))
        }
    }
}

@Suite struct ClaudeClientTests {
    @Test func buildsRequestForOpusWithFallback() throws {
        let client = ClaudeClient(apiKey: "sk-test", model: .opus)
        let request = client.makeRequest(system: "sys", messages: [.user([.text("hi")])], schema: AITasks.tipSchema, effort: .low, maxTokens: 100)
        #expect(request.value(forHTTPHeaderField: "x-api-key") == "sk-test")
        #expect(request.value(forHTTPHeaderField: "anthropic-version") == "2026-10-21")
        #expect(request.value(forHTTPHeaderField: "anthropic-beta") == "server-side-fallback-2026-07-01")
        let body = try JSONValue.parse(try #require(request.httpBody))
        #expect(body["model"] == .string("claude-opus-5-5"))
        #expect(body["fallbacks"] == .string("default"))
        #expect(body["output_config"]?["effort"] == .string("low"))
        #expect(body["output_config"]?["format"]?["type"] == .string("json_schema"))
        #expect(body["temperature"] == nil)
        #expect(body["thinking"] == nil)
    }

    @Test func haikuHasNoFallback() throws {
        let client = ClaudeClient(apiKey: "k", model: .haiku)
        let request = client.makeRequest(system: "s", messages: [.user([.text("x")])], schema: nil, effort: .medium, maxTokens: 10)
        #expect(request.value(forHTTPHeaderField: "anthropic-beta") == nil)
        let body = try JSONValue.parse(try #require(request.httpBody))
        #expect(body["fallbacks"] == nil)
        #expect(body["output_config"]?["format"] == nil)
    }

    @Test func encodesImagesAndPDFs() throws {
        let client = ClaudeClient(apiKey: "k")
        let request = client.makeRequest(system: "s", messages: [.user([.image(Data([1, 2, 3]), mediaType: "image/jpeg"), .pdf(Data([4]))])], schema: nil, effort: .medium, maxTokens: 10)
        let body = try JSONValue.parse(try #require(request.httpBody))
        let content = try #require(body["messages"]?.arrayValue?.first?["content"]?.arrayValue)
        #expect(content[0]["type"] == .string("image"))
        #expect(content[0]["source"]?["data"] == .string("AQID"))
        #expect(content[1]["type"] == .string("document"))
        #expect(content[1]["source"]?["media_type"] == .string("application/pdf"))
    }

    @Test func parsesTextSkippingThinking() throws {
        let response = #"{"content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\"tipAr\":\"أ\",\"tipEn\":\"b\"}"}],"stop_reason":"end_turn"}"#
        let text = try ClaudeClient.parse(data: Data(response.utf8), status: 200)
        let tip = try JSONDecoder().decode(DailyTip.self, from: Data(text.utf8))
        #expect(tip.tipEn == "b")
    }

    @Test func surfacesErrors() {
        let error = #"{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"#
        #expect(throws: ClaudeError.http(status: 401, message: "invalid x-api-key")) {
            try ClaudeClient.parse(data: Data(error.utf8), status: 401)
        }
        #expect(throws: ClaudeError.refusal) {
            try ClaudeClient.parse(data: Data(#"{"content":[],"stop_reason":"refusal"}"#.utf8), status: 200)
        }
        #expect(throws: ClaudeError.truncated) {
            try ClaudeClient.parse(data: Data(#"{"content":[{"type":"text","text":"{"}],"stop_reason":"max_tokens"}"#.utf8), status: 200)
        }
    }

    @Test func missingKeyFailsFast() async {
        await #expect(throws: ClaudeError.missingAPIKey) {
            _ = try await ClaudeClient(apiKey: " ").send(system: "s", messages: [], schema: nil)
        }
    }

    @Test func decodesInBodyExtraction() throws {
        let json = """
        {"scanDate":"2026-10-26","weightKg":96.4,"skeletalMuscleKg":36.8,"bodyFatKg":30.1,"bodyFatPercent":31.2,
         "leanMassKg":66.3,"visceralFatLevel":13,"bmrKcal":1880,"totalBodyWaterL":48.6,"inbodyScore":null,
         "heightCm":178,"ageYears":34,
         "segmentalLean":{"rightArmKg":3.9,"leftArmKg":3.8,"trunkKg":29.4,"rightLegKg":10.1,"leftLegKg":10.0},
         "lowConfidenceFields":["visceralFatLevel"],"summaryAr":"تمام","summaryEn":"Good"}
        """
        let scan = try JSONDecoder().decode(InBodyExtraction.self, from: Data(json.utf8))
        #expect(scan.bmrKcal == 1880)
        #expect(scan.inbodyScore == nil)
        #expect(scan.segmentalLean.trunkKg == 29.4)
    }

    @Test func decodesCoachReplyWithActions() throws {
        let json = """
        {"replyAr":"برافو","replyEn":"Nice","actions":[
          {"type":"log_workout","workout":{"nameAr":"كورة","nameEn":"Football","kind":"football","minutes":60,"kcal":520},"waterGlasses":null,"meal":null,"videos":[]},
          {"type":"suggest_videos","workout":null,"waterGlasses":null,"meal":null,"videos":[{"titleAr":"إطالة","titleEn":"Stretch","query":"post football stretch"}]}
        ]}
        """
        let reply = try JSONDecoder().decode(CoachReply.self, from: Data(json.utf8))
        #expect(reply.actions.first?.workout?.kind == .football)
        #expect(reply.actions.last?.videos.first?.youtubeURL.absoluteString == "https://www.youtube.com/results?search_query=post%20football%20stretch")
    }
}

@Suite struct UserContextTests {
    @Test func promptIncludesConditionsAndTargets() {
        let context = UserContext(
            ageYears: 34, sex: .male, heightCm: 178, weightKg: 96.4, goalWeightKg: 82, weeklyLossKg: 0.6, activity: .light,
            conditions: [.insulinResistance], medications: "Metformin", allergies: "lactose",
            targets: SampleData.targets
        )
        let text = context.promptText()
        #expect(text.contains("Insulin resistance"))
        #expect(text.contains("Metformin"))
        #expect(text.contains("lactose"))
        #expect(text.contains("2050 kcal"))
    }

    @Test func conditionsAdjustMacros() {
        let body = SampleData.body
        let normal = NutritionCalculator.targets(body: body, activityFactor: 1.44, weeklyLossKg: 0.6, conditions: [])
        let diabetic = NutritionCalculator.targets(body: body, activityFactor: 1.44, weeklyLossKg: 0.6, conditions: [.type2Diabetes])
        let kidney = NutritionCalculator.targets(body: body, activityFactor: 1.44, weeklyLossKg: 0.6, conditions: [.kidneyDisease])
        #expect(diabetic.carbsGrams < normal.carbsGrams)
        #expect(kidney.proteinGrams < normal.proteinGrams)
        #expect(normal.calories == 2050)
    }
}

@Suite struct PortionTests {
    let yogurt = LibraryProduct(id: "p1", nameAr: "زبادي", nameEn: "Yogurt", brand: "", servingSizeG: 170,
                                per100g: NutrientsPer100(kcal: 59, proteinG: 10, carbsG: 3.6, fatG: 0.4))

    @Test func productPortion() {
        let item = Portions.food(nameAr: "زبادي", nameEn: "Yogurt", grams: 170, per100g: yogurt.per100g, productId: "p1")
        #expect(item.kcal == 100)
        #expect(item.proteinG == 17)
    }

    @Test func rescaleKeepsDensity() {
        let rice = AnalyzedFood(nameAr: "رز", nameEn: "Rice", grams: 150, kcal: 195, proteinG: 4, carbsG: 42, fatG: 0.5, confidence: .medium)
        let bigger = Portions.rescale(rice, to: 300)
        #expect(bigger.kcal == 390)
        #expect(bigger.carbsG == 84)
    }

    @Test func applyingProductOption() {
        let guess = AnalyzedFood(nameAr: "زبادي", nameEn: "Yogurt", grams: 170, kcal: 104, proteinG: 6, carbsG: 8, fatG: 5.6, confidence: .low)
        let option = QuestionOption(labelAr: "يوناني", labelEn: "Greek", libraryProductId: "p1", grams: nil)
        let applied = Portions.apply(option, to: guess, library: [yogurt])
        #expect(applied.libraryProductId == "p1")
        #expect(applied.kcal == 100)
    }

    @Test func totals() {
        let total = Portions.total([
            AnalyzedFood(nameAr: "a", nameEn: "a", grams: 1, kcal: 100.4, proteinG: 10, carbsG: 5, fatG: 1, confidence: .high),
            AnalyzedFood(nameAr: "b", nameEn: "b", grams: 1, kcal: 200.4, proteinG: 5, carbsG: 5, fatG: 2, confidence: .high)
        ])
        #expect(total.calories == 301)
        #expect(total.proteinGrams == 15)
    }
}

@Suite struct ProgressTests {
    let calendar = Calendar(identifier: .gregorian)

    func day(_ offset: Int, from base: Date) -> Date {
        calendar.date(byAdding: .day, value: offset, to: base)!
    }

    @Test func streakCountsBackFromToday() {
        let today = calendar.startOfDay(for: Date())
        let days: Set<Date> = [day(0, from: today), day(-1, from: today), day(-2, from: today), day(-4, from: today)]
        #expect(ProgressMath.streak(loggedDays: days, today: today, calendar: calendar) == 3)
    }

    @Test func streakAllowsEmptyToday() {
        let today = calendar.startOfDay(for: Date())
        let days: Set<Date> = [day(-1, from: today), day(-2, from: today)]
        #expect(ProgressMath.streak(loggedDays: days, today: today, calendar: calendar) == 2)
        #expect(ProgressMath.streak(loggedDays: [day(-3, from: today)], today: today, calendar: calendar) == 0)
    }

    @Test func journeyInterpolates() {
        #expect(ProgressMath.journey(first: 100, now: 96, goal: 82, t: 0) == 100)
        #expect(ProgressMath.journey(first: 100, now: 96, goal: 82, t: 0.5) == 96)
        #expect(ProgressMath.journey(first: 100, now: 96, goal: 82, t: 1) == 82)
        #expect(ProgressMath.journey(first: 100, now: 96, goal: 82, t: 0.25) == 98)
    }

    @Test func badgesReflectProgress() {
        let input = AchievementInput(scanCount: 2, startWeightKg: 100.6, currentWeightKg: 96.4, streakDays: 8, bestStepsDay: 9000, loggedMealCount: 30, goalWeightKg: 82)
        let earned = Set(Achievements.badges(for: input).filter(\.achieved).map(\.id))
        #expect(earned == ["first-meal", "first-inbody", "streak-7", "lost-3"])
    }
}

@Suite struct OpenFoodFactsTests {
    @Test func parsesProduct() throws {
        let json = """
        {"status":1,"product":{"product_name":"Greek Yogurt","brands":"Brand A, Brand B","serving_quantity":"170",
         "nutriments":{"energy-kcal_100g":59,"proteins_100g":10,"carbohydrates_100g":"3.6","fat_100g":0.4,"sodium_100g":0.04}}}
        """
        let product = try #require(OpenFoodFacts.parse(Data(json.utf8), barcode: "6221234567890"))
        #expect(product.nameEn == "Greek Yogurt")
        #expect(product.brand == "Brand A")
        #expect(product.servingSizeG == 170)
        #expect(product.per100g.carbsG == 3.6)
        #expect(abs((product.per100g.sodiumMg ?? 0) - 40) < 0.001)
    }

    @Test func missingProductIsNil() {
        #expect(OpenFoodFacts.parse(Data(#"{"status":0}"#.utf8), barcode: "1") == nil)
    }
}

@Suite struct WaterStoreTests {
    @Test func addsAndUpdatesSnapshot() {
        let suite = "vitalyn-water-\(UUID().uuidString)"
        var snapshot = SampleData.today
        snapshot.date = Date()
        snapshot.waterGlasses = 0
        SnapshotStore.save(snapshot, suiteName: suite)
        WaterStore.add(1, suiteName: suite)
        let count = WaterStore.add(2, suiteName: suite)
        #expect(count == 3)
        #expect(WaterStore.glasses(suiteName: suite) == 3)
        #expect(SnapshotStore.load(suiteName: suite)?.waterGlasses == 3)
        UserDefaults().removePersistentDomain(forName: suite)
    }
}

@Suite struct HealthItemsTests {
    @Test func listsRoundTrip() {
        #expect(ProfileList.items("WPW\nAsthma; Gout،  ") == ["WPW", "Asthma", "Gout"])
        #expect(ProfileList.text(["Concor", " ", "Aspirin "]) == "Concor\nAspirin")
    }

    @Test func identifyRequestListsItems() throws {
        let request = AITasks.identifyHealthItems([(.condition, "WPW"), (.medication, "Beta core")], context: nil)
        guard case let .text(text)? = request.messages.first?.content.first else {
            Issue.record("expected a text message")
            return
        }
        #expect(text.contains("- condition: WPW"))
        #expect(text.contains("- medication: Beta core"))
        #expect(JSONSchema.problems(in: request.schema).isEmpty)
    }

    @Test func insightsAppearInPrompt() {
        let context = UserContext(ageYears: 34, sex: .male, heightCm: 178, weightKg: 96, goalWeightKg: 80, weeklyLossKg: 0.7,
                                  activity: .light, targets: SampleData.targets,
                                  healthInsights: ["Wolff-Parkinson-White syndrome (WPW): avoid stimulants"])
        #expect(context.promptText().contains("Wolff-Parkinson-White"))
    }

    @Test func insightKeyIgnoresCaseAndSpaces() {
        #expect(HealthItemInsight.key(.condition, " WPW ") == HealthItemInsight.key(.condition, "wpw"))
    }
}
