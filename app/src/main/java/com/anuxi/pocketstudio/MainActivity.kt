package com.anuxi.pocketstudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.anuxi.pocketstudio.ui.PocketApp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        vm.askNotifications = {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (savedInstanceState == null) handleLink(intent)
        setContent { PocketApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    /** "Abrir" desde la ventana de compartir: llega con el enlace ya listo. */
    private fun handleLink(intent: Intent?) {
        intent?.getStringExtra(EXTRA_LINK)?.let { vm.openLink(it) }
        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) vm.showDownloads()
    }

    companion object {
        const val EXTRA_LINK = "ps_link"
        const val EXTRA_OPEN_DOWNLOADS = "ps_open_downloads"
    }
}
