package com.elshal.vitalyn.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What we know about a body at one moment (start, now or goal). */
data class BodyStats(
    val sex: Sex,
    val ageYears: Int,
    val heightCm: Double,
    val weightKg: Double,
    /** Measured (InBody) when available; otherwise estimated from BMI. */
    val bodyFatPercent: Double? = null
) {
    val bmi: Double
        get() {
            val metres = heightCm / 100
            return weightKg / (metres * metres)
        }

    /** Measured body fat, or the Deurenberg (1991) estimate from BMI, age and sex. */
    val effectiveBodyFatPercent: Double
        get() {
            val measured = bodyFatPercent
            if (measured != null && measured > 2 && measured < 75) return measured
            val estimate = 1.2 * bmi + 0.23 * ageYears.toDouble() - 10.8 * (if (sex == Sex.MALE) 1 else 0) - 5.4
            return min(60.0, max(if (sex == Sex.MALE) 6.0 else 12.0, estimate))
        }

    /** Fat-free mass index (kg/m²). */
    val ffmi: Double
        get() {
            val metres = heightCm / 100
            return weightKg * (1 - effectiveBodyFatPercent / 100) / (metres * metres)
        }
}

/** The fitted shape for some stats. */
data class BodyFitResult(
    val parameters: BodyShapeParameters,
    /** Metres per mesh unit, so the mesh stands exactly at the person's height. */
    val scale: Float,
    /** The mass the fitted mesh would have at the person's body density. */
    val modelMassKg: Double
)

object BodyFit {
    /** MakeHuman age: 25 years = 0.5, 90 years = 1 (younger adults use the young-adult body). */
    fun makeHumanAge(years: Int): Double = min(1.0, max(0.5, 0.5 + (years - 25).toDouble() / 130))

    /** Tone, mostly from body fat (MakeHuman's low "muscle" is a soft, higher-fat body), nudged by lean mass. */
    fun muscle(stats: BodyStats): Double {
        val (ffmiAverage, fatAverage) = if (stats.sex == Sex.MALE) 19.0 to 18.0 else 15.5 to 27.0
        val tone = 0.5 - 0.03 * (stats.effectiveBodyFatPercent - fatAverage) + 0.02 * (stats.ffmi - ffmiAverage)
        return min(0.95, max(0.05, tone))
    }

    /** Body density (kg/L) from body fat, inverting Siri's equation. */
    fun density(bodyFatPercent: Double): Double = 495 / (bodyFatPercent + 450)

    /** Finds the MakeHuman weight value whose mesh, at the person's height and density, matches their weight. */
    fun fit(stats: BodyStats, model: BodyModel): BodyFitResult {
        var parameters = BodyShapeParameters(
            male = if (stats.sex == Sex.MALE) 1.0 else 0.0,
            age = makeHumanAge(stats.ageYears),
            muscle = muscle(stats),
            weight = 0.5
        )
        val density = density(stats.effectiveBodyFatPercent)
        val heightDm = (stats.heightCm / 10).toFloat()

        fun evaluate(weight: Double): Pair<Double, Float> {
            parameters = parameters.copy(weight = weight)
            val vertices = model.vertices(parameters)
            val scale = heightDm / model.height(vertices)
            // Mesh volume includes ~1 L of air in the lungs.
            val litres = model.volume(vertices) * (scale * scale * scale).toDouble()
            return (litres - 1) * density to scale / 10
        }

        // MakeHuman's weight targets add little mass on a toned body, so very heavy bodies get a softer tone
        // rather than pushing the targets far past their range.
        while (parameters.muscle > 0.05 && evaluate(1.15).first < stats.weightKg) {
            parameters = parameters.copy(muscle = max(0.05, parameters.muscle - 0.05))
        }
        var low = -0.2
        var high = 1.4
        repeat(16) {
            val mid = (low + high) / 2
            if (evaluate(mid).first < stats.weightKg) low = mid else high = mid
        }
        val weight = (low + high) / 2
        val final = evaluate(weight)
        parameters = parameters.copy(weight = weight)
        return BodyFitResult(parameters = parameters, scale = final.second, modelMassKg = final.first)
    }
}

/**
 * Display-ready geometry in metres: feet on y = 0, centred, with smoothed clothes and shoes.
 * [positions] and [normals] are xyz triples per vertex, indexed by [BodyModel.parts].
 */
class BodyMesh(val positions: FloatArray, val normals: FloatArray) {
    val vertexCount: Int get() = positions.size / 3

    val height: Float
        get() {
            var top = 0f
            var i = 1
            while (i < positions.size) {
                top = max(top, positions[i])
                i += 3
            }
            return top
        }

    override fun equals(other: Any?): Boolean =
        other is BodyMesh && positions.contentEquals(other.positions) && normals.contentEquals(other.normals)

    override fun hashCode(): Int = 31 * positions.contentHashCode() + normals.contentHashCode()

    companion object {
        fun make(stats: BodyStats, model: BodyModel): BodyMesh {
            val fit = BodyFit.fit(stats, model)
            val positions = model.vertices(fit.parameters)
            for (i in positions.indices) positions[i] *= fit.scale

            val low = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            val high = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (i in positions.indices) {
                val axis = i % 3
                low[axis] = min(low[axis], positions[i])
                high[axis] = max(high[axis], positions[i])
            }
            val offset = floatArrayOf((low[0] + high[0]) / 2, low[1], (low[2] + high[2]) / 2)
            for (i in positions.indices) positions[i] -= offset[i % 3]

            // Fabric hides small anatomy; shoes are smooth and a little bigger than the foot; hair has some volume.
            smooth(positions, model, listOf(BodyModel.Part.SHIRT), iterations = 3)
            smooth(positions, model, listOf(BodyModel.Part.SHOES, BodyModel.Part.SOLE), iterations = 6)
            var normals = vertexNormals(positions, model)
            inflate(positions, normals, model, listOf(BodyModel.Part.SHOES, BodyModel.Part.SOLE), distance = 0.006f)
            inflate(positions, normals, model, listOf(BodyModel.Part.HAIR), distance = 0.004f)
            normals = vertexNormals(positions, model)
            return BodyMesh(positions, normals)
        }

        /** Linear blend between two shapes of the same body (used by the journey slider). */
        fun blend(a: BodyMesh, b: BodyMesh, t: Float): BodyMesh {
            val t = min(1f, max(0f, t))
            val count = min(a.positions.size, b.positions.size) / 3
            val positions = FloatArray(count * 3)
            val normals = FloatArray(count * 3)
            for (i in 0 until count * 3) {
                positions[i] = mix(a.positions[i], b.positions[i], t)
            }
            for (v in 0 until count) {
                val i = 3 * v
                val x = mix(a.normals[i], b.normals[i], t)
                val y = mix(a.normals[i + 1], b.normals[i + 1], t)
                val z = mix(a.normals[i + 2], b.normals[i + 2], t)
                val length = sqrt(x * x + y * y + z * z)
                normals[i] = x / length
                normals[i + 1] = y / length
                normals[i + 2] = z / length
            }
            return BodyMesh(positions, normals)
        }

        private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        internal fun vertices(parts: List<BodyModel.Part>, model: BodyModel): Set<Int> {
            val set = HashSet<Int>()
            for (part in parts) model.parts[part]?.forEach { set.add(it.toInt() and 0xFFFF) }
            return set
        }

        internal fun smooth(positions: FloatArray, model: BodyModel, parts: List<BodyModel.Part>, iterations: Int) {
            val inside = vertices(parts, model)
            // Keep the edges of the region fixed so seams don't pull away from the skin.
            val others = vertices(BodyModel.Part.entries.filter { it !in parts }, model)
            val movable = (inside - others).toIntArray()
            repeat(iterations) {
                val current = positions.copyOf()
                for (i in movable) {
                    val ring = model.neighbours[i]
                    if (ring.isEmpty()) continue
                    var sx = 0f
                    var sy = 0f
                    var sz = 0f
                    for (n in ring) {
                        sx += current[3 * n]
                        sy += current[3 * n + 1]
                        sz += current[3 * n + 2]
                    }
                    val count = ring.size.toFloat()
                    positions[3 * i] = mix(current[3 * i], sx / count, 0.5f)
                    positions[3 * i + 1] = mix(current[3 * i + 1], sy / count, 0.5f)
                    positions[3 * i + 2] = mix(current[3 * i + 2], sz / count, 0.5f)
                }
            }
        }

        internal fun inflate(positions: FloatArray, normals: FloatArray, model: BodyModel, parts: List<BodyModel.Part>, distance: Float) {
            for (i in vertices(parts, model)) {
                positions[3 * i] += normals[3 * i] * distance
                positions[3 * i + 1] += normals[3 * i + 1] * distance
                positions[3 * i + 2] += normals[3 * i + 2] * distance
            }
        }

        internal fun vertexNormals(positions: FloatArray, model: BodyModel): FloatArray {
            val normals = FloatArray(positions.size)
            for (indices in model.parts.values) {
                var t = 0
                while (t < indices.size - 2) {
                    val a = 3 * (indices[t].toInt() and 0xFFFF)
                    val b = 3 * (indices[t + 1].toInt() and 0xFFFF)
                    val c = 3 * (indices[t + 2].toInt() and 0xFFFF)
                    val ux = positions[b] - positions[a]; val uy = positions[b + 1] - positions[a + 1]; val uz = positions[b + 2] - positions[a + 2]
                    val vx = positions[c] - positions[a]; val vy = positions[c + 1] - positions[a + 1]; val vz = positions[c + 2] - positions[a + 2]
                    val nx = uy * vz - uz * vy
                    val ny = uz * vx - ux * vz
                    val nz = ux * vy - uy * vx
                    for (p in intArrayOf(a, b, c)) {
                        normals[p] += nx
                        normals[p + 1] += ny
                        normals[p + 2] += nz
                    }
                    t += 3
                }
            }
            var i = 0
            while (i < normals.size) {
                val length = sqrt(normals[i] * normals[i] + normals[i + 1] * normals[i + 1] + normals[i + 2] * normals[i + 2])
                if (length > 0) {
                    normals[i] /= length
                    normals[i + 1] /= length
                    normals[i + 2] /= length
                } else {
                    normals[i] = 0f
                    normals[i + 1] = 1f
                    normals[i + 2] = 0f
                }
                i += 3
            }
            return normals
        }
    }
}
