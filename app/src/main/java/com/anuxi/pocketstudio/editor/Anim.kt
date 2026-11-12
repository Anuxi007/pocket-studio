package com.anuxi.pocketstudio.editor

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Curva de suavizado de un tramo entre dos keyframes (bezier cubica, como en CSS). */
data class Curve(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val hold: Boolean = false,
) {
    /** Progreso de valor (0..1) para un progreso de tiempo [p] (0..1). */
    fun ease(p: Float): Float {
        if (hold) return if (p >= 1f) 1f else 0f
        if (p <= 0f) return 0f
        if (p >= 1f) return 1f
        // Busca t tal que x(t) = p con biseccion (suficiente y estable).
        var lo = 0f
        var hi = 1f
        var t = p
        repeat(24) {
            t = (lo + hi) / 2f
            val x = bez(t, x1, x2)
            if (x < p) lo = t else hi = t
        }
        return bez(t, y1, y2)
    }

    /** Velocidad relativa (derivada) en el progreso [p]. */
    fun speed(p: Float): Float {
        val a = ease((p - 0.002f).coerceAtLeast(0f))
        val b = ease((p + 0.002f).coerceAtMost(1f))
        val dp = (p + 0.002f).coerceAtMost(1f) - (p - 0.002f).coerceAtLeast(0f)
        return if (dp <= 0f) 0f else (b - a) / dp
    }

    private fun bez(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t
    }

    companion object {
        val Linear = Curve(0f, 0f, 1f, 1f)
        val Smooth = Curve(0.55f, 0f, 0.45f, 1f)
        val EaseIn = Curve(0.55f, 0f, 1f, 1f)
        val EaseOut = Curve(0f, 0f, 0.45f, 1f)
        val Hold = Curve(0f, 0f, 1f, 1f, hold = true)
    }
}

data class Key(val t: Float, val v: Float, val curve: Curve = Curve.Smooth)

/** Una propiedad animable: un valor fijo o una lista de keyframes. */
class Track(var base: Float) {
    val keys = mutableListOf<Key>()

    val animated get() = keys.isNotEmpty()

    fun at(t: Float): Float {
        if (keys.isEmpty()) return base
        if (t <= keys.first().t) return keys.first().v
        if (t >= keys.last().t) return keys.last().v
        var i = 0
        while (i < keys.size - 1 && keys[i + 1].t <= t) i++
        val a = keys[i]
        val b = keys[i + 1]
        val p = (t - a.t) / (b.t - a.t)
        return a.v + (b.v - a.v) * a.curve.ease(p)
    }

    fun indexAt(t: Float, eps: Float): Int = keys.indexOfFirst { abs(it.t - t) <= eps }

    /** Cambia el valor en el instante [t]. Si ya hay keyframes, crea o actualiza uno. */
    fun setAt(t: Float, v: Float, eps: Float) {
        if (keys.isEmpty()) {
            base = v
            return
        }
        val i = indexAt(t, eps)
        if (i >= 0) keys[i] = keys[i].copy(v = v)
        else insert(Key(t, v))
    }

    fun toggleKey(t: Float, eps: Float) {
        val i = indexAt(t, eps)
        if (i >= 0) {
            if (keys.size == 1) base = keys[0].v
            keys.removeAt(i)
        } else {
            insert(Key(t, at(t)))
        }
    }

    fun insert(k: Key) {
        val at = keys.indexOfFirst { it.t > k.t }
        if (at < 0) keys.add(k) else keys.add(at, k)
    }

    fun copyTrack(): Track {
        val c = Track(base)
        c.keys.addAll(keys)
        return c
    }
}

/** Transformacion 2D: x' = a x + c y + e ; y' = b x + d y + f. */
data class Aff(
    val a: Float = 1f, val b: Float = 0f,
    val c: Float = 0f, val d: Float = 1f,
    val e: Float = 0f, val f: Float = 0f,
) {
    /** this * o  (primero se aplica [o], luego esta). */
    operator fun times(o: Aff) = Aff(
        a = a * o.a + c * o.b,
        b = b * o.a + d * o.b,
        c = a * o.c + c * o.d,
        d = b * o.c + d * o.d,
        e = a * o.e + c * o.f + e,
        f = b * o.e + d * o.f + f,
    )

    fun apply(x: Float, y: Float) = Pair(a * x + c * y + e, b * x + d * y + f)

    fun inverse(): Aff? {
        val det = a * d - b * c
        if (abs(det) < 1e-9f) return null
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return Aff(ia, ib, ic, id, -(ia * e + ic * f), -(ib * e + id * f))
    }

    companion object {
        fun translate(x: Float, y: Float) = Aff(e = x, f = y)
        fun scale(x: Float, y: Float) = Aff(a = x, d = y)
        fun rotate(deg: Float): Aff {
            val r = Math.toRadians(deg.toDouble())
            val cs = cos(r).toFloat()
            val sn = sin(r).toFloat()
            return Aff(cs, sn, -sn, cs, 0f, 0f)
        }
    }
}
