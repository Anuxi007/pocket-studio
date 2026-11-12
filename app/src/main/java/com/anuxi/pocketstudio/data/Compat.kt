package com.anuxi.pocketstudio.data

import android.media.MediaCodecInfo
import android.media.MediaCodecList

/** Pregunta al propio celular que codecs y resoluciones puede reproducir con su chip. */
object Compat {
    private val decoders: List<MediaCodecInfo> by lazy {
        try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun mimeFor(vcodec: String?): String? {
        val c = vcodec?.lowercase() ?: return null
        return when {
            c.startsWith("avc") || c.startsWith("h264") -> "video/avc"
            c.startsWith("hev") || c.startsWith("hvc") || c.startsWith("h265") ||
                c.startsWith("bytevc1") -> "video/hevc"
            c.startsWith("vp09") || c.startsWith("vp9") -> "video/x-vnd.on2.vp9"
            c.startsWith("vp8") -> "video/x-vnd.on2.vp8"
            c.startsWith("av01") || c.startsWith("av1") -> "video/av01"
            else -> null
        }
    }

    fun codecName(vcodec: String?): String {
        val c = vcodec?.lowercase() ?: return "?"
        return when {
            c.startsWith("avc") || c.startsWith("h264") -> "H.264"
            c.startsWith("hev") || c.startsWith("hvc") || c.startsWith("h265") ||
                c.startsWith("bytevc1") -> "HEVC"
            c.startsWith("vp09") || c.startsWith("vp9") -> "VP9"
            c.startsWith("vp8") -> "VP8"
            c.startsWith("av01") || c.startsWith("av1") -> "AV1"
            else -> c.substringBefore('.')
        }
    }

    private fun forMime(mime: String) =
        decoders.filter { d -> d.supportedTypes.any { it.equals(mime, ignoreCase = true) } }

    /** true si el celular puede reproducir ese video con aceleracion por hardware. */
    fun supports(vcodec: String?, width: Int, height: Int, fps: Double): Boolean {
        val mime = mimeFor(vcodec) ?: return true // codec raro: no podemos saberlo, no lo bloqueamos
        val all = forMime(mime)
        if (all.isEmpty()) return false
        val hardware = all.filter { it.isHardwareAccelerated }
        val pool = when {
            hardware.isNotEmpty() -> hardware
            // Sin decodificador por hardware solo aceptamos H.264 hasta 1080p por software.
            mime == "video/avc" && height <= 1080 -> all
            else -> return false
        }
        val rate = if (fps < 1.0) 30.0 else fps
        return pool.any { info ->
            try {
                val v = info.getCapabilitiesForType(mime).videoCapabilities
                v.isSizeSupported(width, height) &&
                    (try { v.areSizeAndRateSupported(width, height, rate) } catch (e: Exception) { true })
            } catch (e: Exception) {
                false
            }
        }
    }

    /** Frase corta para mostrar arriba de la lista de video. */
    fun summary(): String {
        val names = mutableListOf<String>()
        var best = 0
        val known = listOf(
            "video/avc" to "H.264",
            "video/hevc" to "HEVC",
            "video/x-vnd.on2.vp9" to "VP9",
            "video/av01" to "AV1",
        )
        for ((mime, name) in known) {
            val hw = forMime(mime).filter { it.isHardwareAccelerated }
            if (hw.isEmpty()) continue
            names += name
            val maxH = hw.maxOf {
                try {
                    it.getCapabilitiesForType(mime).videoCapabilities.supportedHeights.upper
                } catch (e: Exception) {
                    0
                }
            }
            if (maxH > best) best = maxH
        }
        if (names.isEmpty()) return "No pudimos comprobar qué códecs reproduce tu celular."
        val res = if (best >= 2160) "4K" else "${best}p"
        return "Tu celular reproduce hasta $res con ${names.joinToString(", ")}. " +
            "Lo que no puede reproducir aparece bloqueado."
    }
}
