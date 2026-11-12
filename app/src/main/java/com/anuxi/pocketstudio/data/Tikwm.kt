package com.anuxi.pocketstudio.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

class TikwmException(message: String) : Exception(message)

/**
 * Motor alternativo OPCIONAL para TikTok: pide a tikwm.com (servicio de terceros, no oficial)
 * el video original. El enlace del usuario se envia a ese servicio.
 */
object Tikwm {
    private const val BASE = "https://www.tikwm.com"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"

    fun isTiktok(url: String): Boolean {
        val host = try { java.net.URI(url).host?.lowercase() ?: "" } catch (e: Exception) { "" }
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    private fun open(url: String, post: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.requestMethod = if (post) "POST" else "GET"
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
        c.setRequestProperty("Origin", BASE)
        c.setRequestProperty("Referer", "$BASE/originalDownloader.html")
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        return c
    }

    private fun blocked(code: Int? = null): TikwmException = TikwmException(
        tr("TikWM bloqueó la petición", "TikWM blocked the request") + (if (code != null) " (HTTP $code)" else "") +
            tr(", probablemente por su protección Cloudflare. Prueba con Wi-Fi, o desactiva el motor alternativo en Opciones.", ", probably due to its Cloudflare protection. Try Wi-Fi, or turn off the alternative engine in Settings.")
    )

    private fun json(c: HttpURLConnection): JSONObject {
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 403 || code == 503 || code == 429 && !body.trimStart().startsWith("{")) throw blocked(code)
        if (!body.trimStart().startsWith("{")) throw blocked(code)
        return try { JSONObject(body) } catch (e: Exception) { throw blocked(code) }
    }

    private suspend fun submit(url: String): String {
        repeat(3) {
            val c = open("$BASE/api/video/task/submit", post = true)
            c.doOutput = true
            val form = "url=${URLEncoder.encode(url, "UTF-8")}&web=1"
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            c.outputStream.use { it.write(form.toByteArray()) }
            val o = json(c)
            if (o.optInt("code", -1) == 0) {
                val id = o.optJSONObject("data")?.optString("task_id").orEmpty()
                if (id.isNotEmpty()) return id
            }
            val msg = o.optString("msg")
            if (msg.contains("limit", ignoreCase = true)) { delay(2000); return@repeat }
            throw TikwmException(tr("TikWM respondió: ${msg.ifEmpty { "error desconocido" }}", "TikWM responded: ${msg.ifEmpty { "unknown error" }}"))
        }
        throw TikwmException(tr("TikWM está limitando las peticiones. Intenta de nuevo en un momento.", "TikWM is rate-limiting requests. Try again in a moment."))
    }

    suspend fun download(
        context: Context,
        item: DlItem,
        onProgress: (Float, Long) -> Unit,
    ): Saved = withContext(Dispatchers.IO) {
        val taskId = submit(item.url)
        var playUrl: String? = null
        var title = item.title
        for (i in 0 until 40) {
            coroutineContext.ensureActive()
            delay(1500)
            val c = open("$BASE/api/video/task/result?task_id=${URLEncoder.encode(taskId, "UTF-8")}", post = false)
            val o = json(c)
            val detail = o.optJSONObject("data")?.optJSONObject("detail")
            val u = detail?.optString("play_url").orEmpty()
            if (u.isNotEmpty()) {
                playUrl = u
                detail?.optString("title")?.takeIf { it.isNotBlank() }?.let { title = it }
                break
            }
        }
        val url = playUrl ?: throw TikwmException(tr("TikWM tardó demasiado en preparar el video. Intenta de nuevo.", "TikWM took too long to prepare the video. Try again."))

        val tmp = Ytdlp.tmpDir(context, item.id)
        tmp.deleteRecursively() // con este motor, reanudar empieza de nuevo (el enlace caduca)
        tmp.mkdirs()
        val safe = title.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), " ").trim().take(80).ifEmpty { "tiktok" }
        val file = File(tmp, "$safe.mp4")

        val c = open(url, post = false)
        c.setRequestProperty("Accept", "*/*")
        val code = c.responseCode
        if (code !in 200..299) throw TikwmException(tr("No se pudo bajar el archivo (HTTP $code). El enlace pudo caducar; reintenta.", "Couldn't download the file (HTTP $code). The link may have expired; retry."))
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastPct = -1
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != lastPct) { lastPct = pct; onProgress(pct.toFloat(), 0L) }
                    }
                }
            }
        }
        if (file.length() == 0L) throw TikwmException(tr("El archivo descargado está vacío.", "The downloaded file is empty."))
        Storage.save(context, file, item.folderUri)
    }
}
