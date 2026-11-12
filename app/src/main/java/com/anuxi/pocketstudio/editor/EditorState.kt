package com.anuxi.pocketstudio.editor

import com.anuxi.pocketstudio.data.tr
import android.app.Application
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.Surface
import android.media.MediaPlayer
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun fmtTime(t: Float): String {
    val tt = max(0f, t)
    val m = (tt / 60).toInt()
    val s = tt - m * 60
    return "%d:%04.1f".format(m, s)
}

/** Todo el estado del editor abierto: proyecto, seleccion, tiempo y vista previa. */
class EditorState(private val app: Application, private val scope: CoroutineScope) {
    var project by mutableStateOf<Project?>(null)
        private set

    /** Sube cada vez que algo del proyecto cambia, para volver a dibujar. */
    var rev by mutableIntStateOf(0)
        private set

    var playhead by mutableFloatStateOf(0f)
    var playing by mutableStateOf(false)
        private set
    var selected by mutableStateOf<String?>(null)
    var tab by mutableIntStateOf(0)
    var pxPerSec by mutableFloatStateOf(48f)
    var busy by mutableStateOf<String?>(null)

    private val decoder = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var saveJob: Job? = null
    private var playJob: Job? = null

    // ------------------------------------------------------------ abrir y cerrar

    fun open(id: String) {
        val p = ProjectStore.load(app, id) ?: return
        project = p
        playhead = 0f
        selected = p.layers.lastOrNull()?.id
        tab = 0
        rev++
        requestFrames(false)
    }

    fun close() {
        stop()
        project?.let { ProjectStore.save(app, it) }
        saveJob?.cancel()
        project = null
        releaseAll()
    }

    val layer: Layer? get() = project?.layer(selected)

    // ---------------------------------------------------------------- cambios

    /** Avisa de un cambio: vuelve a dibujar y guarda solo. */
    fun touch(frames: Boolean = false) {
        rev++
        scheduleSave()
        if (frames) requestFrames(false)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600)
            val p = project ?: return@launch
            withContext(kotlinx.coroutines.Dispatchers.IO) { ProjectStore.save(app, p) }
        }
    }

    fun rename(name: String) {
        val p = project ?: return
        p.name = name.ifBlank { p.name }
        touch()
    }

    // ------------------------------------------------------------------ capas

    suspend fun addVideo(uri: Uri) {
        val p = project ?: return
        busy = "importando"
        try {
            val info = withContext(decoder) { probe(uri) }
            if (info == null) {
                busy = tr("no se pudo leer ese video", "couldn't read that video")
                return
            }
            val l = Layer(newId(), LayerKind.Video, info.name)
            l.uri = uri.toString()
            l.srcW = info.w; l.srcH = info.h; l.srcDur = info.dur; l.srcFps = info.fps
            l.start = 0f; l.inPoint = 0f; l.length = info.dur
            l.anchorX.base = info.w / 2f; l.anchorY.base = info.h / 2f
            l.posX.base = p.width / 2f; l.posY.base = p.height / 2f
            val fit = min(p.width / info.w.toFloat(), p.height / info.h.toFloat()) * 100f
            l.scaleX.base = fit; l.scaleY.base = fit
            p.layers.add(l)
            selected = l.id
            touch(frames = true)
            busy = null
        } catch (e: Exception) {
            busy = tr("no se pudo importar: ${e.message}", "couldn't import: ${e.message}")
        }
    }

    fun busyClear() {
        busy = null
    }

    /** Cambia el video de una capa (cuando el original ya no esta) sin tocar sus ajustes. */
    fun relink(id: String, uri: Uri) {
        val l = project?.layer(id) ?: return
        val info = probe(uri)
        if (info == null) {
            busy = tr("no se pudo leer ese video", "couldn't read that video")
            return
        }
        releaseLP(id)
        l.uri = uri.toString()
        l.srcW = info.w; l.srcH = info.h; l.srcDur = info.dur; l.srcFps = info.fps
        l.missing = false
        touch(frames = true)
    }

    fun addNull() {
        val p = project ?: return
        val l = Layer(newId(), LayerKind.Null, "Nulo ${p.layers.count { it.kind == LayerKind.Null } + 1}")
        l.srcW = 100; l.srcH = 100
        l.start = 0f; l.length = max(p.duration, 5f)
        l.anchorX.base = 50f; l.anchorY.base = 50f
        l.posX.base = p.width / 2f; l.posY.base = p.height / 2f
        p.layers.add(l)
        selected = l.id
        touch()
    }

    fun deleteLayer(id: String) {
        val p = project ?: return
        p.layers.forEach { if (it.parentId == id) it.parentId = null }
        p.layers.removeAll { it.id == id }
        if (selected == id) selected = p.layers.lastOrNull()?.id
        touch()
    }

    fun duplicateLayer(id: String) {
        val p = project ?: return
        val src = p.layer(id) ?: return
        val copy = projectFromJson(p.toJson()).layer(id) ?: return
        val l = Layer(newId(), src.kind, src.name + " 2")
        // copia por JSON: reutiliza el lector del proyecto
        l.uri = copy.uri; l.srcW = copy.srcW; l.srcH = copy.srcH; l.srcDur = copy.srcDur; l.srcFps = copy.srcFps
        l.start = copy.start; l.inPoint = copy.inPoint; l.length = copy.length
        l.anchorX.base = copy.anchorX.base; l.anchorY.base = copy.anchorY.base
        listOf("anchorX", "anchorY", "posX", "posY", "scaleX", "scaleY", "rot", "opacity", "speed").forEach { n ->
            val t = l.track(n)!!
            val s = copy.track(n)!!
            t.base = s.base; t.keys.clear(); t.keys.addAll(s.keys)
        }
        l.scaleLinked = copy.scaleLinked; l.parentId = copy.parentId; l.mute = copy.mute
        l.blur = copy.blur; l.interp = copy.interp
        p.layers.add(p.layers.indexOf(src) + 1, l)
        selected = l.id
        touch(frames = true)
    }

    fun moveLayerOrder(id: String, up: Boolean) {
        val p = project ?: return
        val i = p.layers.indexOfFirst { it.id == id }
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j !in p.layers.indices) return
        val l = p.layers.removeAt(i)
        p.layers.add(j, l)
        touch(frames = true)
    }

    fun setName(id: String, name: String) {
        project?.layer(id)?.name = name.ifBlank { return }
        touch()
    }

    // ------------------------------------------- mover y recortar clips (con iman)

    var snapEnabled by mutableStateOf(true)

    /** Puntos a los que se pega un borde: marcadores, cursor, bordes de otros clips, 0. */
    private fun snapPoints(except: String): List<Float> {
        val p = project ?: return emptyList()
        val pts = mutableListOf(0f, playhead)
        p.markers.forEach { pts.add(it.time) }
        p.layers.forEach { if (it.id != except) { pts.add(it.start); pts.add(it.end) } }
        return pts
    }

    fun snap(t: Float, except: String, tolerance: Float): Float {
        if (!snapEnabled) return t
        val p = project ?: return t
        val frame = 1f / p.fps
        var best = t
        var bd = tolerance
        for (s in snapPoints(except)) {
            val d = abs(s - t)
            if (d < bd) { bd = d; best = s }
        }
        return if (bd < tolerance) best else Math.round(t / frame) * frame
    }

    fun moveLayer(id: String, newStart: Float, tolerance: Float) {
        val p = project ?: return
        val l = p.layer(id) ?: return
        val a = snap(max(0f, newStart), id, tolerance)
        val b = snap(max(0f, newStart + l.length), id, tolerance) - l.length
        // elige el borde (inicio o final) que quedo mas cerca de un punto de iman
        val s = if (abs(a - newStart) <= abs(b - newStart)) a else b
        l.start = max(0f, s)
        touch(frames = true)
    }

    fun trimStart(id: String, newStart: Float, tolerance: Float) {
        val p = project ?: return
        val l = p.layer(id) ?: return
        var s = snap(newStart, id, tolerance)
        val minLen = 1f / p.fps
        // no se puede empezar antes del inicio del original
        val speed = l.speed.base / 100f
        val delta = s - l.start
        val newIn = l.inPoint + delta * speed
        if (l.kind == LayerKind.Video && newIn < 0f) s = l.start - l.inPoint / speed
        if (s > l.end - minLen) s = l.end - minLen
        val d = s - l.start
        l.inPoint = if (l.kind == LayerKind.Video) max(0f, l.inPoint + d * speed) else l.inPoint
        l.length -= d
        l.start = s
        touch(frames = true)
    }

    fun trimEnd(id: String, newEnd: Float, tolerance: Float) {
        val p = project ?: return
        val l = p.layer(id) ?: return
        var e = snap(newEnd, id, tolerance)
        val minLen = 1f / p.fps
        if (e < l.start + minLen) e = l.start + minLen
        if (l.kind == LayerKind.Video) {
            val maxLen = (l.srcDur - l.inPoint) / (l.speed.base / 100f).coerceAtLeast(0.01f)
            e = min(e, l.start + maxLen)
        }
        l.length = e - l.start
        touch(frames = true)
    }

    // ------------------------------------------------------------ propiedades

    fun eps(): Float = project?.frameEps() ?: 0.01f

    /** Valor de una propiedad de la capa en el cursor. */
    fun valueOf(l: Layer, prop: String): Float = l.track(prop)?.at(playhead) ?: 0f

    fun setProp(l: Layer, prop: String, value: Float) {
        val t = l.track(prop) ?: return
        val e = eps()
        if (prop == "scaleX" && l.scaleLinked) {
            val old = l.scaleX.at(playhead)
            val oldY = l.scaleY.at(playhead)
            l.scaleX.setAt(playhead, value, e)
            l.scaleY.setAt(playhead, if (abs(old) < 1e-3f) value else oldY * value / old, e)
        } else if (prop == "scaleY" && l.scaleLinked) {
            val old = l.scaleY.at(playhead)
            val oldX = l.scaleX.at(playhead)
            l.scaleY.setAt(playhead, value, e)
            l.scaleX.setAt(playhead, if (abs(old) < 1e-3f) value else oldX * value / old, e)
        } else {
            t.setAt(playhead, value, e)
        }
        touch()
    }

    fun toggleKey(l: Layer, prop: String) {
        val e = eps()
        l.track(prop)?.toggleKey(playhead, e)
        if (l.scaleLinked && prop == "scaleX") l.scaleY.toggleKey(playhead, e)
        if (l.scaleLinked && prop == "scaleY") l.scaleX.toggleKey(playhead, e)
        touch()
    }

    fun setParent(l: Layer, parentId: String?) {
        val p = project ?: return
        if (parentId == null) {
            // al soltar el padre, la capa conserva su lugar en pantalla
            val world = p.worldMatrix(l, playhead)
            val (wx, wy) = world.apply(l.anchorX.at(playhead), l.anchorY.at(playhead))
            l.parentId = null
            shiftPosition(l, wx - l.posX.at(playhead), wy - l.posY.at(playhead))
        } else {
            val parent = p.layer(parentId) ?: return
            if (p.wouldLoop(l, parent)) return
            // compensa la posicion para que no salte al emparentar
            val pw = p.worldMatrix(parent, playhead)
            val inv = pw.inverse()
            if (inv != null) {
                val (nx, ny) = inv.apply(l.posX.at(playhead), l.posY.at(playhead))
                shiftPosition(l, nx - l.posX.at(playhead), ny - l.posY.at(playhead))
            }
            l.parentId = parentId
        }
        touch()
    }

    private fun shiftPosition(l: Layer, dx: Float, dy: Float) {
        l.posX.base += dx; l.posY.base += dy
        for (i in l.posX.keys.indices) l.posX.keys[i] = l.posX.keys[i].copy(v = l.posX.keys[i].v + dx)
        for (i in l.posY.keys.indices) l.posY.keys[i] = l.posY.keys[i].copy(v = l.posY.keys[i].v + dy)
    }

    // ---------------------------------------------------------------- marcadores

    val markerColors = listOf(0xFFB79CFF.toInt(), 0xFF5ED4FF.toInt(), 0xFFFFD36B.toInt(), 0xFFFF9C6B.toInt(), 0xFF7CF2C8.toInt())

    fun addMarker(): Marker? {
        val p = project ?: return null
        val m = Marker(newId(), playhead, "", markerColors[p.markers.size % markerColors.size])
        p.markers.add(m)
        p.markers.sortBy { it.time }
        touch()
        return m
    }

    fun deleteMarker(id: String) {
        project?.markers?.removeAll { it.id == id }
        touch()
    }

    fun seekMarker(forward: Boolean) {
        val p = project ?: return
        val e = eps()
        val target = if (forward) p.markers.firstOrNull { it.time > playhead + e }
        else p.markers.lastOrNull { it.time < playhead - e }
        if (target != null) seek(target.time)
    }

    // -------------------------------------------------------- ajustes de proyecto

    fun setSize(w: Int, h: Int, mode: ResizeMode) {
        val p = project ?: return
        val ow = p.width.toFloat()
        val oh = p.height.toFloat()
        if (w == p.width && h == p.height) return
        val fx = w / ow
        val fy = h / oh
        val s = when (mode) { ResizeMode.Fit -> min(fx, fy); ResizeMode.Fill -> max(fx, fy); ResizeMode.Keep -> 1f }
        fun map(t: Track, f: (Float) -> Float) {
            t.base = f(t.base)
            for (i in t.keys.indices) t.keys[i] = t.keys[i].copy(v = f(t.keys[i].v))
        }
        p.layers.filter { it.parentId == null }.forEach { l ->
            if (mode == ResizeMode.Keep) {
                map(l.posX) { it + (w - ow) / 2f }
                map(l.posY) { it + (h - oh) / 2f }
            } else {
                map(l.posX) { it * fx }
                map(l.posY) { it * fy }
                map(l.scaleX) { it * s }
                map(l.scaleY) { it * s }
            }
        }
        p.width = w; p.height = h; p.resize = mode
        touch(frames = true)
    }

    // ------------------------------------------------------------- reproduccion

    fun seek(t: Float) {
        val p = project ?: return
        playhead = t.coerceIn(0f, max(p.duration, 0.001f))
        requestFrames(playing)
        if (playing) seekPlayers()
    }

    fun togglePlay() {
        if (playing) stop() else play()
    }

    private fun play() {
        val p = project ?: return
        if (p.duration <= 0f) return
        if (playhead >= p.duration - 0.05f) playhead = 0f
        ensurePlayers()
        playing = true
        playJob?.cancel()
        playJob = scope.launch {
            var last = System.nanoTime()
            while (playing) {
                delay(33)
                val now = System.nanoTime()
                val dt = (now - last) / 1_000_000_000f
                last = now
                playhead += dt
                if (playhead >= p.duration) {
                    playhead = p.duration
                    playing = false
                }
                if (playing) syncPlayback(playhead) else { pauseAll(); seekPlayers() }
            }
        }
    }

    fun stop() {
        playing = false
        playJob?.cancel()
        pauseAll()
        seekPlayers()
    }

    // ------------------------------------------------- reproduccion de verdad
    // Cada capa de video tiene su MediaPlayer: decodifica por hardware, pinta sobre una
    // TextureView (la ve la pantalla de edicion) y suena con su propio audio.

    private class LP(val mp: MediaPlayer, val uri: String?) {
        var prepared = false
        var seeking = false
        var want = -1L
        var issued = -2L
        var speed = 1f
        var done = false
    }

    private val lps = HashMap<String, LP>()
    private val surfaces = HashMap<String, Surface>()

    fun attachSurface(id: String, s: Surface) {
        surfaces[id] = s
        lps[id]?.let { runCatching { it.mp.setSurface(s) } }
    }

    fun detachSurface(id: String) {
        surfaces.remove(id)
        lps[id]?.let { runCatching { it.mp.setSurface(null) } }
    }

    private fun releaseLP(id: String) {
        lps.remove(id)?.let { runCatching { it.mp.release() } }
    }

    private fun releaseAll() {
        lps.keys.toList().forEach { releaseLP(it) }
    }

    private fun issue(lp: LP) {
        lp.seeking = true
        lp.issued = lp.want
        lp.done = false
        runCatching { lp.mp.seekTo(lp.want, MediaPlayer.SEEK_CLOSEST) }.onFailure { lp.seeking = false }
    }

    private fun ensurePlayers() {
        val p = project ?: return
        val vids = p.layers.filter { it.kind == LayerKind.Video && !it.missing && it.uri != null }.associateBy { it.id }
        lps.keys.filter { it !in vids || lps[it]?.uri != vids[it]?.uri }.toList().forEach { releaseLP(it) }
        for ((id, l) in vids) {
            if (id in lps) continue
            val mp = MediaPlayer()
            val lp = LP(mp, l.uri)
            lps[id] = lp
            try {
                mp.setDataSource(app, Uri.parse(l.uri))
                surfaces[id]?.let { mp.setSurface(it) }
                mp.setOnPreparedListener {
                    lp.prepared = true
                    if (playing) syncPlayback(playhead) else seekPlayers()
                }
                mp.setOnSeekCompleteListener {
                    lp.seeking = false
                    if (lp.want >= 0 && lp.want != lp.issued) issue(lp)
                }
                mp.setOnCompletionListener { lp.done = true }
                mp.setOnErrorListener { _, _, _ -> lp.prepared = false; lp.seeking = false; true }
                mp.prepareAsync()
            } catch (e: Exception) {
                releaseLP(id)
            }
        }
    }

    private fun srcMs(l: Layer, t: Float): Long {
        val end = max(0L, (l.srcDur * 1000).toLong() - 1)
        return (l.sourceTime(t) * 1000).toLong().coerceIn(0L, end)
    }

    /** Con la reproduccion parada: pone cada capa visible en el frame del cursor. */
    private fun seekPlayers() {
        val p = project ?: return
        val t = playhead
        for (l in p.layers) {
            val lp = lps[l.id] ?: continue
            if (!lp.prepared) continue
            if (t < l.start - 1e-3f || t > l.end + 1e-3f) continue
            lp.want = srcMs(l, t)
            if (!lp.seeking) issue(lp)
        }
    }

    /** Durante la reproduccion: arranca, pausa y corrige la deriva de cada capa. */
    private fun syncPlayback(t: Float) {
        val p = project ?: return
        for (l in p.layers) {
            val lp = lps[l.id] ?: continue
            if (!lp.prepared) continue
            val mp = lp.mp
            val inRange = t >= l.start && t < l.end
            runCatching {
                if (!inRange) {
                    if (mp.isPlaying) mp.pause()
                    return@runCatching
                }
                val v = if (l.mute) 0f else 1f
                mp.setVolume(v, v)
                val src = srcMs(l, t)
                val sp = (l.speed.at(t) / 100f).coerceIn(0.25f, 4f)
                if (!mp.isPlaying) {
                    if (lp.done) return@runCatching
                    lp.want = src
                    if (!lp.seeking) issue(lp)
                    mp.start()
                    lp.speed = -1f
                }
                if (abs(sp - lp.speed) > 0.02f) {
                    lp.speed = sp
                    mp.playbackParams = mp.playbackParams.setSpeed(sp).setPitch(1f)
                } else if (!lp.seeking && abs(mp.currentPosition - src) > 700) {
                    lp.want = src
                    issue(lp)
                }
            }
        }
    }

    private fun pauseAll() {
        lps.values.forEach { lp -> runCatching { if (lp.prepared && lp.mp.isPlaying) lp.mp.pause() } }
    }

    /** Vuelve a colocar los reproductores tras un cambio en el proyecto o en el cursor. */
    fun requestFrames(@Suppress("UNUSED_PARAMETER") fast: Boolean) {
        ensurePlayers()
        if (!playing) seekPlayers()
    }

    // ------------------------------------------------------------------ sondeo

    private class Probe(val name: String, val w: Int, val h: Int, val dur: Float, val fps: Float)

    private fun probe(uri: Uri): Probe? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(app, uri)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val x = w; w = h; h = x }
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val dur = durMs / 1000f
            val frames = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull()
            var fps = if (frames != null && dur > 0f) frames / dur else 0f
            if (fps < 1f) {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(app, uri, null)
                    for (i in 0 until ex.trackCount) {
                        val f = ex.getTrackFormat(i)
                        if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true &&
                            f.containsKey(MediaFormat.KEY_FRAME_RATE)
                        ) fps = f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                    }
                } finally { ex.release() }
            }
            if (fps < 1f) fps = 30f
            return Probe(displayName(uri), w, h, dur, fps)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        }
        return uri.lastPathSegment ?: "video"
    }
}
