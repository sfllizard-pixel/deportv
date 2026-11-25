package com.deportv.app.data

/** Lógica pura (sin Android) para poder probarla con tests. */
object EventBuilder {

    private val sportWords = listOf(
        "deporte", "deportes", "sport", "sports", "dazn", "laliga", "la liga", "liga", "futbol", "football",
        "f1", "formula", "motogp", "moto", "eurosport", "movistar", "m+", "gol", "champions", "campeones",
        "bein", "espn", "tnt", "sky sport", "canal+", "teledeporte", "esport3", "real madrid", "barca",
        "ppv", "events", "eventos", "nba", "acb", "tenis", "golf"
    )

    fun isSportName(name: String): Boolean {
        val n = Relevance.norm(name)
        return sportWords.any { w -> Regex("(^|[^a-z0-9])" + Regex.escape(w) + "($|[^a-z0-9])").containsMatchIn(n) }
    }

    fun selectChannels(all: List<Channel>, chosenCategories: Set<String>): List<Channel> =
        if (chosenCategories.isNotEmpty()) all.filter { it.categoryId in chosenCategories }
        else all.filter { isSportName(it.categoryName) || isSportName(it.name) }

    /** Orden de preferencia de calidad para el canal por defecto: FHD > HD > 4K > resto. */
    fun qualityRank(name: String): Int {
        val n = Relevance.norm(name)
        return when {
            Regex("fhd|1080").containsMatchIn(n) -> 0
            Regex("(^|[^a-z])hd($|[^a-z])|720").containsMatchIn(n) -> 1
            Regex("4k|uhd").containsMatchIn(n) -> 2
            Regex("(^|[^a-z])sd($|[^a-z])").containsMatchIn(n) -> 4
            else -> 3
        }
    }

    private fun cleanTitle(t: String) = Relevance.norm(t)
        .replace(Regex("\\((d|directo|en directo|live)\\)|\\b(directo|en directo|live)\\b"), "")
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    fun build(
        programs: List<Program>,
        channels: List<Channel>,
        favorites: List<String>,
        now: Long
    ): List<SportEvent> {
        val byEpg = channels.filter { it.epgId != null }.groupBy { it.epgId!!.lowercase() }
        val groups = LinkedHashMap<String, MutableList<Pair<Program, Channel>>>()
        for (p in programs) {
            val chs = byEpg[p.epgId.lowercase()] ?: continue
            val key = cleanTitle(p.title) + "|" + p.start
            val list = groups.getOrPut(key) { mutableListOf() }
            chs.forEach { list.add(p to it) }
        }
        return groups.values.mapNotNull { items ->
            val (p, firstCh) = items.first()
            val r = Relevance.classify(p.title, p.desc, firstCh.name, favorites, p.start, p.stop, now) ?: return@mapNotNull null
            SportEvent(
                title = p.title, desc = p.desc, start = p.start, stop = items.maxOf { it.first.stop },
                sport = r.sport, competition = r.competition, score = r.score, isReplay = r.isReplay,
                channels = items.map { it.second }.distinctBy { it.streamId }.sortedBy { qualityRank(it.name) }
            )
        }
    }

    /** Recalcula la puntuación (directo/próximo cambia con la hora). */
    fun rescore(events: List<SportEvent>, favorites: List<String>, now: Long): List<SportEvent> =
        events.mapNotNull { e ->
            val r = Relevance.classify(e.title, e.desc, e.channels.firstOrNull()?.name ?: "", favorites, e.start, e.stop, now)
                ?: return@mapNotNull null
            e.copy(score = r.score, sport = r.sport, competition = r.competition, isReplay = r.isReplay)
        }.sortedByDescending { it.score }
}
