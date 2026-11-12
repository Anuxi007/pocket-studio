package com.anuxi.pocketstudio.editor

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class LayerKind { Video, Null }

/** Como se genera el tiempo que falta al estirar un clip. */
enum class Interp { None, Blend, Flow }

/** Que pasa con las capas al cambiar el tamano del proyecto. */
enum class ResizeMode { Fit, Fill, Keep }

class Marker(val id: String, var time: Float, var name: String, var color: Int)

/** Efecto Motion Tile de una capa de video. */
class TileFx {
    var enabled = true
    val centerX = Track(0f)
    val centerY = Track(0f)
    var tileW = 100f        // % del tamano de la capa
    var tileH = 100f
    val outW = Track(300f)  // % del tamano de la capa
    val outH = Track(300f)
    var mirror = true
    var phase = 0f
    var offsetRows = false
}

class Layer(
    val id: String,
    val kind: LayerKind,
    var name: String,
) {
    var uri: String? = null
    var srcW = 0
    var srcH = 0
    var srcDur = 0f
    var srcFps = 30f

    var start = 0f       // donde empieza en la linea de tiempo (s)
    var inPoint = 0f     // desde que segundo del original se toma
    var length = 0f      // cuanto dura en la linea de tiempo (s)

    val anchorX = Track(0f)
    val anchorY = Track(0f)
    val posX = Track(0f)
    val posY = Track(0f)
    val scaleX = Track(100f)
    val scaleY = Track(100f)
    val rot = Track(0f)
    val opacity = Track(100f)
    val speed = Track(100f)  // % de velocidad

    var scaleLinked = true
    var parentId: String? = null
    var mute = false
    var hidden = false
    var blur = false
    var interp = Interp.Flow
    var tile: TileFx? = null

    /** No se guarda: se calcula al abrir el proyecto. */
    var missing = false

    val end get() = start + length

    fun tracks(): List<Pair<String, Track>> = listOf(
        "anchorX" to anchorX, "anchorY" to anchorY, "posX" to posX, "posY" to posY,
        "scaleX" to scaleX, "scaleY" to scaleY, "rot" to rot, "opacity" to opacity, "speed" to speed,
    )

    fun track(prop: String): Track? = when (prop) {
        "anchorX" -> anchorX; "anchorY" -> anchorY
        "posX" -> posX; "posY" -> posY
        "scaleX" -> scaleX; "scaleY" -> scaleY
        "rot" -> rot; "opacity" -> opacity; "speed" -> speed
        "tileCX" -> tile?.centerX; "tileCY" -> tile?.centerY
        "tileOW" -> tile?.outW; "tileOH" -> tile?.outH
        else -> null
    }

    /** Segundo del original que se ve en el instante [t] de la linea de tiempo. */
    fun sourceTime(t: Float): Float {
        val local = (t - start).coerceIn(0f, length)
        if (!speed.animated) return (inPoint + local * speed.base / 100f).coerceIn(0f, srcDur)
        // Integra la velocidad: suficiente para vista previa y para calcular frames.
        val step = 1f / 60f
        var acc = 0f
        var x = 0f
        while (x < local) {
            val dx = minOf(step, local - x)
            acc += speed.at(start + x + dx / 2f) / 100f * dx
            x += dx
        }
        return (inPoint + acc).coerceIn(0f, srcDur)
    }
}

class Project(val id: String, var name: String) {
    var created = System.currentTimeMillis()
    var modified = created
    var width = 1080
    var height = 1920
    var fps = 30f
    var previewDiv = 2          // 1 = completa, 2 = 1/2, 4 = 1/4, 8 = 1/8
    var blurAngle = 180f
    var blurSamples = 8
    var blurInPreview = false
    var resize = ResizeMode.Fit
    var bg = 0xFF000000.toInt()
    val layers = mutableListOf<Layer>()   // de abajo hacia arriba
    val markers = mutableListOf<Marker>()

    val duration: Float get() = layers.maxOfOrNull { it.end } ?: 0f

    fun layer(id: String?) = layers.firstOrNull { it.id == id }
    fun frameEps() = 0.5f / fps

    // ------------------------------------------------------------ transformaciones

    /** Matriz local: posicion * rotacion * escala * (-punto de anclaje). */
    fun localMatrix(l: Layer, t: Float): Aff =
        Aff.translate(l.posX.at(t), l.posY.at(t)) *
            Aff.rotate(l.rot.at(t)) *
            Aff.scale(l.scaleX.at(t) / 100f, l.scaleY.at(t) / 100f) *
            Aff.translate(-l.anchorX.at(t), -l.anchorY.at(t))

    /** Matriz final de la capa, incluyendo a todos sus padres (nulos). */
    fun worldMatrix(l: Layer, t: Float): Aff {
        var m = localMatrix(l, t)
        var p = layer(l.parentId)
        var guard = 0
        while (p != null && guard++ < 16) {
            m = localMatrix(p, t) * m
            p = layer(p.parentId)
        }
        return m
    }

    fun worldMatrixOfParent(l: Layer, t: Float): Aff {
        val p = layer(l.parentId) ?: return Aff()
        return worldMatrix(p, t)
    }

    /** true si poner a [parent] como padre de [child] crearia un ciclo. */
    fun wouldLoop(child: Layer, parent: Layer): Boolean {
        var p: Layer? = parent
        var guard = 0
        while (p != null && guard++ < 32) {
            if (p.id == child.id) return true
            p = layer(p.parentId)
        }
        return false
    }

    /** Opacidad efectiva 0..1 (los nulos no se ven). */
    fun alpha(l: Layer, t: Float) = (l.opacity.at(t) / 100f).coerceIn(0f, 1f)
}

// ===================================================================== JSON

private fun Track.toJson(): JSONObject = JSONObject().apply {
    put("v", base.toDouble())
    if (keys.isNotEmpty()) put("k", JSONArray().also { arr ->
        keys.forEach { k ->
            arr.put(JSONObject().apply {
                put("t", k.t.toDouble()); put("v", k.v.toDouble())
                put("x1", k.curve.x1.toDouble()); put("y1", k.curve.y1.toDouble())
                put("x2", k.curve.x2.toDouble()); put("y2", k.curve.y2.toDouble())
                if (k.curve.hold) put("h", true)
            })
        }
    })
}

private fun Track.fromJson(o: JSONObject?) {
    if (o == null) return
    base = o.optDouble("v", base.toDouble()).toFloat()
    keys.clear()
    val arr = o.optJSONArray("k") ?: return
    for (i in 0 until arr.length()) {
        val k = arr.getJSONObject(i)
        keys.add(
            Key(
                k.getDouble("t").toFloat(), k.getDouble("v").toFloat(),
                Curve(
                    k.optDouble("x1", 0.55).toFloat(), k.optDouble("y1", 0.0).toFloat(),
                    k.optDouble("x2", 0.45).toFloat(), k.optDouble("y2", 1.0).toFloat(),
                    k.optBoolean("h", false),
                ),
            )
        )
    }
}

fun Project.toJson(): JSONObject = JSONObject().apply {
    put("v", 1)
    put("id", id); put("name", name)
    put("created", created); put("modified", modified)
    put("w", width); put("h", height); put("fps", fps.toDouble())
    put("previewDiv", previewDiv)
    put("blurAngle", blurAngle.toDouble()); put("blurSamples", blurSamples)
    put("blurInPreview", blurInPreview)
    put("resize", resize.name); put("bg", bg)
    put("layers", JSONArray().also { arr ->
        layers.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id); put("kind", l.kind.name); put("name", l.name)
                put("uri", l.uri ?: JSONObject.NULL)
                put("srcW", l.srcW); put("srcH", l.srcH)
                put("srcDur", l.srcDur.toDouble()); put("srcFps", l.srcFps.toDouble())
                put("start", l.start.toDouble()); put("inPoint", l.inPoint.toDouble())
                put("length", l.length.toDouble())
                l.tracks().forEach { (n, t) -> put(n, t.toJson()) }
                put("scaleLinked", l.scaleLinked)
                put("parent", l.parentId ?: JSONObject.NULL)
                put("mute", l.mute); put("hidden", l.hidden); put("blur", l.blur); put("interp", l.interp.name)
                l.tile?.let { f ->
                    put("tile", JSONObject().apply {
                        put("enabled", f.enabled)
                        put("cx", f.centerX.toJson()); put("cy", f.centerY.toJson())
                        put("tw", f.tileW.toDouble()); put("th", f.tileH.toDouble())
                        put("ow", f.outW.toJson()); put("oh", f.outH.toJson())
                        put("mirror", f.mirror); put("phase", f.phase.toDouble())
                        put("rows", f.offsetRows)
                    })
                }
            })
        }
    })
    put("markers", JSONArray().also { arr ->
        markers.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id); put("t", m.time.toDouble()); put("name", m.name); put("color", m.color)
            })
        }
    })
}

fun projectFromJson(o: JSONObject): Project {
    val p = Project(o.getString("id"), o.optString("name", "Proyecto"))
    p.created = o.optLong("created", p.created)
    p.modified = o.optLong("modified", p.modified)
    p.width = o.optInt("w", 1080); p.height = o.optInt("h", 1920)
    p.fps = o.optDouble("fps", 30.0).toFloat()
    p.previewDiv = o.optInt("previewDiv", 2)
    p.blurAngle = o.optDouble("blurAngle", 180.0).toFloat()
    p.blurSamples = o.optInt("blurSamples", 8)
    p.blurInPreview = o.optBoolean("blurInPreview", false)
    p.resize = runCatching { ResizeMode.valueOf(o.optString("resize", "Fit")) }.getOrDefault(ResizeMode.Fit)
    p.bg = o.optInt("bg", 0xFF000000.toInt())
    val arr = o.optJSONArray("layers")
    if (arr != null) for (i in 0 until arr.length()) {
        val j = arr.getJSONObject(i)
        val kind = runCatching { LayerKind.valueOf(j.optString("kind")) }.getOrDefault(LayerKind.Video)
        val l = Layer(j.getString("id"), kind, j.optString("name", "Capa"))
        l.uri = if (j.isNull("uri")) null else j.optString("uri")
        l.srcW = j.optInt("srcW"); l.srcH = j.optInt("srcH")
        l.srcDur = j.optDouble("srcDur", 0.0).toFloat(); l.srcFps = j.optDouble("srcFps", 30.0).toFloat()
        l.start = j.optDouble("start", 0.0).toFloat()
        l.inPoint = j.optDouble("inPoint", 0.0).toFloat()
        l.length = j.optDouble("length", 0.0).toFloat()
        l.tracks().forEach { (n, t) -> t.fromJson(j.optJSONObject(n)) }
        l.scaleLinked = j.optBoolean("scaleLinked", true)
        l.parentId = if (j.isNull("parent")) null else j.optString("parent")
        l.hidden = j.optBoolean("hidden", false); l.mute = j.optBoolean("mute", false); l.blur = j.optBoolean("blur", false)
        l.interp = runCatching { Interp.valueOf(j.optString("interp", "Flow")) }.getOrDefault(Interp.Flow)
        j.optJSONObject("tile")?.let { t ->
            val f = TileFx()
            f.enabled = t.optBoolean("enabled", true)
            f.centerX.fromJson(t.optJSONObject("cx")); f.centerY.fromJson(t.optJSONObject("cy"))
            f.tileW = t.optDouble("tw", 100.0).toFloat(); f.tileH = t.optDouble("th", 100.0).toFloat()
            f.outW.fromJson(t.optJSONObject("ow")); f.outH.fromJson(t.optJSONObject("oh"))
            f.mirror = t.optBoolean("mirror", true); f.phase = t.optDouble("phase", 0.0).toFloat()
            f.offsetRows = t.optBoolean("rows", false)
            l.tile = f
        }
        p.layers.add(l)
    }
    val ma = o.optJSONArray("markers")
    if (ma != null) for (i in 0 until ma.length()) {
        val m = ma.getJSONObject(i)
        p.markers.add(Marker(m.getString("id"), m.getDouble("t").toFloat(), m.optString("name", ""), m.optInt("color")))
    }
    return p
}

fun newId(): String = UUID.randomUUID().toString().take(8)
