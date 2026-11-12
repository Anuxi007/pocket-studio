package com.anuxi.pocketstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuxi.pocketstudio.data.tr
import com.anuxi.pocketstudio.editor.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed interface EditorSheet {
    data class CurveFor(val layerId: String, val prop: String, val keyIndex: Int) : EditorSheet
    data class TileFor(val layerId: String) : EditorSheet
    data class SpeedFor(val layerId: String) : EditorSheet
    data class ParentFor(val layerId: String) : EditorSheet
    data class MenuFor(val layerId: String) : EditorSheet
}

private class PropDef(
    val label: String, val prop: String, val short: String,
    val unit: String = "", val decimals: Int = 0, val lo: Float? = null, val hi: Float? = null,
)

fun propName(prop: String): String = when (prop) {
    "anchorX" -> tr("Anclaje X", "Anchor X"); "anchorY" -> tr("Anclaje Y", "Anchor Y")
    "posX" -> tr("Posición X", "Position X"); "posY" -> tr("Posición Y", "Position Y")
    "scaleX" -> tr("Escala X", "Scale X"); "scaleY" -> tr("Escala Y", "Scale Y")
    "rot" -> tr("Rotación", "Rotation"); "opacity" -> tr("Opacidad", "Opacity")
    "speed" -> tr("Velocidad", "Speed")
    "tileCX" -> tr("Mosaico · centro X", "Tile · center X"); "tileCY" -> tr("Mosaico · centro Y", "Tile · center Y")
    "tileOW" -> tr("Mosaico · salida ancho", "Tile · output width"); "tileOH" -> tr("Mosaico · salida alto", "Tile · output height")
    else -> prop
}

private fun fmt(v: Float, decimals: Int) = if (decimals == 0) "%.0f".format(v) else "%.${decimals}f".format(v)

// ============================================================ PROPIEDADES

@Composable
fun PropertiesPanel(ed: EditorState, openDial: (DialSpec) -> Unit, openSheet: (EditorSheet) -> Unit) {
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val p = ed.project ?: return
    val l = ed.layer
    if (l == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("Elige una capa en la pestaña Capas.", "Pick a layer in the Layers tab."), color = Ps.Muted, fontSize = 14.sp)
        }
        return
    }
    val video = l.kind == LayerKind.Video
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.name, color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    if (video) "${l.srcW}×${l.srcH} · ${"%.2f".format(l.srcFps)} fps" else tr("Capa nula", "Null layer"),
                    color = Ps.Muted2, fontSize = 11.sp,
                )
            }
            Box(Modifier.size(40.dp).clickable { openSheet(EditorSheet.MenuFor(l.id)) }, contentAlignment = Alignment.Center) {
                PsIcon(Icons.Filled.MoreVert, Ps.Muted, 22.dp, tr("Más", "More"))
            }
        }
        PropRow(ed, l, tr("Punto de anclaje", "Anchor point"),
            listOf(PropDef("", "anchorX", "X"), PropDef("", "anchorY", "Y")), openDial)
        PropRow(ed, l, tr("Posición", "Position"),
            listOf(PropDef("", "posX", "X"), PropDef("", "posY", "Y")), openDial)
        PropRow(ed, l, tr("Escala", "Scale"),
            listOf(PropDef("", "scaleX", "X", "%", 1, 0f, 5000f), PropDef("", "scaleY", "Y", "%", 1, 0f, 5000f)), openDial,
            trailing = {
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(if (l.scaleLinked) Ps.Mint.copy(alpha = 0.25f) else Color(0x14FFFFFF))
                        .clickable { l.scaleLinked = !l.scaleLinked; ed.touch() },
                    contentAlignment = Alignment.Center,
                ) { Text(if (l.scaleLinked) "🔗" else "⛓", fontSize = 14.sp) }
            })
        Note(
            if (l.scaleLinked) tr("Escala enlazada: el botón de la cadena separa X e Y", "Linked scale: the chain button separates X and Y")
            else tr("Escala por ejes: X e Y se mueven por separado", "Per-axis scale: X and Y move separately")
        )
        PropRow(ed, l, tr("Rotación", "Rotation"), listOf(PropDef("", "rot", "", "°", 1)), openDial)
        if (video) PropRow(ed, l, tr("Opacidad", "Opacity"), listOf(PropDef("", "opacity", "", "%", 0, 0f, 100f)), openDial)

        LinkRow(
            tr("Padre", "Parent"),
            p.layer(l.parentId)?.name ?: tr("Ninguno", "None"),
            Violet,
        ) { openSheet(EditorSheet.ParentFor(l.id)) }

        if (video) {
            val tile = l.tile
            LinkRow(
                tr("Efecto", "Effect"),
                if (tile == null) tr("Agregar Motion Tile", "Add Motion Tile")
                else "Motion Tile · " + if (tile.enabled) tr("Activo", "On") else tr("Apagado", "Off"),
                SkyBlue,
            ) {
                if (l.tile == null) {
                    l.tile = TileFx().also { it.centerX.base = l.srcW / 2f; it.centerY.base = l.srcH / 2f }
                    ed.touch()
                }
                openSheet(EditorSheet.TileFor(l.id))
            }
            LinkRow(
                tr("Tiempo", "Time"),
                (if (l.speed.animated) tr("Rampa", "Ramp") else "${fmt(l.speed.base, 1)}%") + " · " + interpName(l.interp),
                Ps.Mint,
            ) { openSheet(EditorSheet.SpeedFor(l.id)) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Orange.copy(alpha = 0.10f))
                    .border(1.5.dp, Orange.copy(alpha = 0.35f), RoundedCornerShape(13.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Desenfoque de movimiento", "Motion blur"), color = Ps.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(tr("Ajustes en el proyecto", "Settings are in the project"), color = Ps.Muted, fontSize = 11.sp)
                }
                PsSwitch(l.blur) { l.blur = it; ed.touch() }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun interpName(i: Interp) = when (i) {
    Interp.None -> tr("Sin interpolar", "No interpolation")
    Interp.Blend -> tr("Mezcla", "Blend")
    Interp.Flow -> tr("Flujo óptico", "Optical flow")
}

@Composable
private fun LinkRow(title: String, value: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.10f))
            .border(1.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ps.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Ps.Muted, fontSize = 11.sp)
        }
        PsIcon(Icons.Filled.KeyboardArrowRight, Ps.Muted, 20.dp)
    }
}

@Composable
private fun PropRow(
    ed: EditorState,
    l: Layer,
    label: String,
    defs: List<PropDef>,
    openDial: (DialSpec) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val tracks = defs.mapNotNull { l.track(it.prop) }
    val e = ed.eps()
    val keyedHere = tracks.any { it.indexAt(ed.playhead, e) >= 0 }
    val animated = tracks.any { it.animated }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0x0DFFFFFF))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(88.dp), lineHeight = 15.sp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            defs.forEach { d ->
                val v = ed.valueOf(l, d.prop)
                Row(
                    Modifier.widthIn(min = 54.dp).height(32.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x14FFFFFF))
                        .clickable {
                            openDial(DialSpec(
                                "$label${if (d.short.isNotEmpty()) " · " + d.short else ""}", l.name,
                                v, d.unit, d.decimals, d.lo, d.hi, { nv -> ed.setProp(l, d.prop, nv) },
                            ))
                        }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (d.short.isNotEmpty()) {
                        Text(d.short, color = Ps.Muted2, fontSize = 10.sp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(fmt(v, d.decimals) + d.unit, color = Ps.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        trailing?.invoke()
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable { defs.forEach { ed.toggleKey(l, it.prop) } },
            contentAlignment = Alignment.Center,
        ) { Diamond(filled = keyedHere, outline = animated) }
    }
}

// ============================================================== KEYFRAMES

@Composable
fun KeyframesPanel(ed: EditorState, openSheet: (EditorSheet) -> Unit) {
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val l = ed.layer
    if (l == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("Elige una capa en la pestaña Capas.", "Pick a layer in the Layers tab."), color = Ps.Muted, fontSize = 14.sp)
        }
        return
    }
    var prop by remember(l.id) { mutableStateOf("posX") }
    val props = buildList {
        add("anchorX"); add("anchorY"); add("posX"); add("posY"); add("scaleX"); add("scaleY"); add("rot")
        if (l.kind == LayerKind.Video) { add("opacity"); add("speed") }
        l.tile?.let { addAll(listOf("tileCX", "tileCY", "tileOW", "tileOH")) }
    }
    val t = l.track(prop)
    val e = ed.eps()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(l.name, color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            props.forEach { pr ->
                val tr0 = l.track(pr) ?: return@forEach
                val sel = pr == prop
                Row(
                    Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) SkyBlue.copy(alpha = 0.16f) else Color(0x0AFFFFFF))
                        .clickable { prop = pr },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(propName(pr), color = Ps.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 13.sp,
                        modifier = Modifier.width(92.dp).padding(start = 10.dp))
                    Canvas(
                        Modifier.weight(1f).fillMaxHeight().padding(end = 10.dp)
                            .pointerInput(pr, l.start, l.length, tr0.keys.size) {
                                detectTapGestures { pos ->
                                    val span = max(l.length, 0.001f)
                                    val tt = l.start + (pos.x / size.width) * span
                                    val i = tr0.keys.indices.minByOrNull { abs(tr0.keys[it].t - tt) }
                                    if (i != null && abs(tr0.keys[i].t - tt) < span * 0.06f) ed.seek(tr0.keys[i].t)
                                    else ed.seek(tt)
                                    prop = pr
                                }
                            },
                    ) {
                        val span = max(l.length, 0.001f)
                        val cy = size.height / 2
                        if (tr0.keys.size >= 2) {
                            val x0 = ((tr0.keys.first().t - l.start) / span) * size.width
                            val x1 = ((tr0.keys.last().t - l.start) / span) * size.width
                            drawLine(Color(0x38FFFFFF), Offset(x0, cy), Offset(x1, cy), 3.dp.toPx())
                        } else drawLine(Color(0x14FFFFFF), Offset(0f, cy), Offset(size.width, cy), 2.dp.toPx())
                        tr0.keys.forEach { k ->
                            val x = ((k.t - l.start) / span) * size.width
                            val r = 7.dp.toPx()
                            val atHere = abs(k.t - ed.playhead) <= e
                            val path = Path().apply {
                                moveTo(x, cy - r); lineTo(x + r, cy); lineTo(x, cy + r); lineTo(x - r, cy); close()
                            }
                            drawPath(path, if (atHere) Ps.Mint else Color(0xFF9DA1CC))
                        }
                        val px = ((ed.playhead - l.start) / span) * size.width
                        if (px in 0f..size.width) drawLine(Coral, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
                    }
                }
            }
        }
        // controles de la propiedad elegida
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x14FFFFFF)).pressable {
                t?.keys?.lastOrNull { it.t < ed.playhead - e }?.let { ed.seek(it.t) }
            }, contentAlignment = Alignment.Center) { PsIcon(Icons.Filled.KeyboardArrowLeft, Ps.Text, 22.dp) }
            Box(Modifier.size(38.dp).clip(CircleShape).background(Ps.Mint.copy(alpha = 0.2f)).pressable { ed.toggleKey(l, prop) }, contentAlignment = Alignment.Center) {
                Diamond(filled = (t?.indexAt(ed.playhead, e) ?: -1) >= 0, outline = t?.animated == true, size = 16.dp)
            }
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x14FFFFFF)).pressable {
                t?.keys?.firstOrNull { it.t > ed.playhead + e }?.let { ed.seek(it.t) }
            }, contentAlignment = Alignment.Center) { PsIcon(Icons.Filled.KeyboardArrowRight, Ps.Text, 22.dp) }
            val near = t?.keys?.indices?.minByOrNull { abs(t.keys[it].t - ed.playhead) }
            val canCurve = t != null && t.keys.size >= 2 && near != null
            PsChip(
                tr("Editar curva", "Edit curve"), canCurve, Modifier.weight(1f), Ps.Mint,
            ) { if (canCurve) openSheet(EditorSheet.CurveFor(l.id, prop, near!!)) }
        }
        Note(
            if (t == null || t.keys.size < 2) tr("Pon un keyframe (◆) en dos momentos para poder editar la curva entre ellos.", "Add a keyframe (◆) at two moments to edit the curve between them.")
            else tr("La curva se edita para el keyframe más cercano al cursor y su vecino.", "The curve is edited for the keyframe nearest the playhead and its neighbour.")
        )
    }
}

// ================================================================== HOJAS

@Composable
fun EditorSheets(
    ed: EditorState,
    s: EditorSheet,
    openDial: (DialSpec) -> Unit,
    openSheet: (EditorSheet) -> Unit,
    onDismiss: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val p = ed.project ?: return
    when (s) {
        is EditorSheet.MenuFor -> {
            val l = p.layer(s.layerId) ?: return
            var name by remember(l.id) { mutableStateOf(l.name) }
            PsSheet(onDismiss) {
                SheetTitle(tr("Capa", "Layer"), l.name)
                PsTextField(name, { name = it }, tr("Nombre", "Name"))
                SheetButtons(null, {}, tr("Guardar nombre", "Save name"), { ed.setName(l.id, name); onDismiss() }, okEnabled = name.isNotBlank())
                if (ed.canSplit(l.id)) {
                    SheetButtons(null, {}, tr("Dividir en el cursor", "Split at playhead"), { ed.splitLayer(l.id); onDismiss() })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PsChip(tr("Subir", "Move up"), false, Modifier.weight(1f)) { ed.moveLayerOrder(l.id, true) }
                    PsChip(tr("Bajar", "Move down"), false, Modifier.weight(1f)) { ed.moveLayerOrder(l.id, false) }
                    PsChip(tr("Duplicar", "Duplicate"), false, Modifier.weight(1f)) { ed.duplicateLayer(l.id); onDismiss() }
                }
                SheetButtons(null, {}, tr("Borrar capa", "Delete layer"), { ed.deleteLayer(l.id); onDismiss() }, danger = true)
            }
        }
        is EditorSheet.ParentFor -> {
            val l = p.layer(s.layerId) ?: return
            PsSheet(onDismiss) {
                SheetTitle(tr("Emparentar", "Parent"), tr("La capa seguirá los movimientos del padre.", "The layer follows the parent's movement."))
                val options = p.layers.filter { it.id != l.id && !p.wouldLoop(l, it) }
                ParentOption(tr("Ninguno", "None"), l.parentId == null) { ed.setParent(l, null); onDismiss() }
                options.asReversed().forEach { o ->
                    ParentOption(o.name + if (o.kind == LayerKind.Null) "  ·  " + tr("nulo", "null") else "", l.parentId == o.id) {
                        ed.setParent(l, o.id); onDismiss()
                    }
                }
                if (options.isEmpty()) Note(tr("Agrega un nulo (+ Nulo) para poder emparentar.", "Add a null (+ Null) to be able to parent."))
            }
        }
        is EditorSheet.TileFor -> TileSheet(ed, p.layer(s.layerId) ?: return, openDial, openSheet, onDismiss)
        is EditorSheet.SpeedFor -> SpeedSheet(ed, p.layer(s.layerId) ?: return, openSheet, onDismiss)
        is EditorSheet.CurveFor -> CurveSheet(ed, p.layer(s.layerId) ?: return, s, onDismiss)
    }
}

@Composable
private fun ParentOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) Violet.copy(alpha = 0.2f) else Color(0x0DFFFFFF)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(label, color = Ps.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

// ---------------------------------------------------------------- curva

@Composable
private fun CurveSheet(ed: EditorState, l: Layer, s: EditorSheet.CurveFor, onDismiss: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val t = l.track(s.prop) ?: return
    var withNext by remember { mutableStateOf(true) }
    var speedView by remember { mutableStateOf(false) }
    val seg = if (withNext) s.keyIndex else s.keyIndex - 1
    if (seg < 0 || seg >= t.keys.size - 1) {
        PsSheet(onDismiss) {
            SheetTitle(tr("Curva", "Curve"), propName(s.prop))
            Note(tr("Ese keyframe no tiene un vecino en ese lado. Elige el otro lado.", "That keyframe has no neighbour on that side. Pick the other side."))
            SegControl(listOf(tr("Con el anterior", "With previous"), tr("Con el siguiente", "With next")), if (withNext) 1 else 0) { withNext = it == 1 }
            SheetButtons(null, {}, tr("Cerrar", "Close"), onDismiss)
        }
        return
    }
    val k = t.keys[seg]
    val next = t.keys[seg + 1]
    fun set(c: Curve) { t.keys[seg] = k.copy(curve = c); ed.touch() }
    PsSheet(onDismiss) {
        SheetTitle(tr("Curva · ", "Curve · ") + propName(s.prop), "${fmtTime(k.t)} → ${fmtTime(next.t)}")
        SegControl(listOf(tr("Con el anterior", "With previous"), tr("Con el siguiente", "With next")), if (withNext) 1 else 0, SkyBlue) { withNext = it == 1 }
        SegControl(listOf(tr("Gráfico de valor", "Value graph"), tr("Gráfico de velocidad", "Speed graph")), if (speedView) 1 else 0, Violet) { speedView = it == 1 }
        CurveEditor(k.curve, speedView, { set(it) }, Modifier.fillMaxWidth().height(210.dp))
        Note(
            if (speedView) tr("Arrastra los círculos para cambiar la influencia de cada extremo.", "Drag the circles to change each end's influence.")
            else tr("Arrastra los círculos naranjas para cambiar la forma.", "Drag the orange circles to change the shape.")
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val presets = listOf(
                tr("Lineal", "Linear") to Curve.Linear, tr("Suave", "Smooth") to Curve.Smooth,
                tr("Entrada", "Ease in") to Curve.EaseIn, tr("Salida", "Ease out") to Curve.EaseOut,
                tr("Mantener", "Hold") to Curve.Hold,
            )
            presets.forEach { (n, c) -> PsChip(n, k.curve == c, Modifier.weight(1f), height = 32.dp) { set(c) } }
        }
        SheetButtons(null, {}, tr("Listo", "Done"), onDismiss)
    }
}

// ---------------------------------------------------------- Motion Tile

@Composable
private fun TileSheet(
    ed: EditorState, l: Layer,
    openDial: (DialSpec) -> Unit, openSheet: (EditorSheet) -> Unit, onDismiss: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val f = l.tile ?: return
    PsSheet(onDismiss) {
        SheetTitle("Motion Tile", l.name + " · " + tr("repite la capa en mosaico", "repeats the layer as tiles"))
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SwitchRow("Motion Tile", null, f.enabled) { f.enabled = it; ed.touch() }
            TileRow(ed, l, tr("Centro del mosaico", "Tile center"), listOf("tileCX" to "X", "tileCY" to "Y"), "px", openDial)
            FixedRow(
                tr("Tamaño del mosaico", "Tile size"),
                listOf(
                    Triple(tr("Ancho", "Width"), f.tileW) { v: Float -> f.tileW = v },
                    Triple(tr("Alto", "Height"), f.tileH) { v: Float -> f.tileH = v },
                ), "%", ed, openDial,
            )
            TileRow(ed, l, tr("Tamaño de salida", "Output size"), listOf("tileOW" to tr("Ancho", "Width"), "tileOH" to tr("Alto", "Height")), "%", openDial)
            SwitchRow(tr("Espejar bordes", "Mirror edges"), tr("Cada mosaico vecino se voltea", "Each neighbouring tile is flipped"), f.mirror) { f.mirror = it; ed.touch() }
            FixedRow(tr("Fase", "Phase"), listOf(Triple("", f.phase) { v: Float -> f.phase = v }), "°", ed, openDial)
            SwitchRow(tr("Desfase horizontal", "Horizontal offset"), tr("Mueve las filas en lugar de las columnas", "Moves rows instead of columns"), f.offsetRows) { f.offsetRows = it; ed.touch() }
        }
        Note(tr("Se ve en la exportación. En la vista previa de esta versión no se dibuja el mosaico.", "Shown on export. This version's preview doesn't draw the tiling."))
        SheetButtons(
            tr("Quitar efecto", "Remove effect"), { l.tile = null; ed.touch(); onDismiss() },
            tr("Listo", "Done"), onDismiss,
        )
    }
}

@Composable
private fun TileRow(ed: EditorState, l: Layer, label: String, items: List<Pair<String, String>>, unit: String, openDial: (DialSpec) -> Unit) {
    val e = ed.eps()
    val tracks = items.mapNotNull { l.track(it.first) }
    val keyedHere = tracks.any { it.indexAt(ed.playhead, e) >= 0 }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0x0DFFFFFF)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(88.dp), lineHeight = 15.sp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            items.forEach { (prop, short) ->
                val v = ed.valueOf(l, prop)
                ValueChip(short, "%.0f".format(v) + unit) {
                    openDial(DialSpec("$label · $short", l.name, v, unit, 0, null, null, { nv -> ed.setProp(l, prop, nv) }))
                }
            }
        }
        Box(Modifier.size(32.dp).clip(CircleShape).clickable { items.forEach { ed.toggleKey(l, it.first) } }, contentAlignment = Alignment.Center) {
            Diamond(filled = keyedHere, outline = tracks.any { it.animated })
        }
    }
}

@Composable
private fun FixedRow(
    label: String,
    items: List<Triple<String, Float, (Float) -> Unit>>,
    unit: String, ed: EditorState, openDial: (DialSpec) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0x0DFFFFFF)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(88.dp), lineHeight = 15.sp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            items.forEach { (short, v, set) ->
                ValueChip(short, "%.1f".format(v) + unit) {
                    openDial(DialSpec(if (short.isEmpty()) label else "$label · $short", null, v, unit, 1, null, null, { nv -> set(nv); ed.touch() }))
                }
            }
        }
    }
}

@Composable
fun ValueChip(short: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier.widthIn(min = 54.dp).height(32.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x14FFFFFF))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (short.isNotEmpty()) { Text(short, color = Ps.Muted2, fontSize = 10.sp); Spacer(Modifier.width(4.dp)) }
        Text(text, color = Ps.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ------------------------------------------------------------- tiempo

@Composable
private fun SpeedSheet(ed: EditorState, l: Layer, openSheet: (EditorSheet) -> Unit, onDismiss: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val p = ed.project ?: return
    val e = ed.eps()
    val outFrames = (l.length * p.fps).toInt()
    val srcSpan = max(0f, l.sourceTime(l.end) - l.inPoint)
    val real = min(outFrames, (srcSpan * l.srcFps).toInt())
    val newF = max(0, outFrames - real)
    PsSheet(onDismiss) {
        SheetTitle(tr("Tiempo del clip", "Clip time"), l.name)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0DFFFFFF)).padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(tr("Original", "Original"), color = Ps.Muted2, fontSize = 11.sp)
                Text("${"%.2f".format(l.srcFps)} fps", color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("${"%.1f".format(l.srcDur)} s", color = Ps.Muted, fontSize = 11.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(tr("Resultado (${p.fps.toInt()} fps)", "Result (${p.fps.toInt()} fps)"), color = Ps.Muted2, fontSize = 11.sp)
                Text("${"%.1f".format(l.length)} s", color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("$outFrames frames", color = Ps.Muted, fontSize = 11.sp)
            }
        }
        // velocidad
        val keyedHere = l.speed.indexAt(ed.playhead, e) >= 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Velocidad", "Speed"), color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { ed.toggleKey(l, "speed") }, contentAlignment = Alignment.Center) {
                Diamond(keyedHere, l.speed.animated)
            }
            ValueChip("", "%.2f".format(ed.valueOf(l, "speed")).trimEnd('0').trimEnd('.') + "%") {}
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(100f, 25f, 12.5f, 6.25f).forEach { v ->
                PsChip(("%.2f".format(v)).trimEnd('0').trimEnd('.') + "%", nearlyEqual(ed.valueOf(l, "speed"), v), Modifier.weight(1f), height = 30.dp) {
                    ed.setProp(l, "speed", v)
                }
            }
        }
        RampGraph(l, ed.playhead)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x14FFFFFF)).pressable {
                l.speed.keys.lastOrNull { it.t < ed.playhead - e }?.let { ed.seek(it.t) }
            }, contentAlignment = Alignment.Center) { PsIcon(Icons.Filled.KeyboardArrowLeft, Ps.Text, 20.dp) }
            val idx = l.speed.keys.indexOfFirst { it.t >= ed.playhead - e }
            Text(
                if (l.speed.animated) tr("Rampa · ${l.speed.keys.size} keyframes", "Ramp · ${l.speed.keys.size} keyframes")
                else tr("Velocidad fija", "Fixed speed"),
                color = Ps.Muted2, fontSize = 11.sp, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x14FFFFFF)).pressable {
                l.speed.keys.firstOrNull { it.t > ed.playhead + e }?.let { ed.seek(it.t) }
            }, contentAlignment = Alignment.Center) { PsIcon(Icons.Filled.KeyboardArrowRight, Ps.Text, 20.dp) }
            val near = if (l.speed.keys.isEmpty()) -1 else l.speed.keys.indices.minByOrNull { abs(l.speed.keys[it].t - ed.playhead) } ?: -1
            PsChip(tr("Editar curva", "Edit curve"), l.speed.keys.size >= 2, height = 30.dp) {
                if (l.speed.keys.size >= 2 && near >= 0) { onDismiss(); openSheet(EditorSheet.CurveFor(l.id, "speed", near)) }
            }
        }
        // frames
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0DFFFFFF)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(tr("Reales ", "Real ") + real, color = Ps.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(tr("Nuevos ", "New ") + newF, color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                val tot = max(1, real + newF).toFloat()
                if (real > 0) Box(Modifier.weight(max(0.001f, real / tot)).fillMaxHeight().background(Ps.Mint))
                if (newF > 0) Box(Modifier.weight(max(0.001f, newF / tot)).fillMaxHeight().background(Violet.copy(alpha = 0.7f)))
            }
            if (newF > 0) Text(
                tr("A esta velocidad faltan frames: los nuevos se generan con la interpolación.", "At this speed frames are missing: new ones are generated by interpolation."),
                color = Ps.Amber, fontSize = 11.sp, lineHeight = 15.sp,
            )
        }
        Text(tr("Interpolación", "Interpolation"), color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        SegControl(
            listOf(tr("Ninguna", "None"), tr("Mezcla", "Blend"), tr("Flujo óptico", "Optical flow")),
            when (l.interp) { Interp.None -> 0; Interp.Blend -> 1; Interp.Flow -> 2 }, Violet,
        ) { l.interp = when (it) { 0 -> Interp.None; 1 -> Interp.Blend; else -> Interp.Flow }; ed.touch() }
        Note(tr("Se aplica al exportar; la vista previa usa Mezcla para ir fluida.", "Applied on export; the preview uses Blend to stay smooth."))
        SwitchRow(tr("Silenciar audio", "Mute audio"), tr("Estirado suena grave y raro", "Stretched audio sounds low and odd"), l.mute) { l.mute = it; ed.touch() }
        SheetButtons(null, {}, tr("Listo", "Done"), onDismiss)
    }
}

@Composable
private fun RampGraph(l: Layer, playhead: Float) {
    Canvas(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x2E000000))) {
        val span = max(l.length, 0.001f)
        val topV = max(100f, (l.speed.keys.maxOfOrNull { it.v } ?: l.speed.base)).coerceAtLeast(100f)
        val lowV = 1f
        fun y(v: Float): Float {
            // escala logaritmica: se ven por igual 100%, 25% y 6.25%
            val a = kotlin.math.ln(max(v, lowV)); val b = kotlin.math.ln(lowV); val c = kotlin.math.ln(topV)
            val f = ((a - b) / (c - b)).coerceIn(0f, 1f)
            return size.height - 10.dp.toPx() - f * (size.height - 20.dp.toPx())
        }
        val n = 80
        val path = Path()
        for (i in 0..n) {
            val tt = l.start + span * i / n
            val x = size.width * i / n
            val yy = y(l.speed.at(tt))
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        drawPath(path, Ps.Mint, style = Stroke(width = 2.5.dp.toPx()))
        l.speed.keys.forEach { k ->
            val x = ((k.t - l.start) / span) * size.width
            val cy = y(k.v)
            val r = 6.dp.toPx()
            val dp = Path().apply { moveTo(x, cy - r); lineTo(x + r, cy); lineTo(x, cy + r); lineTo(x - r, cy); close() }
            drawPath(dp, Ps.Mint)
        }
        val px = ((playhead - l.start) / span) * size.width
        if (px in 0f..size.width) drawLine(Coral, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
    }
}

// ------------------------------------------------------ ajustes de proyecto

@Composable
fun ProjectSettingsSheet(ed: EditorState, openDial: (DialSpec) -> Unit, onDismiss: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val p = ed.project ?: return
    PsSheet(onDismiss) {
        SheetTitle(tr("Ajustes del proyecto", "Project settings"), p.name)
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // dimensiones
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x0DFFFFFF)).padding(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Dimensiones", "Dimensions"), color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val cur = Formats.indexOfFirst { it.w == p.width && it.h == p.height }
                    ChipRow(Formats.map { it.label }, cur) { ed.setSize(Formats[it].w, Formats[it].h, p.resize) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var wTxt by remember(p.width) { mutableStateOf(p.width.toString()) }
                        var hTxt by remember(p.height) { mutableStateOf(p.height.toString()) }
                        fun even(v: Int) = (v.coerceIn(16, 4096) / 2) * 2
                        NumField(wTxt, { wTxt = it }, tr("Ancho", "Width"), Modifier.weight(1f)) {
                            val v = wTxt.toIntOrNull()
                            if (v != null && even(v) != p.width) ed.setSize(even(v), p.height, p.resize) else wTxt = p.width.toString()
                        }
                        PsChip("⇄", false, height = 32.dp) { ed.setSize(p.height, p.width, p.resize) }
                        NumField(hTxt, { hTxt = it }, tr("Alto", "Height"), Modifier.weight(1f)) {
                            val v = hTxt.toIntOrNull()
                            if (v != null && even(v) != p.height) ed.setSize(p.width, even(v), p.resize) else hTxt = p.height.toString()
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x0DFFFFFF)).padding(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Fotogramas por segundo", "Frames per second"), color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val fl = listOf(24f, 30f, 60f)
                    ChipRow(fl.map { it.toInt().toString() } + tr("Otro", "Other"), fl.indexOfFirst { nearlyEqual(it, p.fps) }.let { if (it < 0) 3 else it }) {
                        if (it < 3) { p.fps = fl[it]; ed.touch(frames = true) }
                        else openDial(DialSpec(tr("Fotogramas por segundo", "Frames per second"), null, p.fps, "", 2, 1f, 240f, { v -> p.fps = v; ed.touch() }))
                    }
                    Note(tr("A 30 fps un clip de 239.76 fps se puede estirar hasta 12.5% sin frames repetidos.", "At 30 fps a 239.76 fps clip can be stretched down to 12.5% with no repeated frames."))
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x0DFFFFFF)).padding(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Calidad de la vista previa", "Preview quality"), color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val divs = listOf(1, 2, 4, 8)
                    ChipRow(listOf(tr("Completa", "Full"), "1/2", "1/4", "1/8"), divs.indexOf(p.previewDiv).coerceAtLeast(0)) {
                        p.previewDiv = divs[it]; ed.touch(frames = true)
                    }
                    Note(tr(
                        "Solo cambia la vista previa; la exportación siempre sale al tamaño del proyecto. 1/2 es la mitad por lado: ${p.width / 2}×${p.height / 2}.",
                        "Only changes the preview; export is always at the project size. 1/2 is half per side: ${p.width / 2}×${p.height / 2}.",
                    ))
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x0DFFFFFF)).padding(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Desenfoque de movimiento", "Motion blur"), color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(tr("Ángulo del obturador", "Shutter angle"), color = Ps.Muted2, fontSize = 11.sp)
                    val angles = listOf(90f, 180f, 360f)
                    ChipRow(angles.map { "${it.toInt()}°" }, angles.indexOfFirst { nearlyEqual(it, p.blurAngle) }.coerceAtLeast(0)) { p.blurAngle = angles[it]; ed.touch() }
                    Text(tr("Calidad (muestras por frame)", "Quality (samples per frame)"), color = Ps.Muted2, fontSize = 11.sp)
                    val q = listOf(4, 8, 16)
                    ChipRow(listOf(tr("Baja · 4", "Low · 4"), tr("Media · 8", "Medium · 8"), tr("Alta · 16", "High · 16")), q.indexOf(p.blurSamples).coerceAtLeast(1), Violet) {
                        p.blurSamples = q[it]; ed.touch()
                    }
                    SwitchRow(tr("Mostrar en la vista previa", "Show in preview"), tr("Apagado para que vaya fluida", "Off to keep it smooth"), p.blurInPreview) { p.blurInPreview = it; ed.touch() }
                    Note(tr("Se enciende o apaga en cada capa de video. Aquí se define cómo se ve en todo el proyecto.", "Turned on or off per video layer. This defines how it looks across the project."))
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x0DFFFFFF)).padding(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Al cambiar el tamaño", "When resizing"), color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    SegControl(listOf(tr("Ajustar capas", "Fit layers"), tr("Rellenar", "Fill"), tr("Mantener", "Keep")), p.resize.ordinal) {
                        p.resize = ResizeMode.values()[it]; ed.touch()
                    }
                    Note(tr("Ajustar: cada capa se escala para caber completa. Mantener: no se toca nada.", "Fit: each layer scales to fit entirely. Keep: nothing is touched."))
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x0DFFFFFF)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Color de fondo", "Background color"), color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                listOf(0xFF000000, 0xFFFFFFFF, 0xFF1A1D3A, 0xFF7CF2C8, 0xFFFF6B8B).forEach { c ->
                    val ci = c.toInt()
                    Box(
                        Modifier.padding(start = 8.dp).size(28.dp).clip(CircleShape).background(Color(ci))
                            .border(if (p.bg == ci) 2.5.dp else 1.dp, if (p.bg == ci) Ps.Mint else Color(0x66FFFFFF), CircleShape)
                            .clickable { p.bg = ci; ed.touch() },
                    )
                }
            }
        }
        SheetButtons(null, {}, tr("Listo", "Done"), onDismiss)
    }
}

@Composable
private fun SizeChip(label: String, value: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x14FFFFFF)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = Ps.Muted2, fontSize = 10.sp)
        Spacer(Modifier.width(6.dp))
        Text("$value", color = Ps.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ----------------------------------------------------------- marcadores

@Composable
fun MarkerSheet(ed: EditorState, id: String, openDial: (DialSpec) -> Unit, onDismiss: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") ed.rev
    val p = ed.project ?: return
    var currentId by remember(id) { mutableStateOf(id) }
    val m = p.markers.firstOrNull { it.id == currentId } ?: return
    var name by remember(currentId) { mutableStateOf(m.name) }
    PsSheet(onDismiss) {
        SheetTitle(tr("Marcador", "Marker"), tr("Señales en la línea de tiempo", "Signals on the timeline"))
        PsTextField(name, { name = it; m.name = it; ed.touch() }, tr("Nombre (opcional)", "Name (optional)"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Tiempo", "Time"), color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            ValueChip("", fmtTime(m.time)) {
                openDial(DialSpec(tr("Tiempo del marcador", "Marker time"), null, m.time, " s", 2, 0f, 3600f, { v -> m.time = v; p.markers.sortBy { it.time }; ed.touch() }))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Color", color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            ed.markerColors.forEach { c ->
                Box(
                    Modifier.padding(start = 8.dp).size(28.dp).clip(CircleShape).background(Color(c))
                        .border(if (m.color == c) 2.5.dp else 0.dp, Color.White, CircleShape)
                        .clickable { m.color = c; ed.touch() },
                )
            }
        }
        SwitchRow(tr("Imán a marcadores", "Snap to markers"), tr("Los clips se pegan al marcador al arrastrar", "Clips stick to markers when dragged"), ed.snapEnabled) { ed.snapEnabled = it }
        if (p.markers.size > 1) {
            Text("${tr("Marcadores", "Markers")} (${p.markers.size})", color = Ps.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                p.markers.forEach { mk ->
                    Row(
                        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (mk.id == currentId) Color(0x1F5ED4FF) else Color(0x0AFFFFFF))
                            .clickable { currentId = mk.id }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FlagShape(Color(mk.color), 12.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(mk.name.ifBlank { tr("Marcador", "Marker") }, color = Ps.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(fmtTime(mk.time), color = Ps.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).border(1.5.dp, Color(0x80FF8A8A), RoundedCornerShape(21.dp))
                    .pressable { ed.deleteMarker(m.id); onDismiss() },
                contentAlignment = Alignment.Center,
            ) { Text(tr("Eliminar", "Delete"), color = Color(0xFFFF8A8A), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0x1AFFFFFF))
                    .pressable { ed.seek(m.time); onDismiss() },
                contentAlignment = Alignment.Center,
            ) { Text(tr("Ir al marcador", "Go to marker"), color = Ps.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(Ps.Mint).pressable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text(tr("Listo", "Done"), color = Ps.OnMint, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
