package com.elshal.vitalyn.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okio.Buffer
import okhttp3.Request
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private fun Request.bodyJson() = JSONValue.parse(Buffer().also { assertNotNull(body).writeTo(it) }.readByteArray())

class JSONValueTests {
    @Test
    fun serializesInOrderWithEscapes() {
        val value = jsonObjectOf("b" to 1, "a" to "x\"y\\z\n", "c" to jsonArrayOf(true, null, 2.5), "ar" to "سعرات")
        assertEquals("""{"b":1,"a":"x\"y\\z\n","c":[true,null,2.5],"ar":"سعرات"}""", value.serialized())
    }

    @Test
    fun roundTripsThroughFoundation() {
        val value = jsonObjectOf("n" to 1.25, "s" to "تست", "list" to jsonArrayOf(1, 2))
        val parsed = JSONValue.parse(value.serialized().toByteArray())
        assertEquals(JsonPrimitive(1.25), parsed["n"])
        assertEquals(JsonPrimitive("تست"), parsed["s"])
        assertEquals(jsonArrayOf(1, 2), parsed["list"])
    }

    @Test
    fun controlCharactersAreEscaped() {
        assertEquals(""""a\u0001b"""", JsonPrimitive("a\u0001b").serialized())
    }
}

class SchemaTests {
    @Test
    fun schemaIsValidForStructuredOutputs() {
        for (name in AITasks.allSchemas.keys.sorted()) {
            val schema = assertNotNull(AITasks.allSchemas[name])
            assertTrue(JSONSchema.problems(schema).isEmpty(), "$name: ${JSONSchema.problems(schema)}")
        }
    }

    @Test
    fun problemsAreDetected() {
        val bad = jsonObjectOf(
            "type" to "object",
            "properties" to jsonObjectOf("a" to jsonObjectOf("type" to "number", "minimum" to 0)),
            "required" to jsonArrayOf()
        )
        val issues = JSONSchema.problems(bad)
        assertTrue(issues.any { it.contains("additionalProperties") })
        assertTrue(issues.any { it.contains("not required") })
        assertTrue(issues.any { it.contains("minimum") })
    }

    @Test
    fun schemaRoundTripsAsJSON() {
        for ((_, schema) in AITasks.allSchemas) {
            Json.parseToJsonElement(schema.serialized())
        }
    }
}

class ClaudeClientTests {
    @Test
    fun buildsRequestForOpusWithFallback() {
        val client = ClaudeClient(apiKey = "sk-test", model = ClaudeModel.OPUS)
        val request = client.makeRequest("sys", listOf(ClaudeMessage.user(listOf(ClaudeContent.Text("hi")))), AITasks.tipSchema, ClaudeEffort.LOW, 100)
        assertEquals("sk-test", request.header("x-api-key"))
        assertEquals("2026-10-21", request.header("anthropic-version"))
        assertEquals("server-side-fallback-2026-07-01", request.header("anthropic-beta"))
        val body = request.bodyJson()
        assertEquals(JsonPrimitive("claude-opus-5-5"), body["model"])
        assertEquals(JsonPrimitive("default"), body["fallbacks"])
        assertEquals(JsonPrimitive("low"), body["output_config"]["effort"])
        assertEquals(JsonPrimitive("json_schema"), body["output_config"]["format"]["type"])
        assertNull(body["temperature"])
        assertNull(body["thinking"])
        assertEquals(JsonPrimitive(true), body["stream"])
    }

    @Test
    fun collectsStreamedText() {
        val stream = StreamAccumulator()
        val lines = listOf(
            "event: message_start",
            """data: {"type":"message_start","message":{"id":"msg_1"}}""",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hmm"}}""",
            """data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"{\"tipAr\":\"أ\","}}""",
            """data: {"type":"ping"}""",
            """data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"\"tipEn\":\"b\"}"}}""",
            """data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":12}}""",
            """data: {"type":"message_stop"}"""
        )
        for (line in lines) stream.consume(line)
        val tip = ClaudeClient.json.decodeFromString<DailyTip>(stream.finish())
        assertEquals("أ", tip.tipAr)
        assertEquals("b", tip.tipEn)
    }

    @Test
    fun parsesWholeStreamedBody() {
        val body = """
            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"{\"tipAr\":\"أ\",\"tipEn\":\"b\"}"}}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}

        """.trimIndent() + "\n"
        val text = ClaudeClient.parseStream(body.toByteArray(), 200)
        assertEquals("b", ClaudeClient.json.decodeFromString<DailyTip>(text).tipEn)
        assertTrue(ClaudeClient.partialStreamText(body.toByteArray()).startsWith("{"))
        val error = """{"type":"error","error":{"type":"rate_limit_error","message":"slow down"}}"""
        val thrown = assertFailsWith<ClaudeError> { ClaudeClient.parseStream(error.toByteArray(), 429) }
        assertEquals(ClaudeError.Http(status = 429, errorMessage = "slow down"), thrown)
    }

    @Test
    fun streamSurfacesStopsAndErrors() {
        val truncated = StreamAccumulator()
        truncated.consume("""data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"{"}}""")
        truncated.consume("""data: {"type":"message_delta","delta":{"stop_reason":"max_tokens"}}""")
        assertEquals<ClaudeError>(ClaudeError.Truncated(), assertFailsWith<ClaudeError> { truncated.finish() })

        val overloaded = assertFailsWith<ClaudeError> {
            StreamAccumulator().consume("""data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
        }
        assertEquals<ClaudeError>(ClaudeError.Http(status = 529, errorMessage = "Overloaded"), overloaded)
    }

    @Test
    fun haikuHasNoFallback() {
        val client = ClaudeClient(apiKey = "k", model = ClaudeModel.HAIKU)
        val request = client.makeRequest("s", listOf(ClaudeMessage.user(listOf(ClaudeContent.Text("x")))), null, ClaudeEffort.MEDIUM, 10)
        assertNull(request.header("anthropic-beta"))
        val body = request.bodyJson()
        assertNull(body["fallbacks"])
        assertNull(body["output_config"]["format"])
    }

    @Test
    fun encodesImagesAndPDFs() {
        val client = ClaudeClient(apiKey = "k")
        val content = listOf(ClaudeContent.Image(byteArrayOf(1, 2, 3), mediaType = "image/jpeg"), ClaudeContent.Pdf(byteArrayOf(4)))
        val request = client.makeRequest("s", listOf(ClaudeMessage.user(content)), null, ClaudeEffort.MEDIUM, 10)
        val body = request.bodyJson()
        val blocks = assertNotNull(body["messages"].arrayValue?.firstOrNull()["content"].arrayValue)
        assertEquals(JsonPrimitive("image"), blocks[0]["type"])
        assertEquals(JsonPrimitive("AQID"), blocks[0]["source"]["data"])
        assertEquals(JsonPrimitive("document"), blocks[1]["type"])
        assertEquals(JsonPrimitive("application/pdf"), blocks[1]["source"]["media_type"])
    }

    @Test
    fun parsesTextSkippingThinking() {
        val response = """{"content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\"tipAr\":\"أ\",\"tipEn\":\"b\"}"}],"stop_reason":"end_turn"}"""
        val text = ClaudeClient.parse(response.toByteArray(), 200)
        val tip = ClaudeClient.json.decodeFromString<DailyTip>(text)
        assertEquals("b", tip.tipEn)
    }

    @Test
    fun surfacesErrors() {
        val error = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        assertEquals<ClaudeError>(
            ClaudeError.Http(status = 401, errorMessage = "invalid x-api-key"),
            assertFailsWith<ClaudeError> { ClaudeClient.parse(error.toByteArray(), 401) }
        )
        assertEquals<ClaudeError>(
            ClaudeError.Refusal(),
            assertFailsWith<ClaudeError> { ClaudeClient.parse("""{"content":[],"stop_reason":"refusal"}""".toByteArray(), 200) }
        )
        assertEquals<ClaudeError>(
            ClaudeError.Truncated(),
            assertFailsWith<ClaudeError> {
                ClaudeClient.parse("""{"content":[{"type":"text","text":"{"}],"stop_reason":"max_tokens"}""".toByteArray(), 200)
            }
        )
    }

    @Test
    fun missingKeyFailsFast() = runBlocking {
        val thrown = assertFailsWith<ClaudeError> {
            ClaudeClient(apiKey = " ").send(system = "s", messages = emptyList(), schema = null)
        }
        assertEquals<ClaudeError>(ClaudeError.MissingAPIKey(), thrown)
    }

    @Test
    fun decodesInBodyExtraction() {
        val json = """
            {"scanDate":"2026-10-26","weightKg":96.4,"skeletalMuscleKg":36.8,"bodyFatKg":30.1,"bodyFatPercent":31.2,
             "leanMassKg":66.3,"visceralFatLevel":13,"bmrKcal":1880,"totalBodyWaterL":48.6,"inbodyScore":null,
             "heightCm":178,"ageYears":34,
             "segmentalLean":{"rightArmKg":3.9,"leftArmKg":3.8,"trunkKg":29.4,"rightLegKg":10.1,"leftLegKg":10.0},
             "lowConfidenceFields":["visceralFatLevel"],"summaryAr":"تمام","summaryEn":"Good"}
        """.trimIndent()
        val scan = ClaudeClient.json.decodeFromString<InBodyExtraction>(json)
        assertEquals(1880.0, scan.bmrKcal)
        assertNull(scan.inbodyScore)
        assertEquals(29.4, scan.segmentalLean.trunkKg)
    }

    @Test
    fun decodesCoachReplyWithActions() {
        val json = """
            {"replyAr":"برافو","replyEn":"Nice","actions":[
              {"type":"log_workout","workout":{"nameAr":"كورة","nameEn":"Football","kind":"football","minutes":60,"kcal":520},"waterGlasses":null,"meal":null,"videos":[]},
              {"type":"suggest_videos","workout":null,"waterGlasses":null,"meal":null,"videos":[{"titleAr":"إطالة","titleEn":"Stretch","query":"post football stretch"}]}
            ]}
        """.trimIndent()
        val reply = ClaudeClient.json.decodeFromString<CoachReply>(json)
        assertEquals(WorkoutKind.FOOTBALL, reply.actions.firstOrNull()?.workout?.kind)
        assertEquals(
            "https://www.youtube.com/results?search_query=post%20football%20stretch",
            reply.actions.lastOrNull()?.videos?.firstOrNull()?.youtubeURL
        )
    }
}

class UserContextTests {
    @Test
    fun promptIncludesConditionsAndTargets() {
        val context = UserContext(
            ageYears = 34, sex = Sex.MALE, heightCm = 178.0, weightKg = 96.4, goalWeightKg = 82.0, weeklyLossKg = 0.6, activity = ActivityLevel.LIGHT,
            conditions = listOf(ChronicCondition.INSULIN_RESISTANCE), medications = "Metformin", allergies = "lactose",
            targets = SampleData.targets
        )
        val text = context.promptText()
        assertTrue(text.contains("Insulin resistance"))
        assertTrue(text.contains("Metformin"))
        assertTrue(text.contains("lactose"))
        assertTrue(text.contains("2050 kcal"))
    }

    @Test
    fun conditionsAdjustMacros() {
        val body = SampleData.body
        val normal = NutritionCalculator.targets(body, activityFactor = 1.44, weeklyLossKg = 0.6, conditions = emptyList())
        val diabetic = NutritionCalculator.targets(body, activityFactor = 1.44, weeklyLossKg = 0.6, conditions = listOf(ChronicCondition.TYPE2_DIABETES))
        val kidney = NutritionCalculator.targets(body, activityFactor = 1.44, weeklyLossKg = 0.6, conditions = listOf(ChronicCondition.KIDNEY_DISEASE))
        assertTrue(diabetic.carbsGrams < normal.carbsGrams)
        assertTrue(kidney.proteinGrams < normal.proteinGrams)
        assertEquals(2050, normal.calories)
    }
}

class PortionTests {
    private val yogurt = LibraryProduct(
        id = "p1", nameAr = "زبادي", nameEn = "Yogurt", brand = "", servingSizeG = 170.0,
        per100g = NutrientsPer100(kcal = 59.0, proteinG = 10.0, carbsG = 3.6, fatG = 0.4)
    )

    @Test
    fun productPortion() {
        val item = Portions.food(nameAr = "زبادي", nameEn = "Yogurt", grams = 170.0, per100g = yogurt.per100g, productId = "p1")
        assertEquals(100.0, item.kcal)
        assertEquals(17.0, item.proteinG)
    }

    @Test
    fun rescaleKeepsDensity() {
        val rice = AnalyzedFood(nameAr = "رز", nameEn = "Rice", grams = 150.0, kcal = 195.0, proteinG = 4.0, carbsG = 42.0, fatG = 0.5, confidence = Confidence.MEDIUM)
        val bigger = Portions.rescale(rice, grams = 300.0)
        assertEquals(390.0, bigger.kcal)
        assertEquals(84.0, bigger.carbsG)
    }

    @Test
    fun applyingProductOption() {
        val guess = AnalyzedFood(nameAr = "زبادي", nameEn = "Yogurt", grams = 170.0, kcal = 104.0, proteinG = 6.0, carbsG = 8.0, fatG = 5.6, confidence = Confidence.LOW)
        val option = QuestionOption(labelAr = "يوناني", labelEn = "Greek", libraryProductId = "p1", grams = null)
        val applied = Portions.apply(option, guess, library = listOf(yogurt))
        assertEquals("p1", applied.libraryProductId)
        assertEquals(100.0, applied.kcal)
    }

    @Test
    fun totals() {
        val total = Portions.total(
            listOf(
                AnalyzedFood(nameAr = "a", nameEn = "a", grams = 1.0, kcal = 100.4, proteinG = 10.0, carbsG = 5.0, fatG = 1.0, confidence = Confidence.HIGH),
                AnalyzedFood(nameAr = "b", nameEn = "b", grams = 1.0, kcal = 200.4, proteinG = 5.0, carbsG = 5.0, fatG = 2.0, confidence = Confidence.HIGH)
            )
        )
        assertEquals(301, total.calories)
        assertEquals(15, total.proteinGrams)
    }
}

class ProgressTests {
    private fun day(offset: Int, base: LocalDate): LocalDate = base.plusDays(offset.toLong())

    @Test
    fun streakCountsBackFromToday() {
        val today = LocalDate.now()
        val days = setOf(day(0, today), day(-1, today), day(-2, today), day(-4, today))
        assertEquals(3, ProgressMath.streak(loggedDays = days, today = today))
    }

    @Test
    fun streakAllowsEmptyToday() {
        val today = LocalDate.now()
        val days = setOf(day(-1, today), day(-2, today))
        assertEquals(2, ProgressMath.streak(loggedDays = days, today = today))
        assertEquals(0, ProgressMath.streak(loggedDays = setOf(day(-3, today)), today = today))
    }

    @Test
    fun journeyInterpolates() {
        assertEquals(100.0, ProgressMath.journey(first = 100.0, now = 96.0, goal = 82.0, t = 0.0))
        assertEquals(96.0, ProgressMath.journey(first = 100.0, now = 96.0, goal = 82.0, t = 0.5))
        assertEquals(82.0, ProgressMath.journey(first = 100.0, now = 96.0, goal = 82.0, t = 1.0))
        assertEquals(98.0, ProgressMath.journey(first = 100.0, now = 96.0, goal = 82.0, t = 0.25))
    }

    @Test
    fun badgesReflectProgress() {
        val input = AchievementInput(scanCount = 2, startWeightKg = 100.6, currentWeightKg = 96.4, streakDays = 8, bestStepsDay = 9000, loggedMealCount = 30, goalWeightKg = 82.0)
        val earned = Achievements.badges(input).filter { it.achieved }.map { it.id }.toSet()
        assertEquals(setOf("first-meal", "first-inbody", "streak-7", "lost-3"), earned)
    }
}

class OpenFoodFactsTests {
    @Test
    fun parsesProduct() {
        val json = """
            {"status":1,"product":{"product_name":"Greek Yogurt","brands":"Brand A, Brand B","serving_quantity":"170",
             "nutriments":{"energy-kcal_100g":59,"proteins_100g":10,"carbohydrates_100g":"3.6","fat_100g":0.4,"sodium_100g":0.04}}}
        """.trimIndent()
        val product = assertNotNull(OpenFoodFacts.parse(json.toByteArray(), barcode = "6221234567890"))
        assertEquals("Greek Yogurt", product.nameEn)
        assertEquals("Brand A", product.brand)
        assertEquals(170.0, product.servingSizeG)
        assertEquals(3.6, product.per100g.carbsG)
        assertTrue(abs((product.per100g.sodiumMg ?: 0.0) - 40) < 0.001)
    }

    @Test
    fun missingProductIsNil() {
        assertNull(OpenFoodFacts.parse("""{"status":0}""".toByteArray(), barcode = "1"))
    }
}

/**
 * The iOS WaterStoreTests exercise UserDefaults (App Group) storage, which is not part of the Kotlin core:
 * storage is left to the Android app. This ports the part that is core logic: today's snapshot picks up the
 * new water count and survives the JSON round trip the store would do.
 */
class WaterStoreTests {
    @Test
    fun addsAndUpdatesSnapshot() {
        var snapshot = SampleData.today.copy(date = java.time.Instant.now(), waterGlasses = 0)
        var count = 0
        count = maxOf(0, count + 1)
        count = maxOf(0, count + 2)
        snapshot = snapshot.copy(waterGlasses = count)
        assertEquals(3, count)
        val loaded = VitalynJson.decodeFromString<TodaySnapshot>(VitalynJson.encodeToString(TodaySnapshot.serializer(), snapshot))
        assertEquals(3, loaded.waterGlasses)
    }
}

class HealthItemsTests {
    @Test
    fun listsRoundTrip() {
        assertEquals(listOf("WPW", "Asthma", "Gout"), ProfileList.items("WPW\nAsthma; Gout،  "))
        assertEquals("Concor\nAspirin", ProfileList.text(listOf("Concor", " ", "Aspirin ")))
    }

    @Test
    fun identifyRequestListsItems() {
        val request = AITasks.identifyHealthItems(listOf(HealthItemKind.CONDITION to "WPW", HealthItemKind.MEDICATION to "Beta core"), context = null)
        val text = (request.messages.firstOrNull()?.content?.firstOrNull() as? ClaudeContent.Text)?.text
            ?: fail("expected a text message")
        assertTrue(text.contains("- condition: WPW"))
        assertTrue(text.contains("- medication: Beta core"))
        assertTrue(JSONSchema.problems(request.schema).isEmpty())
    }

    @Test
    fun insightsAppearInPrompt() {
        val context = UserContext(
            ageYears = 34, sex = Sex.MALE, heightCm = 178.0, weightKg = 96.0, goalWeightKg = 80.0, weeklyLossKg = 0.7,
            activity = ActivityLevel.LIGHT, targets = SampleData.targets,
            healthInsights = listOf("Wolff-Parkinson-White syndrome (WPW): avoid stimulants")
        )
        assertTrue(context.promptText().contains("Wolff-Parkinson-White"))
    }

    @Test
    fun insightKeyIgnoresCaseAndSpaces() {
        assertEquals(HealthItemInsight.key(HealthItemKind.CONDITION, "wpw"), HealthItemInsight.key(HealthItemKind.CONDITION, " WPW "))
    }
}

class MotivationTests {
    private val input = MotivationInput(name = "Ahmed Elshal", streakDays = 5, lostKg = 4.2, remainingKg = 14.4, proteinTarget = 150, stepsGoal = 8000)

    @Test
    fun progressMessagesComeFirst() {
        val messages = Motivation.messages(input, count = 7, seed = 0)
        assertEquals(7, messages.size)
        assertEquals("4.2 kg down 💪", messages[0].titleEn)
        assertTrue(messages[0].bodyEn.startsWith("Ahmed, "))
        assertEquals("5-day streak 🔥", messages[1].titleEn)
        assertTrue(messages[2].titleEn.contains("14.4 kg"))
    }

    @Test
    fun noProgressMeansGeneralOnly() {
        val fresh = MotivationInput(name = "", streakDays = 0, lostKg = 0.0, remainingKg = 0.0, proteinTarget = 120, stepsGoal = 7000)
        val messages = Motivation.messages(fresh, count = 3, seed = 1)
        assertEquals(3, messages.size)
        assertEquals("Protein first 🍗", messages[0].titleEn)
        assertTrue(messages[0].bodyEn.contains("120 g"))
    }

    @Test
    fun seedRotatesAndNegativeSeedIsSafe() {
        val a = Motivation.messages(input, count = 7, seed = 2)
        val b = Motivation.messages(input, count = 7, seed = 3)
        assertNotEquals(a, b)
        assertEquals(4, Motivation.messages(input, count = 4, seed = -5).size)
    }
}
