package com.anuxi.pocketstudio.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("pocketstudio", Context.MODE_PRIVATE)

    var welcomeAccepted: Boolean
        get() = sp.getBoolean("welcome", false)
        set(v) = sp.edit().putBoolean("welcome", v).apply()

    var reduceMotion: Boolean
        get() = sp.getBoolean("reduce_motion", false)
        set(v) = sp.edit().putBoolean("reduce_motion", v).apply()

    var folderUri: String?
        get() = sp.getString("folder_uri", null)
        set(v) = sp.edit().putString("folder_uri", v).apply()
}

object Storage {
    const val DEFAULT_LABEL = "Download/Pocket Studio"

    fun folderLabel(context: Context): String {
        val uri = Prefs(context).folderUri ?: return DEFAULT_LABEL
        val seg = Uri.parse(uri).lastPathSegment ?: return "Carpeta elegida"
        return seg.replace("primary:", "Almacenamiento interno/")
    }

    /** Mueve el archivo ya descargado a la carpeta del usuario (o a la predeterminada). */
    fun save(context: Context, file: File): Saved {
        val prefs = Prefs(context)
        val mime = mimeOf(file.name)
        var fellBack = false
        prefs.folderUri?.let { u ->
            try {
                saveToTree(context, Uri.parse(u), file, mime)
                return Saved("${folderLabel(context)}/${file.name}", false)
            } catch (e: Exception) {
                // La carpeta elegida ya no existe o perdimos el permiso: volvemos a la predeterminada.
                prefs.folderUri = null
                fellBack = true
            }
        }
        saveToDownloads(context, file, mime)
        return Saved("$DEFAULT_LABEL/${file.name}", fellBack)
    }

    private fun saveToDownloads(context: Context, file: File, mime: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Pocket Studio")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("No se pudo crear el archivo en Descargas")
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("No se pudo escribir el archivo")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun saveToTree(context: Context, tree: Uri, file: File, mime: String) {
        val dir = DocumentFile.fromTreeUri(context, tree)
            ?: throw IllegalStateException("Carpeta no disponible")
        if (!dir.exists() || !dir.canWrite()) throw IllegalStateException("Sin permiso de escritura")
        val doc = dir.createFile(mime, file.name)
            ?: throw IllegalStateException("No se pudo crear el archivo")
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("No se pudo escribir el archivo")
    }

    private fun mimeOf(name: String): String {
        return when (val ext = name.substringAfterLast('.', "").lowercase()) {
            "mkv" -> "video/x-matroska"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus" -> "audio/ogg"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
        }
    }
}
