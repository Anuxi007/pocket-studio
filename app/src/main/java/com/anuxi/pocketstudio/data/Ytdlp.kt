package com.anuxi.pocketstudio.data

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Prepara yt-dlp, Python y FFmpeg la primera vez que se necesitan. */
object Engine {
    private val mutex = Mutex()

    @Volatile
    private var ready = false

    suspend fun ensure(context: Context) {
        if (ready) return
        mutex.withLock {
            if (!ready) {
                withContext(Dispatchers.IO) {
                    YoutubeDL.init(context.applicationContext)
                    FFmpeg.init(context.applicationContext)
                }
                ready = true
            }
        }
    }
}

object Ytdlp {
    private val urlRegex = Regex("""https?://\S+""")

    fun extractUrl(text: String): String? =
        urlRegex.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')

    // ---------------------------------------------------------------- info

    suspend fun fetchInfo(context: Context, url: String): VideoMeta = withContext(Dispatchers.IO) {
        Engine.ensure(context)
        val request = YoutubeDLRequest(url)
        request.addOption("--dump-single-json")
        request.addOption("--no-playlist")
        request.addOption("--no-warnings")
        val out = YoutubeDL.execute(request).out
        parse(url, out)
    }

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.int(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.long(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.dbl(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun fmtFrom(f: JSONObject, fallbackId: String): Fmt = Fmt(
        id = f.str("format_id") ?: fallbackId,
        ext = f.str("ext") ?: "mp4",
        vcodec = f.str("vcodec"),
        acodec = f.str("acodec"),
        width = f.int("width"),
        height = f.int("height"),
        fps = f.dbl("fps"),
        sizeBytes = f.long("filesize") ?: f.long("filesize_approx"),
        tbr = f.dbl("tbr"),
        abr = f.dbl("abr"),
    )

    private fun parse(url: String, out: String): VideoMeta {
        val o = JSONObject(out.trim())
        val formats = mutableListOf<Fmt>()
        val arr = o.optJSONArray("formats")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                if (f.str("ext") == "mhtml") continue // miniaturas, no es video
                formats += fmtFrom(f, "f$i")
            }
        }
        if (formats.isEmpty()) formats += fmtFrom(o, "best")
        return VideoMeta(
            url = url,
            title = o.str("title") ?: "Video",
            thumbnail = o.str("thumbnail"),
            durationSec = o.optDouble("duration", 0.0).toLong(),
            formats = formats,
        )
    }

    // ------------------------------------------------------------- opciones

    private fun codecRank(vcodec: String?): Int = when (Compat.codecName(vcodec)) {
        "H.264" -> 3
        "HEVC" -> 2
        "VP9" -> 1
        else -> 0
    }

    fun buildVideoOptions(meta: VideoMeta): List<VideoOption> {
        val dur = meta.durationSec
        val videos = meta.formats.filter { it.hasVideo && (it.height ?: 0) > 0 }
        if (videos.isEmpty()) return emptyList()

        val audioOnly = meta.formats.filter { !it.hasVideo && it.hasAudio }
        val audioBytes: Long = audioOnly.maxByOrNull { it.abr ?: 0.0 }?.let { a ->
            a.sizeBytes ?: a.abr?.let { (it * 125 * dur).toLong() }
        } ?: 0L

        data class Row(val fmt: Fmt, val compatible: Boolean)

        val rows = videos.groupBy { it.height!! }.map { (h, list) ->
            val checked = list.map { f ->
                val w = f.width ?: (h * 16 / 9)
                Row(f, Compat.supports(f.vcodec, w, h, f.fps ?: 30.0))
            }
            val pool = checked.filter { it.compatible }.ifEmpty { checked }
            pool.sortedWith(
                compareByDescending<Row> { codecRank(it.fmt.vcodec) }
                    .thenByDescending { it.fmt.tbr ?: 0.0 }
            ).first()
        }.sortedByDescending { it.fmt.height }

        // Si el celular no acepta nada (o no lo pudimos saber), no bloqueamos nada.
        val anyCompatible = rows.any { it.compatible }
        val firstCompatibleId = rows.firstOrNull { it.compatible || !anyCompatible }?.fmt?.id

        return rows.take(10).map { r ->
            val f = r.fmt
            val h = f.height!!
            val own = f.sizeBytes ?: f.tbr?.let { (it * 125 * dur).toLong() }
            val total = own?.let { if (f.hasAudio) it else it + audioBytes }
            val codec = Compat.codecName(f.vcodec)
            VideoOption(
                formatId = f.id,
                label = if (h >= 2160) "4K (${h}p)" else "${h}p",
                height = h,
                fps = f.fps?.takeIf { it > 0 }?.toInt(),
                codecName = codec,
                container = f.ext.uppercase(),
                sizeBytes = total,
                compatible = r.compatible || !anyCompatible,
                recommended = f.id == firstCompatibleId,
                hasAudio = f.hasAudio,
                mergeToMp4 = codec == "H.264" || codec == "HEVC",
                warning = if (r.compatible || !anyCompatible) null
                else Compat.reason(f.vcodec, f.width ?: (h * 16 / 9), h, f.fps ?: 30.0),
            )
        }
    }

    // ------------------------------------------------------------- descarga

    /** Carpeta temporal que se conserva al pausar para poder reanudar. */
    fun tmpDir(context: Context, id: String) = File(context.filesDir, "dl/$id")

    /** Descarga (o reanuda) un elemento de la cola y lo guarda en su carpeta final. */
    suspend fun download(
        context: Context,
        item: DlItem,
        onProgress: (Float, Long) -> Unit,
    ): Saved = withContext(Dispatchers.IO) {
        Engine.ensure(context)
        val tmp = tmpDir(context, item.id)
        tmp.mkdirs()
        val request = YoutubeDLRequest(item.url)
        request.addOption("--no-playlist")
        request.addOption("--continue")
        request.addOption("-o", "${tmp.absolutePath}/%(title).80s.%(ext)s")
        if (item.kind == "audio") {
            request.addOption("-f", "bestaudio/best")
            request.addOption("-x")
            request.addOption("--audio-format", item.audioExt ?: "m4a")
            request.addOption("--audio-quality", "${item.audioKbps}K")
        } else {
            val id = item.formatId ?: "best"
            val selector = when {
                id == "tikwm" -> "bv*+ba/b" // respaldo cuando TikWM falla
                item.hasAudio -> id
                else -> "$id+bestaudio[ext=m4a]/$id+bestaudio/$id"
            }
            request.addOption("-f", selector)
            if (item.mergeToMp4) request.addOption("--merge-output-format", "mp4")
        }
        YoutubeDL.execute(request, item.id) { progress, eta, _ -> onProgress(progress, eta) }

        val file = tmp.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".temp") }
            ?.maxByOrNull { it.length() }
            ?: throw YoutubeDLException("No se generó ningún archivo")
        Storage.save(context, file, item.folderUri)
    }
}
