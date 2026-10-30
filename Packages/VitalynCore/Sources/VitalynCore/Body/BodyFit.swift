import Foundation
import simd

/// What we know about a body at one moment (start, now or goal).
public struct BodyStats: Equatable, Sendable {
    public var sex: Sex
    public var ageYears: Int
    public var heightCm: Double
    public var weightKg: Double
    /// Measured (InBody) when available; otherwise estimated from BMI.
    public var bodyFatPercent: Double?

    public init(sex: Sex, ageYears: Int, heightCm: Double, weightKg: Double, bodyFatPercent: Double? = nil) {
        self.sex = sex
        self.ageYears = ageYears
        self.heightCm = heightCm
        self.weightKg = weightKg
        self.bodyFatPercent = bodyFatPercent
    }

    public var bmi: Double {
        let metres = heightCm / 100
        return weightKg / (metres * metres)
    }

    /// Measured body fat, or the Deurenberg (1991) estimate from BMI, age and sex.
    public var effectiveBodyFatPercent: Double {
        if let bodyFatPercent, bodyFatPercent > 2, bodyFatPercent < 75 { return bodyFatPercent }
        let estimate = 1.2 * bmi + 0.23 * Double(ageYears) - 10.8 * (sex == .male ? 1 : 0) - 5.4
        return min(60, max(sex == .male ? 6 : 12, estimate))
    }

    /// Fat-free mass index (kg/m²).
    public var ffmi: Double {
        let metres = heightCm / 100
        return weightKg * (1 - effectiveBodyFatPercent / 100) / (metres * metres)
    }
}

/// The fitted shape for some stats.
public struct BodyFitResult: Sendable {
    public var parameters: BodyShapeParameters
    /// Metres per mesh unit, so the mesh stands exactly at the person's height.
    public var scale: Float
    /// The mass the fitted mesh would have at the person's body density.
    public var modelMassKg: Double
}

public enum BodyFit {
    /// MakeHuman age: 25 years = 0.5, 90 years = 1 (younger adults use the young-adult body).
    public static func makeHumanAge(_ years: Int) -> Double {
        min(1, max(0.5, 0.5 + Double(years - 25) / 130))
    }

    /// Tone, mostly from body fat (MakeHuman's low "muscle" is a soft, higher-fat body), nudged by lean mass.
    public static func muscle(for stats: BodyStats) -> Double {
        let (ffmiAverage, fatAverage) = stats.sex == .male ? (19.0, 18.0) : (15.5, 27.0)
        let tone = 0.5 - 0.03 * (stats.effectiveBodyFatPercent - fatAverage) + 0.02 * (stats.ffmi - ffmiAverage)
        return min(0.95, max(0.05, tone))
    }

    /// Body density (kg/L) from body fat, inverting Siri's equation.
    public static func density(bodyFatPercent: Double) -> Double {
        495 / (bodyFatPercent + 450)
    }

    /// Finds the MakeHuman weight value whose mesh, at the person's height and density, matches their weight.
    public static func fit(_ stats: BodyStats, model: BodyModel) -> BodyFitResult {
        var parameters = BodyShapeParameters(
            male: stats.sex == .male ? 1 : 0,
            age: makeHumanAge(stats.ageYears),
            muscle: muscle(for: stats),
            weight: 0.5
        )
        let density = density(bodyFatPercent: stats.effectiveBodyFatPercent)
        let heightDm = Float(stats.heightCm / 10)

        func evaluate(_ weight: Double) -> (mass: Double, scale: Float) {
            parameters.weight = weight
            let vertices = model.vertices(for: parameters)
            let scale = heightDm / model.height(of: vertices)
            // Mesh volume includes ~1 L of air in the lungs.
            let litres = model.volume(of: vertices) * Double(scale * scale * scale)
            return ((litres - 1) * density, scale / 10)
        }

        // MakeHuman's weight targets add little mass on a toned body, so very heavy bodies get a softer tone
        // rather than pushing the targets far past their range.
        while parameters.muscle > 0.05, evaluate(1.15).mass < stats.weightKg {
            parameters.muscle = max(0.05, parameters.muscle - 0.05)
        }
        var low = -0.2, high = 1.4
        for _ in 0..<16 {
            let mid = (low + high) / 2
            if evaluate(mid).mass < stats.weightKg { low = mid } else { high = mid }
        }
        let weight = (low + high) / 2
        let final = evaluate(weight)
        parameters.weight = weight
        return BodyFitResult(parameters: parameters, scale: final.scale, modelMassKg: final.mass)
    }
}

/// Display-ready geometry in metres: feet on y = 0, centred, with smoothed clothes and shoes.
public struct BodyMesh: Sendable {
    public var positions: [SIMD3<Float>]
    public var normals: [SIMD3<Float>]

    public var height: Float {
        positions.reduce(0) { max($0, $1.y) }
    }

    public static func make(_ stats: BodyStats, model: BodyModel) -> BodyMesh {
        let fit = BodyFit.fit(stats, model: model)
        var positions = model.vertices(for: fit.parameters).map { $0 * fit.scale }

        var low = SIMD3<Float>(repeating: .greatestFiniteMagnitude), high = -low
        for p in positions {
            low = simd_min(low, p)
            high = simd_max(high, p)
        }
        let offset = SIMD3<Float>((low.x + high.x) / 2, low.y, (low.z + high.z) / 2)
        for i in positions.indices { positions[i] -= offset }

        // Fabric hides small anatomy; shoes are smooth and a little bigger than the foot; hair has some volume.
        smooth(&positions, model: model, parts: [.shirt], iterations: 3)
        smooth(&positions, model: model, parts: [.shoes, .sole], iterations: 6)
        var normals = vertexNormals(positions, model: model)
        inflate(&positions, normals: normals, model: model, parts: [.shoes, .sole], by: 0.006)
        inflate(&positions, normals: normals, model: model, parts: [.hair], by: 0.004)
        normals = vertexNormals(positions, model: model)
        return BodyMesh(positions: positions, normals: normals)
    }

    /// Linear blend between two shapes of the same body (used by the journey slider).
    public static func blend(_ a: BodyMesh, _ b: BodyMesh, t: Float) -> BodyMesh {
        let t = min(1, max(0, t))
        return BodyMesh(
            positions: zip(a.positions, b.positions).map { simd_mix($0, $1, SIMD3(repeating: t)) },
            normals: zip(a.normals, b.normals).map { simd_normalize(simd_mix($0, $1, SIMD3(repeating: t))) }
        )
    }

    static func vertices(of parts: [BodyModel.Part], model: BodyModel) -> Set<Int> {
        var set = Set<Int>()
        for part in parts { model.parts[part]?.forEach { set.insert(Int($0)) } }
        return set
    }

    static func smooth(_ positions: inout [SIMD3<Float>], model: BodyModel, parts: [BodyModel.Part], iterations: Int) {
        let inside = vertices(of: parts, model: model)
        // Keep the edges of the region fixed so seams don't pull away from the skin.
        let others = vertices(of: BodyModel.Part.allCases.filter { !parts.contains($0) }, model: model)
        let movable = inside.subtracting(others)
        for _ in 0..<iterations {
            let current = positions
            for i in movable {
                let ring = model.neighbours[i]
                guard !ring.isEmpty else { continue }
                var sum = SIMD3<Float>.zero
                for n in ring { sum += current[Int(n)] }
                positions[i] = simd_mix(current[i], sum / Float(ring.count), SIMD3(repeating: 0.5))
            }
        }
    }

    static func inflate(_ positions: inout [SIMD3<Float>], normals: [SIMD3<Float>], model: BodyModel, parts: [BodyModel.Part], by distance: Float) {
        for i in vertices(of: parts, model: model) {
            positions[i] += normals[i] * distance
        }
    }

    static func vertexNormals(_ positions: [SIMD3<Float>], model: BodyModel) -> [SIMD3<Float>] {
        var normals = [SIMD3<Float>](repeating: .zero, count: positions.count)
        for indices in model.parts.values {
            for t in stride(from: 0, to: indices.count - 2, by: 3) {
                let a = Int(indices[t]), b = Int(indices[t + 1]), c = Int(indices[t + 2])
                let n = simd_cross(positions[b] - positions[a], positions[c] - positions[a])
                normals[a] += n
                normals[b] += n
                normals[c] += n
            }
        }
        return normals.map { simd_length($0) > 0 ? simd_normalize($0) : SIMD3(0, 1, 0) }
    }
}
