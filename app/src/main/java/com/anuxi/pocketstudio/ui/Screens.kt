package com.anuxi.pocketstudio.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anuxi.pocketstudio.BuildConfig
import com.anuxi.pocketstudio.MainViewModel
import com.anuxi.pocketstudio.R
import com.anuxi.pocketstudio.data.*

private val CardShape = RoundedCornerShape(20.dp)

// ============================================================== INICIO

@Composable
fun HomeScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(bottom = 24.dp)) {
        ScreenHeader("Pocket Studio", trailing = {
            RoundIconButton(Icons.Filled.Settings, "Opciones") { vm.go(Screen.Settings) }
        })
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("¿Qué quieres hacer hoy?", color = Ps.Muted, fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Ps.Mint)
                    .pressable { vm.go(Screen.Downloader) }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Ps.OnMint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { PsIcon(R.drawable.ic_download, Ps.OnMint, 28.dp) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Downloader", color = Ps.OnMint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Descarga video o audio con un enlace",
                        color = Ps.OnMint.copy(alpha = 0.78f),
                        fontSize = 14.sp,
                    )
                }
                PsIcon(Icons.Filled.KeyboardArrowRight, Ps.OnMint)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.5.dp, Ps.Line), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Ps.Card),
                    contentAlignment = Alignment.Center,
                ) { PsIcon(R.drawable.ic_scissors, Ps.Muted, 26.dp) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Editor", color = Color(0xFFD6D8F2), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Próximamente", color = Ps.Muted, fontSize = 14.sp)
                }
            }
        }
        Text(
            "Úsala solo con contenido propio, libre o con permiso.",
            color = Ps.Muted2,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ========================================================== DOWNLOADER

@Composable
fun DownloaderScreen(vm: MainViewModel) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader("Downloader", onBack = vm::back)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Enlace del video",
                    color = Ps.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Pegar",
                    color = Ps.Mint,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .pressable { clipboard.getText()?.text?.let(vm::onLinkChange) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
            OutlinedTextField(
                value = vm.link,
                onValueChange = vm::onLinkChange,
                singleLine = true,
                placeholder = { Text("Pega el enlace aquí", color = Ps.Muted2) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Ps.Text,
                    unfocusedTextColor = Ps.Text,
                    focusedBorderColor = Ps.Mint,
                    unfocusedBorderColor = Ps.Line,
                    cursorColor = Ps.Mint,
                    focusedContainerColor = Ps.Card,
                    unfocusedContainerColor = Ps.Card,
                ),
            )
        }

        when (val s = vm.info) {
            InfoState.Idle -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Pega un enlace de video para empezar.",
                    color = Ps.Muted2,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }

            InfoState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = Ps.Mint)
                    Text("Buscando video…", color = Ps.Muted, fontSize = 15.sp)
                }
            }

            is InfoState.Ready -> {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                    VideoCard(s.meta)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("¿Qué quieres descargar?", color = Ps.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        MintButton(
                            "Video",
                            iconRes = R.drawable.ic_video,
                            enabled = s.videoOptions.isNotEmpty(),
                        ) { vm.go(Screen.Video) }
                        OutlineButton("Audio", iconRes = R.drawable.ic_music) { vm.go(Screen.Audio) }
                        if (s.videoOptions.isEmpty()) {
                            Text(
                                "Este enlace solo tiene audio.",
                                color = Ps.Muted2,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            is InfoState.Failed -> Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    ErrorCard(s.error)
                }
                MintButton(s.error.actionLabel, enabled = !vm.ytBusy) {
                    when (s.error.action) {
                        ErrorAction.UpdateYtdlp -> vm.updateYtdlp(thenRetry = true)
                        ErrorAction.Clear -> vm.clearLink()
                        ErrorAction.Retry -> vm.retry()
                    }
                }
                if (vm.ytBusy || vm.ytStatus != null) {
                    Text(
                        vm.ytStatus ?: "",
                        color = Ps.Muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCard(meta: VideoMeta) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Ps.Card)) {
        Box(
            Modifier.fillMaxWidth().height(170.dp).background(Color(0xFF232750)),
            contentAlignment = Alignment.Center,
        ) {
            if (meta.thumbnail != null) {
                AsyncImage(
                    model = meta.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PsIcon(R.drawable.ic_video, Ps.Mint, 44.dp)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                meta.title,
                color = Ps.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val dur = formatDuration(meta.durationSec)
            if (dur.isNotEmpty()) Text("Duración $dur", color = Ps.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun ErrorCard(error: AppError) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Ps.Amber.copy(alpha = 0.10f))
                .border(BorderStroke(1.5.dp, Ps.Amber.copy(alpha = 0.35f)), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Ps.Amber.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { PsIcon(Icons.Filled.Warning, Ps.Amber, 26.dp) }
            Text(error.title, color = Ps.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(error.text, color = Color(0xFFE3E5F7), fontSize = 15.sp, lineHeight = 23.sp)
            Text(error.hint, color = Color(0xFFC3C6E8), fontSize = 13.sp, lineHeight = 19.sp)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ps.Card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Detalles técnicos", color = Ps.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                error.detail,
                color = Color(0xFFC3C6E8),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "Copiar detalles",
                color = Ps.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .border(BorderStroke(1.5.dp, Ps.Line), RoundedCornerShape(22.dp))
                    .pressable { clipboard.setText(AnnotatedString(error.detail)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

// ================================================================ VIDEO

@Composable
fun OptionRow(
    title: String,
    subtitle: String,
    size: String,
    selected: Boolean,
    enabled: Boolean = true,
    recommended: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Ps.Mint.copy(alpha = 0.10f) else Ps.Card)
            .border(
                BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) Ps.Mint else Ps.Line),
                shape,
            )
            .pressable(enabled = enabled, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = if (enabled) Ps.Text else Ps.Muted2,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (recommended) {
                    Spacer(Modifier.width(8.dp))
                    Badge("Recomendado", Ps.Mint, Ps.OnMint)
                }
                if (!enabled) {
                    Spacer(Modifier.width(8.dp))
                    Badge("No compatible", Ps.Amber.copy(alpha = 0.16f), Ps.Amber)
                }
            }
            Text(subtitle, color = if (enabled) Color(0xFFC3C6E8) else Ps.Muted2, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(size, color = if (enabled) Ps.Text else Ps.Muted2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun VideoScreen(vm: MainViewModel) {
    val ready = vm.info as? InfoState.Ready
    val selected = ready?.videoOptions?.firstOrNull { it.formatId == vm.selectedVideo }
    Column(
        Modifier.fillMaxSize().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Video", onBack = vm::back)
        Text(remember { Compat.summary() }, color = Ps.Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ready?.videoOptions?.forEach { o ->
                val sub = listOfNotNull(
                    o.fps?.let { "$it fps" },
                    o.codecName,
                    o.container,
                ).joinToString(" · ")
                OptionRow(
                    title = o.label,
                    subtitle = sub,
                    size = formatSize(o.sizeBytes),
                    selected = o.formatId == vm.selectedVideo,
                    enabled = o.compatible,
                    recommended = o.recommended,
                ) { vm.selectedVideo = o.formatId }
            }
        }
        Text(
            "Incluye el mejor audio compatible (AAC)",
            color = Ps.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        MintButton(
            text = if (selected != null) "Descargar ${selected.label} · ${formatSize(selected.sizeBytes)}" else "Elige una opción",
            iconRes = R.drawable.ic_download,
            enabled = selected != null,
        ) { vm.startVideoDownload() }
    }
}

// ================================================================ AUDIO

@Composable
fun AudioScreen(vm: MainViewModel) {
    val ready = vm.info as? InfoState.Ready
    val duration = ready?.meta?.durationSec ?: 0L
    fun sizeFor(q: AudioQuality): Long? = if (duration > 0) duration * q.kbps * 125 else null
    Column(
        Modifier.fillMaxSize().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader("Audio", onBack = vm::back)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Formato")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AudioFmt.entries.forEach { f ->
                    val sel = vm.audioFormat == f
                    val shape = RoundedCornerShape(18.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (sel) Ps.Mint.copy(alpha = 0.10f) else Ps.Card)
                            .border(BorderStroke(if (sel) 2.dp else 1.5.dp, if (sel) Ps.Mint else Ps.Line), shape)
                            .pressable { vm.audioFormat = f }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(f.label, color = Ps.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(f.desc, color = Color(0xFFC3C6E8), fontSize = 12.sp)
                        if (f == AudioFmt.AAC) {
                            Spacer(Modifier.height(2.dp))
                            Badge("Recomendado", Ps.Mint, Ps.OnMint)
                        }
                    }
                }
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Calidad")
            AudioQuality.entries.forEach { q ->
                OptionRow(
                    title = q.label,
                    subtitle = q.desc,
                    size = formatSize(sizeFor(q)),
                    selected = vm.audioQuality == q,
                    recommended = q == AudioQuality.BEST,
                ) { vm.audioQuality = q }
            }
        }

        MintButton(
            text = "Descargar ${vm.audioFormat.label} · ${formatSize(sizeFor(vm.audioQuality))}",
            iconRes = R.drawable.ic_download,
            enabled = ready != null,
        ) { vm.startAudioDownload() }
    }
}

// ============================================================== OPCIONES

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Ps.Card), content = content)
}

@Composable
private fun SmallButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = Ps.OnMint,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) Ps.Mint else Ps.Mint.copy(alpha = 0.35f))
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.setFolder(uri)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScreenHeader("Opciones", onBack = vm::back)

        // --- yt-dlp
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Componentes")
            SettingsCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("yt-dlp", color = Ps.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            vm.ytVersion?.let { "Versión $it" } ?: "Preparando…",
                            color = Ps.Mint,
                            fontSize = 13.sp,
                        )
                    }
                    SmallButton(if (vm.ytBusy) "Buscando…" else "Actualizar", enabled = !vm.ytBusy) {
                        vm.updateYtdlp()
                    }
                }
                vm.ytStatus?.let {
                    Text(it, color = Ps.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
                }
                HorizontalDivider(color = Ps.Line.copy(alpha = 0.5f))
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Python y FFmpeg", color = Ps.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("Incluidos en la app", color = Ps.Muted, fontSize = 13.sp)
                }
            }
        }

        // --- la app
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Aplicación")
            SettingsCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Pocket Studio", color = Ps.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Versión ${BuildConfig.VERSION_NAME} (alpha)", color = Ps.Muted, fontSize = 13.sp)
                    }
                    SmallButton(
                        if (vm.apkUpdate == ApkUpdateState.Checking) "Buscando…" else "Buscar actualización",
                        enabled = vm.apkUpdate != ApkUpdateState.Checking,
                    ) { vm.checkApkUpdate() }
                }
                when (val u = vm.apkUpdate) {
                    ApkUpdateState.Idle, ApkUpdateState.Checking -> {}
                    is ApkUpdateState.UpToDate -> Text(
                        "Ya tienes la última versión (${u.version}).",
                        color = Ps.Mint, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    )

                    is ApkUpdateState.Failed -> Text(
                        u.message,
                        color = Ps.Amber, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    )

                    is ApkUpdateState.Available -> Column(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Hay una versión nueva: ${u.version}", color = Ps.Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (u.notes.isNotEmpty()) Text(u.notes, color = Ps.Muted, fontSize = 13.sp, lineHeight = 19.sp)
                        Text(
                            "Se abrirá GitHub para descargar el APK. Luego ábrelo para instalar la actualización.",
                            color = Ps.Muted2, fontSize = 12.sp, lineHeight = 18.sp,
                        )
                        SmallButton("Descargar ${u.version}") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                        }
                    }
                }
            }
        }

        // --- descargas
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Descargas")
            SettingsCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable { picker.launch(null) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Carpeta de descargas", color = Ps.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(vm.folderLabel, color = Ps.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
                if (vm.folderLabel != Storage.DEFAULT_LABEL) {
                    HorizontalDivider(color = Ps.Line.copy(alpha = 0.5f))
                    Text(
                        "Usar la carpeta predeterminada",
                        color = Ps.Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().pressable { vm.resetFolder() }.padding(16.dp),
                    )
                }
            }
        }

        // --- apariencia
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Apariencia")
            SettingsCard {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Reducir animaciones", color = Ps.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = vm.reduceMotion,
                        onCheckedChange = vm::setReduceMotion,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ps.OnMint,
                            checkedTrackColor = Ps.Mint,
                            uncheckedThumbColor = Ps.Muted,
                            uncheckedTrackColor = Ps.CardStrong,
                        ),
                    )
                }
            }
        }

        // --- acerca de
        SettingsCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Uso responsable", color = Ps.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Pocket Studio es gratis y de código abierto (GPL-3.0). Úsala solo con contenido " +
                        "propio, libre o con permiso. Descargar contenido puede ir contra los términos " +
                        "de algunas plataformas.",
                    color = Ps.Muted, fontSize = 13.sp, lineHeight = 19.sp,
                )
                Text("github.com/Anuxi007/pocket-studio", color = Ps.Muted2, fontSize = 12.sp)
            }
        }
    }
}
