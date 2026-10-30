package com.elshal.vitalyn.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The CC0 MakeHuman body packed by tools/avatar/build_body.py, plus the maths that shapes it.
 *
 * Units in the file are decimetres (y up, +z towards the viewer). Shapes come from MakeHuman's
 * macro targets: sex, age, muscle (tone) and weight, blended the same way MakeHuman blends them.
 *
 * Vertex arrays are flat `FloatArray`s of x, y, z triples (vertex `i` at `3 * i`), ready for a
 * `FloatBuffer`; [parts] holds each part's triangles as unsigned 16-bit indices (`GL_UNSIGNED_SHORT`).
 */
class BodyModel(data: ByteArray) {
    enum class Part(val raw: String) {
        SKIN("skin"), SHIRT("shirt"), LEGGINGS("leggings"), SHOES("shoes"), SOLE("sole"),
        HAIR("hair"), LONGHAIR("longhair"), BROWS("brows"), EYES("eyes"), IRIS("iris");

        companion object {
            fun fromRaw(raw: String): Part? = entries.firstOrNull { it.raw == raw }
        }
    }

    internal class Target(val indices: IntArray, val deltas: FloatArray)

    /** The file is not a Vitalyn body (Swift `LoadError.badFormat`). */
    class LoadError(message: String = "badFormat") : Exception(message)

    /** Base vertices, xyz triples in decimetres. */
    val base: FloatArray

    /** Each part's triangle list as unsigned 16-bit vertex indices. */
    val parts: Map<Part, ShortArray>
    internal val targets: Map<String, Target>

    /** Each vertex's neighbours, for smoothing clothes and shoes. */
    internal val neighbours: Array<IntArray>

    val vertexCount: Int get() = base.size / 3

    val targetCount: Int get() = targets.size

    /** A part's triangle indices as ints (each entry is a vertex index). */
    fun partIndices(part: Part): IntArray =
        parts[part]?.let { shorts -> IntArray(shorts.size) { shorts[it].toInt() and 0xFFFF } } ?: IntArray(0)

    init {
        val raw = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 0
        fun need(bytes: Int) {
            if (bytes < 0 || offset.toLong() + bytes.toLong() > data.size) throw LoadError()
        }
        fun u32(): Int {
            need(4)
            val value = raw.getInt(offset).toLong() and 0xFFFFFFFFL
            offset += 4
            if (value > Int.MAX_VALUE) throw LoadError()
            return value.toInt()
        }
        // Callers check the length first.
        fun f32(): Float = raw.getFloat(offset).also { offset += 4 }
        fun u16(): Int = (raw.getShort(offset).toInt() and 0xFFFF).also { offset += 2 }
        fun i16(): Short = raw.getShort(offset).also { offset += 2 }
        fun name(): String {
            need(1)
            val length = data[offset].toInt() and 0xFF
            need(1 + length)
            val text = String(data, offset + 1, length, Charsets.UTF_8)
            offset += 1 + length
            return text
        }

        if (!(data.size > 8 && data[0] == 0x56.toByte() && data[1] == 0x42.toByte() && data[2] == 0x44.toByte() && data[3] == 0x59.toByte())) {
            throw LoadError()
        }
        offset = 8
        val vertexCount = u32()
        need(vertexCount * 12)
        val base = FloatArray(vertexCount * 3)
        for (i in 0 until vertexCount * 3) base[i] = f32()

        val parts = LinkedHashMap<Part, ShortArray>()
        val partCount = u32()
        repeat(partCount) {
            val partName = name()
            val count = u32()
            need(count * 2)
            val indices = ShortArray(count) { u16().toShort() }
            Part.fromRaw(partName)?.let { parts[it] = indices }
        }

        val targets = HashMap<String, Target>()
        val targetCount = u32()
        repeat(targetCount) {
            val targetName = name()
            need(8)
            val scale = f32()
            val count = u32()
            need(count * 8)
            val indices = IntArray(count)
            val deltas = FloatArray(count * 3)
            for (i in 0 until count) {
                indices[i] = u16()
                val x = i16().toFloat()
                val y = i16().toFloat()
                val z = i16().toFloat()
                deltas[3 * i] = x * scale
                deltas[3 * i + 1] = y * scale
                deltas[3 * i + 2] = z * scale
            }
            targets[targetName] = Target(indices, deltas)
        }
        if (base.isEmpty() || parts[Part.SKIN] == null) throw LoadError()

        val sets = Array(vertexCount) { LinkedHashSet<Int>() }
        for (shorts in parts.values) {
            var t = 0
            while (t < shorts.size - 2) {
                val a = shorts[t].toInt() and 0xFFFF
                val b = shorts[t + 1].toInt() and 0xFFFF
                val c = shorts[t + 2].toInt() and 0xFFFF
                if (a >= vertexCount || b >= vertexCount || c >= vertexCount) throw LoadError()
                sets[a].add(b); sets[a].add(c)
                sets[b].add(a); sets[b].add(c)
                sets[c].add(a); sets[c].add(b)
                t += 3
            }
        }
        this.base = base
        this.parts = parts
        this.targets = targets
        this.neighbours = Array(vertexCount) { sets[it].toIntArray() }
    }

    // Shaping

    /** Vertices (decimetres, xyz triples) for MakeHuman-style macro parameters. */
    fun vertices(shape: BodyShapeParameters): FloatArray {
        val vertices = base.copyOf()
        for ((name, weight) in shape.targetWeights) {
            if (!(weight > 0.0001)) continue
            val target = targets[name] ?: continue
            val w = weight.toFloat()
            val deltas = target.deltas
            target.indices.forEachIndexed { i, index ->
                val v = 3 * index
                vertices[v] += deltas[3 * i] * w
                vertices[v + 1] += deltas[3 * i + 1] * w
                vertices[v + 2] += deltas[3 * i + 2] * w
            }
        }
        return vertices
    }

    /** Standing height of the mesh, in the mesh's units. */
    fun height(vertices: FloatArray): Float {
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        var i = 1
        while (i < vertices.size) {
            low = min(low, vertices[i])
            high = max(high, vertices[i])
            i += 3
        }
        return high - low
    }

    /** Enclosed volume of the body surface (cubic mesh units; litres for decimetres). */
    fun volume(vertices: FloatArray): Double {
        val surface = listOf(Part.SKIN, Part.SHIRT, Part.LEGGINGS, Part.SHOES, Part.SOLE, Part.HAIR, Part.BROWS)
        val count = vertices.size / 3
        var cx = 0.0
        var cy = 0.0
        var cz = 0.0
        for (i in 0 until count) {
            cx += vertices[3 * i].toDouble()
            cy += vertices[3 * i + 1].toDouble()
            cz += vertices[3 * i + 2].toDouble()
        }
        cx /= count.toDouble()
        cy /= count.toDouble()
        cz /= count.toDouble()
        var total = 0.0
        for (part in surface) {
            val indices = parts[part] ?: continue
            var t = 0
            while (t < indices.size - 2) {
                val a = 3 * (indices[t].toInt() and 0xFFFF)
                val b = 3 * (indices[t + 1].toInt() and 0xFFFF)
                val c = 3 * (indices[t + 2].toInt() and 0xFFFF)
                val ax = vertices[a] - cx; val ay = vertices[a + 1] - cy; val az = vertices[a + 2] - cz
                val bx = vertices[b] - cx; val by = vertices[b + 1] - cy; val bz = vertices[b + 2] - cz
                val qx = vertices[c] - cx; val qy = vertices[c + 1] - cy; val qz = vertices[c + 2] - cz
                // a · (b × c)
                total += ax * (by * qz - bz * qy) + ay * (bz * qx - bx * qz) + az * (bx * qy - by * qx)
                t += 3
            }
        }
        return abs(total) / 6
    }

    companion object {
        /** The body shipped in the jar's resources (`/vitalyn-body.bin`), or null if it is missing or unreadable. */
        val bundled: BodyModel? by lazy {
            try {
                BodyModel::class.java.getResourceAsStream("/vitalyn-body.bin")?.use { BodyModel(it.readBytes()) }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** MakeHuman macro values, each 0–1 (weight may go a little past 1 for very heavy bodies). */
data class BodyShapeParameters(
    val male: Double,
    val age: Double,
    val muscle: Double,
    val weight: Double
) {
    /** Target blend weights, following MakeHuman's macro modifier (apps/human.py). */
    val targetWeights: Map<String, Double>
        get() {
            val genders = listOf("male" to male, "female" to 1 - male)
            val old = max(0.0, age * 2 - 1)
            val ages = listOf("young" to 1 - old, "old" to old)
            val maxMuscle = max(0.0, muscle * 2 - 1)
            val minMuscle = max(0.0, 1 - muscle * 2)
            val muscles = listOf("minmuscle" to minMuscle, "averagemuscle" to 1 - maxMuscle - minMuscle, "maxmuscle" to maxMuscle)
            val maxWeight = max(0.0, weight * 2 - 1)
            val minWeight = max(0.0, 1 - weight * 2)
            val weights = listOf("minweight" to minWeight, "averageweight" to 1 - maxWeight - minWeight, "maxweight" to maxWeight)
            val out = LinkedHashMap<String, Double>()
            for ((g, gw) in genders) {
                if (!(gw > 0)) continue
                for ((a, aw) in ages) {
                    if (!(aw > 0)) continue
                    out["race-$g-$a"] = gw * aw
                    for ((m, mw) in muscles) {
                        if (mw == 0.0) continue
                        for ((w, ww) in weights) {
                            if (ww == 0.0) continue
                            out["universal-$g-$a-$m-$w"] = gw * aw * mw * ww
                        }
                    }
                }
            }
            return out
        }
}
