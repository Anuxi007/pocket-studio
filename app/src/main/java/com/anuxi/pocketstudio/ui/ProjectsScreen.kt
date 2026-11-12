package com.anuxi.pocketstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuxi.pocketstudio.MainViewModel
import com.anuxi.pocketstudio.data.tr
import com.anuxi.pocketstudio.editor.ProjectStore
import com.anuxi.pocketstudio.editor.ProjectSummary
import com.anuxi.pocketstudio.editor.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Format(val label: String, val w: Int, val h: Int)

val Formats = listOf(
    Format("9:16", 1080, 1920),
    Format("16:9", 1920, 1080),
    Format("1:1", 1080, 1080),
    Format("4:5", 1080, 1350),
)

private fun whenText(ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val dayDiff = ((now.timeInMillis - then.timeInMillis) / 86_400_000L).toInt()
    val sameDay = now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) &&
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val hour = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    return when {
        sameDay -> tr("Editado hoy, $hour", "Edited today, $hour")
        dayDiff <= 1 -> tr("Editado ayer", "Edited yesterday")
        dayDiff < 14 -> tr("Editado hace $dayDiff días", "Edited $dayDiff days ago")
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms)).let {
            tr("Editado el $it", "Edited $it")
        }
    }
}

private fun thumbBrush(id: String): Brush {
    val palette = listOf(
        0xFF6FA8FF to 0xFF7CF2C8, 0xFFB79CFF to 0xFFFF8AB8, 0xFFFFB36B to 0xFFFF6B8B, 0xFF5ED4FF to 0xFF6FA8FF,
    )
    val (a, b) = palette[(id.hashCode() and 0x7fffffff) % palette.size]
    return Brush.linearGradient(listOf(Color(a), Color(b)))
}

@Composable
fun ProjectsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<ProjectSummary>?>(null) }
    LaunchedEffect(vm.projectsRev) {
        list = withContext(Dispatchers.IO) { ProjectStore.list(ctx) }
    }
    var showNew by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<ProjectSummary?>(null) }
    var renaming by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }

    Column(Modifier.fillMaxSize().padding(bottom = 16.dp)) {
        ScreenHeader(tr("Proyectos", "Projects"), onBack = vm::back, trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIconButton(Icons.Filled.Settings, tr("Opciones", "Settings")) { vm.go(com.anuxi.pocketstudio.data.Screen.Settings) }
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Ps.Mint)
                        .pressable { showNew = true }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PsIcon(Icons.Filled.Add, Ps.OnMint, 18.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Nuevo", "New"), color = Ps.OnMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        })
        Spacer(Modifier.height(6.dp))
        Text(tr("Se guardan solos mientras editas", "They save automatically as you edit"), color = Ps.Muted2, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        val items = list
        if (items != null && items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Aún no tienes proyectos", "No projects yet"), color = Ps.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        tr("Crea uno para empezar a editar.", "Create one to start editing."),
                        color = Ps.Muted, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    MintButton(tr("Nuevo proyecto", "New project"), Modifier.width(220.dp)) { showNew = true }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items ?: emptyList(), key = { it.id }) { p ->
                    ProjectCard(p, onOpen = { vm.openProject(p.id) }, onMenu = { menu = p })
                }
            }
        }
    }

    if (showNew) NewProjectSheet(
        defaultName = tr("Proyecto", "Project") + " " + ((list?.size ?: 0) + 1),
        onDismiss = { showNew = false },
        onCreate = { name, f, fps ->
            showNew = false
            val p = ProjectStore.create(ctx, name, f.w, f.h, fps)
            vm.openProject(p.id)
        },
    )

    menu?.let { p ->
        PsSheet({ menu = null }) {
            SheetTitle(p.name, "${fmtTime(p.duration)} · ${p.width}×${p.height} · ${p.fps.toInt()} fps")
            MenuRow(Icons.Filled.Edit, tr("Renombrar", "Rename")) { renaming = p; menu = null }
            MenuRow(Icons.Filled.Share, tr("Duplicar", "Duplicate")) {
                ProjectStore.duplicate(ctx, p.id, p.name + tr(" (copia)", " (copy)"))
                vm.projectsRev++
                menu = null
            }
            MenuRow(Icons.Filled.Delete, tr("Borrar", "Delete"), danger = true) { deleting = p; menu = null }
        }
    }

    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        PsSheet({ renaming = null }) {
            SheetTitle(tr("Renombrar proyecto", "Rename project"))
            PsTextField(name, { name = it }, tr("Nombre", "Name"))
            SheetButtons(tr("Cancelar", "Cancel"), { renaming = null }, tr("Guardar", "Save"), {
                ProjectStore.rename(ctx, p.id, name)
                vm.projectsRev++
                renaming = null
            }, okEnabled = name.isNotBlank())
        }
    }

    deleting?.let { p ->
        PsSheet({ deleting = null }) {
            SheetTitle(tr("¿Borrar «${p.name}»?", "Delete “${p.name}”?"))
            Text(
                tr(
                    "Se borra el proyecto con sus capas, keyframes y marcadores. Tus videos originales no se tocan. No se puede deshacer.",
                    "The project is deleted with its layers, keyframes and markers. Your original videos are not touched. This can't be undone.",
                ),
                color = Ps.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            SheetButtons(tr("Cancelar", "Cancel"), { deleting = null }, tr("Borrar", "Delete"), {
                ProjectStore.delete(ctx, p.id)
                vm.projectsRev++
                deleting = null
            }, danger = true)
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) Color(0xFFFF8A8A) else Ps.Text
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PsIcon(icon, c, 22.dp)
        Spacer(Modifier.width(14.dp))
        Text(label, color = c, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProjectCard(p: ProjectSummary, onOpen: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x0FFFFFFF)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ratio = (p.width.toFloat() / p.height).coerceIn(0.5f, 1.8f)
            Box(
                Modifier.height(88.dp).width((88 * ratio).dp.coerceIn(48.dp, 130.dp))
                    .clip(RoundedCornerShape(9.dp)).background(thumbBrush(p.id)),
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(p.name, color = Ps.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${fmtTime(p.duration)} · ${p.width}×${p.height} · ${p.fps.toInt()} fps", color = Ps.Muted, fontSize = 12.sp)
                Text(whenText(p.modified), color = Ps.Muted2, fontSize = 11.sp)
                if (p.missingClips > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PsIcon(Icons.Filled.Warning, Ps.Amber, 13.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (p.missingClips == 1) tr("Falta 1 clip", "1 clip missing")
                            else tr("Faltan ${p.missingClips} clips", "${p.missingClips} clips missing"),
                            color = Ps.Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Box(Modifier.size(40.dp).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
            PsIcon(Icons.Filled.MoreVert, Ps.Muted, 22.dp, tr("Opciones", "Options"))
        }
    }
}

@Composable
private fun NewProjectSheet(defaultName: String, onDismiss: () -> Unit, onCreate: (String, Format, Float) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    var fmt by remember { mutableIntStateOf(0) }
    var wTxt by remember { mutableStateOf(Formats[0].w.toString()) }
    var hTxt by remember { mutableStateOf(Formats[0].h.toString()) }
    var fps by remember { mutableIntStateOf(1) }
    val fpsList = listOf(24f, 30f, 60f)
    PsSheet(onDismiss) {
        SheetTitle(tr("Nuevo proyecto", "New project"))
        Text(tr("Nombre", "Name"), color = Ps.Muted, fontSize = 12.sp)
        PsTextField(name, { name = it }, tr("Nombre", "Name"))
        Text(tr("Formato", "Format"), color = Ps.Muted, fontSize = 12.sp)
        ChipRow(Formats.map { it.label }, fmt) {
            fmt = it; wTxt = Formats[it].w.toString(); hTxt = Formats[it].h.toString()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumField(wTxt, { wTxt = it; fmt = -1 }, tr("Ancho", "Width"), Modifier.weight(1f))
            Text("×", color = Ps.Muted, fontSize = 15.sp)
            NumField(hTxt, { hTxt = it; fmt = -1 }, tr("Alto", "Height"), Modifier.weight(1f))
        }
        Text(tr("Fotogramas por segundo", "Frames per second"), color = Ps.Muted, fontSize = 12.sp)
        ChipRow(fpsList.map { it.toInt().toString() }, fps) { fps = it }
        Note(tr("Lo puedes cambiar después en los ajustes del proyecto.", "You can change this later in the project settings."))
        SheetButtons(tr("Cancelar", "Cancel"), onDismiss, tr("Crear", "Create"), {
            fun ev(t: String, d: Int) = ((t.toIntOrNull() ?: d).coerceIn(16, 4096) / 2) * 2
            onCreate(name, Format("custom", ev(wTxt, 1080), ev(hTxt, 1920)), fpsList[fps])
        }, okEnabled = name.isNotBlank() && wTxt.isNotEmpty() && hTxt.isNotEmpty())
    }
}
