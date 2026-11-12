package com.anuxi.pocketstudio.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Abrir y compartir archivos descargados. */
object FileActions {
    fun open(context: Context, item: DlItem) {
        val uriStr = item.savedUri ?: return missing(context)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uriStr), item.mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val intent = if (Prefs(context).askPlayer) Intent.createChooser(view, "Abrir con") else view
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            if (e is ActivityNotFoundException || e is SecurityException) {
                Toast.makeText(context, "No hay una app para abrir este archivo, o ya no existe.", Toast.LENGTH_LONG).show()
            } else throw e
        }
    }

    fun share(context: Context, items: List<DlItem>) {
        val uris = items.mapNotNull { it.savedUri?.let(Uri::parse) }
        if (uris.isEmpty()) return missing(context)
        val mimes = items.mapNotNull { it.mime }
        val type = when {
            mimes.size == 1 -> mimes[0]
            mimes.isNotEmpty() && mimes.all { it.startsWith("video/") } -> "video/*"
            mimes.isNotEmpty() && mimes.all { it.startsWith("audio/") } -> "audio/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = type
        val clip = ClipData.newRawUri("archivos", uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        send.clipData = clip
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(send, "Compartir"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No hay apps para compartir.", Toast.LENGTH_LONG).show()
        }
    }

    private fun missing(context: Context) {
        Toast.makeText(context, "Este archivo ya no está disponible.", Toast.LENGTH_LONG).show()
    }
}
