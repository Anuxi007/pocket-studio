package com.anuxi.pocketstudio.data

import java.util.Locale

enum class Screen { Home, Downloader, Video, Audio, Settings }

/** Un formato tal como lo reporta yt-dlp. */
data class Fmt(
    val id: String,
    val ext: String,
    val vcodec: String?,
    val acodec: String?,
    val width: Int?,
    val height: Int?,
    val fps: Double?,
    val sizeBytes: Long?,
    val tbr: Double?,
    val abr: Double?,
) {
    val hasVideo get() = vcodec != null && vcodec != "none"
    val hasAudio get() = acodec != null && acodec != "none"
}

data class VideoMeta(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val durationSec: Long,
    val formats: List<Fmt>,
)

/** Una fila de la lista de video (una por resolucion). */
data class VideoOption(
    val formatId: String,
    val label: String,
    val height: Int,
    val fps: Int?,
    val codecName: String,
    val container: String,
    val sizeBytes: Long?,
    val compatible: Boolean,
    val recommended: Boolean,
    val hasAudio: Boolean,
    val mergeToMp4: Boolean,
)

enum class AudioFmt(val label: String, val ext: String, val desc: String) {
    AAC("AAC", "m4a", "M4A · mejor calidad por tamaño"),
    MP3("MP3", "mp3", "Lo lee cualquier aparato"),
}

enum class AudioQuality(val label: String, val kbps: Int, val desc: String) {
    BEST("Mejor", 256, "Hasta 256 kbps"),
    MEDIUM("Media", 160, "160 kbps"),
    SAVER("Ahorro", 96, "96 kbps"),
}

sealed interface DlJob {
    data class Video(val option: VideoOption) : DlJob
    data class Audio(val format: AudioFmt, val quality: AudioQuality) : DlJob
}

data class Saved(val where: String, val fellBack: Boolean)

enum class ErrorAction { UpdateYtdlp, Clear, Retry }

data class AppError(
    val title: String,
    val text: String,
    val hint: String,
    val detail: String,
    val action: ErrorAction,
    val actionLabel: String,
)

sealed interface InfoState {
    data object Idle : InfoState
    data object Loading : InfoState
    data class Ready(val meta: VideoMeta, val videoOptions: List<VideoOption>) : InfoState
    data class Failed(val error: AppError) : InfoState
}

sealed interface DlState {
    data object Idle : DlState
    data class Running(val progress: Float, val text: String) : DlState
    data class Done(val saved: Saved) : DlState
    data class Failed(val error: AppError) : DlState
}

sealed interface ApkUpdateState {
    data object Idle : ApkUpdateState
    data object Checking : ApkUpdateState
    data class UpToDate(val version: String) : ApkUpdateState
    data class Available(val version: String, val notes: String, val url: String) : ApkUpdateState
    data class Failed(val message: String) : ApkUpdateState
}

fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "tamaño desconocido"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        mb < 1 -> String.format(Locale.US, "~%.0f KB", kb)
        mb < 10 -> String.format(Locale.US, "~%.1f MB", mb)
        gb < 1 -> String.format(Locale.US, "~%.0f MB", mb)
        else -> String.format(Locale.US, "~%.1f GB", gb)
    }
}

fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
