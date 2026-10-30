import SwiftUI
import SceneKit
import VitalynCore

/// Look of the generated body: skin tone and hair length.
struct AvatarStyle: Equatable {
    var skin: Int
    var longHair: Bool

    static let skinTones: [UInt32] = [0xF2D3BC, 0xE2B999, 0xC9966E, 0xA8754F, 0x7C5236, 0x4E3324]
    static let hair: UInt32 = 0x3B2A20
}

/// The keyframe bodies (start, now, goal), built once per change in the person's numbers.
struct AvatarBodies {
    var start: BodyMesh
    var now: BodyMesh
    var goal: BodyMesh

    static func make(start: BodyStats, now: BodyStats, goal: BodyStats) async -> AvatarBodies? {
        await Task.detached(priority: .userInitiated) {
            guard let model = BodyModel.bundled else { return nil }
            return AvatarBodies(
                start: BodyMesh.make(start, model: model),
                now: BodyMesh.make(now, model: model),
                goal: BodyMesh.make(goal, model: model)
            )
        }.value
    }

    /// The body along the journey: 0 = start, 0.5 = now, 1 = goal.
    func at(_ t: Double) -> BodyMesh {
        t <= 0.5 ? BodyMesh.blend(start, now, t: Float(t / 0.5)) : BodyMesh.blend(now, goal, t: Float((t - 0.5) / 0.5))
    }
}

/// Rotatable 3D body on a light stage, one figure or several side by side.
struct BodyAvatarView: UIViewRepresentable {
    var meshes: [BodyMesh]
    var style: AvatarStyle

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> SCNView {
        let view = SCNView()
        view.backgroundColor = UIColor(VColor.ground)
        view.antialiasingMode = .multisampling4X
        view.allowsCameraControl = true
        view.defaultCameraController.interactionMode = .orbitTurntable
        view.defaultCameraController.maximumVerticalAngle = 12
        view.defaultCameraController.minimumVerticalAngle = -8
        view.scene = context.coordinator.scene
        view.pointOfView = context.coordinator.cameraNode
        view.accessibilityLabel = "الأفاتار 3D — 3D body"
        context.coordinator.update(meshes: meshes, style: style, view: view)
        return view
    }

    func updateUIView(_ view: SCNView, context: Context) {
        context.coordinator.update(meshes: meshes, style: style, view: view)
    }

    final class Coordinator {
        let scene = SCNScene()
        let cameraNode = SCNNode()
        private var bodies: [SCNNode] = []
        private var materials: [BodyModel.Part: SCNMaterial] = [:]
        private var style: AvatarStyle?
        private var framedCount = 0

        init() {
            let camera = SCNCamera()
            camera.fieldOfView = 28
            camera.zNear = 0.05
            cameraNode.camera = camera
            scene.rootNode.addChildNode(cameraNode)

            let floor = SCNFloor()
            floor.reflectivity = 0
            floor.firstMaterial?.lightingModel = .shadowOnly
            scene.rootNode.addChildNode(SCNNode(geometry: floor))

            addLight(.ambient, intensity: 380, color: 0xFFFFFF)
            let key = addLight(.directional, intensity: 950, color: 0xFFF6EC, euler: SCNVector3(-0.75, 0.45, 0))
            key.light?.castsShadow = true
            key.light?.shadowMode = .deferred
            key.light?.shadowColor = UIColor.black.withAlphaComponent(0.22)
            key.light?.shadowRadius = 6
            key.light?.shadowSampleCount = 8
            addLight(.directional, intensity: 380, color: 0xEAF3FF, euler: SCNVector3(-0.3, -0.9, 0))
            addLight(.directional, intensity: 520, color: 0xFFFFFF, euler: SCNVector3(-0.2, .pi, 0))
        }

        @discardableResult
        private func addLight(_ type: SCNLight.LightType, intensity: CGFloat, color: UInt32, euler: SCNVector3 = SCNVector3Zero) -> SCNNode {
            let light = SCNLight()
            light.type = type
            light.intensity = intensity
            light.color = UIColor(Color(hex: color))
            let node = SCNNode()
            node.light = light
            node.eulerAngles = euler
            scene.rootNode.addChildNode(node)
            return node
        }

        func update(meshes: [BodyMesh], style: AvatarStyle, view: SCNView) {
            guard let model = BodyModel.bundled else { return }
            if style != self.style {
                materials = Self.materials(style)
                self.style = style
            }
            while bodies.count < meshes.count {
                let node = SCNNode()
                scene.rootNode.addChildNode(node)
                bodies.append(node)
            }
            while bodies.count > meshes.count {
                bodies.removeLast().removeFromParentNode()
            }
            let spacing: Float = 0.75
            let height = meshes.map(\.height).max() ?? 1.75
            for (i, mesh) in meshes.enumerated() {
                bodies[i].geometry = geometry(mesh, model: model, longHair: style.longHair)
                bodies[i].position = SCNVector3((Float(i) - Float(meshes.count - 1) / 2) * spacing, 0, 0)
            }
            // Frame the figures once per layout so the person's own rotation isn't reset by the slider.
            if framedCount != meshes.count {
                framedCount = meshes.count
                let width = Float(meshes.count - 1) * spacing + 0.8
                let distance = max(height * 2.6, width * 2.3)
                cameraNode.position = SCNVector3(0, height * 0.55, distance)
                cameraNode.look(at: SCNVector3(0, height * 0.5, 0))
                view.defaultCameraController.target = SCNVector3(0, height * 0.5, 0)
                view.pointOfView = cameraNode
            }
        }

        private func geometry(_ mesh: BodyMesh, model: BodyModel, longHair: Bool) -> SCNGeometry {
            let stride = MemoryLayout<SIMD3<Float>>.stride
            let positions = mesh.positions.withUnsafeBufferPointer { Data(buffer: $0) }
            let normals = mesh.normals.withUnsafeBufferPointer { Data(buffer: $0) }
            let sources = [
                SCNGeometrySource(data: positions, semantic: .vertex, vectorCount: mesh.positions.count, usesFloatComponents: true, componentsPerVector: 3, bytesPerComponent: 4, dataOffset: 0, dataStride: stride),
                SCNGeometrySource(data: normals, semantic: .normal, vectorCount: mesh.normals.count, usesFloatComponents: true, componentsPerVector: 3, bytesPerComponent: 4, dataOffset: 0, dataStride: stride)
            ]
            var elements: [SCNGeometryElement] = []
            var used: [SCNMaterial] = []
            for part in BodyModel.Part.allCases {
                guard let indices = model.parts[part], !indices.isEmpty, let material = materials[part] else { continue }
                if part == .longhair && !longHair { continue }
                elements.append(SCNGeometryElement(indices: indices, primitiveType: .triangles))
                used.append(material)
            }
            let geometry = SCNGeometry(sources: sources, elements: elements)
            geometry.materials = used
            return geometry
        }

        private static func materials(_ style: AvatarStyle) -> [BodyModel.Part: SCNMaterial] {
            let skin = AvatarStyle.skinTones[min(max(style.skin, 0), AvatarStyle.skinTones.count - 1)]
            func material(_ hex: UInt32, roughness: CGFloat, doubleSided: Bool = false) -> SCNMaterial {
                let m = SCNMaterial()
                m.lightingModel = .physicallyBased
                m.diffuse.contents = UIColor(Color(hex: hex))
                m.roughness.contents = roughness
                m.metalness.contents = 0.0
                m.isDoubleSided = doubleSided
                return m
            }
            return [
                .skin: material(skin, roughness: 0.62),
                .shirt: material(0xF7F7F5, roughness: 0.9),
                .leggings: material(0x1C1E22, roughness: 0.75),
                .shoes: material(0x23293A, roughness: 0.55),
                .sole: material(0xEDEDE8, roughness: 0.8),
                .hair: material(AvatarStyle.hair, roughness: 0.85),
                .longhair: material(AvatarStyle.hair, roughness: 0.85, doubleSided: true),
                .brows: material(0x2E2119, roughness: 0.9),
                .eyes: material(0xF4F2EE, roughness: 0.3),
                .iris: material(0x3A2A1E, roughness: 0.2)
            ]
        }
    }
}
