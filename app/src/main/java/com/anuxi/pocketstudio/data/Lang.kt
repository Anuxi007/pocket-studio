package com.anuxi.pocketstudio.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Idioma de la app (espanol o ingles). Es un estado de Compose: al cambiarlo, todas las
 * pantallas que usen [tr] se vuelven a dibujar al instante, sin reiniciar la app.
 */
object Lang {
    var english by mutableStateOf(false)
        private set

    private var prefs: Prefs? = null

    fun init(p: Prefs) {
        prefs = p
        english = when (p.language) {
            "en" -> true
            "es" -> false
            else -> Locale.getDefault().language != "es"
        }
    }

    fun set(en: Boolean) {
        english = en
        prefs?.language = if (en) "en" else "es"
    }
}

/** Devuelve el texto en el idioma elegido: tr("Hola", "Hello"). */
fun tr(es: String, en: String): String = if (Lang.english) en else es
