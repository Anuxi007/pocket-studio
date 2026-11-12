package com.anuxi.pocketstudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.anuxi.pocketstudio.data.DownloadManager
import com.anuxi.pocketstudio.data.Prefs
import com.anuxi.pocketstudio.data.Ytdlp
import com.anuxi.pocketstudio.ui.PocketTheme
import com.anuxi.pocketstudio.ui.ShareSheet

/**
 * Ventana flotante que aparece encima de la app desde la que compartes un enlace
 * (YouTube, TikTok, etc.). Ofrece "Descarga rápida" o "Abrir".
 */
class ShareActivity : ComponentActivity() {
    private var url: String? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startQuick() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val text = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val link = text?.let { Ytdlp.extractUrl(it) }
        if (link == null) {
            finish()
            return
        }
        url = link
        // La primera vez mostramos el aviso de uso responsable dentro de la app.
        if (!Prefs(this).welcomeAccepted) {
            openApp()
            return
        }
        DownloadManager.init(this)
        val duplicate = DownloadManager.findDuplicate(DownloadManager.items.value, link) != null
        setContent {
            PocketTheme(Prefs(this).reduceMotion) {
                ShareSheet(
                    url = link,
                    duplicate = duplicate,
                    onQuick = ::quick,
                    onOpen = ::openApp,
                    onDismiss = ::finish,
                )
            }
        }
    }

    private fun quick() {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startQuick()
        }
    }

    private fun startQuick() {
        val link = url ?: return
        ContextCompat.startForegroundService(this, DownloadService.intent(this, link))
        Toast.makeText(this, "Descargando… mira la notificación", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun openApp() {
        val link = url ?: return
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_LINK, link)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
