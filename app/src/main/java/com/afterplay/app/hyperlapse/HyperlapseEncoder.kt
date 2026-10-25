package com.afterplay.app.hyperlapse

import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.*
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Offline encoding processes only the sampled frames, never a full-length source movie. */
internal object HyperlapseEncoder {
    fun encode(frames: List<File>, output: File, onProgress: (Int) -> Unit = {}) {
        require(frames.isNotEmpty() && frames.size <= HyperlapsePlan.MAX_FRAMES)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var renderer: FrameRenderer? = null
        var input: Surface? = null
        var started = false
        var muxerStarted = false
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, HyperlapsePlan.SIZE, HyperlapsePlan.SIZE).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, HyperlapsePlan.FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            input = codec.createInputSurface()
            renderer = FrameRenderer(input)
            codec.start()
            started = true
            val writer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            val info = MediaCodec.BufferInfo()
            var track = -1
            fun drain(end: Boolean) {
                val timeoutAt = android.os.SystemClock.elapsedRealtime() + 30_000
                while (true) {
                    val index = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!end) return
                        check(android.os.SystemClock.elapsedRealtime() < timeoutAt) { "Video encoder timed out" }
                    } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        check(!muxerStarted)
                        track = writer.addTrack(codec.outputFormat)
                        writer.start()
                        muxerStarted = true
                    } else if (index >= 0) {
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            check(muxerStarted)
                            val buffer = requireNotNull(codec.getOutputBuffer(index))
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            writer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (eos) {
                            // Explicitly terminate the final sample at N / 30 seconds.
                            check(muxerStarted)
                            info.set(0, 0, HyperlapsePlan.presentationUs(frames.size), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            writer.writeSampleData(track, ByteBuffer.allocate(0), info)
                            return
                        }
                    }
                }
            }
            var previous: File? = null
            frames.forEachIndexed { index, frame ->
                drain(false)
                renderer.draw(frame, previous != frame, HyperlapsePlan.presentationUs(index) * 1_000)
                previous = frame
                if (index % 30 == 0) onProgress(index * 100 / frames.size)
            }
            codec.signalEndOfInputStream()
            drain(true)
            writer.stop()
            muxerStarted = false
            onProgress(100)
        } finally {
            renderer?.close()
            input?.release()
            if (started) runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            muxer?.release()
        }
    }
}

/** A GL surface is required: locking a MediaCodec input Surface with Canvas is unsupported. */
private class FrameRenderer(surface: Surface) : AutoCloseable {
    private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private var context = EGL14.EGL_NO_CONTEXT
    private var window = EGL14.EGL_NO_SURFACE
    private var program = 0
    private val texture = IntArray(1)
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        // JPEG pixels have a top-left origin; GL texture coordinates account for that here.
        put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)); position(0)
    }
    init {
        try {
            check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0) && count[0] > 0)
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT)
            window = EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            check(window != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display, window, window, context))
            fun shader(type: Int, code: String): Int {
                val id = GLES20.glCreateShader(type)
                GLES20.glShaderSource(id, code)
                GLES20.glCompileShader(id)
                val result = IntArray(1)
                GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, result, 0)
                check(result[0] != 0) { GLES20.glGetShaderInfoLog(id) }
                return id
            }
            val vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec4 p; attribute vec2 t; varying vec2 uv; void main(){gl_Position=p;uv=t;}")
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER, "precision mediump float; varying vec2 uv; uniform sampler2D s; void main(){gl_FragColor=texture2D(s,uv);}")
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
            val result = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0)
            check(result[0] != 0) { GLES20.glGetProgramInfoLog(program) }
            GLES20.glUseProgram(program)
            GLES20.glGenTextures(1, texture, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val position = GLES20.glGetAttribLocation(program, "p")
            val tex = GLES20.glGetAttribLocation(program, "t")
            vertices.position(0)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
            GLES20.glEnableVertexAttribArray(position)
            vertices.position(2)
            GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 16, vertices)
            GLES20.glEnableVertexAttribArray(tex)
            GLES20.glViewport(0, 0, HyperlapsePlan.SIZE, HyperlapsePlan.SIZE)
        } catch (error: Exception) { close(); throw error }
    }
    fun draw(file: File, upload: Boolean, timestampNs: Long) {
        if (upload) {
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0) } finally { bitmap.recycle() }
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR)
        check(EGLExt.eglPresentationTimeANDROID(display, window, timestampNs))
        check(EGL14.eglSwapBuffers(display, window))
    }
    override fun close() {
        if (context != EGL14.EGL_NO_CONTEXT) {
            GLES20.glDeleteTextures(1, texture, 0)
            GLES20.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        }
        if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        if (display != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display)
    }
}
