package com.anuxi.pocketstudio.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.*
import android.net.Uri
import android.opengl.*
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Opciones de una exportacion. fit: 0 ajustar, 1 rellenar, 2 estirar. */
class ExportSettings(
    val w: Int, val h: Int, val fps: Int, val hevc: Boolean, val bitrate: Int,
    val audio: Boolean, val fit: Int,
)

/** Dibuja cada cuadro con Canvas, lo sube a una textura GL y lo manda al codificador. */
object Exporter {
    private const val AUDIO_RATE = 44100
    private const val VIDEO_TIMEOUT = 10_000L

    suspend fun run(ctx: Context, source: Project, s: ExportSettings, onProgress: (Float) -> Unit): Uri =
        withContext(Dispatchers.IO) { render(ctx, projectFromJson(source.toJson()), s, onProgress) }

    private class Pcm(val data: ShortArray, val rate: Int, val channels: Int)

    private class Sample(val data: ByteArray, val ptsUs: Long, val flags: Int)

    private fun CoroutineScope.render(ctx: Context, p: Project, s: ExportSettings, onProgress: (Float) -> Unit): Uri {
        val temp = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        val dur = max(p.duration, 0.1f)
        val layers = p.layers.filter { it.kind == LayerKind.Video && it.uri != null }
        val retrievers = HashMap<String, MediaMetadataRetriever>()
        var muxer: MediaMuxer? = null
        var venc: MediaCodec? = null
        var gl: Gl? = null
        try {
            // ---------------------------------------------------------- audio
            val audioSamples = ArrayList<Sample>()
            var audioFormat: MediaFormat? = null
            if (s.audio) {
                val mix = mixAudio(ctx, p, layers, dur) { ensureActive() }
                if (mix != null) {
                    val r = encodeAudio(mix) { ensureActive() }
                    audioFormat = r.first; audioSamples.addAll(r.second)
                }
            }
            onProgress(0.05f)

            // ---------------------------------------------------------- video
            val mime = if (s.hevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
            val fmt = MediaFormat.createVideoFormat(mime, s.w, s.h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, s.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, s.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val enc = MediaCodec.createEncoderByType(mime)
            venc = enc
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = enc.createInputSurface()
            enc.start()
            gl = Gl(surface, s.w, s.h)

            val mux = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            var vTrack = -1
            var aTrack = -1
            var started = false
            val info = MediaCodec.BufferInfo()

            fun drain(end: Boolean) {
                while (true) {
                    val i = enc.dequeueOutputBuffer(info, if (end) VIDEO_TIMEOUT else 0)
                    when {
                        i == MediaCodec.INFO_TRY_AGAIN_LATER -> { if (!end) return }
                        i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            vTrack = mux.addTrack(enc.outputFormat)
                            audioFormat?.let { aTrack = mux.addTrack(it) }
                            mux.start(); started = true
                            for (a in audioSamples) {
                                val bi = MediaCodec.BufferInfo().apply { set(0, a.data.size, a.ptsUs, a.flags) }
                                mux.writeSampleData(aTrack, ByteBuffer.wrap(a.data), bi)
                            }
                            audioSamples.clear()
                        }
                        i >= 0 -> {
                            val buf = enc.getOutputBuffer(i)!!
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && started) {
                                buf.position(info.offset); buf.limit(info.offset + info.size)
                                mux.writeSampleData(vTrack, buf, info)
                            }
                            enc.releaseOutputBuffer(i, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            val frameBmp = Bitmap.createBitmap(s.w, s.h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBmp)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val cache = HashMap<String, Pair<Long, Bitmap?>>()
            val total = max(1, (dur * s.fps).roundToInt())

            // proyecto -> salida
            val pw = p.width.toFloat(); val ph = p.height.toFloat()
            val (sx, sy) = when (s.fit) {
                2 -> (s.w / pw) to (s.h / ph)
                1 -> max(s.w / pw, s.h / ph).let { it to it }
                else -> min(s.w / pw, s.h / ph).let { it to it }
            }
            val base = Matrix().apply { setScale(sx, sy); postTranslate((s.w - pw * sx) / 2f, (s.h - ph * sy) / 2f) }

            for (n in 0 until total) {
                ensureActive()
                val t = n / s.fps.toFloat()
                canvas.drawColor(p.bg)
                for (l in layers) {
                    if (l.hidden || l.missing || t < l.start || t >= l.end) continue
                    val a = p.alpha(l, t)
                    if (a <= 0f) continue
                    val st = l.sourceTime(t)
                    val idx = (st * max(1f, l.srcFps)).roundToLong()
                    val key = l.id
                    var entry = cache[key]
                    if (entry == null || entry.first != idx) {
                        val r = retrievers.getOrPut(l.uri!!) {
                            MediaMetadataRetriever().apply { setDataSource(ctx, Uri.parse(l.uri)) }
                        }
                        val bmp = runCatching {
                            r.getFrameAtTime((st * 1_000_000f).toLong(), MediaMetadataRetriever.OPTION_CLOSEST)
                        }.getOrNull()
                        entry = idx to bmp
                        cache[key] = entry
                    }
                    val bmp = entry.second ?: continue
                    val w = p.worldMatrix(l, t)
                    val m = Matrix()
                    m.setValues(floatArrayOf(w.a, w.c, w.e, w.b, w.d, w.f, 0f, 0f, 1f))
                    m.postConcat(base)
                    m.preScale(l.srcW.toFloat() / bmp.width, l.srcH.toFloat() / bmp.height)
                    paint.alpha = (a * 255f).roundToInt().coerceIn(0, 255)
                    canvas.drawBitmap(bmp, m, paint)
                }
                gl.draw(frameBmp, (n * 1_000_000_000L / s.fps))
                drain(false)
                onProgress(0.05f + 0.93f * (n + 1) / total)
            }
            enc.signalEndOfInputStream()
            drain(true)
            if (!started) error("El codificador no produjo video")
            mux.stop()
            onProgress(0.99f)
            return save(ctx, temp, p.name)
        } finally {
            runCatching { gl?.release() }
            runCatching { venc?.stop() }
            runCatching { venc?.release() }
            runCatching { muxer?.release() }
            retrievers.values.forEach { runCatching { it.release() } }
            temp.delete()
        }
    }

    // ============================================================== guardado

    private fun save(ctx: Context, file: File, name: String): Uri {
        val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "_").ifBlank { "video" }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$safe.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PocketStudio")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val res = ctx.contentResolver
        val uri = res.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("No se pudo crear el archivo")
        res.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        res.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    // ================================================================= audio

    private fun decodePcm(ctx: Context, uri: String, check: () -> Unit): Pcm? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, Uri.parse(uri), null)
            val ti = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(ti)
            val fmt = ex.getTrackFormat(ti)
            val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            dec.configure(fmt, null, null, 0)
            dec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var out = ShortArray(rate * ch * 10)
            var n = 0
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            val limit = rate * ch * 600
            while (!outDone && n < limit) {
                check()
                if (!inDone) {
                    val i = dec.dequeueInputBuffer(5000)
                    if (i >= 0) {
                        val b = dec.getInputBuffer(i)!!
                        val size = ex.readSampleData(b, 0)
                        if (size < 0) { dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { dec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 5000)
                if (o >= 0) {
                    if (info.size > 0) {
                        val sb = dec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val cnt = info.size / 2
                        if (n + cnt > out.size) out = out.copyOf(max(out.size * 2, n + cnt))
                        sb.get(out, n, cnt)
                        n += cnt
                    }
                    dec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = dec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            dec.stop(); dec.release()
            return Pcm(out.copyOf(n), rate, max(1, ch))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        } finally {
            ex.release()
        }
    }

    /** Mezcla el audio de las capas en PCM estereo a 44100 Hz, siguiendo la velocidad de cada clip. */
    private fun mixAudio(ctx: Context, p: Project, layers: List<Layer>, dur: Float, check: () -> Unit): ShortArray? {
        val total = (dur * AUDIO_RATE).toInt()
        val mix = IntArray(total * 2)
        var any = false
        val pcmCache = HashMap<String, Pcm?>()
        for (l in layers) {
            if (l.mute || l.missing) continue
            val pcm = pcmCache.getOrPut(l.uri!!) { decodePcm(ctx, l.uri!!, check) } ?: continue
            any = true
            val i0 = (l.start * AUDIO_RATE).toInt().coerceIn(0, total)
            val i1 = (l.end * AUDIO_RATE).toInt().coerceIn(0, total)
            // tiempo del original cada 10 ms, con interpolacion lineal entre puntos
            val blk = AUDIO_RATE / 100
            val nPts = (i1 - i0) / blk + 2
            val pts = FloatArray(nPts) { l.sourceTime(min(l.end, l.start + it * 0.01f)) }
            val frames = pcm.data.size / pcm.channels
            for (i in i0 until i1) {
                if ((i and 0xFFFF) == 0) check()
                val k = (i - i0) / blk
                val fr = ((i - i0) % blk) / blk.toFloat()
                val st = pts[k] + (pts[k + 1] - pts[k]) * fr
                val si = (st * pcm.rate).toInt()
                if (si < 0 || si >= frames) continue
                val l0 = pcm.data[si * pcm.channels].toInt()
                val r0 = if (pcm.channels > 1) pcm.data[si * pcm.channels + 1].toInt() else l0
                mix[i * 2] += l0; mix[i * 2 + 1] += r0
            }
        }
        if (!any) return null
        return ShortArray(mix.size) { mix[it].coerceIn(-32768, 32767).toShort() }
    }

    private fun encodeAudio(pcm: ShortArray, check: () -> Unit): Pair<MediaFormat, List<Sample>> {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        val out = ArrayList<Sample>()
        var outFmt: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var pos = 0
        var inDone = false
        var outDone = false
        try {
            while (!outDone) {
                check()
                if (!inDone) {
                    val i = enc.dequeueInputBuffer(5000)
                    if (i >= 0) {
                        val b = enc.getInputBuffer(i)!!
                        b.clear()
                        val room = b.capacity() / 2
                        val cnt = min(room, pcm.size - pos)
                        val pts = pos / 2L * 1_000_000L / AUDIO_RATE
                        if (cnt <= 0) { enc.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else {
                            b.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm, pos, cnt)
                            enc.queueInputBuffer(i, 0, cnt * 2, pts, 0)
                            pos += cnt
                        }
                    }
                }
                val o = enc.dequeueOutputBuffer(info, 5000)
                if (o >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val b = enc.getOutputBuffer(o)!!
                        b.position(info.offset); b.limit(info.offset + info.size)
                        val arr = ByteArray(info.size); b.get(arr)
                        out.add(Sample(arr, info.presentationTimeUs, info.flags))
                    }
                    enc.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outFmt = enc.outputFormat
                }
            }
        } finally {
            runCatching { enc.stop() }; runCatching { enc.release() }
        }
        return (outFmt ?: fmt) to out
    }

    // =================================================================== GL

    private class Gl(surface: Surface, val w: Int, val h: Int) {
        private var dpy: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private var ctx: EGLContext
        private var surf: EGLSurface
        private var prog = 0
        private var tex = 0
        private var texReady = false
        private val quad: java.nio.FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)); position(0)
        }

        init {
            val v = IntArray(2)
            EGL14.eglInitialize(dpy, v, 0, v, 1)
            val cfgs = arrayOfNulls<EGLConfig>(1)
            val n = IntArray(1)
            EGL14.eglChooseConfig(
                dpy,
                intArrayOf(
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, 0x3142, 1, EGL14.EGL_NONE,
                ), 0, cfgs, 0, 1, n, 0,
            )
            ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surf = EGL14.eglCreateWindowSurface(dpy, cfgs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            val vs = "attribute vec2 aPos;attribute vec2 aTex;varying vec2 vTex;void main(){gl_Position=vec4(aPos,0.0,1.0);vTex=aTex;}"
            val fs = "precision mediump float;varying vec2 vTex;uniform sampler2D uTex;void main(){gl_FragColor=texture2D(uTex,vTex);}"
            prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, shader(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(prog, shader(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(prog)
            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            tex = t[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        private fun shader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
            return s
        }

        fun draw(bmp: Bitmap, ptsNs: Long) {
            GLES20.glViewport(0, 0, w, h)
            GLES20.glUseProgram(prog)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            if (!texReady) { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); texReady = true }
            else GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bmp)
            val aPos = GLES20.glGetAttribLocation(prog, "aPos")
            val aTex = GLES20.glGetAttribLocation(prog, "aTex")
            quad.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aPos)
            quad.position(2)
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aTex)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uTex"), 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            EGLExt.eglPresentationTimeANDROID(dpy, surf, ptsNs)
            EGL14.eglSwapBuffers(dpy, surf)
        }

        fun release() {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surf)
            EGL14.eglDestroyContext(dpy, ctx)
            EGL14.eglTerminate(dpy)
        }
    }
}
