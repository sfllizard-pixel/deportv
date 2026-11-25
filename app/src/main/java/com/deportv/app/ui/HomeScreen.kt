package com.deportv.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.deportv.app.data.Sport
import com.deportv.app.data.SportEvent

data class TvApp(val label: String, val pkg: String, val banner: Drawable?)

fun loadTvApps(ctx: Context): List<TvApp> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            val ai = it.activityInfo
            TvApp(it.loadLabel(pm).toString(), ai.packageName, ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) ?: ai.loadIcon(pm))
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun HomeScreen(vm: AppViewModel, onEvent: (SportEvent) -> Unit) {
    val now = vm.now
    val ctx = LocalContext.current
    val apps = remember { loadTvApps(ctx) }

    val visible = vm.events.filter { !it.isReplay && it.stop > now && it.score > 40 }
    val today = visible.filter { dayIndex(it.start, now) <= 0 || it.isLive(now) }
    val hero = today.firstOrNull() ?: visible.firstOrNull()
    val live = visible.filter { it.isLive(now) }
    val futbolHoy = today.filter { it.sport == Sport.FUTBOL && !it.isLive(now) }.sortedBy { it.start }
    val motor = visible.filter { (it.sport == Sport.F1 || it.sport == Sport.MOTOGP) && dayIndex(it.start, now) <= 2 }
        .sortedBy { it.start }
    val tomorrow = visible.filter { dayIndex(it.start, now) == 1 && it.sport == Sport.FUTBOL }.sortedByDescending { it.score }
    val others = today.filter { it.sport !in setOf(Sport.FUTBOL, Sport.F1, Sport.MOTOGP) }

    val heroFocus = remember { FocusRequester() }

    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { TopBar(vm) }
        if (hero != null) item {
            Hero(hero, now, Modifier.focusRequester(heroFocus)) { onEvent(hero) }
            LaunchedEffect(hero.key) { runCatching { heroFocus.requestFocus() } }
        } else item {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        vm.loading -> vm.progress
                        vm.error != null -> vm.error!!
                        vm.progress.isNotEmpty() -> vm.progress
                        else -> "No hay eventos. Pulsa Actualizar."
                    }, color = Color.White.copy(alpha = .8f), fontSize = 18.sp
                )
            }
        }
        if (live.size > 1 || (live.size == 1 && live.first() != hero)) item { Row_("🔴 En directo ahora", live, now, onEvent) }
        if (futbolHoy.isNotEmpty()) item { Row_("⚽ Fútbol hoy", futbolHoy, now, onEvent) }
        if (motor.isNotEmpty()) item { Row_("🏁 Motor: F1 y MotoGP", motor, now, onEvent) }
        if (tomorrow.isNotEmpty()) item { Row_("📅 Mañana", tomorrow, now, onEvent) }
        if (others.isNotEmpty()) item { Row_("🏆 Otros deportes hoy", others, now, onEvent) }
        if (apps.isNotEmpty()) item { AppsRow(apps) }
    }
}

@Composable
private fun TopBar(vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("DeporTV", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Accent)
        Spacer(Modifier.width(24.dp))
        Text(hour(vm.now), fontSize = 22.sp, color = Color.White)
        if (vm.loading) {
            Spacer(Modifier.width(24.dp))
            Text(vm.progress, fontSize = 14.sp, color = Color.White.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = { vm.screen = Screen.GUIDE }) { Text("📋 Programación") }
        Spacer(Modifier.width(12.dp))
        Button(onClick = { vm.refresh() }, enabled = !vm.loading) { Text(if (vm.loading) "Actualizando…" else "⟳ Actualizar") }
        Spacer(Modifier.width(12.dp))
        Button(onClick = { vm.screen = Screen.SETTINGS }) { Text("⚙ Ajustes") }
    }
}

@Composable
private fun Hero(e: SportEvent, now: Long, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 48.dp).fillMaxWidth().height(250.dp),
        scale = CardDefaults.scale(focusedScale = 1.02f),
        border = CardDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White)))
    ) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(sportColors(e.sport)))) {
            e.channels.firstOrNull()?.logo?.let {
                AsyncImage(it, null, Modifier.align(Alignment.CenterEnd).padding(40.dp).size(220.dp, 130.dp), contentScale = ContentScale.Fit, alpha = .9f)
            }
            Column(Modifier.fillMaxHeight().padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text("${sportIcon(e.sport)}  ${(e.competition ?: e.sport.label).uppercase()}", color = Color.White.copy(alpha = .85f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val teams = splitTeams(e.title)
                Text(teams?.let { "${it.first}  vs  ${it.second}" } ?: e.title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.7f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (e.isLive(now)) LiveBadge() else Text(whenLabel(e, now), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Text(e.channels.joinToString(" · ") { it.name }.take(80), color = Color.White.copy(alpha = .75f), fontSize = 15.sp, maxLines = 1)
                }
                Spacer(Modifier.height(10.dp))
                Text("Pulsa OK para ver", color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Row_(title: String, events: List<SportEvent>, now: Long, onEvent: (SportEvent) -> Unit) {
    Column(Modifier.padding(top = 28.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 48.dp, bottom = 10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(events, key = { it.key }) { e -> EventCard(e, now) { onEvent(e) } }
        }
    }
}

@Composable
private fun AppsRow(apps: List<TvApp>) {
    val ctx = LocalContext.current
    Column(Modifier.padding(top = 28.dp)) {
        Text("📺 Aplicaciones", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 48.dp, bottom = 10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(apps, key = { it.pkg }) { a ->
                Card(onClick = {
                    ctx.packageManager.getLeanbackLaunchIntentForPackage(a.pkg)?.let { ctx.startActivity(it) }
                }, modifier = Modifier.size(200.dp, 112.dp)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1E262F)), contentAlignment = Alignment.Center) {
                        if (a.banner != null) AsyncImage(a.banner, a.label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Text(a.label, color = Color.White)
                    }
                }
            }
        }
    }
}
