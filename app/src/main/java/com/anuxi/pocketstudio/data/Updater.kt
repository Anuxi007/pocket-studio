package com.anuxi.pocketstudio.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext
import com.anuxi.pocketstudio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Busca versiones nuevas de la app en los Releases del repositorio de GitHub. */
object ApkUpdater {
    private const val REPO = "Anuxi007/pocket-studio"

    suspend fun check(): ApkUpdateState = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("https://api.github.com/repos/$REPO/releases?per_page=5")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PocketStudio/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code == 404) {
                return@withContext ApkUpdateState.Failed(
                    "No se pudo comprobar. Si el repositorio es privado o aún no tiene " +
                        "versiones publicadas, las actualizaciones no están disponibles."
                )
            }
            if (code != 200) {
                return@withContext ApkUpdateState.Failed("GitHub respondió con el código $code.")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            var latest: org.json.JSONObject? = null
            for (i in 0 until releases.length()) {
                val r = releases.getJSONObject(i)
                if (!r.optBoolean("draft", false)) {
                    latest = r
                    break
                }
            }
            if (latest == null) {
                return@withContext ApkUpdateState.Failed("Todavía no hay versiones publicadas.")
            }
            val tag = latest.optString("tag_name").removePrefix("v")
            val current = BuildConfig.VERSION_NAME
            if (!isNewer(tag, current)) {
                return@withContext ApkUpdateState.UpToDate(current)
            }
            val assets = latest.optJSONArray("assets") ?: JSONArray()
            val apks = mutableListOf<Pair<String, String>>()
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk")) apks += name to a.optString("browser_download_url")
            }
            val url = pickApk(apks) ?: latest.optString("html_url")
            ApkUpdateState.Available(tag, latest.optString("body").trim().take(600), url)
        } catch (e: Exception) {
            ApkUpdateState.Failed("No se pudo comprobar: ${e.message ?: "error de red"}")
        }
    }

    private fun apkFile(context: Context) = File(context.cacheDir, "updates/PocketStudio.apk")

    /** Baja el APK a la carpeta temporal de la app. Lanza una excepcion con un mensaje claro si falla. */
    suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val file = apkFile(context)
            file.parentFile?.mkdirs()
            file.delete()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "PocketStudio/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub respondió con el código $code al bajar el APK.")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; onProgress(pct / 100f) }
                        }
                    }
                }
            }
            if (file.length() < 1_000_000) throw IllegalStateException("El archivo descargado parece incompleto.")
        }

    /**
     * Abre el instalador de Android. Devuelve false si falta el permiso de
     * "instalar apps desconocidas": en ese caso abre la pantalla donde se activa.
     */
    fun install(context: Context): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile(context))
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun pickApk(apks: List<Pair<String, String>>): String? {
        for (abi in Build.SUPPORTED_ABIS) {
            apks.firstOrNull { it.first.contains(abi) }?.let { return it.second }
        }
        return apks.firstOrNull()?.second
    }

    /** Compara "0.0.2" con "0.0.1" ignorando sufijos como "-alpha". */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
