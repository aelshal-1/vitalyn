package com.elshal.vitalyn.features.body

import android.opengl.GLSurfaceView
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elshal.vitalyn.core.BodyMesh
import com.elshal.vitalyn.core.BodyModel
import com.elshal.vitalyn.core.BodyStats
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs

/** Look of the generated body: skin tone and hair length. */
data class AvatarStyle(val skin: Int, val longHair: Boolean) {
    companion object {
        val skinTones: IntArray = intArrayOf(0xF2D3BC, 0xE2B999, 0xC9966E, 0xA8754F, 0x7C5236, 0x4E3324)
        const val HAIR: Int = 0x3B2A20

        /** The iOS @AppStorage keys (same names and defaults). */
        const val SKIN_KEY = "avatar-skin"
        const val LONG_HAIR_KEY = "avatar-long-hair"
    }
}

/** The keyframe bodies (start, now, goal), built once per change in the person's numbers. */
class AvatarBodies(val start: BodyMesh, val now: BodyMesh, val goal: BodyMesh) {
    /** The body along the journey: 0 = start, 0.5 = now, 1 = goal. */
    fun at(t: Double): BodyMesh =
        if (t <= 0.5) BodyMesh.blend(start, now, (t / 0.5).toFloat())
        else BodyMesh.blend(now, goal, ((t - 0.5) / 0.5).toFloat())

    companion object {
        suspend fun make(start: BodyStats, now: BodyStats, goal: BodyStats): AvatarBodies? = withContext(Dispatchers.Default) {
            val model = BodyModel.bundled ?: return@withContext null
            AvatarBodies(
                start = BodyMesh.make(start, model),
                now = BodyMesh.make(now, model),
                goal = BodyMesh.make(goal, model),
            )
        }
    }
}

/** Preset camera angles around the body. */
private enum class AvatarAngle(val ar: String, val en: String, val yaw: Float) {
    FRONT("قدّام", "Front", 0f),
    SIDE("جنب", "Side", (PI / 2).toFloat()),
    BACK("ضهر", "Back", PI.toFloat()),
}

private val TWO_PI: Float = (2 * PI).toFloat()

/** Angle wrapped into (-π, π]. */
private fun wrap(angle: Float): Float {
    var a = angle % TWO_PI
    if (a <= -PI.toFloat()) a += TWO_PI
    if (a > PI.toFloat()) a -= TWO_PI
    return a
}

/** What the UI thread last sent to the renderer, so recompositions don't re-upload unchanged meshes. */
private class Sent {
    var meshes: List<BodyMesh> = emptyList()
    var style: AvatarStyle? = null
    var yaw = Float.NaN
    var zoom = Float.NaN
    var view: GLSurfaceView? = null
}

private fun sameMeshes(a: List<BodyMesh>, b: List<BodyMesh>): Boolean =
    a.size == b.size && a.indices.all { a[it] === b[it] }

/**
 * Rotatable 3D body on a light stage, one figure or several side by side (OpenGL ES 2.0).
 * Horizontal drag turns it 360°, pinch zooms, and the front / side / back buttons jump to an angle.
 * Pass stable [BodyMesh] instances (remembered), since a new instance means a new upload.
 */
@Composable
fun BodyAvatar(meshes: List<BodyMesh>, style: AvatarStyle, modifier: Modifier = Modifier) {
    val renderer = remember { BodyRenderer() }
    val sent = remember { Sent() }
    val scope = rememberCoroutineScope()
    var yaw by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var turning by remember { mutableStateOf<Job?>(null) }

    // GLSurfaceView must follow the activity's pause/resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sent.view?.onResume()
                Lifecycle.Event.ON_PAUSE -> sent.view?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun turnTo(angle: AvatarAngle) {
        turning?.cancel()
        val from = yaw
        val to = from + wrap(angle.yaw - from)
        turning = scope.launch {
            animate(from, to, animationSpec = tween(durationMillis = 450)) { value, _ -> yaw = value }
            yaw = wrap(to)
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { context ->
                GLSurfaceView(context).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(BodyRenderer.ConfigChooser())
                    preserveEGLContextOnPause = true
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    contentDescription = "الأفاتار 3D — 3D body"
                    sent.view = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view ->
                view.queueEvent { renderer.release() }
                if (sent.view === view) sent.view = null
                sent.meshes = emptyList()
                sent.style = null
                sent.yaw = Float.NaN
                sent.zoom = Float.NaN
            },
            update = { view ->
                val model = BodyModel.bundled
                if (model != null && !sameMeshes(meshes, sent.meshes)) {
                    val list = meshes.toList()
                    sent.meshes = list
                    view.queueEvent { renderer.setScene(model, list) }
                }
                if (style != sent.style) {
                    sent.style = style
                    view.queueEvent { renderer.setStyle(style) }
                }
                val currentYaw = yaw
                val currentZoom = zoom
                if (currentYaw != sent.yaw || currentZoom != sent.zoom) {
                    sent.yaw = currentYaw
                    sent.zoom = currentZoom
                    view.queueEvent { renderer.setCamera(currentYaw, currentZoom) }
                }
                view.requestRender()
            },
        )

        // Touch layer above the GL view: horizontal drag turns, pinch zooms; vertical drags scroll the page.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTurnAndZoom(
                        onTurn = { dx ->
                            turning?.cancel()
                            yaw -= dx * 0.0105f
                        },
                        onZoom = { factor -> zoom = (zoom * factor).coerceIn(0.6f, 2.5f) },
                    )
                },
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (angle in AvatarAngle.entries) {
                val selected = abs(wrap(yaw - angle.yaw)) < 0.05f
                AngleButton(angle, selected) { turnTo(angle) }
            }
        }
    }
}

@Composable
private fun AngleButton(angle: AvatarAngle, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .background(if (selected) VColor.ink else VColor.surface, shape)
            .border(1.dp, if (selected) VColor.ink else VColor.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "${angle.ar} — ${angle.en}"
                this.selected = selected
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = if (selected) VColor.nightText else VColor.ink
        Text(angle.ar, style = VFont.style(12f, 600, color))
        Text(angle.en, style = VFont.style(9f, 400, if (selected) VColor.nightMuted else VColor.muted))
    }
}

/**
 * One finger: once past the touch slop, a mostly horizontal drag turns the model (and is consumed);
 * a vertical one is left alone so the page still scrolls. Two fingers: pinch to zoom, pan to turn.
 */
private suspend fun PointerInputScope.detectTurnAndZoom(onTurn: (Float) -> Unit, onZoom: (Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var decided = false
        var horizontal = false
        var travel = Offset.Zero
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val factor = event.calculateZoom()
                if (factor != 1f) onZoom(factor)
                val pan = event.calculatePan()
                if (pan.x != 0f) onTurn(pan.x)
                event.changes.forEach { it.consume() }
                decided = true
                horizontal = true
            } else if (pressed.size == 1) {
                val change = pressed[0]
                val delta = change.positionChange()
                if (!decided) {
                    travel += delta
                    if (travel.getDistance() > viewConfiguration.touchSlop) {
                        decided = true
                        horizontal = abs(travel.x) > abs(travel.y)
                    }
                }
                if (decided && horizontal && !change.isConsumed) {
                    onTurn(delta.x)
                    change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
