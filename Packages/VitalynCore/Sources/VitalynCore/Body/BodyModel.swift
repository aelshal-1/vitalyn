import Foundation
import simd

/// The CC0 MakeHuman body packed by tools/avatar/build_body.py, plus the maths that shapes it.
///
/// Units in the file are decimetres (y up, +z towards the viewer). Shapes come from MakeHuman's
/// macro targets: sex, age, muscle (tone) and weight, blended the same way MakeHuman blends them.
public struct BodyModel: Sendable {
    public enum Part: String, CaseIterable, Sendable {
        case skin, shirt, leggings, shoes, sole, hair, longhair, brows, eyes, iris
    }

    struct Target: Sendable {
        let indices: [UInt16]
        let deltas: [SIMD3<Float>]
    }

    public let base: [SIMD3<Float>]
    public let parts: [Part: [UInt16]]
    let targets: [String: Target]
    /// Each vertex's neighbours, for smoothing clothes and shoes.
    let neighbours: [[UInt16]]

    public static let bundled: BodyModel? = {
        guard let url = Bundle.module.url(forResource: "vitalyn-body", withExtension: "bin"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? BodyModel(data: data)
    }()

    public enum LoadError: Error { case badFormat }

    public init(data: Data) throws {
        var base: [SIMD3<Float>] = []
        var parts: [Part: [UInt16]] = [:]
        var targets: [String: Target] = [:]
        try data.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            var offset = 0
            func need(_ bytes: Int) throws {
                guard offset + bytes <= raw.count else { throw LoadError.badFormat }
            }
            func u32() throws -> Int {
                try need(4)
                defer { offset += 4 }
                return Int(UInt32(littleEndian: raw.loadUnaligned(fromByteOffset: offset, as: UInt32.self)))
            }
            // Callers check the length first.
            func f32() -> Float {
                defer { offset += 4 }
                return Float(bitPattern: UInt32(littleEndian: raw.loadUnaligned(fromByteOffset: offset, as: UInt32.self)))
            }
            func u16() -> UInt16 {
                defer { offset += 2 }
                return UInt16(littleEndian: raw.loadUnaligned(fromByteOffset: offset, as: UInt16.self))
            }
            func i16() -> Int16 { Int16(bitPattern: u16()) }
            func name() throws -> String {
                try need(1)
                let length = Int(raw[offset])
                try need(1 + length)
                defer { offset += 1 + length }
                return String(decoding: raw[(offset + 1)..<(offset + 1 + length)], as: UTF8.self)
            }

            guard raw.count > 8, raw[0] == 0x56, raw[1] == 0x42, raw[2] == 0x44, raw[3] == 0x59 else { throw LoadError.badFormat }
            offset = 8
            let vertexCount = try u32()
            try need(vertexCount * 12)
            base.reserveCapacity(vertexCount)
            for _ in 0..<vertexCount {
                let x = f32(), y = f32(), z = f32()
                base.append(SIMD3(x, y, z))
            }
            let partCount = try u32()
            for _ in 0..<partCount {
                let partName = try name()
                let count = try u32()
                try need(count * 2)
                let indices = (0..<count).map { _ in u16() }
                if let part = Part(rawValue: partName) { parts[part] = indices }
            }
            let targetCount = try u32()
            for _ in 0..<targetCount {
                let targetName = try name()
                try need(8)
                let scale = f32()
                let count = try u32()
                try need(count * 8)
                var indices: [UInt16] = []
                var deltas: [SIMD3<Float>] = []
                indices.reserveCapacity(count)
                deltas.reserveCapacity(count)
                for _ in 0..<count {
                    indices.append(u16())
                    let x = Float(i16()), y = Float(i16()), z = Float(i16())
                    deltas.append(SIMD3(x, y, z) * scale)
                }
                targets[targetName] = Target(indices: indices, deltas: deltas)
            }
        }
        guard !base.isEmpty, parts[.skin] != nil else { throw LoadError.badFormat }

        var sets = [Set<UInt16>](repeating: [], count: base.count)
        for indices in parts.values {
            for t in stride(from: 0, to: indices.count - 2, by: 3) {
                let a = indices[t], b = indices[t + 1], c = indices[t + 2]
                sets[Int(a)].formUnion([b, c])
                sets[Int(b)].formUnion([a, c])
                sets[Int(c)].formUnion([a, b])
            }
        }
        self.base = base
        self.parts = parts
        self.targets = targets
        self.neighbours = sets.map { Array($0) }
    }

    public var targetCount: Int { targets.count }

    // MARK: Shaping

    /// Vertices (decimetres) for MakeHuman-style macro parameters.
    public func vertices(for shape: BodyShapeParameters) -> [SIMD3<Float>] {
        var vertices = base
        for (name, weight) in shape.targetWeights where weight > 0.0001 {
            guard let target = targets[name] else { continue }
            let w = Float(weight)
            for (i, index) in target.indices.enumerated() {
                vertices[Int(index)] += target.deltas[i] * w
            }
        }
        return vertices
    }

    /// Standing height of the mesh, in the mesh's units.
    public func height(of vertices: [SIMD3<Float>]) -> Float {
        var low = Float.greatestFiniteMagnitude, high = -Float.greatestFiniteMagnitude
        for v in vertices {
            low = min(low, v.y)
            high = max(high, v.y)
        }
        return high - low
    }

    /// Enclosed volume of the body surface (cubic mesh units; litres for decimetres).
    public func volume(of vertices: [SIMD3<Float>]) -> Double {
        let surface: [Part] = [.skin, .shirt, .leggings, .shoes, .sole, .hair, .brows]
        var center = SIMD3<Double>.zero
        for v in vertices { center += SIMD3<Double>(v) }
        center /= Double(vertices.count)
        var total = 0.0
        for part in surface {
            guard let indices = parts[part] else { continue }
            for t in stride(from: 0, to: indices.count - 2, by: 3) {
                let a = SIMD3<Double>(vertices[Int(indices[t])]) - center
                let b = SIMD3<Double>(vertices[Int(indices[t + 1])]) - center
                let c = SIMD3<Double>(vertices[Int(indices[t + 2])]) - center
                total += simd_dot(a, simd_cross(b, c))
            }
        }
        return abs(total) / 6
    }
}

/// MakeHuman macro values, each 0–1 (weight may go a little past 1 for very heavy bodies).
public struct BodyShapeParameters: Equatable, Sendable {
    public var male: Double
    public var age: Double
    public var muscle: Double
    public var weight: Double

    public init(male: Double, age: Double, muscle: Double, weight: Double) {
        self.male = male
        self.age = age
        self.muscle = muscle
        self.weight = weight
    }

    /// Target blend weights, following MakeHuman's macro modifier (apps/human.py).
    var targetWeights: [String: Double] {
        let genders = [("male", male), ("female", 1 - male)]
        let old = max(0, age * 2 - 1)
        let ages = [("young", 1 - old), ("old", old)]
        let maxMuscle = max(0, muscle * 2 - 1), minMuscle = max(0, 1 - muscle * 2)
        let muscles = [("minmuscle", minMuscle), ("averagemuscle", 1 - maxMuscle - minMuscle), ("maxmuscle", maxMuscle)]
        let maxWeight = max(0, weight * 2 - 1), minWeight = max(0, 1 - weight * 2)
        let weights = [("minweight", minWeight), ("averageweight", 1 - maxWeight - minWeight), ("maxweight", maxWeight)]
        var out: [String: Double] = [:]
        for (g, gw) in genders where gw > 0 {
            for (a, aw) in ages where aw > 0 {
                out["race-\(g)-\(a)"] = gw * aw
                for (m, mw) in muscles where mw != 0 {
                    for (w, ww) in weights where ww != 0 {
                        out["universal-\(g)-\(a)-\(m)-\(w)"] = gw * aw * mw * ww
                    }
                }
            }
        }
        return out
    }
}
