package com.anuxi.pocketstudio.data

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anuxi.pocketstudio.DownloadService
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ItemStatus { Queued, Running, Paused, Done, Failed }

/** Una descarga de la cola o del historial. */
data class DlItem(
    val id: String,
    val url: String,
    val title: String,
    val thumb: String?,
    /** "video", "audio" o "quick" (descarga rapida: el formato se elige al empezar). */
    val kind: String,
    val label: String,
    val formatId: String? = null,
    val hasAudio: Boolean = true,
    val mergeToMp4: Boolean = false,
    val audioExt: String? = null,
    val audioKbps: Int = 256,
    val folderUri: String? = null,
    val status: ItemStatus = ItemStatus.Queued,
    val progress: Float = 0f,
    val infoText: String = "",
    val savedWhere: String? = null,
    val savedUri: String? = null,
    val mime: String? = null,
    val sizeBytes: Long? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isAudio get() = kind == "audio" || (mime?.startsWith("audio/") == true)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("url", url); put("title", title); put("thumb", thumb ?: JSONObject.NULL)
        put("kind", kind); put("label", label); put("formatId", formatId ?: JSONObject.NULL)
        put("hasAudio", hasAudio); put("mergeToMp4", mergeToMp4)
        put("audioExt", audioExt ?: JSONObject.NULL); put("audioKbps", audioKbps)
        put("folderUri", folderUri ?: JSONObject.NULL); put("status", status.name)
        put("savedWhere", savedWhere ?: JSONObject.NULL); put("savedUri", savedUri ?: JSONObject.NULL)
        put("mime", mime ?: JSONObject.NULL); put("sizeBytes", sizeBytes ?: JSONObject.NULL)
        put("error", error ?: JSONObject.NULL); put("createdAt", createdAt)
    }

    companion object {
        private fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k)

        fun fromJson(o: JSONObject): DlItem = DlItem(
            id = o.getString("id"),
            url = o.getString("url"),
            title = o.optString("title", "Video"),
            thumb = o.s("thumb"),
            kind = o.optString("kind", "video"),
            label = o.optString("label", ""),
            formatId = o.s("formatId"),
            hasAudio = o.optBoolean("hasAudio", true),
            mergeToMp4 = o.optBoolean("mergeToMp4", false),
            audioExt = o.s("audioExt"),
            audioKbps = o.optInt("audioKbps", 256),
            folderUri = o.s("folderUri"),
            status = try { ItemStatus.valueOf(o.optString("status")) } catch (e: Exception) { ItemStatus.Paused },
            savedWhere = o.s("savedWhere"),
            savedUri = o.s("savedUri"),
            mime = o.s("mime"),
            sizeBytes = if (o.isNull("sizeBytes")) null else o.optLong("sizeBytes"),
            error = o.s("error"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

/**
 * Cola de descargas de toda la app. Vive mientras el proceso este vivo; el servicio
 * en primer plano (DownloadService) solo existe para que Android no lo cierre.
 */
object DownloadManager {
    private lateinit var app: Context
    private var inited = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val jobs = HashMap<String, Job>()
    private val stopping = HashMap<String, String>()

    private val _items = MutableStateFlow<List<DlItem>>(emptyList())
    val items: StateFlow<List<DlItem>> = _items

    private val historyFile get() = File(app.filesDir, "history.json")

    fun init(context: Context) {
        synchronized(lock) {
            if (inited) return
            app = context.applicationContext
            inited = true
            _items.value = load()
        }
    }

    // ------------------------------------------------------------ historial

    private fun load(): List<DlItem> = try {
        val arr = JSONArray(historyFile.readText())
        (0 until arr.length()).map { DlItem.fromJson(arr.getJSONObject(it)) }.map {
            // Lo que estaba en curso cuando se cerro la app queda en pausa.
            if (it.status == ItemStatus.Running || it.status == ItemStatus.Queued) {
                it.copy(status = ItemStatus.Paused)
            } else it
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            _items.value.take(300).forEach { arr.put(it.toJson()) }
            historyFile.writeText(arr.toString())
        } catch (e: Exception) {
            // si no se puede guardar el historial, las descargas siguen funcionando
        }
    }

    private fun get(id: String) = _items.value.firstOrNull { it.id == id }

    private fun update(id: String, f: (DlItem) -> DlItem) {
        _items.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    // ------------------------------------------------------------ duplicados

    /** Quita lo que no cambia el video (rastreadores, www, youtu.be) para comparar enlaces. */
    fun normalizeUrl(raw: String): String {
        return try {
            val u = Uri.parse(raw.trim())
            var host = (u.host ?: return raw.trim()).lowercase().removePrefix("www.").removePrefix("m.")
            if (host == "youtu.be") {
                return "youtube.com/watch?v=" + (u.pathSegments.firstOrNull() ?: "")
            }
            if (host.endsWith("youtube.com")) {
                val v = u.getQueryParameter("v")
                if (v != null) return "youtube.com/watch?v=$v"
                host = "youtube.com"
            }
            val tracking = setOf("si", "igsh", "igshid", "fbclid", "feature", "utm_source", "utm_medium",
                "utm_campaign", "utm_term", "utm_content", "is_from_webapp", "sender_device", "share_app_id")
            val query = u.queryParameterNames.filter { it !in tracking }.sorted()
                .joinToString("&") { "$it=${u.getQueryParameter(it)}" }
            host + (u.path ?: "").trimEnd('/') + if (query.isNotEmpty()) "?$query" else ""
        } catch (e: Exception) {
            raw.trim()
        }
    }

    fun findDuplicate(list: List<DlItem>, url: String): DlItem? {
        val key = normalizeUrl(url)
        return list.firstOrNull { it.status != ItemStatus.Failed && normalizeUrl(it.url) == key }
    }

    // --------------------------------------------------------------- acciones

    fun enqueue(item: DlItem) {
        _items.update { listOf(item) + it }
        persist()
        wakeService()
        pump()
    }

    fun enqueueQuick(context: Context, url: String) {
        init(context)
        enqueue(
            DlItem(
                id = "q${System.nanoTime()}",
                url = url,
                title = Uri.parse(url).host?.removePrefix("www.") ?: "Descarga rápida",
                thumb = null,
                kind = "quick",
                label = "Descarga rápida",
            )
        )
    }

    fun pause(id: String) {
        val item = get(id) ?: return
        when (item.status) {
            ItemStatus.Running -> {
                synchronized(lock) { stopping[id] = "pause" }
                update(id) { it.copy(status = ItemStatus.Paused, infoText = "") }
                stopProcess(id)
            }
            ItemStatus.Queued -> update(id) { it.copy(status = ItemStatus.Paused, infoText = "") }
            else -> {}
        }
        persist()
    }

    fun resume(id: String) {
        val item = get(id) ?: return
        if (item.status != ItemStatus.Paused && item.status != ItemStatus.Failed) return
        hideNotifications(id)
        update(id) { it.copy(status = ItemStatus.Queued, error = null, infoText = "") }
        persist()
        wakeService()
        pump()
    }

    fun cancel(id: String) {
        val item = get(id) ?: return
        if (item.status == ItemStatus.Running) {
            synchronized(lock) { stopping[id] = "cancel" }
            stopProcess(id)
        }
        hideNotifications(id)
        _items.update { list -> list.filter { it.id != id } }
        Ytdlp.tmpDir(app, id).deleteRecursively()
        persist()
    }

    fun pauseAll() = _items.value.filter { it.status == ItemStatus.Running || it.status == ItemStatus.Queued }
        .forEach { pause(it.id) }

    fun cancelAll() = _items.value.filter { it.status != ItemStatus.Done }.forEach { cancel(it.id) }

    /** Quita del historial; si deleteFile, tambien borra el archivo del celular. */
    fun remove(ids: Collection<String>, deleteFile: Boolean) {
        val targets = _items.value.filter { it.id in ids && it.status == ItemStatus.Done }
        if (deleteFile) targets.forEach { it.savedUri?.let { u -> Storage.delete(app, u) } }
        val set = targets.map { it.id }.toSet()
        _items.update { list -> list.filter { it.id !in set } }
        persist()
    }

    fun clearCompleted() {
        remove(_items.value.filter { it.status == ItemStatus.Done }.map { it.id }, deleteFile = false)
    }

    private fun stopProcess(id: String) {
        try {
            YoutubeDL.destroyProcessById(id)
        } catch (e: Exception) {
            // no habia proceso activo
        }
        synchronized(lock) { jobs[id]?.cancel() }
    }

    // ------------------------------------------------------------------ motor

    private fun pump() {
        synchronized(lock) {
            val max = Prefs(app).maxParallel
            while (jobs.size < max) {
                val next = _items.value
                    .filter { it.status == ItemStatus.Queued && it.id !in jobs }
                    .minByOrNull { it.createdAt } ?: break
                update(next.id) { it.copy(status = ItemStatus.Running, infoText = "Preparando…") }
                val job = scope.launch(start = CoroutineStart.LAZY) { run(next.id) }
                jobs[next.id] = job
                job.start()
            }
        }
    }

    private suspend fun run(id: String) {
        val self = currentCoroutineContext()[Job]
        try {
            var item = get(id) ?: return
            if (item.kind == "quick") item = resolveQuick(item)
            var lastPct = -2
            val useTikwm = Prefs(app).tikwmEnabled && Tikwm.isTiktok(item.url)
            if (useTikwm) update(id) { it.copy(label = "Original · TikWM") }
            val onProg: (Float, Long) -> Unit = { progress, eta ->
                val pct = progress.toInt()
                if (pct != lastPct) {
                    lastPct = pct
                    val text = if (eta > 0) "$pct % · faltan ${eta} s" else if (pct >= 0) "$pct %" else "Descargando…"
                    update(id) { it.copy(progress = progress.coerceIn(0f, 100f), infoText = text) }
                }
            }
            val saved = if (useTikwm) Tikwm.download(app, item, onProg) else Ytdlp.download(app, item, onProg)
            update(id) {
                it.copy(
                    status = ItemStatus.Done, progress = 100f, infoText = "",
                    savedWhere = saved.where, savedUri = saved.uri, mime = saved.mime,
                    sizeBytes = saved.sizeBytes, error = null,
                    createdAt = System.currentTimeMillis(),
                )
            }
            Ytdlp.tmpDir(app, id).deleteRecursively()
        } catch (e: CancellationException) {
            // pausada o cancelada: ya se actualizo el estado
        } catch (e: Exception) {
            val flag = synchronized(lock) { stopping[id] }
            if (flag == null) {
                val msg = if (e is TikwmException) e.message ?: "Error de TikWM" else {
                    val err = Errors.classify(e.message ?: e.toString())
                    err.title + ": " + err.text
                }
                update(id) { it.copy(status = ItemStatus.Failed, infoText = "", error = msg) }
            }
        } finally {
            synchronized(lock) {
                if (jobs[id] === self) jobs.remove(id)
                stopping.remove(id)
            }
            persist()
            pump()
        }
    }

    /** Descarga rapida: busca el video y elige el mejor formato compatible. */
    private suspend fun resolveQuick(item: DlItem): DlItem {
        val meta = Ytdlp.fetchInfo(app, item.url)
        val options = Ytdlp.buildVideoOptions(meta)
        val pick = options.firstOrNull { it.recommended } ?: options.firstOrNull()
        val resolved = if (pick != null) {
            item.copy(
                title = meta.title, thumb = meta.thumbnail, kind = "video",
                label = listOfNotNull(pick.label, pick.codecName, pick.container).joinToString(" · "),
                formatId = pick.formatId, hasAudio = pick.hasAudio, mergeToMp4 = pick.mergeToMp4,
            )
        } else {
            item.copy(
                title = meta.title, thumb = meta.thumbnail, kind = "audio",
                label = "AAC · M4A", audioExt = AudioFmt.AAC.ext, audioKbps = AudioQuality.BEST.kbps,
            )
        }
        update(item.id) { resolved.copy(status = it.status) }
        return resolved
    }

    // ---------------------------------------------------------- notificaciones

    fun pausedNotificationId(id: String) = 100000 + (id.hashCode() and 0xFFFF)
    fun resultNotificationId(id: String) = 200000 + (id.hashCode() and 0xFFFF)

    private fun hideNotifications(id: String) {
        try {
            val nm = NotificationManagerCompat.from(app)
            nm.cancel(pausedNotificationId(id))
            nm.cancel(resultNotificationId(id))
        } catch (e: Exception) {
            // nada que quitar
        }
    }

    private fun wakeService() {
        try {
            ContextCompat.startForegroundService(app, DownloadService.wakeIntent(app))
        } catch (e: Exception) {
            // si Android no deja iniciar el servicio, la descarga sigue mientras la app este abierta
        }
    }
}
