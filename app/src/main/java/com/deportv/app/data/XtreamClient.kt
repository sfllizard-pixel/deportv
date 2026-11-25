package com.deportv.app.data

import android.util.Base64
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class XtreamClient(private val cfg: XtreamConfig) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun apiUrl(action: String? = null, extra: Map<String, String> = emptyMap()): String {
        val b = "${cfg.base}/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", cfg.username)
            .addQueryParameter("password", cfg.password)
        if (action != null) b.addQueryParameter("action", action)
        extra.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw IOException("Respuesta vacía")
        }
    }

    /** Devuelve null si va bien, o un mensaje de error. */
    suspend fun testLogin(): String? = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(get(apiUrl()))
            val info = j.optJSONObject("user_info") ?: return@withContext "El servidor no parece Xtream Codes"
            if (info.optInt("auth", 0) != 1) return@withContext "Usuario o contraseña incorrectos"
            val status = info.optString("status", "")
            if (status.isNotEmpty() && !status.equals("Active", true)) "Cuenta en estado: $status" else null
        } catch (e: Exception) {
            "No se pudo conectar: ${e.message}"
        }
    }

    suspend fun categories(): List<Category> = withContext(Dispatchers.IO) {
        val arr = JSONArray(get(apiUrl("get_live_categories")))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Category(o.optString("category_id"), o.optString("category_name"))
        }
    }

    suspend fun channels(cats: Map<String, String>): List<Channel> = withContext(Dispatchers.IO) {
        val arr = JSONArray(get(apiUrl("get_live_streams")))
        (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            val id = o.optInt("stream_id", -1)
            if (id < 0) return@mapNotNull null
            val catId = o.optString("category_id").ifEmpty { null }
            Channel(
                streamId = id,
                name = o.optString("name").trim(),
                logo = o.optString("stream_icon").ifBlank { null },
                epgId = o.optString("epg_channel_id").ifBlank { null }?.takeIf { it != "null" },
                categoryId = catId,
                categoryName = catId?.let { c -> cats[c] } ?: ""
            )
        }
    }

    /** EPG completo vía xmltv.php, filtrado en streaming. */
    suspend fun epgXmltv(
        epgIds: Set<String>, from: Long, to: Long, keep: (Program) -> Boolean
    ): List<Program> = withContext(Dispatchers.IO) {
        val url = "${cfg.base}/xmltv.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", cfg.username)
            .addQueryParameter("password", cfg.password)
            .build()
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("xmltv HTTP ${r.code}")
            val body = r.body ?: throw IOException("xmltv vacío")
            EpgParser.parse(body.byteStream(), Xml.newPullParser(), epgIds, from, to, keep)
        }
    }

    /** Alternativa si el proveedor no da xmltv.php: EPG por canal (más lento). */
    suspend fun epgPerChannel(
        channels: List<Channel>, from: Long, to: Long, keep: (Program) -> Boolean
    ): List<Program> = coroutineScope {
        val sem = Semaphore(6)
        channels.distinctBy { it.epgId }.take(250).map { ch ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    try {
                        val j = JSONObject(get(apiUrl("get_simple_data_table", mapOf("stream_id" to ch.streamId.toString()))))
                        val list = j.optJSONArray("epg_listings") ?: return@withPermit emptyList()
                        (0 until list.length()).mapNotNull { i ->
                            val o = list.getJSONObject(i)
                            val start = o.optLong("start_timestamp", 0) * 1000
                            val stop = o.optLong("stop_timestamp", 0) * 1000
                            if (start == 0L || stop < from || start > to) return@mapNotNull null
                            val p = Program(ch.epgId ?: "", start, stop, b64(o.optString("title")), b64(o.optString("description")))
                            p.takeIf { it.title.isNotBlank() && keep(it) }
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
        }.awaitAll().flatten()
    }

    private fun b64(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
    } catch (e: Exception) { s }

    companion object {
        const val UA = "DeporTV/0.1 (Android TV)"
    }
}
