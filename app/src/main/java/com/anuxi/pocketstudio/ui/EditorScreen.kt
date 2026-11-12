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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
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
import androidx.compose.ui.graphics.PathEffect
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
    @Suppress("UNUSED_EXPRESSION") ed.rev
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

    var frac by remember(ed.tab) { mutableFloatStateOf(if (ed.tab == 0) 0.40f else 0.30f) }
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
            Column(Modifier.weight(1f).clickable { showRename = true }) {
                Text(p.name, color = Ps.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr("Guardado · toca para renombrar", "Saved · tap to rename"), color = Ps.Muted2, fontSize = 10.sp)
            }
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
            when (ed.tab) {
                0 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        Timeline(
                            ed,
                            onMarker = { markerSheet = it },
                            onResize = { dy -> frac = (frac + dy / totalH).coerceIn(0.18f, 0.62f) },
                            onRelink = { relinkLayer.value = it; pickRelink.launch(arrayOf("video/*")) },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashedButton("+ " + tr("Multimedia", "Media"), Ps.Muted, Modifier.weight(1f)) { pickVideo.launch(arrayOf("video/*")) }
                        DashedButton("+ " + tr("Nulo", "Null"), Ps.Amber, Modifier.weight(1f)) { ed.addNull() }
                        DashedButton("⚑ " + tr("Marcador", "Marker"), Violet, Modifier.weight(1f)) {
                            val m = ed.addMarker()
                            if (m != null) markerSheet = m.id
                        }
                    }
                }
                1 -> PropertiesPanel(ed, openDial = { dial = it }, openSheet = { sheet = it })
                else -> KeyframesPanel(ed, openSheet = { sheet = it })
            }
        }

        // --------------------------------------------------------------- pestañas
        SegControl(
            listOf(tr("Capas", "Layers"), tr("Propiedades", "Properties"), "Keyframes"),
            ed.tab,
        ) { ed.tab = it }
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
private fun DashedButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
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
    @Suppress("UNUSED_EXPRESSION") ed.rev
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
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val t = ed.playhead
    val inRange = t >= l.start - 1e-3f && t <= l.end + 1e-3f
    val a = if (inRange) p.alpha(l, t).coerceIn(0f, 1f) else 0f
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
    @Suppress("UNUSED_EXPRESSION") ed.rev
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
        Text(
            buildTime(ed.playhead, p.duration),
            color = Ps.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
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
) {
    val p = ed.project ?: return
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val density = LocalDensity.current.density
    val scroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var viewportPx by remember { mutableIntStateOf(0) }
    val pps = ed.pxPerSec
    val labelW = 108.dp
    val rowH = 42.dp
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
            val canSplit = ed.canSplit(ed.selected)
            Box(
                Modifier.height(26.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (canSplit) SkyBlue.copy(alpha = 0.22f) else Color(0x0DFFFFFF))
                    .pressable { if (canSplit) ed.splitLayer(ed.selected ?: "") }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✂ " + tr("Dividir", "Split"),
                    color = if (canSplit) SkyBlue else Ps.Muted2, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
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
                val labelPx = labelW.toPx()
                val rulerPx = rulerH.toPx()
                val rowPx = rowH.toPx()
                val gapPx = 5.dp.toPx()
                val edgeOut = 8.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val proj = ed.project ?: return@awaitEachGesture
                    val yC = down.position.y + vScroll.value
                    // la regla tiene sus propios gestos
                    if (yC < rulerPx && down.position.x >= labelPx) return@awaitEachGesture
                    val pps = ed.pxPerSec
                    val pxs = pps * density
                    val tol = 10f / pps
                    val rows = proj.layers.asReversed()
                    val rel = yC - rulerPx - gapPx
                    val r = if (rel < 0f) -1 else (rel / (rowPx + gapPx)).toInt()
                    val inRow = r in rows.indices && rel - r * (rowPx + gapPx) <= rowPx
                    val layer = if (down.position.x >= labelPx && inRow) rows[r] else null
                    val cx = down.position.x - labelPx + scroll.value

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
                        1 -> { if (layer != null && onBody) ed.selected = layer.id; return@awaitEachGesture }
                        null -> {
                            // mantener apretado: mover el clip
                            val l = layer ?: return@awaitEachGesture
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            ed.selected = l.id
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
            Column(Modifier.width(labelW), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Spacer(Modifier.height(rulerH))
                p.layers.asReversed().forEach { l -> LayerLabel(ed, l, rowH, onRelink, ed.rev) }
            }
            Box(
                Modifier.weight(1f).horizontalScroll(scroll, enabled = false).onSizeChanged { viewportPx = it.width },
            ) {
                Box(Modifier.width(contentW)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Ruler(ed, pps, density, rulerH, onMarker) { z, cx -> zoomBy(z, cx) }
                        p.layers.asReversed().forEach { l -> ClipRow(ed, l, pps, density, rowH, ed.rev) }
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
private fun LayerLabel(ed: EditorState, l: Layer, rowH: Dp, onRelink: (String) -> Unit, rev: Int) {
    val sel = ed.selected == l.id
    Row(
        Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(12.dp))
            .background(if (sel) SkyBlue.copy(alpha = 0.30f) else Color(0x0AFFFFFF))
            .then(if (sel) Modifier.border(1.5.dp, SkyBlue, RoundedCornerShape(12.dp)) else Modifier)
            .clickable { ed.selected = l.id }
            .padding(start = if (l.parentId != null) 18.dp else 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (l.missing) {
            Box(Modifier.clickable { onRelink(l.id) }) { PsIcon(Icons.Filled.Warning, Ps.Amber, 16.dp, tr("Falta el clip", "Clip missing")) }
        } else {
            Canvas(Modifier.size(15.dp)) {
                val s = size.minDimension
                val col = if (l.kind == LayerKind.Null) Color(0xFFFFC46B) else Color(0xFFB9BCE0)
                val dash = if (l.kind == LayerKind.Null) PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) else null
                drawRoundRect(col, Offset(s * 0.1f, s * 0.2f), Size(s * 0.8f, s * 0.6f), androidx.compose.ui.geometry.CornerRadius(3f), style = Stroke(width = 2f, pathEffect = dash))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(l.name, color = Ps.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val sel = ed.selected == l.id
    val color = if (l.kind == LayerKind.Null) Color(0xD9FFC46B) else clipColor(l.id)
    Box(
        Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(12.dp))
            .background(if (sel) SkyBlue.copy(alpha = 0.22f) else Color(0x0AFFFFFF))
            .then(if (sel) Modifier.border(1.5.dp, SkyBlue, RoundedCornerShape(12.dp)) else Modifier),
    ) {
        val w = max(10f, l.length * pps).dp
        val tolSec = 10f / pps
        Box(
            Modifier.offset(x = (l.start * pps).dp).width(w).fillMaxHeight().padding(vertical = 3.dp)
                .clip(RoundedCornerShape(7.dp)).background(if (l.missing) Color(0x66FF8A8A) else color)
                .then(if (sel) Modifier.border(3.dp, Color.White, RoundedCornerShape(7.dp)) else Modifier)
                ,
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                if (l.kind == LayerKind.Null) "" else l.name,
                color = Ps.OnMint, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            if (l.kind == LayerKind.Video && l.speed.animated) {
                Text("⏱", fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
            }
            // manijas para recortar: solo se ven con el clip elegido (los gestos los maneja la linea de tiempo)
            if (sel) {
                Box(Modifier.align(Alignment.CenterStart).width(14.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(5.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(SkyBlue))
                }
                Box(Modifier.align(Alignment.CenterEnd).width(14.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(5.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(SkyBlue))
                }
            }
        }
    }
}

private fun clipColor(id: String): Color {
    val palette = listOf(0xFF7CF2C8, 0xFF8FB6FF, 0xFFFF9C9C, 0xFFB79CFF, 0xFFFFD36B)
    return Color(palette[(id.hashCode() and 0x7fffffff) % palette.size])
}
