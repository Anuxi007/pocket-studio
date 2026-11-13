package com.anuxi.pocketstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.anuxi.pocketstudio.data.tr
import com.anuxi.pocketstudio.editor.*
import kotlin.math.abs

private class PropSpec(val unit: String, val decimals: Int, val lo: Float?, val hi: Float?)

private fun propSpec(prop: String): PropSpec = when (prop) {
    "scaleX", "scaleY" -> PropSpec("%", 1, 0f, 5000f)
    "rot" -> PropSpec("°", 1, null, null)
    "opacity" -> PropSpec("%", 0, 0f, 100f)
    "speed" -> PropSpec("%", 2, null, null)
    else -> PropSpec("", 0, null, null)
}

private val transformGroups: List<Pair<String, List<String>>>
    get() = listOf(
        tr("Anclaje", "Anchor") to listOf("anchorX", "anchorY"),
        tr("Posición", "Position") to listOf("posX", "posY"),
        tr("Escala", "Scale") to listOf("scaleX", "scaleY"),
        tr("Rotación", "Rotation") to listOf("rot"),
        tr("Opacidad", "Opacity") to listOf("opacity"),
    )

@Composable
private fun BarBtn(label: String, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
            .background(if (enabled) color.copy(alpha = 0.18f) else Color(0x0DFFFFFF))
            .pressable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) color else Ps.Muted2, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BarTop(title: String, subtitle: String?, onBack: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x14FFFFFF)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", color = Ps.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ps.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Ps.Muted2, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun LayerBar(ed: EditorState, openDial: (DialSpec) -> Unit, openSheet: (EditorSheet) -> Unit) {
    ed.revFlow.collectAsState().value
    val p = ed.project ?: return
    val l = ed.layer ?: return
    if (!ed.barOpen) return
    var confirmDelete by remember { mutableStateOf(false) }
    val video = l.kind == LayerKind.Video
    val sk = ed.selKey
    val e = ed.eps()
    val keyTrack = if (sk != null && sk.first == l.id) l.track(sk.second) else null
    val keyIdx = if (sk != null && keyTrack != null) keyTrack.keys.indexOfFirst { abs(it.t - sk.third) <= e } else -1

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (keyIdx >= 0 && sk != null && keyTrack != null) {
            // ------------------------------------------------ keyframe elegido
            val key = keyTrack.keys[keyIdx]
            val kind = when (keyKind(keyTrack, keyIdx)) {
                2 -> tr("Mantener", "Hold"); 1 -> tr("Curva", "Curve"); else -> tr("Lineal", "Linear")
            }
            BarTop(
                propName(sk.second), "${fmtTime(key.t)} · $kind", { ed.selKey = null },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BarBtn(tr("Editar valor", "Edit value"), Ps.Mint, Modifier.weight(1f)) {
                    ed.seek(key.t)
                    val sp = propSpec(sk.second)
                    openDial(DialSpec(propName(sk.second), l.name, key.v, sp.unit, sp.decimals, sp.lo, sp.hi, { nv -> ed.setProp(l, sk.second, nv) }))
                }
                BarBtn(tr("Curvas", "Curves"), Violet, Modifier.weight(1f), enabled = keyTrack.keys.size >= 2) {
                    openSheet(EditorSheet.CurveFor(l.id, sk.second, keyIdx))
                }
            }
        } else when (ed.barPanel) {
            1 -> {
                // ------------------------------------------------ transform
                BarTop("Transform", l.name, { ed.barPanel = 0 })
                val groups = transformGroups.filter { video || it.second[0] != "opacity" }
                val cur = ed.transformProp.coerceIn(0, groups.size - 1)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEachIndexed { i, (name, props) ->
                        val keyed = props.any { l.track(it)?.animated == true }
                        Box {
                            PsChip(name, i == cur, Modifier, Ps.Mint, 34.dp) { ed.transformProp = i }
                            if (keyed) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp).size(7.dp).clip(CircleShape).background(Coral))
                        }
                    }
                }
                val (name, props) = groups[cur]
                val defs = props.map { pr ->
                    val sp = propSpec(pr)
                    PropDef("", pr, if (props.size > 1) pr.last().toString().uppercase() else "", sp.unit, sp.decimals, sp.lo, sp.hi)
                }
                PropRow(ed, l, name, defs, openDial, trailing = if (props[0] == "scaleX") ({
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .background(if (l.scaleLinked) Ps.Mint.copy(alpha = 0.25f) else Color(0x14FFFFFF))
                            .clickable { l.scaleLinked = !l.scaleLinked; ed.touch() },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (l.scaleLinked) "🔗" else "⛓", fontSize = 14.sp) }
                }) else null)
            }
            2 -> {
                // ------------------------------------------------ efectos
                BarTop(tr("Efectos", "Effects"), l.name, { ed.barPanel = 0 })
                val tile = l.tile
                SwitchRow(
                    "Motion Tile", tr("Repite la capa en mosaico", "Repeats the layer as tiles"),
                    tile?.enabled == true,
                ) { on ->
                    val t = l.tile ?: TileFx().also {
                        it.centerX.base = l.srcW / 2f; it.centerY.base = l.srcH / 2f
                        l.tile = it
                    }
                    t.enabled = on
                    ed.touch()
                }
                if (tile != null) BarBtn(tr("Ajustes de Motion Tile ›", "Motion Tile settings ›"), SkyBlue, Modifier.fillMaxWidth()) {
                    openSheet(EditorSheet.TileFor(l.id))
                }
            }
            3 -> {
                // ------------------------------------------------ flujo optico
                BarTop(tr("Flujo óptico", "Optical flow"), l.name, { ed.barPanel = 0 })
                SegControl(
                    listOf(tr("Apagado", "Off"), tr("Mezcla", "Blend"), tr("Flujo", "Flow")),
                    when (l.interp) { Interp.None -> 0; Interp.Blend -> 1; Interp.Flow -> 2 }, Violet,
                ) { l.interp = when (it) { 0 -> Interp.None; 1 -> Interp.Blend; else -> Interp.Flow }; ed.touch() }
                Note(tr("Crea frames nuevos cuando bajas la velocidad. Se aplica al exportar.", "Creates new frames when you slow the clip down. Applied on export."))
            }
            else -> {
                // ------------------------------------------------ principal
                BarTop(l.name, if (video) "${l.srcW}×${l.srcH}" else tr("Capa nula", "Null layer"), null) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x33FF6B6B)).clickable { confirmDelete = true }, contentAlignment = Alignment.Center) {
                        Text("🗑", fontSize = 15.sp)
                    }
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x14FFFFFF)).clickable { ed.select(null) }, contentAlignment = Alignment.Center) {
                        Text("✕", color = Ps.Text, fontSize = 14.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BarBtn("Transform", Ps.Mint) { ed.barPanel = 1 }
                    BarBtn("✂ " + tr("Dividir", "Split") + " · " + fmtTime(ed.playhead), SkyBlue, enabled = ed.canSplit(l.id)) { ed.splitLayer(l.id) }
                    BarBtn("+ " + tr("Nulo", "Null"), Ps.Amber) { ed.addNullFor(l.id) }
                    if (video) {
                        BarBtn(tr("Efectos", "Effects"), SkyBlue) { ed.barPanel = 2 }
                        BarBtn(tr("Flujo óptico", "Optical flow"), Violet) { ed.barPanel = 3 }
                        BarBtn(tr("Motion blur", "Motion blur") + if (l.blur) " ✓" else "", Orange) { l.blur = !l.blur; ed.touch() }
                    }
                    BarBtn(tr("Padre", "Parent"), Violet) { openSheet(EditorSheet.ParentFor(l.id)) }
                    BarBtn(tr("Más", "More"), Ps.Muted) { openSheet(EditorSheet.MenuFor(l.id)) }
                }
            }
        }
    }

    if (confirmDelete) {
        PsSheet({ confirmDelete = false }) {
            SheetTitle(tr("¿Eliminar la capa?", "Delete this layer?"), l.name)
            Text(
                tr("Se borra de la línea de tiempo con sus keyframes. Puedes deshacerlo con la flecha de arriba.", "It is removed from the timeline with its keyframes. You can undo it with the arrow at the top."),
                color = Ps.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            SheetButtons(
                tr("Cancelar", "Cancel"), { confirmDelete = false },
                tr("Eliminar", "Delete"), { confirmDelete = false; ed.deleteLayer(l.id) }, danger = true,
            )
        }
    }
}
