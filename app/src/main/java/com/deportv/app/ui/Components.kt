package com.deportv.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.deportv.app.data.Channel
import com.deportv.app.data.Settings
import com.deportv.app.data.Sport
import com.deportv.app.data.SportEvent
import com.deportv.app.player.PlayerActivity
import java.text.SimpleDateFormat
import java.util.*

val Bg = Color(0xFF0B0F14)
val Accent = Color(0xFF00C2A8)
val LiveRed = Color(0xFFE53935)

fun sportColors(s: Sport): List<Color> = when (s) {
    Sport.FUTBOL -> listOf(Color(0xFF0E5C3A), Color(0xFF0B2E22))
    Sport.F1 -> listOf(Color(0xFFB71C1C), Color(0xFF3A0B0B))
    Sport.MOTOGP -> listOf(Color(0xFFE65100), Color(0xFF3B1700))
    Sport.BALONCESTO -> listOf(Color(0xFF6A3FB5), Color(0xFF221238))
    Sport.TENIS -> listOf(Color(0xFF7B8E00), Color(0xFF262C00))
    Sport.OTRO -> listOf(Color(0xFF37474F), Color(0xFF151C20))
}

fun sportIcon(s: Sport) = when (s) {
    Sport.FUTBOL -> "⚽"; Sport.F1 -> "🏎"; Sport.MOTOGP -> "🏍"
    Sport.BALONCESTO -> "🏀"; Sport.TENIS -> "🎾"; Sport.OTRO -> "🏆"
}

private val hourFmt = SimpleDateFormat("HH:mm", Locale("es", "ES"))
private val dayFmt = SimpleDateFormat("EEE d", Locale("es", "ES"))

fun hour(t: Long): String = hourFmt.format(Date(t))

fun dayIndex(t: Long, now: Long): Int {
    val a = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return ((t - a.timeInMillis) / 86_400_000L).toInt().let { if (t < a.timeInMillis) -1 else it }
}

fun whenLabel(e: SportEvent, now: Long): String = when {
    e.isLive(now) -> "EN DIRECTO"
    dayIndex(e.start, now) == 0 -> "Hoy ${hour(e.start)}"
    dayIndex(e.start, now) == 1 -> "Mañana ${hour(e.start)}"
    else -> dayFmt.format(Date(e.start)).replaceFirstChar { it.uppercase() } + " " + hour(e.start)
}

/** Separa "Real Madrid - Barcelona" en dos líneas si es un partido. */
fun splitTeams(title: String): Pair<String, String>? {
    val clean = title.replace(Regex("\\((directo|en directo|d|live)\\)", RegexOption.IGNORE_CASE), "").trim()
    val parts = clean.split(Regex("\\s(-|vs\\.?|–)\\s", RegexOption.IGNORE_CASE))
    if (parts.size != 2) return null
    val a = parts[0].substringAfterLast(":").trim()
    return a to parts[1].trim()
}

fun play(ctx: Context, settings: Settings, event: SportEvent, channel: Channel) {
    val cfg = settings.config ?: return
    if (settings.internalPlayer) {
        val ordered = listOf(channel) + event.channels.filter { it.streamId != channel.streamId }
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_TITLE, event.title)
            putExtra(PlayerActivity.EXTRA_URLS, ordered.map { cfg.streamUrl(it.streamId) }.toTypedArray())
            putExtra(PlayerActivity.EXTRA_NAMES, ordered.map { it.name }.toTypedArray())
        })
    } else {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(cfg.streamUrl(channel.streamId)), "video/*")
            putExtra("title", event.title)
        }
        try { ctx.startActivity(i) } catch (e: Exception) {
            Toast.makeText(ctx, "No hay reproductor externo instalado (instala VLC o MX Player)", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun EventCard(e: SportEvent, now: Long, width: Int = 300, height: Int = 170, onClick: () -> Unit) {
    val live = e.isLive(now)
    Card(
        onClick = onClick,
        modifier = Modifier.size(width.dp, height.dp),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White)))
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(sportColors(e.sport)))) {
            e.channels.firstOrNull()?.logo?.let {
                AsyncImage(it, null, Modifier.align(Alignment.TopEnd).padding(10.dp).size(56.dp, 34.dp), contentScale = ContentScale.Fit)
            }
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("${sportIcon(e.sport)}  ${e.competition ?: e.sport.label}", fontSize = 13.sp, color = Color.White.copy(alpha = .85f), maxLines = 1)
                val teams = splitTeams(e.title)
                if (teams != null) {
                    Column {
                        Text(teams.first, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(teams.second, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(e.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (live) LiveBadge() else Text(whenLabel(e, now), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(e.channels.firstOrNull()?.name ?: "", fontSize = 12.sp, color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun LiveBadge() {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(LiveRed).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text("● EN DIRECTO", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** Selector de canal cuando un evento se emite en varios (HD, FHD, 4K, otro operador...). */
@Composable
fun ChannelPicker(e: SportEvent, onPick: (Channel) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A2129)).padding(24.dp).widthIn(min = 420.dp, max = 560.dp)) {
            Text(e.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Elige canal", fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
            Spacer(Modifier.height(12.dp))
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(e.channels, key = { it.streamId }) { c ->
                    val m = if (c == e.channels.first()) Modifier.focusRequester(first) else Modifier
                    Surface(onClick = { onPick(c) }, modifier = m.fillMaxWidth(),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF252E38), focusedContainerColor = Accent)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(c.logo, null, Modifier.size(48.dp, 30.dp), contentScale = ContentScale.Fit)
                            Spacer(Modifier.width(12.dp))
                            Text(c.name, color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvTextField(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = .7f))
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E262F))
                .border(2.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(14.dp)
        )
    }
}

@Composable
fun UpdateDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val rel = vm.update ?: return
    Dialog(onDismissRequest = { if (vm.downloading == null) onDismiss() }) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A2129)).padding(28.dp).widthIn(min = 420.dp, max = 600.dp)) {
            Text("Nueva versión ${rel.version}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
            Text("Tienes la ${com.deportv.app.update.Updater.currentVersion}", fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
            if (rel.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(rel.notes.take(500), fontSize = 15.sp, color = Color.White, maxLines = 10, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            val dl = vm.downloading
            if (dl != null) {
                Text("Descargando… $dl%", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2A333D))) {
                    Box(Modifier.fillMaxWidth(dl / 100f).fillMaxHeight().background(Accent))
                }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.installUpdate() }, modifier = Modifier.focusRequester(focus)) { Text("Actualizar") }
                    Button(onClick = onDismiss) { Text("Ahora no") }
                }
            }
            vm.updateMsg?.let { Spacer(Modifier.height(12.dp)); Text(it, color = Color(0xFFFFC107), fontSize = 14.sp) }
        }
    }
}
