package com.elshal.vitalyn.features.body

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.elshal.vitalyn.core.BodyMesh
import com.elshal.vitalyn.core.BodyModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * OpenGL ES 2.0 renderer for the generated bodies (the SceneKit scene of the iOS `BodyAvatarView`).
 *
 * Every field is owned by the GL thread: the UI thread only talks to it through
 * `GLSurfaceView.queueEvent { … }` (see [BodyAvatar]). Vertex data is kept on the CPU side as well,
 * so it is uploaded again whenever the EGL context is recreated.
 */
internal class BodyRenderer : GLSurfaceView.Renderer {
    private class Material(val color: FloatArray, val roughness: Float, val doubleSided: Boolean = false)

    private class Range(val part: BodyModel.Part, val offsetBytes: Int, val count: Int)

    // Scene (set through queueEvent).
    private var model: BodyModel? = null
    private var meshes: List<BodyMesh> = emptyList()
    private var style = AvatarStyle(skin = 2, longHair = false)
    private var materials: Map<BodyModel.Part, Material> = materialsFor(style)
    private var yaw = 0f
    private var zoom = 1f

    private var indicesDirty = true
    private var meshesDirty = true

    // GL objects (valid for the current EGL context only).
    private var program = 0
    private var aPosition = -1
    private var aNormal = -1
    private var uMVP = -1
    private var uModel = -1
    private var uColor = -1
    private var uEye = -1
    private var uRoughness = -1
    private var uDoubleSided = -1
    private var indexBuffer = 0
    private var ranges: List<Range> = emptyList()
    /** Per mesh: [position buffer, normal buffer]. */
    private val vertexBuffers = mutableListOf<IntArray>()
    private val cpuBuffers = mutableListOf<FloatBuffer>()

    private var viewWidth = 1
    private var viewHeight = 1

    // Framing is done once per layout so the person's own rotation and zoom survive the journey slider.
    private var framedCount = -1
    private var framedHeight = 1.75f

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvp = FloatArray(16)

    // Called on the GL thread (queueEvent).

    fun setScene(model: BodyModel, meshes: List<BodyMesh>) {
        if (model !== this.model) {
            this.model = model
            indicesDirty = true
        }
        this.meshes = meshes
        meshesDirty = true
    }

    fun setStyle(style: AvatarStyle) {
        if (style == this.style) return
        this.style = style
        materials = materialsFor(style)
    }

    fun setCamera(yaw: Float, zoom: Float) {
        this.yaw = yaw
        this.zoom = zoom
    }

    /** Frees the GL objects; call on the GL thread before the view goes away. */
    fun release() {
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
        if (indexBuffer != 0) GLES20.glDeleteBuffers(1, intArrayOf(indexBuffer), 0)
        indexBuffer = 0
        for (buffers in vertexBuffers) GLES20.glDeleteBuffers(2, buffers, 0)
        vertexBuffers.clear()
        cpuBuffers.clear()
        indicesDirty = true
        meshesDirty = true
    }

    // GLSurfaceView.Renderer

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context: every old handle is gone.
        program = 0
        indexBuffer = 0
        vertexBuffers.clear()
        indicesDirty = true
        meshesDirty = true

        GLES20.glClearColor(GROUND[0], GROUND[1], GROUND[2], 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        // Winding isn't guaranteed across parts (and long hair is double-sided), so draw both faces.
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        program = buildProgram()
        if (program != 0) {
            aPosition = GLES20.glGetAttribLocation(program, "aPosition")
            aNormal = GLES20.glGetAttribLocation(program, "aNormal")
            uMVP = GLES20.glGetUniformLocation(program, "uMVP")
            uModel = GLES20.glGetUniformLocation(program, "uModel")
            uColor = GLES20.glGetUniformLocation(program, "uColor")
            uEye = GLES20.glGetUniformLocation(program, "uEye")
            uRoughness = GLES20.glGetUniformLocation(program, "uRoughness")
            uDoubleSided = GLES20.glGetUniformLocation(program, "uDoubleSided")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = max(1, width)
        viewHeight = max(1, height)
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val model = model ?: return
        if (program == 0) return
        if (indicesDirty) uploadIndices(model)
        if (meshesDirty) uploadMeshes()
        if (meshes.isEmpty() || indexBuffer == 0) return

        // Camera: the iOS framing (28° vertical field of view, looking at mid-height from a little above).
        if (framedCount != meshes.size) {
            framedCount = meshes.size
            framedHeight = meshes.maxOf { it.height }.takeIf { it > 0.1f } ?: 1.75f
        }
        val height = framedHeight
        val width = (meshes.size - 1) * SPACING + 0.8f
        val aspect = viewWidth.toFloat() / viewHeight.toFloat()
        val tanHalf = tan(Math.toRadians(FIELD_OF_VIEW / 2.0)).toFloat()
        var distance = max(height * 2.6f, width * 2.3f)
        // Narrow stages: keep every figure inside the frame horizontally too.
        distance = max(distance, (width / 2f * 1.15f) / (tanHalf * aspect))
        distance /= zoom
        val targetY = height * 0.5f
        val eyeX = sin(yaw) * distance
        val eyeY = height * 0.55f
        val eyeZ = cos(yaw) * distance

        Matrix.perspectiveM(projection, 0, FIELD_OF_VIEW.toFloat(), aspect, 0.05f, distance * 3f + 10f)
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, 0f, targetY, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniform3f(uEye, eyeX, eyeY, eyeZ)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glEnableVertexAttribArray(aNormal)

        for ((i, buffers) in vertexBuffers.withIndex()) {
            if (i >= meshes.size) break
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, (i - (meshes.size - 1) / 2f) * SPACING, 0f, 0f)
            Matrix.multiplyMM(mvp, 0, viewProjection, 0, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0])
            GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 12, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[1])
            GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 12, 0)

            for (range in ranges) {
                if (range.part == BodyModel.Part.LONGHAIR && !style.longHair) continue
                val material = materials[range.part] ?: continue
                GLES20.glUniform3f(uColor, material.color[0], material.color[1], material.color[2])
                GLES20.glUniform1f(uRoughness, material.roughness)
                GLES20.glUniform1f(uDoubleSided, if (material.doubleSided) 1f else 0f)
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, range.count, GLES20.GL_UNSIGNED_SHORT, range.offsetBytes)
            }
        }

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aNormal)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    // Uploads

    /** All parts' triangles in one element buffer, one range per part (in the model's part order). */
    private fun uploadIndices(model: BodyModel) {
        indicesDirty = false
        var total = 0
        for (part in BodyModel.Part.entries) total += model.parts[part]?.size ?: 0
        if (total == 0) return
        val shorts: ShortBuffer = ByteBuffer.allocateDirect(total * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        val list = mutableListOf<Range>()
        var offset = 0
        for (part in BodyModel.Part.entries) {
            val indices = model.parts[part] ?: continue
            if (indices.isEmpty()) continue
            shorts.put(indices)
            list += Range(part, offset * 2, indices.size)
            offset += indices.size
        }
        shorts.position(0)
        if (indexBuffer == 0) {
            val ids = IntArray(1)
            GLES20.glGenBuffers(1, ids, 0)
            indexBuffer = ids[0]
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, total * 2, shorts, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        ranges = list
    }

    private fun uploadMeshes() {
        meshesDirty = false
        while (vertexBuffers.size < meshes.size) {
            val ids = IntArray(2)
            GLES20.glGenBuffers(2, ids, 0)
            vertexBuffers += ids
        }
        while (vertexBuffers.size > meshes.size) {
            GLES20.glDeleteBuffers(2, vertexBuffers.removeAt(vertexBuffers.lastIndex), 0)
        }
        for ((i, mesh) in meshes.withIndex()) {
            upload(vertexBuffers[i][0], mesh.positions, 2 * i)
            upload(vertexBuffers[i][1], mesh.normals, 2 * i + 1)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun upload(buffer: Int, values: FloatArray, slot: Int) {
        val data = staging(slot, values.size)
        data.clear()
        data.put(values)
        data.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, values.size * 4, data, GLES20.GL_DYNAMIC_DRAW)
    }

    /** Reuses direct buffers between uploads (the journey slider re-uploads on every move). */
    private fun staging(slot: Int, floats: Int): FloatBuffer {
        while (cpuBuffers.size <= slot) cpuBuffers += newFloatBuffer(floats)
        if (cpuBuffers[slot].capacity() != floats) cpuBuffers[slot] = newFloatBuffer(floats)
        return cpuBuffers[slot]
    }

    private fun newFloatBuffer(floats: Int): FloatBuffer =
        ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    // Shaders

    private fun buildProgram(): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        if (vertex == 0 || fragment == 0) return 0
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Link failed: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    /** Multisampled (4×) RGB888 with a depth buffer when the device has one, falling back to plainer configs. */
    class ConfigChooser : GLSurfaceView.EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val attempts = listOf(
                spec(depth = 24, samples = 4),
                spec(depth = 16, samples = 4),
                spec(depth = 24, samples = 0),
                spec(depth = 16, samples = 0),
                intArrayOf(EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE),
            )
            for (attributes in attempts) {
                val configs = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                if (egl.eglChooseConfig(display, attributes, configs, 1, count) && count[0] > 0) {
                    configs[0]?.let { return it }
                }
            }
            throw IllegalArgumentException("No OpenGL ES 2 config with a depth buffer")
        }

        private fun spec(depth: Int, samples: Int): IntArray {
            val base = mutableListOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, depth,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            )
            if (samples > 0) base += listOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples)
            base += EGL10.EGL_NONE
            return base.toIntArray()
        }
    }

    companion object {
        private const val TAG = "BodyRenderer"
        private const val EGL_OPENGL_ES2_BIT = 4
        private const val FIELD_OF_VIEW = 28.0
        private const val SPACING = 0.75f

        /** VColor.ground (#F3F5F1). */
        private val GROUND = floatArrayOf(0xF3 / 255f, 0xF5 / 255f, 0xF1 / 255f)

        private fun rgb(hex: Int): FloatArray =
            floatArrayOf(((hex shr 16) and 0xFF) / 255f, ((hex shr 8) and 0xFF) / 255f, (hex and 0xFF) / 255f)

        /** The iOS materials: skin from the chosen tone, fitted clothes, hair, brows and eyes. */
        private fun materialsFor(style: AvatarStyle): Map<BodyModel.Part, Material> {
            val skin = AvatarStyle.skinTones[style.skin.coerceIn(0, AvatarStyle.skinTones.size - 1)]
            return mapOf(
                BodyModel.Part.SKIN to Material(rgb(skin), 0.62f),
                BodyModel.Part.SHIRT to Material(rgb(0xF7F7F5), 0.9f),
                BodyModel.Part.LEGGINGS to Material(rgb(0x1C1E22), 0.75f),
                BodyModel.Part.SHOES to Material(rgb(0x23293A), 0.55f),
                BodyModel.Part.SOLE to Material(rgb(0xEDEDE8), 0.8f),
                BodyModel.Part.HAIR to Material(rgb(AvatarStyle.HAIR), 0.85f),
                BodyModel.Part.LONGHAIR to Material(rgb(AvatarStyle.HAIR), 0.85f, doubleSided = true),
                BodyModel.Part.BROWS to Material(rgb(0x2E2119), 0.9f),
                BodyModel.Part.EYES to Material(rgb(0xF4F2EE), 0.3f),
                BodyModel.Part.IRIS to Material(rgb(0x3A2A1E), 0.2f),
            )
        }

        private const val VERTEX_SHADER = """
uniform mat4 uMVP;
uniform mat4 uModel;
attribute vec3 aPosition;
attribute vec3 aNormal;
varying vec3 vNormal;
varying vec3 vWorld;
void main() {
    vWorld = (uModel * vec4(aPosition, 1.0)).xyz;
    vNormal = (uModel * vec4(aNormal, 0.0)).xyz;
    gl_Position = uMVP * vec4(aPosition, 1.0);
}
"""

        // Lights match the iOS scene: ambient, a warm key from the upper front right,
        // a cool fill from the front left and a back light, plus a soft rim.
        private const val FRAGMENT_SHADER = """
precision mediump float;
uniform vec3 uColor;
uniform vec3 uEye;
uniform float uRoughness;
uniform float uDoubleSided;
varying vec3 vNormal;
varying vec3 vWorld;
const vec3 keyDir = vec3(0.318, 0.682, 0.659);
const vec3 keyColor = vec3(1.0, 0.965, 0.925);
const vec3 fillDir = vec3(-0.748, 0.296, 0.594);
const vec3 fillColor = vec3(0.918, 0.953, 1.0);
const vec3 backDir = vec3(0.0, 0.199, -0.980);
void main() {
    vec3 n = normalize(vNormal);
    vec3 v = normalize(uEye - vWorld);
    if (uDoubleSided > 0.5 && dot(n, v) < 0.0) {
        n = -n;
    }
    float key = max(dot(n, keyDir), 0.0);
    float fill = max(dot(n, fillDir), 0.0);
    float back = max(dot(n, backDir), 0.0);
    vec3 light = vec3(0.34) + keyColor * (0.7 * key) + fillColor * (0.26 * fill) + vec3(0.4 * back);
    float shininess = mix(64.0, 6.0, uRoughness);
    vec3 h = normalize(keyDir + v);
    float spec = pow(max(dot(n, h), 0.0), shininess) * (1.0 - uRoughness) * 0.3;
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0) * 0.16;
    vec3 color = uColor * light + vec3(spec + rim);
    gl_FragColor = vec4(min(color, vec3(1.0)), 1.0);
}
"""
    }
}
