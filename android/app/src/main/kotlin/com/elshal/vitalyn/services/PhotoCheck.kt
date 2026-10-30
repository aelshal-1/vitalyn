package com.elshal.vitalyn.services

import android.graphics.Bitmap
import com.elshal.vitalyn.data.PhotoKind
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

/** On-device checks (ML Kit) that a photo shows what the avatar step asked for. */
object PhotoCheck {
    sealed class Result {
        data object Ok : Result()
        data class Problem(val ar: String, val en: String) : Result()
    }

    suspend fun check(image: Bitmap, kind: PhotoKind): Result = when (kind) {
        PhotoKind.FACE_FRONT, PhotoKind.FACE_RIGHT, PhotoKind.FACE_LEFT -> checkFace(image, turned = kind != PhotoKind.FACE_FRONT)
        PhotoKind.BODY_FRONT, PhotoKind.BODY_SIDE -> checkBody(image)
    }

    private suspend fun checkFace(image: Bitmap, turned: Boolean): Result {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).build(),
        )
        val faces = suspendCancellableCoroutine { continuation ->
            detector.process(InputImage.fromBitmap(image, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        }
        detector.close()
        faces ?: return Result.Problem("مقدرتش أحلل الصورة", "Couldn't analyze the photo")
        if (faces.isEmpty()) return Result.Problem("مش لاقي وش في الصورة — قرّب الكاميرا من وشك ونوّر المكان", "No face found — move closer and use good light")
        if (faces.size > 1) return Result.Problem("في أكتر من وش — لازم تكون لوحدك في الصورة", "More than one face — be alone in the photo")
        val face = faces[0]
        if (face.boundingBox.width().toFloat() / image.width < 0.2f) {
            return Result.Problem("وشك صغير أوي — قرّب الكاميرا", "Your face is too small — move closer")
        }
        // ML Kit gives yaw in degrees; the iOS limits were 0.2 and 0.35 radians.
        val yaw = abs(face.headEulerAngleY)
        if (turned && yaw < 11.5f) return Result.Problem("لف وشك لجنب أكتر شوية", "Turn your head a bit more to the side")
        if (!turned && yaw > 20f) return Result.Problem("بص للكاميرا على طول", "Look straight at the camera")
        return Result.Ok
    }

    private suspend fun checkBody(image: Bitmap): Result {
        val detector = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build())
        val pose = suspendCancellableCoroutine { continuation ->
            detector.process(InputImage.fromBitmap(image, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        }
        detector.close()
        pose ?: return Result.Problem("مقدرتش أحلل الصورة", "Couldn't analyze the photo")
        if (pose.allPoseLandmarks.isEmpty()) {
            return Result.Problem("مش لاقي جسم في الصورة — ابعد عن الكاميرا شوية", "No body found — step back from the camera")
        }
        fun seen(type: Int) = (pose.getPoseLandmark(type)?.inFrameLikelihood ?: 0f) > 0.3f
        val head = seen(PoseLandmark.NOSE) || seen(PoseLandmark.LEFT_EAR) || seen(PoseLandmark.RIGHT_EAR)
        val feet = seen(PoseLandmark.LEFT_ANKLE) || seen(PoseLandmark.RIGHT_ANKLE)
        if (!head || !feet) {
            return Result.Problem("لازم جسمك كله يظهر من الراس للرجلين — ابعد شوية", "Your whole body must show, head to feet — step back")
        }
        return Result.Ok
    }
}
