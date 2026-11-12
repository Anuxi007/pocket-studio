package com.anuxi.pocketstudio.editor

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/** Resumen de un proyecto para la lista (sin cargar todas las capas). */
data class ProjectSummary(
    val id: String,
    val name: String,
    val modified: Long,
    val width: Int,
    val height: Int,
    val fps: Float,
    val duration: Float,
    val missingClips: Int,
)

/** Guarda cada proyecto como un archivo JSON dentro de la app. */
object ProjectStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "projects").apply { mkdirs() }
    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.json")

    fun save(ctx: Context, p: Project) {
        p.modified = System.currentTimeMillis()
        val tmp = File(dir(ctx), "${p.id}.tmp")
        tmp.writeText(p.toJson().toString())
        tmp.renameTo(file(ctx, p.id))
    }

    fun load(ctx: Context, id: String): Project? = runCatching {
        projectFromJson(JSONObject(file(ctx, id).readText())).also { markMissing(ctx, it) }
    }.getOrNull()

    fun delete(ctx: Context, id: String) {
        file(ctx, id).delete()
    }

    fun create(ctx: Context, name: String, w: Int, h: Int, fps: Float): Project {
        val p = Project(newId(), name.ifBlank { "Proyecto" })
        p.width = w; p.height = h; p.fps = fps
        save(ctx, p)
        return p
    }

    fun duplicate(ctx: Context, id: String, newName: String): Project? {
        val src = load(ctx, id) ?: return null
        val json = src.toJson()
        val copy = projectFromJson(json.put("id", newId()).put("name", newName))
        copy.created = System.currentTimeMillis()
        save(ctx, copy)
        return copy
    }

    fun rename(ctx: Context, id: String, name: String) {
        val p = load(ctx, id) ?: return
        p.name = name.ifBlank { p.name }
        save(ctx, p)
    }

    fun list(ctx: Context): List<ProjectSummary> =
        (dir(ctx).listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f ->
                runCatching {
                    val p = projectFromJson(JSONObject(f.readText()))
                    markMissing(ctx, p)
                    ProjectSummary(
                        p.id, p.name, p.modified, p.width, p.height, p.fps, p.duration,
                        p.layers.count { it.missing },
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.modified }

    /** Marca las capas cuyo video ya no se puede abrir. */
    fun markMissing(ctx: Context, p: Project) {
        p.layers.forEach { l ->
            l.missing = l.kind == LayerKind.Video && !canOpen(ctx, l.uri)
        }
    }

    fun canOpen(ctx: Context, uri: String?): Boolean {
        if (uri == null) return false
        return runCatching {
            ctx.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
}
