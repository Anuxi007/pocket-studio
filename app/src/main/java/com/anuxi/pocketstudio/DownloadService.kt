package com.anuxi.pocketstudio

import com.anuxi.pocketstudio.data.tr
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.anuxi.pocketstudio.data.DlItem
import com.anuxi.pocketstudio.data.DownloadManager
import com.anuxi.pocketstudio.data.ItemStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mantiene vivo el proceso mientras hay descargas y muestra su progreso en una notificacion
 * (con Pausar y Cancelar). La cola en si vive en [DownloadManager].
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observer: Job? = null
    private var previous: Map<String, ItemStatus> = emptyMap()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DownloadManager.init(this)
        com.anuxi.pocketstudio.data.Lang.init(com.anuxi.pocketstudio.data.Prefs(this))
        createChannels()
        // Android exige llamar a startForeground rapido tras startForegroundService.
        startForeground(
            NOTIF_PROGRESS,
            progressNotification(DownloadManager.items.value.filter(::isActive)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_PAUSE -> id?.let { DownloadManager.pause(it) }
            ACTION_RESUME -> id?.let { DownloadManager.resume(it) }
            ACTION_CANCEL -> id?.let { DownloadManager.cancel(it) }
            ACTION_PAUSE_ALL -> DownloadManager.pauseAll()
            ACTION_CANCEL_ALL -> DownloadManager.cancelAll()
            else -> intent?.getStringExtra(EXTRA_URL)?.let { DownloadManager.enqueueQuick(this, it) }
        }
        if (observer == null) {
            observer = scope.launch { DownloadManager.items.collect { handle(it) } }
        }
        return START_NOT_STICKY
    }

    private fun isActive(i: DlItem) = i.status == ItemStatus.Running || i.status == ItemStatus.Queued

    private fun handle(list: List<DlItem>) {
        val prev = previous
        previous = list.associate { it.id to it.status }
        for (item in list) {
            val before = prev[item.id] ?: continue
            if (before == item.status) continue
            when (item.status) {
                ItemStatus.Done -> notify(
                    DownloadManager.resultNotificationId(item.id),
                    resultNotification(tr("Descarga lista", "Download ready"), tr("${item.title}\nGuardado en ${item.savedWhere ?: "tu carpeta"}", "${item.title}\nSaved to ${item.savedWhere ?: "your folder"}")),
                )
                ItemStatus.Failed -> notify(
                    DownloadManager.resultNotificationId(item.id),
                    resultNotification(tr("No se pudo descargar", "Couldn't download"), "${item.title}\n${item.error ?: ""}"),
                )
                ItemStatus.Paused -> notify(DownloadManager.pausedNotificationId(item.id), pausedNotification(item))
                else -> {}
            }
        }
        val active = list.filter(::isActive)
        if (active.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            notify(NOTIF_PROGRESS, progressNotification(active))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ notificaciones

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, tr("Descargas en curso", "Downloads in progress"), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, tr("Descargas terminadas", "Finished downloads"), NotificationManager.IMPORTANCE_DEFAULT)
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
        this, 0,
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(action: String, id: String?, code: Int): PendingIntent {
        val i = Intent(this, DownloadService::class.java).setAction(action)
        if (id != null) i.putExtra(EXTRA_ID, id)
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun progressNotification(active: List<DlItem>): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
        val running = active.filter { it.status == ItemStatus.Running }
        when {
            active.isEmpty() -> builder.setContentTitle(tr("Descargando", "Downloading")).setProgress(0, 0, true)
            active.size == 1 -> {
                val item = active[0]
                builder.setContentTitle(if (item.status == ItemStatus.Queued) tr("En cola", "Queued") else tr("Descargando", "Downloading"))
                    .setContentText(listOf(item.title, item.infoText).filter { it.isNotEmpty() }.joinToString(" · "))
                if (item.progress > 0f) builder.setProgress(100, item.progress.toInt(), false)
                else builder.setProgress(0, 0, true)
                builder.addAction(0, tr("Pausar", "Pause"), action(ACTION_PAUSE, item.id, 10 + (item.id.hashCode() and 0xFFF)))
                    .addAction(0, tr("Cancelar", "Cancel"), action(ACTION_CANCEL, item.id, 5000 + (item.id.hashCode() and 0xFFF)))
            }
            else -> {
                val avg = if (running.isEmpty()) 0 else (running.sumOf { it.progress.toDouble() } / running.size).toInt()
                builder.setContentTitle(tr("Descargando ${active.size} archivos", "Downloading ${active.size} files"))
                    .setContentText(running.firstOrNull()?.title ?: tr("En cola", "Queued"))
                    .setProgress(100, avg, false)
                    .addAction(0, tr("Pausar todo", "Pause all"), action(ACTION_PAUSE_ALL, null, 1))
                    .addAction(0, tr("Cancelar todo", "Cancel all"), action(ACTION_CANCEL_ALL, null, 2))
            }
        }
        return builder.build()
    }

    private fun pausedNotification(item: DlItem): Notification =
        NotificationCompat.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(tr("Descarga en pausa", "Download paused"))
            .setContentText(item.title)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(0, tr("Reanudar", "Resume"), action(ACTION_RESUME, item.id, 10000 + (item.id.hashCode() and 0xFFF)))
            .addAction(0, tr("Cancelar", "Cancel"), action(ACTION_CANCEL, item.id, 5000 + (item.id.hashCode() and 0xFFF)))
            .build()

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
        private const val EXTRA_ID = "id"
        private const val ACTION_PAUSE = "com.anuxi.pocketstudio.PAUSE"
        private const val ACTION_RESUME = "com.anuxi.pocketstudio.RESUME"
        private const val ACTION_CANCEL = "com.anuxi.pocketstudio.CANCEL"
        private const val ACTION_PAUSE_ALL = "com.anuxi.pocketstudio.PAUSE_ALL"
        private const val ACTION_CANCEL_ALL = "com.anuxi.pocketstudio.CANCEL_ALL"
        private const val CHANNEL_PROGRESS = "progress"
        private const val CHANNEL_RESULT = "result"
        private const val NOTIF_PROGRESS = 1

        /** Descarga rapida desde la ventana de compartir. */
        fun intent(context: Context, url: String): Intent =
            Intent(context, DownloadService::class.java).putExtra(EXTRA_URL, url)

        /** Solo despierta el servicio (la cola ya tiene el elemento). */
        fun wakeIntent(context: Context): Intent = Intent(context, DownloadService::class.java)
    }
}
