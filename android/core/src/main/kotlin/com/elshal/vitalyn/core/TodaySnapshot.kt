package com.elshal.vitalyn.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.max
import kotlin.math.min

/** Serializes an [Instant] as an ISO-8601 string (`2026-10-30T09:15:00Z`); also reads strings with an offset. */
object InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.elshal.vitalyn.core.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return try {
            Instant.parse(text)
        } catch (_: Exception) {
            OffsetDateTime.parse(text).toInstant()
        }
    }
}

/**
 * A small summary of the day that the app writes and the widgets read.
 * (On iOS it lives in the App Group's UserDefaults; on Android, storage is up to the app.)
 */
@Serializable
data class TodaySnapshot(
    @Serializable(with = InstantIso8601Serializer::class)
    val date: Instant,
    val targets: MacroTargets,
    val eatenCalories: Int,
    val activeCalories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val steps: Int,
    val stepsGoal: Int,
    val waterGlasses: Int,
    val waterGoal: Int,
    val streakDays: Int,
    val weightKg: Double,
    val weightChangeKg: Double
) {
    val caloriesLeft: Int get() = max(0, targets.calories - eatenCalories)

    val calorieProgress: Double get() = fraction(eatenCalories, targets.calories)
    val proteinProgress: Double get() = fraction(proteinGrams, targets.proteinGrams)
    val carbsProgress: Double get() = fraction(carbsGrams, targets.carbsGrams)
    val fatProgress: Double get() = fraction(fatGrams, targets.fatGrams)
    val stepsProgress: Double get() = fraction(steps, stepsGoal)

    companion object {
        internal fun fraction(value: Int, goal: Int): Double {
            if (goal <= 0) return 0.0
            return min(1.0, max(0.0, value.toDouble() / goal.toDouble()))
        }
    }
}
