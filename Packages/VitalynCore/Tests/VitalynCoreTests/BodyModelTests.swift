import Foundation
import Testing
@testable import VitalynCore

@Suite struct BodyModelTests {
    let model = BodyModel.bundled!

    @Test func loadsBundledBody() {
        #expect(model.base.count > 13_000)
        #expect(model.targetCount == 36)
        for part in BodyModel.Part.allCases {
            #expect((model.parts[part]?.count ?? 0) > 0, "missing \(part)")
            #expect((model.parts[part]?.count ?? 0) % 3 == 0)
        }
        #expect(model.parts.values.allSatisfy { $0.allSatisfy { Int($0) < model.base.count } })
    }

    @Test func rejectsOtherFiles() {
        #expect(throws: BodyModel.LoadError.self) { try BodyModel(data: Data("not a body".utf8)) }
    }

    @Test func macroWeightsBlendLikeMakeHuman() {
        let shape = BodyShapeParameters(male: 1, age: 0.75, muscle: 0.25, weight: 0.75)
        let weights = shape.targetWeights
        let universal = weights.filter { $0.key.hasPrefix("universal-") }.values.reduce(0, +)
        #expect(abs(universal - 1) < 1e-9)
        #expect(abs((weights["universal-male-young-minmuscle-maxweight"] ?? 0) - 0.5 * 0.5 * 0.5) < 1e-9)
        #expect(weights.keys.allSatisfy { !$0.contains("female") })
    }

    @Test(arguments: [
        BodyStats(sex: .male, ageYears: 35, heightCm: 175, weightKg: 110, bodyFatPercent: 31.2),
        BodyStats(sex: .male, ageYears: 30, heightCm: 180, weightKg: 75, bodyFatPercent: 12),
        BodyStats(sex: .female, ageYears: 30, heightCm: 162, weightKg: 60),
        BodyStats(sex: .female, ageYears: 45, heightCm: 160, weightKg: 95)
    ])
    func fitMatchesWeightAndHeight(stats: BodyStats) {
        let fit = BodyFit.fit(stats, model: model)
        #expect(abs(fit.modelMassKg - stats.weightKg) / stats.weightKg < 0.02)
        let mesh = BodyMesh.make(stats, model: model)
        // The hair adds a few millimetres on top.
        #expect(abs(Double(mesh.height) * 100 - stats.heightCm) < 1.5)
        #expect(mesh.positions.allSatisfy { $0.y > -0.01 })
    }

    @Test func heavierMeansSofterAndBigger() {
        let lean = BodyStats(sex: .male, ageYears: 35, heightCm: 175, weightKg: 75, bodyFatPercent: 15)
        let heavy = BodyStats(sex: .male, ageYears: 35, heightCm: 175, weightKg: 105, bodyFatPercent: 30)
        let a = BodyFit.fit(lean, model: model), b = BodyFit.fit(heavy, model: model)
        #expect(b.parameters.muscle < a.parameters.muscle)
        let volumeA = model.volume(of: model.vertices(for: a.parameters)) * Double(pow(a.scale * 10, 3))
        let volumeB = model.volume(of: model.vertices(for: b.parameters)) * Double(pow(b.scale * 10, 3))
        #expect(volumeB > volumeA * 1.3)
    }

    @Test func estimatesBodyFatWithoutAScan() {
        let stats = BodyStats(sex: .male, ageYears: 35, heightCm: 175, weightKg: 80)
        #expect(abs(stats.effectiveBodyFatPercent - 23.2) < 0.5)
        #expect(BodyFit.makeHumanAge(20) == 0.5)
        #expect(BodyFit.makeHumanAge(90) == 1)
    }

    @Test func blendsBetweenShapes() {
        let a = BodyMesh.make(BodyStats(sex: .female, ageYears: 30, heightCm: 165, weightKg: 80), model: model)
        let b = BodyMesh.make(BodyStats(sex: .female, ageYears: 30, heightCm: 165, weightKg: 62), model: model)
        let mid = BodyMesh.blend(a, b, t: 0.5)
        #expect(mid.positions.count == a.positions.count)
        #expect(abs(mid.positions[100].x - (a.positions[100].x + b.positions[100].x) / 2) < 1e-5)
        #expect(BodyMesh.blend(a, b, t: 0).positions == a.positions)
    }
}
