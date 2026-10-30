package com.elshal.vitalyn.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.elshal.vitalyn.services.ImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private fun cameraUri(context: Context): Uri {
    val folder = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(folder, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

/**
 * Returns a function that opens the camera (asking for the camera permission first) and delivers the photo,
 * upright and at most 2400 px. `front` opens the selfie camera where the camera app supports the hint.
 */
@Composable
fun rememberCameraCapture(front: Boolean = false, onImage: (Bitmap) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(object : ActivityResultContracts.TakePicture() {
        override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input).apply {
            if (front) {
                putExtra("android.intent.extras.CAMERA_FACING", 1)
                putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            }
        }
    }) { saved ->
        val uri = pending?.let(Uri::parse)
        pending = null
        if (saved && uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) { ImageTools.load(context, uri) }?.let(onImage)
            }
        }
    }
    val launch = {
        val uri = cameraUri(context)
        pending = uri.toString()
        camera.launch(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launch()
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launch()
        else permission.launch(Manifest.permission.CAMERA)
    }
}

/** Returns a function that opens the photo picker and delivers the chosen image (upright, ≤ 2400 px). */
@Composable
fun rememberPhotoPicker(onImage: (Bitmap) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { ImageTools.load(context, uri) }?.let(onImage) }
    }
    return { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** A picked file's bytes and MIME type. */
class PickedFile(val bytes: ByteArray, val mimeType: String) {
    val isPDF: Boolean get() = mimeType == "application/pdf"
}

/** Returns a function that opens the system file picker for the given MIME types (e.g. PDF) and delivers the bytes. */
@Composable
fun rememberFilePicker(mimeTypes: Array<String> = arrayOf("application/pdf", "image/*"), onFile: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { PickedFile(it, type) }
                }
                file?.let(onFile)
            }
        }
    }
    return { picker.launch(mimeTypes) }
}

/** "Camera" and "Photos" buttons side by side; delivers a Bitmap. */
@Composable
fun ImageSourceButtons(
    modifier: Modifier = Modifier,
    cameraAr: String = "صوّر",
    cameraEn: String = "Camera",
    front: Boolean = false,
    onImage: (Bitmap) -> Unit,
) {
    val openCamera = rememberCameraCapture(front, onImage)
    val openPhotos = rememberPhotoPicker(onImage)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(onClick = openCamera, modifier = Modifier.weight(1f)) {
            BiLabel(cameraAr, cameraEn, icon = Icons.Rounded.PhotoCamera)
        }
        SecondaryButton(onClick = openPhotos, modifier = Modifier.weight(1f).fillMaxWidth().then(Modifier)) {
            BiLabel("من الصور", "Photos", icon = Icons.Rounded.PhotoLibrary)
        }
    }
}

/** Shares a PNG through the Android share sheet (WhatsApp and other apps). */
fun shareImage(context: Context, bitmap: Bitmap, text: String? = null) {
    val folder = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(folder, "vitalyn-${System.currentTimeMillis()}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .apply { text?.let { putExtra(Intent.EXTRA_TEXT, it) } }
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Shares plain text through the Android share sheet. */
fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Opens a web link (YouTube searches, console.anthropic.com…). */
fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
