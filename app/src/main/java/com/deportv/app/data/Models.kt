package com.deportv.app.data

data class XtreamConfig(
    val server: String,      // p.ej. http://servidor.com:8080
    val username: String,
    val password: String,
    val format: String = "ts" // "ts" o "m3u8"
) {
    val base: String get() = server.trimEnd('/')
    fun streamUrl(streamId: Int) = "$base/live/$username/$password/$streamId.$format"
}

data class Category(val id: String, val name: String)

data class Channel(
    val streamId: Int,
    val name: String,
    val logo: String?,
    val epgId: String?,
    val categoryId: String?,
    val categoryName: String = ""
)

data class Program(
    val epgId: String,
    val start: Long,   // epoch ms
    val stop: Long,
    val title: String,
    val desc: String
)

enum class Sport(val label: String) {
    FUTBOL("Fútbol"), F1("Fórmula 1"), MOTOGP("MotoGP"),
    BALONCESTO("Baloncesto"), TENIS("Tenis"), OTRO("Otros deportes")
}

/** Un evento deportivo agrupado: mismo título y hora en varios canales (HD, FHD, 4K...). */
data class SportEvent(
    val title: String,
    val desc: String,
    val start: Long,
    val stop: Long,
    val sport: Sport,
    val competition: String?,
    val score: Int,
    val isReplay: Boolean,
    val channels: List<Channel>
) {
    fun isLive(now: Long = System.currentTimeMillis()) = now in start until stop
    val key: String get() = "${title.lowercase()}|$start"
}
