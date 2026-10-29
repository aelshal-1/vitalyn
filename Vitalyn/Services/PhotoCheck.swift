import UIKit
import Vision

/// On-device checks that a photo shows what the avatar step asked for.
enum PhotoCheck {
    enum Result: Equatable {
        case ok
        case problem(ar: String, en: String)
    }

    static func check(_ image: UIImage, for kind: PhotoKind) async -> Result {
        guard let cgImage = image.cgImage else { return .problem(ar: "الصورة مش واضحة", en: "Couldn't read the photo") }
        let orientation = CGImagePropertyOrientation(image.imageOrientation)
        switch kind {
        case .faceFront, .faceRight, .faceLeft:
            return await checkFace(cgImage, orientation: orientation, turned: kind != .faceFront)
        case .bodyFront, .bodySide:
            return await checkBody(cgImage, orientation: orientation)
        }
    }

    private static func checkFace(_ image: CGImage, orientation: CGImagePropertyOrientation, turned: Bool) async -> Result {
        let request = VNDetectFaceRectanglesRequest()
        let handler = VNImageRequestHandler(cgImage: image, orientation: orientation)
        do { try handler.perform([request]) } catch { return .problem(ar: "مقدرتش أحلل الصورة", en: "Couldn't analyze the photo") }
        let faces = request.results ?? []
        guard !faces.isEmpty else {
            return .problem(ar: "مش لاقي وش في الصورة — قرّب الكاميرا من وشك ونوّر المكان", en: "No face found — move closer and use good light")
        }
        guard faces.count == 1 else {
            return .problem(ar: "في أكتر من وش — لازم تكون لوحدك في الصورة", en: "More than one face — be alone in the photo")
        }
        let face = faces[0]
        if face.boundingBox.width < 0.2 {
            return .problem(ar: "وشك صغير أوي — قرّب الكاميرا", en: "Your face is too small — move closer")
        }
        let yaw = abs(face.yaw?.doubleValue ?? 0)
        if turned, yaw < 0.2 {
            return .problem(ar: "لف وشك لجنب أكتر شوية", en: "Turn your head a bit more to the side")
        }
        if !turned, yaw > 0.35 {
            return .problem(ar: "بص للكاميرا على طول", en: "Look straight at the camera")
        }
        return .ok
    }

    private static func checkBody(_ image: CGImage, orientation: CGImagePropertyOrientation) async -> Result {
        let request = VNDetectHumanBodyPoseRequest()
        let handler = VNImageRequestHandler(cgImage: image, orientation: orientation)
        do { try handler.perform([request]) } catch { return .problem(ar: "مقدرتش أحلل الصورة", en: "Couldn't analyze the photo") }
        let people = request.results ?? []
        guard !people.isEmpty else {
            return .problem(ar: "مش لاقي جسم في الصورة — ابعد عن الكاميرا شوية", en: "No body found — step back from the camera")
        }
        guard people.count == 1 else {
            return .problem(ar: "في أكتر من شخص — لازم تكون لوحدك", en: "More than one person — be alone in the photo")
        }
        let points = (try? people[0].recognizedPoints(.all)) ?? [:]
        func seen(_ name: VNHumanBodyPoseObservation.JointName) -> Bool { (points[name]?.confidence ?? 0) > 0.3 }
        let head = seen(.nose) || seen(.neck) || seen(.leftEar) || seen(.rightEar)
        let feet = seen(.leftAnkle) || seen(.rightAnkle)
        guard head, feet else {
            return .problem(ar: "لازم جسمك كله يظهر من الراس للرجلين — ابعد شوية", en: "Your whole body must show, head to feet — step back")
        }
        return .ok
    }
}

extension CGImagePropertyOrientation {
    init(_ orientation: UIImage.Orientation) {
        switch orientation {
        case .up: self = .up
        case .upMirrored: self = .upMirrored
        case .down: self = .down
        case .downMirrored: self = .downMirrored
        case .left: self = .left
        case .leftMirrored: self = .leftMirrored
        case .right: self = .right
        case .rightMirrored: self = .rightMirrored
        @unknown default: self = .up
        }
    }
}
