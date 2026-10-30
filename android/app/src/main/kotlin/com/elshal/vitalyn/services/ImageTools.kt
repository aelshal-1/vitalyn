package com.elshal.vitalyn.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageTools {
    /** JPEG sized for vision requests: long edge ≤ 1600 px keeps detail and request size reasonable. */
    fun jpeg(image: Bitmap, maxDimension: Int = 1600, quality: Int = 80): ByteArray {
        val resized = scaled(image, maxDimension)
        return ByteArrayOutputStream().use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }

    fun scaled(image: Bitmap, maxDimension: Int): Bitmap {
        val scale = min(1f, maxDimension.toFloat() / max(image.width, image.height))
        if (scale >= 1f) return image
        return Bitmap.createScaledBitmap(image, (image.width * scale).roundToInt(), (image.height * scale).roundToInt(), true)
    }

    fun decode(bytes: ByteArray?, maxDimension: Int = 1600): Bitmap? {
        if (bytes == null) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds, maxDimension) })
            ?: return null
        val rotation = runCatching { ExifInterface(bytes.inputStream()).rotationDegrees }.getOrDefault(0)
        return rotate(bitmap, rotation)
    }

    fun thumbnail(bytes: ByteArray?, maxDimension: Int = 400): Bitmap? = decode(bytes, maxDimension)?.let { scaled(it, maxDimension) }

    /** Reads a photo from a content URI (camera or picker), upright and at most `maxDimension` px. */
    fun load(context: Context, uri: Uri, maxDimension: Int = 2400): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds, maxDimension) }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        scaled(rotate(bitmap, rotation), maxDimension)
    }.getOrNull()

    private fun sampleSize(bounds: BitmapFactory.Options, maxDimension: Int): Int {
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        return sample
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
