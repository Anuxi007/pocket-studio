package com.anuxi.pocketstudio.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.anuxi.pocketstudio.data.tr
import com.anuxi.pocketstudio.editor.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun even(v: Int) = (v.coerceIn(16, 4096) / 2) * 2

@Composable
private fun OptRow(label: String, value: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(78.dp)) {
            Text(label, color = Ps.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Ps.Muted2, fontSize = 10.sp)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun ExportSheet(ed: EditorState, onDismiss: () -> Unit) {
    val p = ed.project ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableIntStateOf(0) }   // 0 ajustes, 1 renderizando, 2 listo, 3 error
    var progress by remember { mutableFloatStateOf(0f) }
    var resultUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var error by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }

    val fpsOptions = remember { (listOf(24, 30, 60) + p.fps.roundToInt()).distinct().sorted() }
    var fps by remember { mutableIntStateOf(p.fps.roundToInt()) }
    var res by remember { mutableIntStateOf(2) }
    var quality by remember { mutableIntStateOf(2) }
    var audio by remember { mutableStateOf(true) }
    var adv by remember { mutableStateOf(false) }
    var hevc by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var mbps by remember { mutableFloatStateOf(12f) }
    var resize by remember { mutableStateOf(false) }
    var wTxt by remember { mutableStateOf(p.width.toString()) }
    var hTxt by remember { mutableStateOf(p.height.toString()) }
    var fit by remember { mutableIntStateOf(0) }

    val outW: Int
    val outH: Int
    if (resize) {
        outW = even(wTxt.toIntOrNull() ?: p.width); outH = even(hTxt.toIntOrNull() ?: p.height)
    } else if (res == 2) {
        outW = even(p.width); outH = even(p.height)
    } else {
        val short = if (res == 0) 720 else 1080
        val k = short.toFloat() / min(p.width, p.height)
        outW = even((p.width * k).roundToInt()); outH = even((p.height * k).roundToInt())
    }
    val bpp = listOf(0.05f, 0.09f, 0.14f)[quality]
    val autoRate = (outW.toFloat() * outH * fps * bpp * (if (hevc) 0.7f else 1f)).toInt().coerceIn(1_000_000, 80_000_000)
    val bitrate = if (manual) (mbps * 1_000_000).toInt() else autoRate
    val sizeMb = (bitrate + (if (audio) 128_000 else 0)) * max(p.duration, 0.1f) / 8f / 1_000_000f

    PsSheet(onDismiss = { job?.cancel(); onDismiss() }) {
        when (phase) {
            0 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Exportar", "Export"), color = Ps.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${outW}×$outH · ${fmtTime(p.duration)}", color = Ps.Muted2, fontSize = 11.sp)
                }
                OptRow(tr("Resolución", "Resolution"), "${outW}×$outH") {
                    SegControl(listOf("720p", "1080p", tr("Orig.", "Orig.")), if (resize) -1 else res) { res = it; resize = false }
                }
                OptRow(tr("Fotogramas", "Frame rate"), "$fps fps") {
                    SegControl(fpsOptions.map { "$it" }, fpsOptions.indexOf(fps)) { fps = fpsOptions[it] }
                }
                OptRow(tr("Calidad", "Quality"), if (hevc) "H.265" else "H.264") {
                    SegControl(listOf(tr("Baja", "Low"), tr("Media", "Medium"), tr("Alta", "High")), quality) { quality = it }
                }
                SwitchRow(tr("Audio", "Audio"), null, audio) { audio = it }
                Box(Modifier.fillMaxWidth().clickable { adv = !adv }.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Text(if (adv) tr("Avanzado ⌃", "Advanced ⌃") else tr("Avanzado ⌄", "Advanced ⌄"), color = Ps.Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (adv) {
                    OptRow(tr("Códec", "Codec"), "MP4") {
                        SegControl(listOf("H.264", "H.265"), if (hevc) 1 else 0) { hevc = it == 1 }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bitrate", color = Ps.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("%.1f Mbps".format(bitrate / 1_000_000f), color = Ps.Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.width(140.dp)) {
                            SegControl(listOf("Auto", "Manual"), if (manual) 1 else 0) {
                                manual = it == 1
                                if (manual) mbps = (autoRate / 1_000_000f).coerceIn(2f, 50f)
                            }
                        }
                    }
                    if (manual) Slider(
                        value = mbps, onValueChange = { mbps = it }, valueRange = 2f..50f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Ps.Mint),
                    )
                    SwitchRow(tr("Redimensionar", "Resize"), null, resize) { resize = it; if (it) { wTxt = outW.toString(); hTxt = outH.toString() } }
                    if (resize) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumField(wTxt, { wTxt = it }, tr("Ancho", "Width"), Modifier.weight(1f))
                            Text("×", color = Ps.Muted)
                            NumField(hTxt, { hTxt = it }, tr("Alto", "Height"), Modifier.weight(1f))
                        }
                        SegControl(listOf(tr("Ajustar", "Fit"), tr("Rellenar", "Fill"), tr("Estirar", "Stretch")), fit) { fit = it }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Ps.Mint)
                        .pressable {
                            if (ed.playing) ed.togglePlay()
                            phase = 1; progress = 0f
                            val st = ExportSettings(outW, outH, fps, hevc, bitrate, audio, if (resize) fit else 0)
                            job = scope.launch {
                                try {
                                    resultUri = Exporter.run(ctx, p, st) { progress = it }
                                    phase = 2
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    error = e.message ?: e.javaClass.simpleName
                                    phase = 3
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Exportar", "Export"), color = Ps.OnMint, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("~%.0f MB".format(sizeMb), color = Ps.OnMint.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
                Note(tr("Esta versión dibuja posición, escala, rotación, opacidad, velocidad y audio. Motion Tile, desenfoque de movimiento y flujo óptico aún no se aplican al exportar.", "This version renders position, scale, rotation, opacity, speed and audio. Motion Tile, motion blur and optical flow are not applied on export yet."))
            }
            1 -> {
                SheetTitle(tr("Exportando…", "Exporting…"), "${outW}×$outH · $fps fps")
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Ps.Mint)
                Text("${(progress * 100).roundToInt()}%", color = Ps.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Note(tr("Mantén la app abierta. Puede tardar un poco según la duración.", "Keep the app open. It may take a while depending on the length."))
                SheetButtons(null, {}, tr("Cancelar", "Cancel"), { job?.cancel(); phase = 0 }, danger = true)
            }
            2 -> {
                SheetTitle(tr("Listo", "Done"), tr("Guardado en Películas/PocketStudio", "Saved to Movies/PocketStudio"))
                SheetButtons(
                    tr("Cerrar", "Close"), onDismiss,
                    tr("Abrir", "Open"), {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(resultUri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }
                    },
                )
            }
            else -> {
                SheetTitle(tr("No se pudo exportar", "Export failed"))
                Text(error, color = Ps.Muted, fontSize = 13.sp)
                if (hevc) Note(tr("Prueba con H.264: no todos los teléfonos codifican H.265.", "Try H.264: not every phone encodes H.265."))
                SheetButtons(tr("Cerrar", "Close"), onDismiss, tr("Volver", "Back"), { phase = 0 })
            }
        }
    }
}
