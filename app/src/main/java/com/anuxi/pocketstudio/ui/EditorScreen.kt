package com.anuxi.pocketstudio.ui

import android.content.Intent
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import com.anuxi.pocketstudio.MainViewModel
import com.anuxi.pocketstudio.R
import com.anuxi.pocketstudio.data.tr
import com.anuxi.pocketstudio.editor.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Una peticion para abrir la regla de precision. */
class DialSpec(
    val title: String,
    val subtitle: String?,
    val value: Float,
    val unit: String,
    val decimals: Int,
    val lo: Float?,
    val hi: Float?,
    val apply: (Float) -> Unit,
    val commit: (() -> Unit)? = null,
)

@Composable
fun EditorScreen(vm: MainViewModel) {
    val ed = vm.editor
    val p = ed.project ?: return
    ed.revFlow.collectAsState().value
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSettings by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var markerSheet by remember { mutableStateOf<String?>(null) }
    var dial by remember { mutableStateOf<DialSpec?>(null) }
    var sheet by remember { mutableStateOf<EditorSheet?>(null) }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch { ed.addVideo(uri) }
        }
    }
    val relinkLayer = remember { mutableStateOf<String?>(null) }
    val pickRelink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = relinkLayer.value
        if (uri != null && id != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ed.relink(id, uri)
        }
        relinkLayer.value = null
    }

    var frac by remember { mutableFloatStateOf(0.38f) }
    var totalH by remember { mutableIntStateOf(1) }

    Column(
        Modifier.fillMaxSize().onSizeChanged { totalH = it.height }.padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---------------------------------------------------------- encabezado
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr("Proyectos", "Projects")) {
                vm.leaveEditor()
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).clickable { showRename = true }) {
                Text(p.name, color = Ps.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HistoryButton(false, ed.canUndo, tr("Deshacer", "Undo")) { ed.undo() }
            Spacer(Modifier.width(4.dp))
            HistoryButton(true, ed.canRedo, tr("Rehacer", "Redo")) { ed.redo() }
            Spacer(Modifier.width(4.dp))
            RoundIconButton(Icons.Filled.Settings, tr("Ajustes del proyecto", "Project settings")) { showSettings = true }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Ps.Mint)
                    .pressable { showExport = true }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(tr("Exportar", "Export"), color = Ps.OnMint, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }

        // ------------------------------------------------------- vista previa
        Box(Modifier.fillMaxWidth().weight(frac), contentAlignment = Alignment.Center) {
            Preview(ed, onSettings = { showSettings = true })
        }

        // ------------------------------------------------------- reproduccion
        TransportRow(ed) {
            val m = ed.addMarker()
            if (m != null) markerSheet = m.id
        }

        // ------------------------------------------------------------ contenido
        Box(Modifier.fillMaxWidth().weight(1f - frac)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Timeline(
                        ed,
                        onMarker = { markerSheet = it },
                        onResize = { dy -> frac = (frac + dy / totalH).coerceIn(0.18f, 0.62f) },
                        onRelink = { relinkLayer.value = it; pickRelink.launch(arrayOf("video/*")) },
                        onPickMedia = { pickVideo.launch(arrayOf("video/*")) },
                    )
                }
                LayerBar(ed, openDial = { dial = it }, openSheet = { sheet = it })
            }
        }
    }

    // ----------------------------------------------------------------- hojas
    if (showSettings) ProjectSettingsSheet(ed, openDial = { dial = it }, onDismiss = { showSettings = false })
    if (showRename) RenameSheet(p.name, onDismiss = { showRename = false }) { ed.rename(it); showRename = false }
    markerSheet?.let { id ->
        if (p.markers.any { it.id == id }) MarkerSheet(ed, id, openDial = { dial = it }, onDismiss = { markerSheet = null })
    }
    dial?.let { d ->
        PrecisionDial(d.title, d.subtitle, d.value, d.unit, d.decimals, d.lo, d.hi, d.apply) { d.commit?.invoke(); dial = null }
    }
    sheet?.let { s -> EditorSheets(ed, s, openDial = { dial = it }, openSheet = { sheet = it }, onDismiss = { sheet = null }) }
    if (showExport) {
        PsSheet({ showExport = false }) {
            SheetTitle(tr("Exportar", "Export"))
            Text(
                tr(
                    "La exportación del video llega en la próxima versión. Tu proyecto ya se guarda solo, así que no pierdes nada.",
                    "Video export arrives in the next version. Your project already saves automatically, so you won't lose anything.",
                ),
                color = Ps.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            SheetButtons(null, {}, tr("Entendido", "Got it"), { showExport = false })
        }
    }
    ed.busy?.let { b ->
        if (b != "importando") {
            PsSheet({ ed.busyClear() }) {
                SheetTitle(tr("No se pudo importar", "Couldn't import"))
                Text(b, color = Ps.Muted, fontSize = 13.sp)
                SheetButtons(null, {}, tr("Cerrar", "Close"), { ed.busyClear() })
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Text(tr("Importando…", "Importing…"), color = Ps.Text, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoryButton(redo: Boolean, enabled: Boolean, desc: String, onClick: () -> Unit) {
    val col = if (enabled) Ps.Text else Ps.Muted2.copy(alpha = 0.5f)
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0x14FFFFFF)).pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.minDimension
            fun x(v: Float) = if (redo) w - v else v
            val st = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(x(w * 0.28f), w * 0.38f)
                cubicTo(x(w * 0.62f), w * 0.22f, x(w * 0.94f), w * 0.42f, x(w * 0.86f), w * 0.72f)
            }
            drawPath(path, col, style = st)
            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(x(w * 0.14f), w * 0.46f); lineTo(x(w * 0.34f), w * 0.22f); lineTo(x(w * 0.44f), w * 0.54f); close()
            }
            drawPath(head, col)
        }
    }
}

@Composable
private fun RenameSheet(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    PsSheet(onDismiss) {
        SheetTitle(tr("Nombre del proyecto", "Project name"))
        PsTextField(name, { name = it }, tr("Nombre", "Name"))
        SheetButtons(tr("Cancelar", "Cancel"), onDismiss, tr("Guardar", "Save"), { onSave(name) }, okEnabled = name.isNotBlank())
    }
}

// ============================================================= VISTA PREVIA

@Composable
private fun Preview(ed: EditorState, onSettings: () -> Unit) {
    val p = ed.project ?: return
    ed.revFlow.collectAsState().value
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ratio = p.width.toFloat() / p.height
        val maxW = maxWidth
        val maxH = maxHeight
        val w: Dp
        val h: Dp
        if (maxW / maxH > ratio) { h = maxH; w = h * ratio } else { w = maxW; h = w / ratio }
        val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
        val handlePaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF6FA8FF.toInt() }
        }
        Box(Modifier.size(w, h)) {
            val wPx = with(LocalDensity.current) { w.toPx() }
            val hPx = with(LocalDensity.current) { h.toPx() }
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(p.bg))) {
                p.layers.filter { it.kind == LayerKind.Video && !it.missing }.forEach { l ->
                    key(l.id) { LayerSurface(ed, l, wPx, hPx) }
                }
            }
            Canvas(
                Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        // arrastrar sobre la imagen mueve la capa elegida
                        detectDragGestures { change, drag ->
                            change.consume()
                            val l = ed.layer ?: return@detectDragGestures
                            val k = p.width / size.width.toFloat()
                            var dx = drag.x * k
                            var dy = drag.y * k
                            val par = p.worldMatrixOfParent(l, ed.playhead)
                            val inv = par.inverse()
                            if (inv != null) {
                                val nx = inv.a * dx + inv.c * dy
                                val ny = inv.b * dx + inv.d * dy
                                dx = nx; dy = ny
                            }
                            ed.setProp(l, "posX", ed.valueOf(l, "posX") + dx)
                            ed.setProp(l, "posY", ed.valueOf(l, "posY") + dy)
                        }
                    },
            ) {
                @Suppress("UNUSED_EXPRESSION") ed.rev
                val t = ed.playhead
                val s = size.width / p.width
                // contornos de la capa elegida y los nulos
                p.layers.forEach { l ->
                    if (t < l.start - 1e-3f || t > l.end + 1e-3f) return@forEach
                    val isSel = l.id == ed.selected
                    if (l.kind != LayerKind.Null && !isSel) return@forEach
                    val m = p.worldMatrix(l, t)
                    val pts = listOf(
                        m.apply(0f, 0f), m.apply(l.srcW.toFloat(), 0f),
                        m.apply(l.srcW.toFloat(), l.srcH.toFloat()), m.apply(0f, l.srcH.toFloat()),
                    ).map { Offset(it.first * s, it.second * s) }
                    val col = if (l.kind == LayerKind.Null) Color(0xFFFFC46B) else SkyBlue
                    val dash = if (l.kind == LayerKind.Null) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                    for (i in 0..3) drawLine(col, pts[i], pts[(i + 1) % 4], 2.dp.toPx(), pathEffect = dash)
                    if (isSel) {
                        val (ax, ay) = m.apply(l.anchorX.at(t), l.anchorY.at(t))
                        val o = Offset(ax * s, ay * s)
                        drawLine(Color.White, o - Offset(8.dp.toPx(), 0f), o + Offset(8.dp.toPx(), 0f), 2.dp.toPx())
                        drawLine(Color.White, o - Offset(0f, 8.dp.toPx()), o + Offset(0f, 8.dp.toPx()), 2.dp.toPx())
                    }
                }
            }
            // borde y chip de calidad
            Box(Modifier.fillMaxSize().border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(10.dp)))
            Row(
                Modifier.align(Alignment.TopEnd).padding(6.dp).height(24.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0x99000000)).pressable(onClick = onSettings).padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("Vista ", "Preview ") + if (p.previewDiv == 1) tr("completa", "full") else "1/${p.previewDiv}",
                    color = Ps.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                )
            }
            if (p.layers.any { (it.tile?.enabled == true || it.blur) && it.kind == LayerKind.Video }) {
                Text(
                    tr("Motion Tile y desenfoque se ven al exportar", "Motion Tile and blur show on export"),
                    color = Ps.Muted, fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** Una capa de video: su TextureView recibe el decodificador y se coloca con la matriz de la capa. */
@Composable
private fun LayerSurface(ed: EditorState, l: Layer, wPx: Float, hPx: Float) {
    val p = ed.project ?: return
    ed.revFlow.collectAsState().value
    val t = ed.playhead
    val inRange = t >= l.start - 1e-3f && t <= l.end + 1e-3f
    val a = if (inRange && !l.hidden) p.alpha(l, t).coerceIn(0f, 1f) else 0f
    val m = p.worldMatrix(l, t)
    val s = wPx / p.width
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.view.TextureView(ctx).apply {
                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    var surf: android.view.Surface? = null
                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        val sf = android.view.Surface(st)
                        surf = sf
                        ed.attachSurface(l.id, sf)
                    }
                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                        ed.detachSurface(l.id)
                        surf?.release(); surf = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { tv ->
            tv.alpha = a
            val am = android.graphics.Matrix()
            am.setValues(floatArrayOf(m.a, m.c, m.e, m.b, m.d, m.f, 0f, 0f, 1f))
            val mat = android.graphics.Matrix()
            mat.setScale(s, s)
            mat.preConcat(am)
            mat.preScale(l.srcW / wPx, l.srcH / hPx)
            tv.setTransform(mat)
        },
    )
}

// ============================================================= REPRODUCCION

@Composable
private fun TransportRow(ed: EditorState, onAddMarker: () -> Unit) {
    val p = ed.project ?: return
    ed.revFlow.collectAsState().value
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0x14FFFFFF)).pressable { ed.seekMarker(false) }, contentAlignment = Alignment.Center) {
            PsIcon(Icons.Filled.KeyboardArrowLeft, Ps.Text, 20.dp, tr("Marcador anterior", "Previous marker"))
        }
        Box(
            Modifier.size(width = 46.dp, height = 36.dp).clip(RoundedCornerShape(18.dp)).background(Ps.Mint)
                .pressable { ed.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            if (ed.playing) PsIcon(R.drawable.ic_pause, Ps.OnMint, 18.dp)
            else PsIcon(Icons.Filled.PlayArrow, Ps.OnMint, 20.dp)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0x14FFFFFF)).pressable { ed.seekMarker(true) }, contentAlignment = Alignment.Center) {
            PsIcon(Icons.Filled.KeyboardArrowRight, Ps.Text, 20.dp, tr("Marcador siguiente", "Next marker"))
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(Violet.copy(alpha = 0.18f)).pressable(onClick = onAddMarker), contentAlignment = Alignment.Center) {
            FlagShape(Violet, 14.dp)
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (ed.snapEnabled) Ps.Mint.copy(alpha = 0.22f) else Color(0x14FFFFFF))
                .pressable { ed.snapEnabled = !ed.snapEnabled },
            contentAlignment = Alignment.Center,
        ) { MagnetIcon(if (ed.snapEnabled) Ps.Mint else Ps.Muted2) }
        Text(
            buildTime(ed.playhead, p.duration),
            color = Ps.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun MagnetIcon(col: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.minDimension
        val st = Stroke(width = w * 0.2f, cap = StrokeCap.Butt)
        drawArc(col, 180f, 180f, false, Offset(w * 0.18f, w * 0.14f), Size(w * 0.64f, w * 0.64f), style = st)
        drawLine(col, Offset(w * 0.18f, w * 0.46f), Offset(w * 0.18f, w * 0.78f), w * 0.2f)
        drawLine(col, Offset(w * 0.82f, w * 0.46f), Offset(w * 0.82f, w * 0.78f), w * 0.2f)
        drawLine(Color.White, Offset(w * 0.08f, w * 0.9f), Offset(w * 0.28f, w * 0.9f), w * 0.12f)
        drawLine(Color.White, Offset(w * 0.72f, w * 0.9f), Offset(w * 0.92f, w * 0.9f), w * 0.12f)
    }
}

private fun buildTime(t: Float, d: Float) = "${fmtTime(t)} / ${fmtTime(d)}"

// ============================================================ LINEA DE TIEMPO

@Composable
private fun Timeline(
    ed: EditorState,
    onMarker: (String) -> Unit,
    onResize: (Float) -> Unit,
    onRelink: (String) -> Unit,
    onPickMedia: () -> Unit,
) {
    val p = ed.project ?: return
    val rev by ed.revFlow.collectAsState()
    val selKey = ed.selKey
    var labelsHidden by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ps_ui", android.content.Context.MODE_PRIVATE) }
    var tipSeen by remember { mutableStateOf(prefs.getBoolean("kf_tip", false)) }
    val anyKeys = p.layers.any { ed.animatedTracks(it).isNotEmpty() }
    val density = LocalDensity.current.density
    val scroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var viewportPx by remember { mutableIntStateOf(0) }
    val pps = ed.pxPerSec
    val labelW = 108.dp
    val rowH = 42.dp
    val laneH = 26.dp
    val laneGap = 3.dp
    val rulerH = 24.dp
    val dur = max(p.duration, 1f)
    val contentW = ((max(dur + 4f, 6f)) * pps).dp

    fun zoomBy(z: Float, cxPx: Float) {
        val old = ed.pxPerSec
        val nw = (old * z).coerceIn(6f, 600f)
        val t = cxPx / (old * density)
        ed.pxPerSec = nw
        val ncx = t * nw * density
        scope.launch { scroll.scrollBy(ncx - cxPx) }
    }

    LaunchedEffect(ed.playhead, ed.playing) {
        if (ed.playing && viewportPx > 0) {
            val x = ed.playhead * pps * density
            if (x > scroll.value + viewportPx - 40 || x < scroll.value) scroll.scrollTo(max(0f, x - 60f).toInt())
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // zoom y manija de tamano
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniRound("−") { zoomBy(0.8f, scroll.value + viewportPx / 2f) }
            MiniRound("+") { zoomBy(1.25f, scroll.value + viewportPx / 2f) }
            Box(
                Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                    detectDragGestures { c, d -> c.consume(); onResize(d.y) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(44.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x59FFFFFF)))
            }
            Box {
                Box(
                    Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(Ps.Mint)
                        .pressable { addOpen = true }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+ " + tr("Agregar", "Add"), color = Ps.OnMint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (addOpen) {
                    androidx.compose.ui.window.Popup(
                        alignment = Alignment.TopEnd,
                        offset = androidx.compose.ui.unit.IntOffset(0, (30 * density).toInt()),
                        onDismissRequest = { addOpen = false },
                    ) {
                        Column(
                            Modifier.width(190.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A2C4A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)).padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { addOpen = false; onPickMedia() }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) { VideoIcon(true); Text(tr("Multimedia", "Media"), color = Ps.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { addOpen = false; ed.addNull() }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) { NullIcon(); Text(tr("Nulo", "Null"), color = Ps.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            Box(
                Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(Ps.Mint.copy(alpha = 0.15f))
                    .pressable {
                        val vw = viewportPx / density
                        if (vw > 0f) {
                            ed.pxPerSec = ((vw - 24f) / dur).coerceIn(6f, 600f)
                            scope.launch { scroll.scrollTo(0) }
                        }
                    }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tr("Ver todo", "Fit all"),
                    color = Ps.Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        // Toda esta zona recibe gestos: arrastrar mueve la linea de tiempo en cualquier direccion
        // y pellizcar hace zoom. Los clips los consumen solo al mantener apretado o en sus manijas.
        Box(
            Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val rulerPx = rulerH.toPx()
                val rowPx = rowH.toPx()
                val gapPx = 5.dp.toPx()
                val lanePx = laneH.toPx()
                val laneGapPx = laneGap.toPx()
                val edgeOut = 8.dp.toPx()
                awaitEachGesture {
                    val labelPx = if (labelsHidden) 0f else labelW.toPx()
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val proj = ed.project ?: return@awaitEachGesture
                    val yC = down.position.y + vScroll.value
                    // la regla tiene sus propios gestos
                    if (yC < rulerPx && down.position.x >= labelPx) return@awaitEachGesture
                    val pps = ed.pxPerSec
                    val pxs = pps * density
                    val tol = 10f / pps
                    var layerV: Layer? = null
                    var laneLayerV: Layer? = null
                    var lanePropV: String? = null
                    var yy = rulerPx + gapPx
                    for (l in proj.layers.asReversed()) {
                        val lanes = if (l.id in ed.expanded) ed.animatedTracks(l) else emptyList()
                        val blockH = rowPx + lanes.size * (lanePx + laneGapPx)
                        if (yC >= yy && yC <= yy + blockH) {
                            if (down.position.x >= labelPx) {
                                if (yC <= yy + rowPx) layerV = l
                                else for (i in lanes.indices) {
                                    val top = yy + rowPx + laneGapPx + i * (lanePx + laneGapPx)
                                    if (yC >= top && yC <= top + lanePx) { laneLayerV = l; lanePropV = lanes[i].first }
                                }
                            }
                            break
                        }
                        yy += blockH + gapPx
                    }
                    val layer = layerV
                    val cx = down.position.x - labelPx + scroll.value

                    // 0) keyframes de una propiedad desplegada: tocar = ir ahi, arrastrar = moverlo
                    val ll = laneLayerV
                    val lp = lanePropV
                    if (ll != null && lp != null) {
                        val tr = ll.track(lp)
                        val hit = tr?.keys?.indices?.minByOrNull { abs(tr.keys[it].t * pxs - cx) }
                        if (tr != null && hit != null && abs(tr.keys[hit].t * pxs - cx) <= 18.dp.toPx()) {
                            down.consume()
                            ed.select(ll.id)
                            ed.selKey = Triple(ll.id, lp, tr.keys[hit].t)
                            var idx: Int = hit
                            var raw = tr.keys[hit].t
                            var moved = false
                            var totalDx = 0f
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                val dx = ch.positionChange().x
                                totalDx += dx
                                if (!moved && abs(totalDx) > slop) moved = true
                                if (moved && dx != 0f) {
                                    raw += dx / density / ed.pxPerSec
                                    idx = ed.moveKey(ll, lp, idx, raw, tol)
                                    tr.keys.getOrNull(idx)?.let { ed.selKey = Triple(ll.id, lp, it.t) }
                                }
                                ch.consume()
                            }
                            if (!moved) ed.seek(tr.keys[hit].t)
                            return@awaitEachGesture
                        }
                    }

                    // 1) manijas de recorte (solo con el clip elegido)
                    if (layer != null && layer.id == ed.selected) {
                        val sx = layer.start * pxs
                        val ex = layer.end * pxs
                        val hw = min(26.dp.toPx(), (ex - sx) / 3f)
                        val nearStart = cx >= sx - edgeOut && cx <= sx + hw
                        val nearEnd = cx >= ex - hw && cx <= ex + edgeOut
                        if (nearStart || nearEnd) {
                            val atStart = if (nearStart && nearEnd) abs(cx - sx) <= abs(cx - ex) else nearStart
                            down.consume()
                            var raw = if (atStart) layer.start else layer.end
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                val dx = ch.positionChange().x
                                if (dx != 0f) {
                                    raw += dx / density / ed.pxPerSec
                                    if (atStart) ed.trimStart(layer.id, raw, tol) else ed.trimEnd(layer.id, raw, tol)
                                }
                                ch.consume()
                            }
                            return@awaitEachGesture
                        }
                    }

                    // 2) toque, mantener o arrastre
                    val onBody = layer != null && cx >= layer.start * pxs && cx <= layer.end * pxs
                    var total = Offset.Zero
                    val phase = withTimeoutOrNull(if (onBody) viewConfiguration.longPressTimeoutMillis else Long.MAX_VALUE) {
                        var out = 0
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.size > 1) { out = 2; break }
                            val ch = ev.changes.first()
                            if (!ch.pressed) { out = 1; break }
                            total += ch.positionChange()
                            if (total.getDistance() > slop) { out = 2; break }
                        }
                        out
                    }
                    when (phase) {
                        1 -> { if (layer != null && onBody) ed.select(layer.id) else ed.select(null); return@awaitEachGesture }
                        null -> {
                            // mantener apretado: mover el clip
                            val l = layer ?: return@awaitEachGesture
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            ed.select(l.id)
                            var raw = l.start
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                val dx = ch.positionChange().x
                                if (dx != 0f) {
                                    raw += dx / density / ed.pxPerSec
                                    ed.moveLayer(l.id, raw, tol)
                                }
                                ch.consume()
                            }
                            return@awaitEachGesture
                        }
                    }

                    // 3) arrastre libre: mueve la linea de tiempo; dos dedos hacen zoom
                    scroll.dispatchRawDelta(-total.x)
                    vScroll.dispatchRawDelta(-total.y)
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.none { it.pressed }) break
                        val pan = ev.calculatePan()
                        val zoom = ev.calculateZoom()
                        if (abs(zoom - 1f) > 0.003f) zoomBy(zoom, scroll.value + ev.calculateCentroid().x - labelPx)
                        scroll.dispatchRawDelta(-pan.x)
                        vScroll.dispatchRawDelta(-pan.y)
                        ev.changes.forEach { it.consume() }
                    }
                }
            },
        ) {
        Row(Modifier.fillMaxSize().verticalScroll(vScroll, enabled = false)) {
            // columna fija con los nombres de las capas
            if (!labelsHidden) Column(Modifier.width(labelW), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(rulerH).clip(RoundedCornerShape(8.dp)).background(Color(0x0DFFFFFF))
                        .clickable { labelsHidden = true },
                    contentAlignment = Alignment.Center,
                ) { Text("‹ " + tr("Ocultar", "Hide"), color = Ps.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                p.layers.asReversed().forEach { l ->
                    Column(verticalArrangement = Arrangement.spacedBy(laneGap)) {
                        LayerHeader(ed, l, rowH, onRelink, rev)
                        if (l.id in ed.expanded) ed.animatedTracks(l).forEach { (n, _) -> LaneLabel(n, laneH) }
                    }
                }
            }
            Box(
                Modifier.weight(1f).horizontalScroll(scroll, enabled = false).onSizeChanged { viewportPx = it.width },
            ) {
                Box(Modifier.width(contentW)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Ruler(ed, pps, density, rulerH, onMarker) { z, cx -> zoomBy(z, cx) }
                        p.layers.asReversed().forEach { l ->
                            Column(verticalArrangement = Arrangement.spacedBy(laneGap)) {
                                ClipRow(ed, l, pps, density, rowH, rev)
                                if (l.id in ed.expanded) ed.animatedTracks(l).forEach { (n, tr) -> LaneRow(ed, tr, pps, density, laneH, rev, if (selKey != null && selKey.first == l.id && selKey.second == n) selKey.third else null) }
                            }
                        }
                    }
                    // lineas de marcadores y cursor, por encima de todo
                    Canvas(Modifier.matchParentSize()) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        p.markers.forEach { m ->
                            val x = m.time * pps * density
                            drawLine(Color(m.color).copy(alpha = 0.7f), Offset(x, rulerH.toPx()), Offset(x, size.height), 1.5.dp.toPx(), pathEffect = dash)
                        }
                        val px = ed.playhead * pps * density
                        drawLine(Coral, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
                    }
                }
            }
        }
        if (labelsHidden) {
            Box(
                Modifier.align(Alignment.TopStart).padding(top = 2.dp).size(22.dp, 22.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC2A2C4A)).clickable { labelsHidden = false },
                contentAlignment = Alignment.Center,
            ) { Text("›", color = Ps.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
        if (anyKeys && !tipSeen && !labelsHidden) {
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 30.dp).width(labelW + 120.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xF22A2C4A))
                    .border(1.dp, Ps.Mint.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    tr("Aquí están tus keyframes. Toca la flecha ▾ de una capa para verlos y moverlos en la línea de tiempo.",
                        "Your keyframes are here. Tap a layer's ▾ arrow to see them and move them on the timeline."),
                    color = Ps.Text, fontSize = 12.sp, lineHeight = 16.sp,
                )
                Box(
                    Modifier.align(Alignment.End).height(28.dp).clip(RoundedCornerShape(14.dp)).background(Ps.Mint)
                        .clickable { tipSeen = true; prefs.edit().putBoolean("kf_tip", true).apply() }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("Entendido", "Got it"), color = Ps.OnMint, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
        }
    }
}

@Composable
private fun MiniRound(text: String, onClick: () -> Unit) {
    Box(Modifier.size(width = 32.dp, height = 26.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x14FFFFFF)).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun LayerHeader(ed: EditorState, l: Layer, rowH: Dp, onRelink: (String) -> Unit, rev: Int) {
    val sel = ed.selected == l.id
    val hasKeys = ed.animatedTracks(l).isNotEmpty()
    val open = l.id in ed.expanded
    Row(
        Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(12.dp))
            .background(if (sel) SkyBlue.copy(alpha = 0.30f) else Color(0x0AFFFFFF))
            .then(if (sel) Modifier.border(1.5.dp, SkyBlue, RoundedCornerShape(12.dp)) else Modifier)
            .clickable { ed.select(l.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // video: encender / apagar la imagen
        Box(
            Modifier.weight(1f).fillMaxHeight().clickable {
                ed.select(l.id)
                if (l.missing) onRelink(l.id) else if (l.kind == LayerKind.Video) ed.toggleHidden(l.id)
            },
            contentAlignment = Alignment.Center,
        ) {
            when {
                l.missing -> PsIcon(Icons.Filled.Warning, Ps.Amber, 18.dp, tr("Falta el clip", "Clip missing"))
                l.kind == LayerKind.Null -> NullIcon()
                else -> VideoIcon(!l.hidden)
            }
        }
        // audio: silenciar / activar
        Box(
            Modifier.weight(1f).fillMaxHeight().clickable(enabled = l.kind == LayerKind.Video) { ed.select(l.id); ed.toggleMute(l.id) },
            contentAlignment = Alignment.Center,
        ) { if (l.kind == LayerKind.Video) AudioIcon(!l.mute) }
        // flecha: mostrar las propiedades con keyframes
        Box(
            Modifier.weight(1f).fillMaxHeight().clickable { ed.select(l.id); ed.toggleExpanded(l.id) },
            contentAlignment = Alignment.Center,
        ) {
            PsIcon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                if (hasKeys) Ps.Text else Ps.Muted2, 22.dp,
                tr("Propiedades con keyframes", "Keyframed properties"),
            )
            val n = ed.animatedTracks(l).size
            if (n > 0) Box(
                Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 2.dp).size(15.dp).clip(CircleShape).background(Coral),
                contentAlignment = Alignment.Center,
            ) { Text("$n", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun VideoIcon(on: Boolean) {
    val col = if (on) Color(0xFFB9BCE0) else Color(0xFF6C6F96)
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        drawRoundRect(col, Offset(s * 0.06f, s * 0.26f), Size(s * 0.58f, s * 0.48f), androidx.compose.ui.geometry.CornerRadius(s * 0.1f), style = Stroke(width = s * 0.1f))
        val tri = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.70f, s * 0.5f); lineTo(s * 0.94f, s * 0.34f); lineTo(s * 0.94f, s * 0.66f); close()
        }
        drawPath(tri, col)
        if (!on) drawLine(Coral, Offset(s * 0.1f, s * 0.9f), Offset(s * 0.9f, s * 0.1f), s * 0.1f, cap = StrokeCap.Round)
    }
}

@Composable
private fun AudioIcon(on: Boolean) {
    val col = if (on) Color(0xFFB9BCE0) else Color(0xFF6C6F96)
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        val sp = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.08f, s * 0.38f); lineTo(s * 0.28f, s * 0.38f); lineTo(s * 0.5f, s * 0.18f)
            lineTo(s * 0.5f, s * 0.82f); lineTo(s * 0.28f, s * 0.62f); lineTo(s * 0.08f, s * 0.62f); close()
        }
        drawPath(sp, col)
        if (on) {
            drawArc(col, -40f, 80f, false, Offset(s * 0.42f, s * 0.3f), Size(s * 0.3f, s * 0.4f), style = Stroke(width = s * 0.09f, cap = StrokeCap.Round))
            drawArc(col, -45f, 90f, false, Offset(s * 0.42f, s * 0.12f), Size(s * 0.5f, s * 0.76f), style = Stroke(width = s * 0.09f, cap = StrokeCap.Round))
        } else {
            drawLine(Coral, Offset(s * 0.1f, s * 0.9f), Offset(s * 0.9f, s * 0.1f), s * 0.1f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun NullIcon() {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        drawRoundRect(
            Color(0xFFFFC46B), Offset(s * 0.12f, s * 0.12f), Size(s * 0.76f, s * 0.76f),
            androidx.compose.ui.geometry.CornerRadius(3f),
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
        )
    }
}

@Composable
private fun LaneLabel(prop: String, h: Dp) {
    Row(
        Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(8.dp)).background(Color(0x08FFFFFF)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(propName(prop), color = Ps.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun LaneRow(ed: EditorState, tr: Track, pps: Float, density: Float, h: Dp, rev: Int, selT: Float?) {
    Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(8.dp)).background(Color(0x08FFFFFF))) {
        Canvas(Modifier.matchParentSize()) {
            @Suppress("UNUSED_EXPRESSION") ed.rev
            val pxs = ed.pxPerSec * density
            val cy = size.height / 2f
            if (tr.keys.size > 1) {
                drawLine(Color(0x40FFFFFF), Offset(tr.keys.first().t * pxs, cy), Offset(tr.keys.last().t * pxs, cy), 2.dp.toPx())
            }
            val e = ed.eps()
            val r = 6.5.dp.toPx()
            tr.keys.forEachIndexed { i, k ->
                val x = k.t * pxs
                val on = abs(k.t - ed.playhead) <= e
                val sel = selT != null && abs(k.t - selT) <= e
                if (sel) drawCircle(Color.White, r + 4.dp.toPx(), Offset(x, cy), style = Stroke(2.dp.toPx()))
                drawKeyShape(keyKind(tr, i), x, cy, r, if (on) Ps.Mint else Coral)
            }
        }
    }
}

@Composable
private fun Ruler(
    ed: EditorState,
    pps: Float,
    density: Float,
    height: Dp,
    onMarker: (String) -> Unit,
    onZoom: (Float, Float) -> Unit,
) {
    val p = ed.project ?: return
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9DA1CC.toInt() }
    }
    Canvas(
        Modifier.fillMaxWidth().height(height)
            .pointerInput(pps) {
                detectTapGestures { pos ->
                    val hit = p.markers.minByOrNull { abs(it.time * pps * density - pos.x) }
                    if (hit != null && abs(hit.time * pps * density - pos.x) < 14.dp.toPx() && pos.y < size.height) onMarker(hit.id)
                    else ed.seek(pos.x / (pps * density))
                }
            }
            .pointerInput(pps) {
                detectTransformGestures { centroid, _, zoom, _ ->
                    if (abs(zoom - 1f) > 0.003f) onZoom(zoom, centroid.x)
                    else ed.seek(centroid.x / (pps * density))
                }
            },
    ) {
        paint.textSize = 10.sp.toPx()
        val steps = listOf(0.1f, 0.2f, 0.5f, 1f, 2f, 5f, 10f, 30f, 60f)
        val stepSec = steps.firstOrNull { it * pps >= 56f } ?: 60f
        var t = 0f
        val end = size.width / (pps * density)
        while (t <= end) {
            val x = t * pps * density
            drawLine(Color(0x40FFFFFF), Offset(x, size.height - 8.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
            val label = if (stepSec < 1f) "%.1f".format(t) else if (t >= 60f) fmtTime(t).substringBefore('.') else "${t.toInt()}s"
            drawIntoCanvas { it.nativeCanvas.drawText(label, x + 3.dp.toPx(), 10.dp.toPx(), paint) }
            t += stepSec
        }
        // marcadores
        val fh = 12.dp.toPx()
        p.markers.forEach { m ->
            val x = m.time * pps * density
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(x - fh / 2, size.height - fh - 2.dp.toPx()); lineTo(x + fh / 2, size.height - fh - 2.dp.toPx())
                lineTo(x + fh / 2, size.height - fh * 0.4f - 2.dp.toPx()); lineTo(x, size.height - 2.dp.toPx())
                lineTo(x - fh / 2, size.height - fh * 0.4f - 2.dp.toPx()); close()
            }
            drawPath(path, Color(m.color))
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun ClipRow(ed: EditorState, l: Layer, pps: Float, density: Float, rowH: Dp, rev: Int) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
    }
    // Todo se dibuja en la fase de dibujo leyendo ed.rev: se actualiza en vivo aunque no haya recomposicion.
    Canvas(Modifier.fillMaxWidth().height(rowH)) {
        @Suppress("UNUSED_EXPRESSION") ed.rev
        val pxs = ed.pxPerSec * density
        val sel = ed.selected == l.id
        val color = if (l.kind == LayerKind.Null) Color(0xD9FFC46B) else clipColor(l.id)
        val rr = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
        drawRoundRect(if (sel) SkyBlue.copy(alpha = 0.22f) else Color(0x0AFFFFFF), cornerRadius = rr)
        if (sel) drawRoundRect(SkyBlue, cornerRadius = rr, style = Stroke(1.5.dp.toPx()))
        val x = l.start * pxs
        val w = max(10.dp.toPx(), l.length * pxs)
        val top = 3.dp.toPx()
        val h = size.height - top * 2
        val br = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx())
        val fill = if (l.missing) Color(0x66FF8A8A) else if (l.hidden) color.copy(alpha = 0.35f) else color
        drawRoundRect(fill, Offset(x, top), Size(w, h), br)
        if (sel) drawRoundRect(Color.White, Offset(x, top), Size(w, h), br, style = Stroke(3.dp.toPx()))
        clipRect(x, top, x + w, top + h) {
            if (l.kind != LayerKind.Null) {
                paint.color = Ps.OnMint.toArgb()
                paint.textSize = 11.sp.toPx()
                drawIntoCanvas { it.nativeCanvas.drawText(l.name, x + 14.dp.toPx(), size.height / 2f + 4.sp.toPx(), paint) }
            }
            if (l.kind == LayerKind.Video && l.speed.animated) {
                paint.textSize = 10.sp.toPx()
                drawIntoCanvas { it.nativeCanvas.drawText("⏱", x + w - 26.dp.toPx(), size.height / 2f + 4.sp.toPx(), paint) }
            }
        }
        if (sel) {
            val hw = 5.dp.toPx(); val hh = 22.dp.toPx()
            val cy = size.height / 2f
            drawRoundRect(SkyBlue, Offset(x + 4.5.dp.toPx(), cy - hh / 2), Size(hw, hh), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawRoundRect(SkyBlue, Offset(x + w - 4.5.dp.toPx() - hw, cy - hh / 2), Size(hw, hh), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
}

private fun clipColor(id: String): Color {
    val palette = listOf(0xFF7CF2C8, 0xFF8FB6FF, 0xFFFF9C9C, 0xFFB79CFF, 0xFFFFD36B)
    return Color(palette[(id.hashCode() and 0x7fffffff) % palette.size])
}
