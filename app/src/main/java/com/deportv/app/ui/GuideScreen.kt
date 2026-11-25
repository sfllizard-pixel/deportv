package com.deportv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.deportv.app.data.Sport
import com.deportv.app.data.SportEvent
import java.text.SimpleDateFormat
import java.util.*

private enum class Filter(val label: String, val sports: Set<Sport>?) {
    TODO("Todo", null),
    FUTBOL("⚽ Fútbol", setOf(Sport.FUTBOL)),
    F1("🏎 F1", setOf(Sport.F1)),
    MOTO("🏍 MotoGP", setOf(Sport.MOTOGP)),
    OTROS("🏆 Otros", setOf(Sport.BALONCESTO, Sport.TENIS, Sport.OTRO))
}

@Composable
fun GuideScreen(vm: AppViewModel, onEvent: (SportEvent) -> Unit) {
    val now = vm.now
    var day by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(Filter.TODO) }
    var showReplays by remember { mutableStateOf(false) }
    val dayFmt = remember { SimpleDateFormat("EEEE d", Locale("es", "ES")) }

    val list = vm.events
        .filter { dayIndex(it.start, now).coerceAtLeast(0) == day || (day == 0 && it.isLive(now)) }
        .filter { filter.sports == null || it.sport in filter.sports!! }
        .filter { showReplays || !it.isReplay }
        .filter { it.stop > now || day > 0 }
        .distinctBy { it.key }
        .sortedWith(compareBy({ it.start }, { -it.score }))

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 48.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.screen = Screen.HOME }) { Text("← Inicio") }
            Spacer(Modifier.width(24.dp))
            Text("Programación deportiva", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (0..3).forEach { d ->
                val label = when (d) {
                    0 -> "Hoy"; 1 -> "Mañana"
                    else -> dayFmt.format(Date(now + d * 86_400_000L)).replaceFirstChar { it.uppercase() }
                }
                Chip(label, day == d) { day = d }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Filter.entries.forEach { f -> Chip(f.label, filter == f) { filter = f } }
            Spacer(Modifier.width(30.dp))
            Chip(if (showReplays) "Repeticiones: sí" else "Repeticiones: no", showReplays) { showReplays = !showReplays }
        }
        Spacer(Modifier.height(16.dp))
        if (list.isEmpty()) {
            Text("No hay eventos para este filtro.", color = Color.White.copy(alpha = .6f), fontSize = 18.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            items(list, key = { it.key }) { e -> GuideRow(e, now) { onEvent(e) } }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Accent.copy(alpha = .35f) else Color(0xFF1E262F),
            focusedContainerColor = Accent, contentColor = Color.White, focusedContentColor = Color.Black
        )
    ) { Text(label, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 15.sp) }
}

@Composable
private fun GuideRow(e: SportEvent, now: Long, onClick: () -> Unit) {
    val live = e.isLive(now)
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF151C23), focusedContainerColor = Color(0xFF243140)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(110.dp)) {
                Text(hour(e.start), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (live) LiveRed else Color.White)
                Text("hasta ${hour(e.stop)}", fontSize = 12.sp, color = Color.White.copy(alpha = .5f))
            }
            Box(Modifier.size(6.dp, 44.dp).clip(RoundedCornerShape(3.dp)).background(sportColors(e.sport).first()))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${sportIcon(e.sport)} ${e.competition ?: e.sport.label}", fontSize = 13.sp, color = Color.White.copy(alpha = .6f))
                    if (live) { Spacer(Modifier.width(10.dp)); LiveBadge() }
                    if (e.isReplay) { Spacer(Modifier.width(10.dp)); Text("Repetición", fontSize = 12.sp, color = Color.White.copy(alpha = .5f)) }
                }
                Text(e.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.channels.joinToString(" · ") { it.name }, fontSize = 13.sp, color = Color.White.copy(alpha = .55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            e.channels.firstOrNull()?.logo?.let {
                AsyncImage(it, null, Modifier.size(70.dp, 40.dp), contentScale = ContentScale.Fit)
            }
        }
    }
}
