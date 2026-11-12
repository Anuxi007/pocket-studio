package com.anuxi.pocketstudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.anuxi.pocketstudio.data.AudioFmt
import com.anuxi.pocketstudio.data.AudioQuality
import com.anuxi.pocketstudio.data.DlJob
import com.anuxi.pocketstudio.data.Errors
import com.anuxi.pocketstudio.data.Ytdlp
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * "Descarga rápida": baja el mejor formato compatible en segundo plano y muestra
 * el progreso en una notificación. Así la descarga sigue aunque cierres la ventana.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var processId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancel()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL)
        createChannels()
        // Android exige llamar a startForeground rapido tras startForegroundService.
        startForeground(
            NOTIF_PROGRESS,
            progressNotification("Buscando el video…", -1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (url == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) {
            Toast.makeText(this, "Ya hay una descarga en curso", Toast.LENGTH_SHORT).show()
            return START_NOT_STICKY
        }
        start(url)
        return START_NOT_STICKY
    }

    private fun start(url: String) {
        val pid = "svc${System.nanoTime()}"
        processId = pid
        job = scope.launch {
            try {
                val meta = Ytdlp.fetchInfo(this@DownloadService, url)
                val options = Ytdlp.buildVideoOptions(meta)
                val dlJob = (options.firstOrNull { it.recommended } ?: options.firstOrNull())
                    ?.let { DlJob.Video(it) }
                    ?: DlJob.Audio(AudioFmt.AAC, AudioQuality.BEST)
                var lastPct = -2
                val saved = Ytdlp.download(this@DownloadService, meta, dlJob, pid) { progress, _ ->
                    val pct = progress.toInt()
                    if (pct != lastPct) {
                        lastPct = pct
                        notify(NOTIF_PROGRESS, progressNotification(meta.title, pct))
                    }
                }
                notify(
                    NOTIF_RESULT,
                    resultNotification("Descarga lista", "${meta.title}\nGuardado en ${saved.where}"),
                )
            } catch (e: CancellationException) {
                // cancelada desde la notificacion
            } catch (e: YoutubeDL.CanceledException) {
                // cancelada desde la notificacion
            } catch (e: Exception) {
                val err = Errors.classify(e.message ?: e.toString())
                notify(NOTIF_RESULT, resultNotification(err.title, err.text))
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun cancel() {
        processId?.let {
            try {
                YoutubeDL.destroyProcessById(it)
            } catch (e: Exception) {
                // no habia proceso activo
            }
        }
        job?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ notificaciones

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Descargas en curso", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun notify(id: Int, notification: Notification) {
        try {
            getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (e: SecurityException) {
            // sin permiso de notificaciones: la descarga sigue igual
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(title: String, pct: Int): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Descargando")
            .setContentText(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Cancelar", cancelIntent)
        if (pct < 0) builder.setProgress(0, 0, true) else builder.setProgress(100, pct, false)
        return builder.build()
    }

    private fun resultNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

    companion object {
        private const val EXTRA_URL = "url"
        private const val ACTION_CANCEL = "com.anuxi.pocketstudio.CANCEL"
        private const val CHANNEL_PROGRESS = "progress"
        private const val CHANNEL_RESULT = "result"
        private const val NOTIF_PROGRESS = 1
        private const val NOTIF_RESULT = 2

        fun intent(context: Context, url: String): Intent =
            Intent(context, DownloadService::class.java).putExtra(EXTRA_URL, url)
    }
}
