package com.deportv.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Repository(private val ctx: Context, val settings: Settings) {

    private val cacheFile = File(ctx.filesDir, "events.json")
    private val catsFile = File(ctx.filesDir, "categories.json")

    data class RefreshResult(val events: List<SportEvent>, val channelCount: Int, val usedFallback: Boolean)

    suspend fun refresh(progress: (String) -> Unit = {}): RefreshResult {
        val cfg = settings.config ?: error("Configura tu IPTV primero")
        val client = XtreamClient(cfg)
        val now = System.currentTimeMillis()
        val from = now - 4 * 3600_000L
        val to = now + 3 * 24 * 3600_000L
        val favs = settings.favoriteList

        progress("Descargando categorías…")
        val cats = client.categories()
        saveCategories(cats)
        progress("Descargando canales…")
        val all = client.channels(cats.associate { it.id to it.name })
        val selected = EventBuilder.selectChannels(all, settings.categoryIds)
        val nameByEpg = selected.filter { it.epgId != null }
            .associate { it.epgId!!.lowercase() to it.name }
        val keep: (Program) -> Boolean = { p ->
            Relevance.classify(p.title, p.desc, nameByEpg[p.epgId.lowercase()] ?: "", favs) != null
        }

        progress("Descargando guía (EPG) de ${selected.size} canales… puede tardar un poco")
        var fallback = false
        var programs = try {
            client.epgXmltv(nameByEpg.keys, from, to, keep)
        } catch (e: Exception) {
            null
        }
        if (programs.isNullOrEmpty()) {
            fallback = true
            progress("Probando EPG canal a canal…")
            programs = client.epgPerChannel(selected.filter { it.epgId != null }, from, to, keep)
        }

        progress("Ordenando eventos…")
        val events = EventBuilder.build(programs, selected, favs, now)
        withContext(Dispatchers.IO) { saveEvents(events) }
        settings.lastRefresh = now
        return RefreshResult(EventBuilder.rescore(events, favs, now), selected.size, fallback)
    }

    suspend fun cached(): List<SportEvent> = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext emptyList()
        try {
            val arr = JSONArray(cacheFile.readText())
            val events = (0 until arr.length()).map { eventFromJson(arr.getJSONObject(it)) }
            EventBuilder.rescore(events, settings.favoriteList, System.currentTimeMillis())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cachedCategories(): List<Category> = try {
        val arr = JSONArray(catsFile.readText())
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Category(o.getString("id"), o.getString("name")) } }
    } catch (e: Exception) { emptyList() }

    private fun saveCategories(cats: List<Category>) {
        val arr = JSONArray()
        cats.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        catsFile.writeText(arr.toString())
    }

    private fun saveEvents(events: List<SportEvent>) {
        val arr = JSONArray()
        events.forEach { e ->
            val chs = JSONArray()
            e.channels.forEach { c ->
                chs.put(JSONObject().put("id", c.streamId).put("name", c.name).put("logo", c.logo ?: "")
                    .put("epg", c.epgId ?: "").put("cat", c.categoryId ?: "").put("catName", c.categoryName))
            }
            arr.put(JSONObject().put("title", e.title).put("desc", e.desc).put("start", e.start).put("stop", e.stop)
                .put("sport", e.sport.name).put("comp", e.competition ?: "").put("score", e.score)
                .put("replay", e.isReplay).put("channels", chs))
        }
        cacheFile.writeText(arr.toString())
    }

    private fun eventFromJson(o: JSONObject): SportEvent {
        val chs = o.getJSONArray("channels")
        return SportEvent(
            title = o.getString("title"), desc = o.optString("desc"),
            start = o.getLong("start"), stop = o.getLong("stop"),
            sport = Sport.valueOf(o.getString("sport")),
            competition = o.optString("comp").ifEmpty { null },
            score = o.optInt("score"), isReplay = o.optBoolean("replay"),
            channels = (0 until chs.length()).map { i ->
                val c = chs.getJSONObject(i)
                Channel(c.getInt("id"), c.getString("name"), c.optString("logo").ifEmpty { null },
                    c.optString("epg").ifEmpty { null }, c.optString("cat").ifEmpty { null }, c.optString("catName"))
            }
        )
    }
}
