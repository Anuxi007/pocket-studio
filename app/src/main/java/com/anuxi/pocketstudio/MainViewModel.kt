package com.anuxi.pocketstudio

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anuxi.pocketstudio.data.*
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()
    val prefs = Prefs(app)

    // navegacion y estado general
    var screen by mutableStateOf(Screen.Home)
    var welcomeAccepted by mutableStateOf(prefs.welcomeAccepted)
    var reduceMotion by mutableStateOf(prefs.reduceMotion)
    var folderLabel by mutableStateOf(Storage.folderLabel(app))

    // enlace y resultados
    var link by mutableStateOf("")
    var info by mutableStateOf<InfoState>(InfoState.Idle)
    var selectedVideo by mutableStateOf<String?>(null)
    var audioFormat by mutableStateOf(AudioFmt.AAC)
    var audioQuality by mutableStateOf(AudioQuality.BEST)

    // descargas
    var downloaderTab by mutableStateOf(0)
    var pendingFolderUri by mutableStateOf<String?>(null)
    var pendingFolderLabel by mutableStateOf<String?>(null)
    var maxParallel by mutableStateOf(prefs.maxParallel)
    var askPlayer by mutableStateOf(prefs.askPlayer)
    var askNotifications: (() -> Unit)? = null

    // actualizaciones
    var apkUpdate by mutableStateOf<ApkUpdateState>(ApkUpdateState.Idle)
    var ytVersion by mutableStateOf<String?>(null)
    var ytStatus by mutableStateOf<String?>(null)
    var ytBusy by mutableStateOf(false)

    private var infoJob: Job? = null

    init {
        DownloadManager.init(app)
        // Prepara el motor en segundo plano para que la primera descarga no espere.
        viewModelScope.launch {
            try {
                Engine.ensure(ctx)
                ytVersion = YoutubeDL.versionName(ctx)
            } catch (e: Exception) {
                ytStatus = "No se pudo preparar yt-dlp: ${e.message ?: "error desconocido"}"
            }
        }
    }

    // ------------------------------------------------------------ navegacion

    fun go(to: Screen) {
        screen = to
    }

    fun showDownloads() {
        screen = Screen.Downloader
        downloaderTab = 1
    }

    fun back() {
        screen = when (screen) {
            Screen.Video, Screen.Audio -> Screen.Downloader
            else -> Screen.Home
        }
    }

    // --------------------------------------------------------------- enlace

    fun onLinkChange(text: String) {
        link = text
        infoJob?.cancel()
        selectedVideo = null
        val url = Ytdlp.extractUrl(text)
        if (url == null) {
            info = InfoState.Idle
            return
        }
        info = InfoState.Loading
        infoJob = viewModelScope.launch {
            delay(500)
            try {
                val meta = Ytdlp.fetchInfo(ctx, url)
                val options = Ytdlp.buildVideoOptions(meta)
                info = InfoState.Ready(meta, options)
                selectedVideo = options.firstOrNull { it.recommended }?.formatId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                info = InfoState.Failed(Errors.classify(e.message ?: e.toString()))
            }
        }
    }

    fun retry() = onLinkChange(link)

    fun clearLink() = onLinkChange("")

    // ---------------------------------------------------- enlace desde "Compartir"

    /** Llega desde la ventana de compartir al tocar "Abrir". */
    fun openLink(url: String) {
        screen = Screen.Downloader
        downloaderTab = 0
        onLinkChange(url)
    }

    // -------------------------------------------------------------- descargas

    fun startVideoDownload(option: VideoOption? = null) {
        val ready = info as? InfoState.Ready ?: return
        val o = option ?: ready.videoOptions.firstOrNull { it.formatId == selectedVideo } ?: return
        enqueue(
            DlItem(
                id = newId(), url = ready.meta.url, title = ready.meta.title, thumb = ready.meta.thumbnail,
                kind = "video",
                label = listOfNotNull(o.label, o.codecName, o.container).joinToString(" · "),
                formatId = o.formatId, hasAudio = o.hasAudio, mergeToMp4 = o.mergeToMp4,
                sizeBytes = o.sizeBytes, folderUri = pendingFolderUri,
            )
        )
    }

    fun startAudioDownload() {
        val ready = info as? InfoState.Ready ?: return
        enqueue(
            DlItem(
                id = newId(), url = ready.meta.url, title = ready.meta.title, thumb = ready.meta.thumbnail,
                kind = "audio", label = "${audioFormat.label} · ${audioQuality.label}",
                audioExt = audioFormat.ext, audioKbps = audioQuality.kbps,
                folderUri = pendingFolderUri,
            )
        )
    }

    private fun newId() = "dl${System.nanoTime()}"

    private fun enqueue(item: DlItem) {
        askNotifications?.invoke()
        DownloadManager.enqueue(item)
        pendingFolderUri = null
        pendingFolderLabel = null
        link = ""
        info = InfoState.Idle
        selectedVideo = null
        downloaderTab = 1
        screen = Screen.Downloader
    }

    /** "Descargar otra versión" desde el historial. */
    fun downloadAgain(item: DlItem) {
        downloaderTab = 0
        onLinkChange(item.url)
    }

    /** Carpeta elegida solo para la descarga que se esta preparando. */
    fun setDownloadFolder(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            pendingFolderUri = uri.toString()
            pendingFolderLabel = Storage.labelOf(uri.toString())
        } catch (e: Exception) {
            // si el sistema no deja guardar el permiso, seguimos con la carpeta de siempre
        }
    }

    fun resetDownloadFolder() {
        pendingFolderUri = null
        pendingFolderLabel = null
    }

    fun changeMaxParallel(n: Int) {
        prefs.maxParallel = n
        maxParallel = prefs.maxParallel
    }

    fun changeAskPlayer(v: Boolean) {
        prefs.askPlayer = v
        askPlayer = v
    }

    // ------------------------------------------------------------- ajustes

    fun acceptWelcome() {
        prefs.welcomeAccepted = true
        welcomeAccepted = true
    }

    fun changeReduceMotion(v: Boolean) {
        prefs.reduceMotion = v
        reduceMotion = v
    }

    fun setFolder(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            prefs.folderUri = uri.toString()
            folderLabel = Storage.folderLabel(ctx)
        } catch (e: Exception) {
            // si el sistema no deja guardar el permiso, seguimos con la carpeta anterior
        }
    }

    fun resetFolder() {
        prefs.folderUri = null
        folderLabel = Storage.folderLabel(ctx)
    }

    fun updateYtdlp(thenRetry: Boolean = false) {
        if (ytBusy) return
        ytBusy = true
        ytStatus = "Buscando actualización…"
        viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    Engine.ensure(ctx)
                    YoutubeDL.updateYoutubeDL(ctx)
                }
                ytVersion = YoutubeDL.versionName(ctx)
                ytStatus = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "yt-dlp actualizado"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Ya tienes la última versión"
                    else -> "Listo"
                }
                if (thenRetry) retry()
            } catch (e: Exception) {
                ytStatus = "No se pudo actualizar: ${e.message ?: "error de red"}"
            } finally {
                ytBusy = false
            }
        }
    }

    fun checkApkUpdate() {
        apkUpdate = ApkUpdateState.Checking
        viewModelScope.launch { apkUpdate = ApkUpdater.check() }
    }
}
