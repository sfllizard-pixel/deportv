package com.deportv.app.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.deportv.app.data.EventBuilder
import com.deportv.app.data.XtreamClient
import com.deportv.app.data.XtreamConfig
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(vm: AppViewModel) {
    val old = vm.settings.config
    var server by remember { mutableStateOf(old?.server ?: "http://") }
    var user by remember { mutableStateOf(old?.username ?: "") }
    var pass by remember { mutableStateOf(old?.password ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp)) {
            Text("DeporTV", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Accent)
            Text("Conecta tu IPTV (Xtream Codes). Son los mismos datos que tienes en TiviMate.", fontSize = 15.sp, color = Color.White.copy(alpha = .7f))
            Spacer(Modifier.height(20.dp))
            TvTextField("Servidor (con puerto), p.ej. http://servidor.com:8080", server, { server = it.trim() })
            Spacer(Modifier.height(12.dp))
            TvTextField("Usuario", user, { user = it.trim() })
            Spacer(Modifier.height(12.dp))
            TvTextField("Contraseña", pass, { pass = it.trim() }, password = true)
            Spacer(Modifier.height(20.dp))
            Row {
                Button(enabled = !busy, onClick = {
                    var s = server
                    if (!s.startsWith("http")) s = "http://$s"
                    val cfg = XtreamConfig(s, user, pass, old?.format ?: "ts")
                    busy = true; status = "Comprobando…"
                    scope.launch {
                        val err = XtreamClient(cfg).testLogin()
                        busy = false
                        if (err == null) {
                            vm.settings.config = cfg
                            status = null
                            vm.screen = Screen.HOME
                            vm.refresh()
                        } else status = err
                    }
                }) { Text("Conectar") }
                if (old != null) {
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { vm.screen = Screen.SETTINGS }) { Text("Cancelar") }
                }
            }
            status?.let { Spacer(Modifier.height(12.dp)); Text(it, color = if (busy) Color.White else LiveRed, fontSize = 15.sp) }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    var fav by remember { mutableStateOf(s.favoriteTeams) }
    var internal by remember { mutableStateOf(s.internalPlayer) }
    var format by remember { mutableStateOf(s.config?.format ?: "ts") }
    var chosen by remember { mutableStateOf(s.categoryIds) }
    val cats = remember { vm.repo.cachedCategories() }
    val autoIds = remember(cats) { cats.filter { EventBuilder.isSportName(it.name) }.map { it.id }.toSet() }
    val launcherAlias = remember { ComponentName(ctx, "com.deportv.app.LauncherAlias") }
    var launcher by remember {
        mutableStateOf(ctx.packageManager.getComponentEnabledSetting(launcherAlias) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    s.favoriteTeams = fav; s.internalPlayer = internal; s.categoryIds = chosen
                    s.config?.let { s.config = it.copy(format = format) }
                    vm.reorder(); vm.screen = Screen.HOME
                }) { Text("← Guardar y volver") }
                Spacer(Modifier.width(24.dp))
                Text("Ajustes", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        item {
            TvTextField("Equipos favoritos (separados por comas) — salen primero en la portada", fav, { fav = it }, modifier = Modifier.width(700.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Reproductor:", color = Color.White, fontSize = 17.sp)
                Toggle("Propio (recomendado)", internal) { internal = true }
                Toggle("Externo (VLC / MX Player)", !internal) { internal = false }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Formato del stream:", color = Color.White, fontSize = 17.sp)
                Toggle("TS (normal)", format == "ts") { format = "ts" }
                Toggle("HLS (m3u8)", format == "m3u8") { format = "m3u8" }
                Text("Cambia a HLS si algún canal no carga", color = Color.White.copy(alpha = .5f), fontSize = 13.sp)
            }
        }
        item {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Modo launcher:", color = Color.White, fontSize = 17.sp)
                    Toggle(if (launcher) "Activado" else "Desactivado", launcher) {
                        launcher = !launcher
                        ctx.packageManager.setComponentEnabledSetting(
                            launcherAlias,
                            if (launcher) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
                Text("Registra DeporTV como pantalla de inicio. En la Mi Box hay que desactivar además el launcher de Google por ADB (ver instrucciones).",
                    color = Color.White.copy(alpha = .5f), fontSize = 13.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.screen = Screen.SETUP }) { Text("Cambiar cuenta IPTV") }
                Button(onClick = {
                    runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) }
                }) { Text("Ajustes de Android") }
                val rel = vm.update
                Button(onClick = { if (rel != null) vm.installUpdate() else vm.checkUpdate(manual = true) }) {
                    Text(if (rel != null) "⬇ Actualizar a ${rel.version}" else "Buscar actualizaciones (v${com.deportv.app.update.Updater.currentVersion})")
                }
            }
        }
        item {
            val dl = vm.downloading
            val msg = if (dl != null) "Descargando actualización… $dl%" else vm.updateMsg
            if (msg != null) Text(msg, color = Color(0xFFFFC107), fontSize = 15.sp)
        }
        item {
            Text("Categorías de canales a analizar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                if (chosen.isEmpty()) "Ahora: automático (${autoIds.size} categorías deportivas detectadas). Marca alguna para elegir a mano."
                else "Ahora: ${chosen.size} elegidas a mano.",
                fontSize = 14.sp, color = Color.White.copy(alpha = .6f)
            )
            if (chosen.isNotEmpty()) Button(onClick = { chosen = emptySet() }, modifier = Modifier.padding(top = 8.dp)) { Text("Volver a automático") }
            if (cats.isEmpty()) Text("Pulsa Actualizar en la portada para cargar las categorías.", color = Color.White.copy(alpha = .6f))
        }
        items(cats, key = { it.id }) { c ->
            val on = if (chosen.isEmpty()) c.id in autoIds else c.id in chosen
            Toggle((if (on) "☑ " else "☐ ") + c.name, on) {
                val base = if (chosen.isEmpty()) autoIds else chosen
                chosen = if (c.id in base) base - c.id else base + c.id
            }
        }
    }
}

@Composable
private fun Toggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Accent.copy(alpha = .35f) else Color(0xFF1E262F),
            focusedContainerColor = Accent, contentColor = Color.White, focusedContentColor = Color.Black
        )
    ) { Text(label, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 16.sp) }
}
