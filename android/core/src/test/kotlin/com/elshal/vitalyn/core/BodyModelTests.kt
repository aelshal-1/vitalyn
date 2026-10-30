package com.elshal.vitalyn.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BodyModelTests {
    private val model = BodyModel.bundled!!

    @Test
    fun loadsBundledBody() {
        assertTrue(model.vertexCount > 13_000)
        assertEquals(36, model.targetCount)
        for (part in BodyModel.Part.entries) {
            val count = model.parts[part]?.size ?: 0
            assertTrue(count > 0, "missing $part")
            assertTrue(count % 3 == 0)
        }
        assertTrue(model.parts.values.all { indices -> indices.all { (it.toInt() and 0xFFFF) < model.vertexCount } })
    }

    @Test
    fun rejectsOtherFiles() {
        assertFailsWith<BodyModel.LoadError> { BodyModel("not a body".toByteArray()) }
    }

    @Test
    fun macroWeightsBlendLikeMakeHuman() {
        val shape = BodyShapeParameters(male = 1.0, age = 0.75, muscle = 0.25, weight = 0.75)
        val weights = shape.targetWeights
        val universal = weights.filterKeys { it.startsWith("universal-") }.values.sum()
        assertTrue(abs(universal - 1) < 1e-9)
        assertTrue(abs((weights["universal-male-young-minmuscle-maxweight"] ?: 0.0) - 0.5 * 0.5 * 0.5) < 1e-9)
        assertTrue(weights.keys.all { !it.contains("female") })
    }

    @Test
    fun fitMatchesWeightAndHeight() {
        val cases = listOf(
            BodyStats(sex = Sex.MALE, ageYears = 35, heightCm = 175.0, weightKg = 110.0, bodyFatPercent = 31.2),
            BodyStats(sex = Sex.MALE, ageYears = 30, heightCm = 180.0, weightKg = 75.0, bodyFatPercent = 12.0),
            BodyStats(sex = Sex.FEMALE, ageYears = 30, heightCm = 162.0, weightKg = 60.0),
            BodyStats(sex = Sex.FEMALE, ageYears = 45, heightCm = 160.0, weightKg = 95.0)
        )
        for (stats in cases) {
            val fit = BodyFit.fit(stats, model)
            assertTrue(abs(fit.modelMassKg - stats.weightKg) / stats.weightKg < 0.02, "$stats: mass ${fit.modelMassKg}")
            val mesh = BodyMesh.make(stats, model)
            // The hair adds a few millimetres on top.
            assertTrue(abs(mesh.height.toDouble() * 100 - stats.heightCm) < 1.5, "$stats: height ${mesh.height}")
            assertTrue((0 until mesh.vertexCount).all { mesh.positions[3 * it + 1] > -0.01f })
        }
    }

    @Test
    fun heavierMeansSofterAndBigger() {
        val lean = BodyStats(sex = Sex.MALE, ageYears = 35, heightCm = 175.0, weightKg = 75.0, bodyFatPercent = 15.0)
        val heavy = BodyStats(sex = Sex.MALE, ageYears = 35, heightCm = 175.0, weightKg = 105.0, bodyFatPercent = 30.0)
        val a = BodyFit.fit(lean, model)
        val b = BodyFit.fit(heavy, model)
        assertTrue(b.parameters.muscle < a.parameters.muscle)
        val volumeA = model.volume(model.vertices(a.parameters)) * (a.scale * 10).pow(3).toDouble()
        val volumeB = model.volume(model.vertices(b.parameters)) * (b.scale * 10).pow(3).toDouble()
        assertTrue(volumeB > volumeA * 1.3)
    }

    @Test
    fun estimatesBodyFatWithoutAScan() {
        val stats = BodyStats(sex = Sex.MALE, ageYears = 35, heightCm = 175.0, weightKg = 80.0)
        assertTrue(abs(stats.effectiveBodyFatPercent - 23.2) < 0.5)
        assertEquals(0.5, BodyFit.makeHumanAge(20))
        assertEquals(1.0, BodyFit.makeHumanAge(90))
    }

    @Test
    fun blendsBetweenShapes() {
        val a = BodyMesh.make(BodyStats(sex = Sex.FEMALE, ageYears = 30, heightCm = 165.0, weightKg = 80.0), model)
        val b = BodyMesh.make(BodyStats(sex = Sex.FEMALE, ageYears = 30, heightCm = 165.0, weightKg = 62.0), model)
        val mid = BodyMesh.blend(a, b, t = 0.5f)
        assertEquals(a.vertexCount, mid.vertexCount)
        assertTrue(abs(mid.positions[3 * 100] - (a.positions[3 * 100] + b.positions[3 * 100]) / 2) < 1e-5f)
        assertTrue(BodyMesh.blend(a, b, t = 0f).positions.contentEquals(a.positions))
    }
}
