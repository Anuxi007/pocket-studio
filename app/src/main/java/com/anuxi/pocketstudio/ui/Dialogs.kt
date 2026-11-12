package com.anuxi.pocketstudio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anuxi.pocketstudio.MainViewModel
import com.anuxi.pocketstudio.R
import com.anuxi.pocketstudio.data.DlState
import com.anuxi.pocketstudio.data.ErrorAction

private val SheetColor = Color(0xFF262A52)
private val SheetShape = RoundedCornerShape(28.dp)
private val WideDialog = DialogProperties(
    usePlatformDefaultWidth = false,
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
)

@Composable
private fun SheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(SheetShape)
            .background(SheetColor)
            .border(BorderStroke(1.dp, Ps.Line), SheetShape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** Primera vez que se abre la app: aviso de uso responsable. */
@Composable
fun WelcomeDialog(vm: MainViewModel) {
    var checked by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = {}, properties = WideDialog) {
        SheetColumn {
            Text("Bienvenido a Pocket Studio", color = Ps.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Descarga video o audio a partir de un enlace y guárdalo en tu celular. " +
                    "Es una versión alpha: puede tener errores.",
                color = Color(0xFFC3C6E8), fontSize = 15.sp, lineHeight = 22.sp,
            )
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Ps.Mint,
                        checkmarkColor = Ps.OnMint,
                        uncheckedColor = Ps.Muted,
                    ),
                )
                Text(
                    "Entiendo que debo usar la app solo con contenido propio, libre o con permiso.",
                    color = Color(0xFFC3C6E8), fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            MintButton("Entendido", enabled = checked) { vm.acceptWelcome() }
        }
    }
}

/** Ventana flotante que aparece encima de otra app cuando comparte un enlace con Pocket Studio. */
@Composable
fun ShareSheet(url: String, onQuick: () -> Unit, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99080916))
            .clickable(interactionSource = none, indication = null, onClick = onDismiss)
            .systemBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // El clic vacio evita que tocar la tarjeta cierre la ventana.
        Box(
            Modifier
                .padding(bottom = 16.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            SheetColumn {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ps.Mint),
                        contentAlignment = Alignment.Center,
                    ) { PsIcon(R.drawable.ic_download, Ps.OnMint) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Pocket Studio", color = Ps.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(url, color = Ps.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Ps.Mint)
                        .pressable { onQuick() }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PsIcon(R.drawable.ic_bolt, Ps.OnMint, 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Descarga rápida", color = Ps.OnMint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Baja el mejor formato que tu celular puede reproducir y lo guarda en tu carpeta. " +
                            "Verás el progreso en la notificación.",
                        color = Ps.OnMint.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp,
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .border(BorderStroke(1.5.dp, Ps.Line), RoundedCornerShape(22.dp))
                        .pressable { onOpen() }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PsIcon(R.drawable.ic_open, Ps.Text, 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir", color = Ps.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Abre la app con el enlace ya pegado para elegir formato y calidad.",
                        color = Ps.Muted, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                }
            }
        }
    }
}

/** Progreso, resultado o error de una descarga. */
@Composable
fun DownloadDialog(vm: MainViewModel) {
    val state = vm.dl
    if (state == DlState.Idle) return
    Dialog(onDismissRequest = {}, properties = WideDialog) {
        SheetColumn {
            when (state) {
                DlState.Idle -> {}

                is DlState.Running -> {
                    Text("Descargando", color = Ps.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (state.progress < 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Ps.Mint,
                            trackColor = Ps.CardStrong,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = state.progress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Ps.Mint,
                            trackColor = Ps.CardStrong,
                        )
                    }
                    Text(state.text, color = Ps.Muted, fontSize = 14.sp)
                    OutlineButton("Cancelar") { vm.cancelDownload() }
                }

                is DlState.Done -> {
                    Text("¡Listo!", color = Ps.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Guardado en:", color = Ps.Muted, fontSize = 14.sp)
                    Text(state.saved.where, color = Ps.Mint, fontSize = 14.sp, lineHeight = 20.sp)
                    if (state.saved.fellBack) {
                        Text(
                            "La carpeta que elegiste ya no estaba disponible, así que usamos la predeterminada.",
                            color = Ps.Amber, fontSize = 13.sp, lineHeight = 19.sp,
                        )
                    }
                    MintButton("Cerrar") { vm.dismissDownload() }
                }

                is DlState.Failed -> {
                    val e = state.error
                    Text(e.title, color = Ps.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(e.text, color = Color(0xFFE3E5F7), fontSize = 15.sp, lineHeight = 22.sp)
                    Text(e.hint, color = Color(0xFFC3C6E8), fontSize = 13.sp, lineHeight = 19.sp)
                    Text(
                        e.detail,
                        color = Ps.Muted2, fontSize = 12.sp, lineHeight = 18.sp,
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                    if (e.action == ErrorAction.UpdateYtdlp) {
                        MintButton(e.actionLabel, enabled = !vm.ytBusy) {
                            vm.dismissDownload()
                            vm.go(com.anuxi.pocketstudio.data.Screen.Settings)
                            vm.updateYtdlp()
                        }
                        OutlineButton("Cerrar") { vm.dismissDownload() }
                    } else {
                        MintButton("Cerrar") { vm.dismissDownload() }
                    }
                }
            }
        }
    }
}
