package com.anuxi.pocketstudio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.anuxi.pocketstudio.MainViewModel
import com.anuxi.pocketstudio.R
import com.anuxi.pocketstudio.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Danger = Color(0xFFFF8A8A)
private val RowShape = RoundedCornerShape(18.dp)
private val MenuDialog = DialogProperties(usePlatformDefaultWidth = false)

fun formatDay(millis: Long): String = SimpleDateFormat("d MMM", Locale("es", "ES")).format(Date(millis))

// ------------------------------------------------------------ piezas chicas

@Composable
fun SmallPill(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val fg = when {
        filled -> Ps.OnMint
        danger -> Danger
        else -> Ps.Text
    }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(if (enabled) Ps.Mint else Ps.Mint.copy(alpha = 0.35f))
                else Modifier.border(BorderStroke(1.5.dp, if (danger) Danger.copy(alpha = 0.5f) else Ps.Line), shape)
            )
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun TextAction(text: String, color: Color = Ps.Mint, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.pressable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
private fun MiniButton(description: String, tint: Color = Ps.Text, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Ps.CardStrong)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun Thumb(item: DlItem) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF232750)),
        contentAlignment = Alignment.Center,
    ) {
        if (item.thumb != null) {
            AsyncImage(
                model = item.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PsIcon(if (item.isAudio) R.drawable.ic_music else R.drawable.ic_video, Ps.Mint, 22.dp)
        }
    }
}

// ---------------------------------------------------------------- pestanas

@Composable
fun DownloaderTabs(selected: Int, activeCount: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ps.Card)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        @Composable
        fun Tab(index: Int, text: String, badge: Int) {
            val on = selected == index
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) Ps.Mint else Color.Transparent)
                    .pressable { onSelect(index) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text, color = if (on) Ps.OnMint else Ps.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (badge > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$badge",
                        color = if (on) Ps.Mint else Ps.OnMint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (on) Ps.OnMint else Ps.Mint)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Tab(0, tr("Nuevo enlace", "New link"), 0)
        Tab(1, tr("Descargas", "Downloads"), activeCount)
    }
}

// ------------------------------------------------------------- lista

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColumnScope.DownloadsTab(vm: MainViewModel, items: List<DlItem>) {
    val context = LocalContext.current
    val active = items.filter { it.status != ItemStatus.Done }
    val done = items.filter { it.status == ItemStatus.Done }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var menu by remember { mutableStateOf<DlItem?>(null) }
    var confirmDelete by remember { mutableStateOf<List<String>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    // Si lo seleccionado ya no existe (se borro), lo quitamos de la seleccion.
    LaunchedEffect(done.size) {
        selected.retainAll(done.map { it.id }.toSet())
        if (done.isEmpty()) selecting = false
    }

    if (items.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                tr("Aquí verás tus descargas.\nEmpieza pegando un enlace.", "Your downloads will show up here.\nStart by pasting a link."),
                color = Ps.Muted2, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
            )
        }
        return
    }

    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!selecting && active.isNotEmpty()) {
            SectionLabel(tr("En curso", "In progress"))
            active.forEach { ActiveRow(it) }
            Spacer(Modifier.height(6.dp))
        }
        if (done.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (selecting) {
                        Text("${selected.size} seleccionados", color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    } else SectionLabel(tr("Completadas", "Completed"))
                }
                if (selecting) {
                    TextAction(tr("Todos", "All")) { selected.clear(); selected.addAll(done.map { it.id }) }
                    TextAction(tr("Listo", "Done"), Ps.Muted) { selecting = false; selected.clear() }
                } else {
                    TextAction(tr("Seleccionar", "Select")) { selecting = true }
                    TextAction(tr("Limpiar", "Clear"), Ps.Muted) { confirmClear = true }
                }
            }
            done.forEach { item ->
                val isSel = item.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RowShape)
                        .background(if (isSel) Ps.Mint.copy(alpha = 0.10f) else Ps.Card)
                        .border(BorderStroke(1.5.dp, if (isSel) Ps.Mint.copy(alpha = 0.5f) else Color.Transparent), RowShape)
                        .combinedClickable(
                            onClick = {
                                if (selecting) {
                                    if (isSel) selected.remove(item.id) else selected.add(item.id)
                                } else FileActions.open(context, item)
                            },
                            onLongClick = {
                                if (!selecting) selecting = true
                                if (!isSel) selected.add(item.id)
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSel) Ps.Mint else Color.Transparent)
                                .border(BorderStroke(2.dp, if (isSel) Ps.Mint else Ps.Muted2), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (isSel) PsIcon(Icons.Filled.Check, Ps.OnMint, 16.dp) }
                        Spacer(Modifier.width(10.dp))
                    }
                    Thumb(item)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.title, color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(item.label, item.sizeBytes?.let { formatSize(it) }, formatDay(item.createdAt)).joinToString(" · "),
                            color = Ps.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!selecting) {
                        Spacer(Modifier.width(8.dp))
                        MiniButton(tr("Más opciones", "More options"), onClick = { menu = item }) {
                            PsIcon(Icons.Filled.MoreVert, Ps.Text, 20.dp)
                        }
                    }
                }
            }
        }
    }

    // barra de abajo
    if (selecting) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val chosen = done.filter { it.id in selected }
            SmallPill(tr("Compartir (${chosen.size})", "Share (${chosen.size})"), Modifier.weight(1f), filled = true, enabled = chosen.isNotEmpty()) {
                FileActions.share(context, chosen)
            }
            SmallPill(tr("Eliminar (${chosen.size})", "Delete (${chosen.size})"), Modifier.weight(1f), danger = true, enabled = chosen.isNotEmpty()) {
                confirmDelete = chosen.map { it.id }
            }
        }
    } else if (active.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallPill(tr("Pausar todo", "Pause all"), Modifier.weight(1f)) { DownloadManager.pauseAll() }
            SmallPill(tr("Cancelar todo", "Cancel all"), Modifier.weight(1f), danger = true) { DownloadManager.cancelAll() }
        }
    }

    // ---- menu del archivo
    menu?.let { item ->
        Dialog(onDismissRequest = { menu = null }, properties = MenuDialog) {
            SheetColumn {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Thumb(item)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(item.title, color = Ps.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(item.label, item.sizeBytes?.let { formatSize(it) }).joinToString(" · "),
                            color = Ps.Muted, fontSize = 12.sp,
                        )
                    }
                }
                MenuRow(tr("Abrir con…", "Open with…"), if (vm.askPlayer) tr("Eliges el reproductor", "Pick the player") else tr("Reproductor predeterminado", "Default player")) {
                    menu = null; FileActions.open(context, item)
                }
                MenuRow(tr("Compartir", "Share")) { menu = null; FileActions.share(context, listOf(item)) }
                MenuRow(tr("Descargar otra versión", "Download another version"), tr("Otro formato o calidad", "Another format or quality")) { menu = null; vm.downloadAgain(item) }
                MenuRow(tr("Quitar del historial", "Remove from history"), tr("El archivo se queda en tu celular", "The file stays on your phone")) {
                    menu = null; DownloadManager.remove(listOf(item.id), deleteFile = false)
                }
                MenuRow(tr("Eliminar archivo", "Delete file"), tr("Se borra del celular", "Deleted from your phone"), Danger) {
                    menu = null; confirmDelete = listOf(item.id)
                }
            }
        }
    }

    // ---- confirmaciones
    confirmDelete?.let { ids ->
        Dialog(onDismissRequest = { confirmDelete = null }, properties = MenuDialog) {
            SheetColumn {
                Text(
                    if (ids.size == 1) tr("¿Eliminar este archivo?", "Delete this file?") else tr("¿Eliminar ${ids.size} archivos?", "Delete ${ids.size} files?"),
                    color = Ps.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Text(tr("Se borrarán del celular. No se puede deshacer.", "They will be deleted from your phone. This can't be undone."), color = Color(0xFFC3C6E8), fontSize = 14.sp, lineHeight = 21.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallPill(tr("Cancelar", "Cancel"), Modifier.weight(1f)) { confirmDelete = null }
                    SmallPill(tr("Eliminar", "Delete"), Modifier.weight(1f), danger = true) {
                        DownloadManager.remove(ids, deleteFile = true)
                        selected.clear(); selecting = false; confirmDelete = null
                    }
                }
            }
        }
    }
    if (confirmClear) {
        Dialog(onDismissRequest = { confirmClear = false }, properties = MenuDialog) {
            SheetColumn {
                Text(tr("¿Limpiar completadas?", "Clear completed?"), color = Ps.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    tr("Se quitan de la lista, pero los archivos se quedan en tu celular.", "They're removed from the list, but the files stay on your phone."),
                    color = Color(0xFFC3C6E8), fontSize = 14.sp, lineHeight = 21.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallPill(tr("Cancelar", "Cancel"), Modifier.weight(1f)) { confirmClear = false }
                    SmallPill(tr("Limpiar", "Clear"), Modifier.weight(1f), filled = true) {
                        DownloadManager.clearCompleted(); confirmClear = false
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String? = null, color: Color = Ps.Text, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pressable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, color = Ps.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ActiveRow(item: DlItem) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ps.Card).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(item)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val pct = item.progress.toInt()
                val (text, color) = when (item.status) {
                    ItemStatus.Running -> listOf(item.label, item.infoText.ifEmpty { tr("Preparando…", "Preparing…") }).joinToString(" · ") to Ps.Muted
                    ItemStatus.Paused -> tr("Pausada", "Paused") + (if (pct > 0) " · $pct %" else "") to Ps.Amber
                    ItemStatus.Queued -> tr("En cola · espera su turno", "Queued · waiting its turn") to Ps.Muted
                    ItemStatus.Failed -> (item.error ?: tr("Falló", "Failed")) to Ps.Amber
                    ItemStatus.Done -> "" to Ps.Muted
                }
                Text(text, color = color, fontSize = 12.sp, maxLines = if (item.status == ItemStatus.Failed) 3 else 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item.status) {
                    ItemStatus.Running -> MiniButton(tr("Pausar", "Pause"), onClick = { DownloadManager.pause(item.id) }) {
                        PsIcon(R.drawable.ic_pause, Ps.Text, 18.dp)
                    }
                    ItemStatus.Paused -> MiniButton(tr("Reanudar", "Resume"), onClick = { DownloadManager.resume(item.id) }) {
                        PsIcon(Icons.Filled.PlayArrow, Ps.Mint, 20.dp)
                    }
                    ItemStatus.Failed -> MiniButton(tr("Reintentar", "Retry"), onClick = { DownloadManager.resume(item.id) }) {
                        PsIcon(Icons.Filled.Refresh, Ps.Mint, 20.dp)
                    }
                    else -> {}
                }
                MiniButton(tr("Cancelar", "Cancel"), onClick = { DownloadManager.cancel(item.id) }) {
                    PsIcon(Icons.Filled.Close, Danger, 20.dp)
                }
            }
        }
        if (item.status != ItemStatus.Failed) {
            val track = Ps.CardStrong
            val barColor = if (item.status == ItemStatus.Paused) Ps.Amber else Ps.Mint
            val mod = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            if (item.status == ItemStatus.Running && item.progress <= 0f) {
                LinearProgressIndicator(modifier = mod, color = barColor, trackColor = track)
            } else {
                LinearProgressIndicator(
                    progress = (item.progress / 100f).coerceIn(0f, 1f),
                    modifier = mod, color = barColor, trackColor = track,
                )
            }
        }
    }
}

// --------------------------------------------------- avisos y carpeta

@Composable
fun DuplicateBanner(item: DlItem, onSee: () -> Unit) {
    val blue = Color(0xFF8FB6FF)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(blue.copy(alpha = 0.10f))
            .border(BorderStroke(1.5.dp, blue.copy(alpha = 0.38f)), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(tr("Ya descargaste este video", "You already downloaded this video"), color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        val where = if (item.status == ItemStatus.Done) "${formatDay(item.createdAt)} · ${item.label}" else tr("Está en tu lista de descargas", "It's in your downloads list")
        Text(
            tr("$where. Puedes bajarlo otra vez en otro formato o calidad.", "$where. You can download it again in another format or quality."),
            color = Color(0xFFE3E5F7), fontSize = 13.sp, lineHeight = 19.sp,
        )
        TextAction(tr("Ver en descargas", "View in downloads"), blue, onSee)
    }
}

/** Fila tr("Guardar en", "Save to") con la carpeta de esta descarga. */
@Composable
fun FolderRow(vm: MainViewModel, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ps.Card)
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("Guardar en", "Save to"), color = Ps.Muted, fontSize = 12.sp)
            Text(
                vm.pendingFolderLabel ?: vm.folderLabel,
                color = Ps.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (vm.pendingFolderUri != null) TextAction(tr("Restablecer", "Reset"), Ps.Muted) { vm.resetDownloadFolder() }
        TextAction(tr("Cambiar", "Change"), onClick = onPick)
    }
}

/** Aviso (sin bloqueo) cuando el video elegido quiza no se reproduzca bien en este celular. */
@Composable
fun CompatWarningDialog(
    option: VideoOption,
    recommended: VideoOption?,
    onRecommended: () -> Unit,
    onAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = MenuDialog) {
        SheetColumn {
            Text(tr("Puede no reproducirse bien", "May not play well"), color = Ps.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "${option.label} · ${option.codecName} · ${option.container}",
                color = Ps.Amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(option.warning ?: tr("Tu celular podría no reproducirlo bien.", "Your phone may not play it well."), color = Color(0xFFE3E5F7), fontSize = 14.sp, lineHeight = 21.sp)
            Text(
                tr("Puedes descargarlo igual, por ejemplo para verlo en otro dispositivo.", "You can still download it, for example to watch on another device."),
                color = Color(0xFFC3C6E8), fontSize = 13.sp, lineHeight = 19.sp,
            )
            if (recommended != null) {
                MintButton(tr("Descargar ${recommended.label} ${recommended.codecName} · ${formatSize(recommended.sizeBytes)}", "Download ${recommended.label} ${recommended.codecName} · ${formatSize(recommended.sizeBytes)}")) { onRecommended() }
            }
            OutlineButton(tr("Descargar ${option.label} de todos modos", "Download ${option.label} anyway")) { onAnyway() }
        }
    }
}
