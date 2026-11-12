package com.anuxi.pocketstudio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anuxi.pocketstudio.MainViewModel
import com.anuxi.pocketstudio.data.Screen

@Composable
fun PocketTheme(reduceMotion: Boolean, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Ps.Mint,
        onPrimary = Ps.OnMint,
        background = Ps.BgTop,
        surface = Ps.BgTop,
        onSurface = Ps.Text,
        onBackground = Ps.Text,
    )
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun PocketApp(vm: MainViewModel) {
    PocketTheme(vm.reduceMotion) {
        Box(Modifier.fillMaxSize().background(Ps.Background)) {
            BackHandler(enabled = vm.screen != Screen.Home) { vm.back() }

            val duration = if (vm.reduceMotion) 0 else 220
            AnimatedContent(
                targetState = vm.screen,
                transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                label = "screens",
            ) { screen ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .imePadding()
                        .padding(horizontal = 24.dp),
                ) {
                    when (screen) {
                        Screen.Home -> HomeScreen(vm)
                        Screen.Downloader -> DownloaderScreen(vm)
                        Screen.Video -> VideoScreen(vm)
                        Screen.Audio -> AudioScreen(vm)
                        Screen.Settings -> SettingsScreen(vm)
                    }
                }
            }

            if (!vm.welcomeAccepted) WelcomeDialog(vm)
        }
    }
}
