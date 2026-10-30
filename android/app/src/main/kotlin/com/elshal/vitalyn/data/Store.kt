package com.elshal.vitalyn.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Vitalyn's on-device store (the iOS SwiftData container): all records in one JSON file,
 * photos and documents as separate files. Read it as a [StateFlow]; change it with [update].
 * Writes are batched and atomic, so a crash never leaves a half-written file.
 */
class VitalynStore(context: Context) {
    private val folder = File(context.filesDir, "store").apply { mkdirs() }
    private val file = File(folder, "vitalyn.json")
    private val blobs = File(folder, "blobs").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<StoreState> = _state.asStateFlow()

    /** The current value (for code outside composition). */
    val value: StoreState get() = _state.value

    init {
        @OptIn(FlowPreview::class)
        _state.drop(1).debounce(250).onEach { write(it) }.launchIn(scope)
    }

    /** Atomically replaces the state with `transform(current)`. Safe from any thread. */
    fun update(transform: (StoreState) -> StoreState) {
        _state.update(transform)
    }

    /** Writes pending changes now (call before the process may be killed, e.g. from a worker). */
    fun flush() = write(_state.value)

    private fun load(): StoreState = try {
        if (file.exists()) json.decodeFromString(StoreState.serializer(), file.readText()) else StoreState()
    } catch (error: Exception) {
        Log.e("VitalynStore", "Could not read the store; keeping a copy and starting fresh", error)
        file.copyTo(File(folder, "vitalyn-unreadable-${System.currentTimeMillis()}.json"), overwrite = true)
        StoreState()
    }

    @Synchronized
    private fun write(state: StoreState) {
        val temp = File(folder, "vitalyn.json.tmp")
        temp.writeText(json.encodeToString(StoreState.serializer(), state))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    // MARK: Blobs (photos, PDFs)

    /** Saves bytes as a new blob and returns its name (store the name in a record). */
    fun saveBlob(bytes: ByteArray, extension: String = "jpg"): String {
        val name = "${newId()}.$extension"
        File(blobs, name).writeBytes(bytes)
        return name
    }

    fun blob(name: String?): ByteArray? = name?.let { File(blobs, it).takeIf(File::exists)?.readBytes() }

    fun blobFile(name: String): File = File(blobs, name)

    /** Decodes an image blob, downsampled so its long edge is at most `maxDimension` px. */
    fun blobBitmap(name: String?, maxDimension: Int = 1200): Bitmap? {
        val target = name?.let { File(blobs, it) }?.takeIf(File::exists) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        return BitmapFactory.decodeFile(target.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun deleteBlob(name: String?) {
        name?.let { File(blobs, it).delete() }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}
