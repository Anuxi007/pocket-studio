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

    // descarga
    var dl by mutableStateOf<DlState>(DlState.Idle)
    var shareUrl by mutableStateOf<String?>(null)

    // actualizaciones
    var apkUpdate by mutableStateOf<ApkUpdateState>(ApkUpdateState.Idle)
    var ytVersion by mutableStateOf<String?>(null)
    var ytStatus by mutableStateOf<String?>(null)
    var ytBusy by mutableStateOf(false)

    private var infoJob: Job? = null
    private var dlJob: Job? = null
    private var currentProcess: String? = null

    init {
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

    // -------------------------------------------------------------- compartir

    fun onShared(url: String) {
        shareUrl = url
    }

    fun openShared() {
        val url = shareUrl ?: return
        shareUrl = null
        screen = Screen.Downloader
        onLinkChange(url)
    }

    fun dismissShared() {
        shareUrl = null
    }

    fun quickDownload() {
        val url = shareUrl ?: return
        shareUrl = null
        val pid = newProcessId()
        dl = DlState.Running(-1f, "Buscando el video…")
        dlJob = viewModelScope.launch {
            try {
                val meta = Ytdlp.fetchInfo(ctx, url)
                val options = Ytdlp.buildVideoOptions(meta)
                val job = (options.firstOrNull { it.recommended } ?: options.firstOrNull())
                    ?.let { DlJob.Video(it) }
                    ?: DlJob.Audio(AudioFmt.AAC, AudioQuality.BEST)
                runDownload(meta, job, pid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: YoutubeDL.CanceledException) {
                dl = DlState.Idle
            } catch (e: Exception) {
                dl = DlState.Failed(Errors.classify(e.message ?: e.toString()))
            }
        }
    }

    // -------------------------------------------------------------- descargas

    private fun newProcessId(): String {
        val id = "dl${System.nanoTime()}"
        currentProcess = id
        return id
    }

    fun startVideoDownload() {
        val ready = info as? InfoState.Ready ?: return
        val option = ready.videoOptions.firstOrNull { it.formatId == selectedVideo } ?: return
        start(ready.meta, DlJob.Video(option))
    }

    fun startAudioDownload() {
        val ready = info as? InfoState.Ready ?: return
        start(ready.meta, DlJob.Audio(audioFormat, audioQuality))
    }

    private fun start(meta: VideoMeta, job: DlJob) {
        val pid = newProcessId()
        dl = DlState.Running(-1f, "Preparando…")
        dlJob?.cancel()
        dlJob = viewModelScope.launch {
            try {
                runDownload(meta, job, pid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: YoutubeDL.CanceledException) {
                dl = DlState.Idle
            } catch (e: Exception) {
                dl = DlState.Failed(Errors.classify(e.message ?: e.toString()))
            }
        }
    }

    private suspend fun runDownload(meta: VideoMeta, job: DlJob, pid: String) {
        val saved = Ytdlp.download(ctx, meta, job, pid) { progress, eta ->
            val pct = if (progress < 0) "" else " ${progress.toInt()} %"
            val rest = if (eta > 0) " · faltan ${eta} s" else ""
            dl = DlState.Running(progress / 100f, "Descargando…$pct$rest")
        }
        dl = DlState.Done(saved)
    }

    fun cancelDownload() {
        currentProcess?.let {
            try {
                YoutubeDL.destroyProcessById(it)
            } catch (e: Exception) {
                // si no habia proceso, no pasa nada
            }
        }
        dlJob?.cancel()
        dl = DlState.Idle
    }

    fun dismissDownload() {
        dl = DlState.Idle
    }

    // ------------------------------------------------------------- ajustes

    fun acceptWelcome() {
        prefs.welcomeAccepted = true
        welcomeAccepted = true
    }

    fun setReduceMotion(v: Boolean) {
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
