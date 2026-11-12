package com.anuxi.pocketstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuxi.pocketstudio.data.tr
import com.anuxi.pocketstudio.editor.Curve
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val SheetBg = Color(0xFF23264F)
val Violet = Color(0xFFB79CFF)
val Coral = Color(0xFFFF6B8B)
val Orange = Color(0xFFFF9C6B)
val SkyBlue = Color(0xFF6FA8FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        contentColor = Ps.Text,
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF))
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, color = Ps.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Ps.Muted, fontSize = 12.sp)
    }
}

@Composable
fun Note(text: String, color: Color = Ps.Muted2) {
    Text(text, color = color, fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
fun PsChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Ps.Mint,
    height: Dp = 34.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (selected) color else Color(0x14FFFFFF))
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Ps.OnMint else Ps.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun ChipRow(
    options: List<String>,
    selected: Int,
    color: Color = Ps.Mint,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, s ->
            PsChip(s, i == selected, Modifier.weight(1f), color) { onSelect(i) }
        }
    }
}

@Composable
fun SegControl(
    options: List<String>,
    selected: Int,
    color: Color = Ps.Mint,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x12FFFFFF)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, s ->
            Box(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (i == selected) color else Color.Transparent)
                    .pressable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s,
                    color = if (i == selected) Ps.OnMint else Ps.Muted,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun PsSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Ps.OnMint, checkedTrackColor = Ps.Mint,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0x2EFFFFFF),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0x0DFFFFFF))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFFD6D8F2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Ps.Muted2, fontSize = 11.sp)
        }
        PsSwitch(checked, onChange)
    }
}

@Composable
fun PsTextField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF)).border(1.5.dp, Ps.Mint, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, color = Ps.Muted2, fontSize = 15.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(Ps.Mint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SheetButtons(
    cancel: String?, onCancel: () -> Unit,
    ok: String, onOk: () -> Unit,
    danger: Boolean = false,
    okEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (cancel != null) {
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp))
                    .border(1.5.dp, Ps.Line, RoundedCornerShape(21.dp)).pressable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) { Text(cancel, color = Ps.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Box(
            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp))
                .background(
                    if (danger) Color(0xFFFF6B6B) else if (okEnabled) Ps.Mint else Ps.Mint.copy(alpha = 0.35f)
                )
                .pressable(enabled = okEnabled, onClick = onOk),
            contentAlignment = Alignment.Center,
        ) {
            Text(ok, color = if (danger) Color.White else Ps.OnMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Rombo de keyframe: lleno = hay keyframe aqui, contorno = la propiedad esta animada. */
@Composable
fun Diamond(filled: Boolean, outline: Boolean, size: Dp = 14.dp, color: Color = Ps.Mint) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val p = Path().apply {
            moveTo(s / 2, 0f); lineTo(s, s / 2); lineTo(s / 2, s); lineTo(0f, s / 2); close()
        }
        if (filled) drawPath(p, color)
        else drawPath(p, if (outline) color else Color(0x73FFFFFF), style = Stroke(width = s * 0.16f))
    }
}

/** Bandera de marcador. */
@Composable
fun FlagShape(color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.6f); lineTo(w / 2, h); lineTo(0f, h * 0.6f); close()
        }
        drawPath(p, color)
    }
}

// ============================================================ REGLA DE PRECISION

/**
 * "Rueda" para ajustar un valor con precision: se arrastra la regla a los lados.
 * El paso (x0.1, x1, x10) cambia cuanto vale cada marca.
 */
@Composable
fun PrecisionDial(
    title: String,
    subtitle: String?,
    value: Float,
    unit: String,
    decimals: Int,
    lo: Float?,
    hi: Float?,
    onChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val onChangeNow by rememberUpdatedState(onChange)
    var v by remember { mutableFloatStateOf(value) }
    var stepIdx by remember { mutableIntStateOf(1) }
    val steps = listOf(0.1f, 1f, 10f)
    val step = steps[stepIdx]
    fun apply(nv: Float) {
        var x = nv
        if (lo != null) x = max(lo, x)
        if (hi != null) x = min(hi, x)
        v = x
        onChangeNow(x)
    }
    PsSheet(onDismiss) {
        SheetTitle(title, subtitle)
        Text(
            "%.${decimals}f".format(v) + unit,
            color = Ps.Mint, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        val tickGap = 14.dp
        Canvas(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x0FFFFFFF))
                .pointerInput(step) {
                    val gapPx = tickGap.toPx()
                    var acc = 0f
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        acc += -dx
                        val n = (acc / gapPx).toInt()
                        if (n != 0) {
                            acc -= n * gapPx
                            apply(v + n * step)
                        }
                    }
                },
        ) {
            val gap = tickGap.toPx()
            val cx = size.width / 2
            // fraccion de marca en la que esta el valor actual (para que la regla "se deslice")
            val frac = (v / step) - floor(v / step)
            val first = -(size.width / gap).toInt() / 2 - 2
            for (i in first..(-first + 2)) {
                val x = cx + (i - frac) * gap
                if (x < 0 || x > size.width) continue
                val idx = floor(v / step).toInt() + i
                val big = idx % 5 == 0
                drawLine(
                    if (big) Color(0xB3FFFFFF) else Color(0x59FFFFFF),
                    Offset(x, size.height - (if (big) 34.dp.toPx() else 20.dp.toPx())),
                    Offset(x, size.height - 6.dp.toPx()),
                    strokeWidth = if (big) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawLine(Ps.Mint, Offset(cx, 6.dp.toPx()), Offset(cx, size.height - 6.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        ChipRow(listOf(tr("Fino ×0.1", "Fine ×0.1"), "×1", tr("Rápido ×10", "Fast ×10")), stepIdx) { stepIdx = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PsChip("−", false, Modifier.weight(1f)) { apply(v - step) }
            PsChip(tr("Restablecer", "Reset"), false, Modifier.weight(2f)) { apply(value) }
            PsChip("+", false, Modifier.weight(1f)) { apply(v + step) }
        }
        SheetButtons(null, {}, tr("Listo", "Done"), onDismiss)
    }
}

// ============================================================ EDITOR DE CURVAS

/**
 * Editor de la curva de un tramo. Dos vistas del mismo dato:
 * grafico de valor (los puntos de control) y grafico de velocidad (la influencia de cada extremo).
 */
@Composable
fun CurveEditor(
    curve: Curve,
    speedView: Boolean,
    onChange: (Curve) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(curve)
    val latest by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .clip(RoundedCornerShape(14.dp)).background(Color(0x0DFFFFFF))
            .pointerInput(speedView) {
                var which = 0
                detectDragGestures(
                    onDragStart = { pos ->
                        val c = current
                        val padPx = 22.dp.toPx()
                        val w = size.width - padPx * 2
                        val h = size.height - padPx * 2
                        fun px(x: Float) = padPx + x * w
                        fun py(y: Float) = size.height - padPx - y * h
                        val p1 = if (speedView) Offset(px(c.x1), py(0f)) else Offset(px(c.x1), py(c.y1))
                        val p2 = if (speedView) Offset(px(c.x2), py(0f)) else Offset(px(c.x2), py(c.y2))
                        which = if ((pos - p1).getDistance() <= (pos - p2).getDistance()) 1 else 2
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val c = current
                        val padPx = 22.dp.toPx()
                        val w = size.width - padPx * 2
                        val h = size.height - padPx * 2
                        val x = ((change.position.x - padPx) / w).coerceIn(0f, 1f)
                        val y = ((size.height - padPx - change.position.y) / h).coerceIn(-0.5f, 1.5f)
                        val n = if (speedView) {
                            if (which == 1) c.copy(x1 = x, hold = false) else c.copy(x2 = x, hold = false)
                        } else {
                            if (which == 1) c.copy(x1 = x, y1 = y, hold = false) else c.copy(x2 = x, y2 = y, hold = false)
                        }
                        latest(n)
                    },
                )
            },
    ) {
        val pad = 22.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        fun px(x: Float) = pad + x * w
        fun py(y: Float) = size.height - pad - y * h
        // cuadricula
        for (i in 0..4) {
            val gx = px(i / 4f)
            val gy = py(i / 4f)
            drawLine(Color(0x14FFFFFF), Offset(gx, py(0f)), Offset(gx, py(1f)), 1.dp.toPx())
            drawLine(Color(0x14FFFFFF), Offset(px(0f), gy), Offset(px(1f), gy), 1.dp.toPx())
        }
        if (!speedView) {
            drawLine(
                Color(0x2EFFFFFF), Offset(px(0f), py(0f)), Offset(px(1f), py(1f)), 1.dp.toPx(),
            )
            // manijas
            drawLine(Color(0xFFFFC46B), Offset(px(0f), py(0f)), Offset(px(curve.x1), py(curve.y1)), 1.5.dp.toPx())
            drawLine(Color(0xFFFFC46B), Offset(px(1f), py(1f)), Offset(px(curve.x2), py(curve.y2)), 1.5.dp.toPx())
            val path = Path().apply {
                moveTo(px(0f), py(0f))
                if (curve.hold) { lineTo(px(1f), py(0f)); lineTo(px(1f), py(1f)) }
                else cubicTo(
                    px(curve.x1), py(curve.y1), px(curve.x2), py(curve.y2), px(1f), py(1f),
                )
            }
            drawPath(path, Ps.Mint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(Color(0xFFFFC46B), 9.dp.toPx(), Offset(px(curve.x1), py(curve.y1)))
            drawCircle(Color(0xFFFFC46B), 9.dp.toPx(), Offset(px(curve.x2), py(curve.y2)))
        } else {
            val n = 60
            val speeds = FloatArray(n + 1) { curve.speed(it / n.toFloat()) }
            val top = max(1f, speeds.max()).coerceAtMost(6f)
            val path = Path().apply {
                moveTo(px(0f), py(0f))
                for (i in 0..n) {
                    val s = (speeds[i] / top).coerceIn(0f, 1.2f) * 0.9f
                    lineTo(px(i / n.toFloat()), py(s))
                }
                lineTo(px(1f), py(0f))
            }
            drawPath(path, Ps.Mint.copy(alpha = 0.14f))
            drawPath(path, Ps.Mint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            drawLine(Color(0xFFFFC46B), Offset(px(0f), py(0f)), Offset(px(curve.x1), py(0f)), 1.5.dp.toPx())
            drawLine(Color(0xFFFFC46B), Offset(px(1f), py(0f)), Offset(px(curve.x2), py(0f)), 1.5.dp.toPx())
            drawCircle(Color(0xFFFFC46B), 9.dp.toPx(), Offset(px(curve.x1), py(0f)))
            drawCircle(Color(0xFFFFC46B), 9.dp.toPx(), Offset(px(curve.x2), py(0f)))
        }
        // rombos de los extremos
        fun diamond(cx: Float, cy: Float, c: Color) {
            val r = 8.dp.toPx()
            val p = Path().apply {
                moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close()
            }
            drawPath(p, c)
        }
        if (!speedView) {
            diamond(px(0f), py(0f), SkyBlue)
            diamond(px(1f), py(1f), Color(0xFF9DA1CC))
        } else {
            diamond(px(0f), py(0f), SkyBlue)
            diamond(px(1f), py(0f), Color(0xFF9DA1CC))
        }
    }
}

fun roundTo(v: Float, decimals: Int): Float {
    var m = 1f
    repeat(decimals) { m *= 10f }
    return (v * m).roundToInt() / m
}

fun nearlyEqual(a: Float, b: Float, eps: Float = 1e-3f) = abs(a - b) <= eps
