package com.anuxi.pocketstudio

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.anuxi.pocketstudio.ui.PocketApp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
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
    }

    companion object {
        const val EXTRA_LINK = "ps_link"
    }
}
