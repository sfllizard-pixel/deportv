package com.deportv.app.data

import java.text.Normalizer

/**
 * Clasifica programas del EPG (fútbol, F1, MotoGP...) y les da una puntuación de relevancia.
 * Prioridad: fútbol (LaLiga / Champions / tus equipos) > F1 > MotoGP > resto.
 */
object Relevance {

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    private fun has(text: String, vararg words: String) = words.any { w ->
        Regex("(^|[^a-z0-9])" + Regex.escape(w) + "($|[^a-z0-9])").containsMatchIn(text)
    }

    // ---------- Palabras clave ----------
    private val replayWords = arrayOf(
        "resumen", "resumenes", "repeticion", "reposicion", "diferido", "highlights",
        "mejores momentos", "(r)", "en diferido", "clasico historico", "partidos historicos",
        "goles de la jornada", "todos los goles", "informativo", "noticias", "telediario",
        "magazine", "tertulia", "debate", "el chiringuito", "documental", "replay"
    )
    private val liveWords = arrayOf("directo", "en vivo", "live", "(d)", "en directo")

    private val motogp = arrayOf("motogp", "moto gp", "moto2", "moto3", "motoe", "mundial de motociclismo", "superbike", "wsbk")
    private val f1 = arrayOf("formula 1", "formula1", "f1", "formula uno", "gran premio de formula")
    private val basket = arrayOf("acb", "liga endesa", "euroliga", "euroleague", "nba", "baloncesto", "basket", "eurocup")
    private val tenis = arrayOf("tenis", "atp", "wta", "roland garros", "wimbledon", "us open", "open de australia", "copa davis", "masters 1000")

    private val competitions = linkedMapOf(
        "LaLiga" to arrayOf("laliga ea sports", "laliga easports", "la liga ea sports", "laliga", "la liga", "liga ea sports", "primera division"),
        "Champions" to arrayOf("champions league", "liga de campeones", "uefa champions", "champions"),
        "Copa del Rey" to arrayOf("copa del rey", "copa s.m. el rey", "copa de s.m. el rey"),
        "Supercopa" to arrayOf("supercopa"),
        "Europa League" to arrayOf("europa league", "uefa europa"),
        "Conference" to arrayOf("conference league"),
        "Selección" to arrayOf("seleccion espanola", "clasificacion mundial", "nations league", "eurocopa", "copa del mundo", "mundial 2026", "mundial de clubes"),
        "Hypermotion" to arrayOf("hypermotion", "segunda division", "laliga 2"),
        "Liga F" to arrayOf("liga f"),
        "Premier League" to arrayOf("premier league", "premier"),
        "Serie A" to arrayOf("serie a", "calcio"),
        "Bundesliga" to arrayOf("bundesliga"),
        "Ligue 1" to arrayOf("ligue 1"),
        "Saudi Pro League" to arrayOf("saudi pro league", "liga saudi")
    )
    private val footballGeneric = arrayOf("futbol", "football", "soccer", "jornada", "uefa", "fifa")

    /** Equipos españoles (para detectar partidos aunque el EPG no diga la competición). */
    val spanishTeams = listOf(
        "real madrid", "barcelona", "barca", "atletico", "atletico de madrid", "athletic", "athletic club",
        "real sociedad", "villarreal", "betis", "real betis", "sevilla", "valencia", "girona", "osasuna",
        "celta", "mallorca", "rayo vallecano", "rayo", "getafe", "alaves", "espanyol", "las palmas",
        "leganes", "valladolid", "levante", "elche", "oviedo", "real oviedo", "deportivo", "racing",
        "zaragoza", "sporting", "granada", "almeria", "cadiz", "malaga", "eibar", "tenerife", "huesca",
        "albacete", "burgos", "cordoba", "castellon", "mirandes", "cultural leonesa", "andorra", "ceuta"
    )
    private val bigTeams = arrayOf("real madrid", "barcelona", "barca", "atletico de madrid", "atletico")

    private val matchPattern = Regex("\\S+.*\\s(-|vs\\.?|v|–|x)\\s.*\\S+")

    data class Result(val sport: Sport, val competition: String?, val score: Int, val isReplay: Boolean)

    /**
     * @return null si no parece un evento deportivo.
     */
    fun classify(
        title: String,
        desc: String,
        channelName: String,
        favoriteTeams: List<String> = emptyList(),
        start: Long = 0,
        stop: Long = 0,
        now: Long = System.currentTimeMillis()
    ): Result? {
        val t = norm(title)
        val d = norm(desc)
        val c = norm(channelName)
        val td = "$t $d"

        val replay = has(t, *replayWords) || Regex("\\(r\\)|\\[r\\]").containsMatchIn(t)
        val live = has(t, *liveWords) || has(d, "en directo", "directo")
        val isMatch = matchPattern.containsMatchIn(t)

        // ---- Deporte ----
        val sport: Sport
        var competition: String? = null
        var score: Int

        when {
            has(td, *motogp) || (has(td, "gran premio", "gp") && has(c, "motogp", "moto gp")) -> {
                sport = Sport.MOTOGP; score = 85; competition = "MotoGP"
                when {
                    has(t, "moto2") -> { score -= 25; competition = "Moto2" }
                    has(t, "moto3") -> { score -= 30; competition = "Moto3" }
                    has(t, "superbike", "wsbk") -> { score -= 30; competition = "Superbike" }
                }
                score += when {
                    has(t, "sprint") -> 20
                    has(t, "carrera", "race") -> 30
                    has(t, "clasificacion", "qualifying", "q1", "q2") -> 15
                    has(t, "libres", "practica", "fp1", "fp2", "warm up", "entrenamientos") -> 0
                    else -> 5
                }
            }
            has(td, *f1) || (has(td, "gran premio", "gp") && has(c, "f1", "formula 1", "formula")) -> {
                sport = Sport.F1; score = 90; competition = "Fórmula 1"
                if (has(t, "f2", "formula 2", "f3", "formula 3", "f1 academy")) { score -= 35; competition = "F2/F3" }
                score += when {
                    has(t, "sprint") && !has(t, "clasificacion", "shootout") -> 25
                    has(t, "carrera", "race") -> 35
                    has(t, "clasificacion", "qualifying", "shootout") -> 18
                    has(t, "libres", "practica", "fp1", "fp2", "fp3", "entrenamientos") -> 0
                    has(t, "previo", "previa", "pre-carrera", "parrilla") -> 5
                    else -> 5
                }
            }
            has(td, *basket) -> { sport = Sport.BALONCESTO; score = 40; competition = null }
            has(td, *tenis) -> { sport = Sport.TENIS; score = 35; competition = null }
            else -> {
                competition = competitions.entries.firstOrNull { (_, words) -> has(td, *words) }?.key
                val teamHit = spanishTeams.any { has(t, it) }
                val genericHit = has(td, *footballGeneric)
                val sportsChannel = has(c, "laliga", "dazn", "futbol", "gol", "champions", "liga de campeones",
                    "m+", "movistar", "deportes", "sport", "sports", "bein", "eurosport", "espn", "tnt", "teledeporte", "esport3")
                if (competition == null && !teamHit && !genericHit && !(isMatch && sportsChannel)) {
                    // Deporte genérico en canal deportivo (ciclismo, golf...) o nada
                    if (sportsChannel && (live || isMatch)) {
                        return Result(Sport.OTRO, null, 20 + (if (live) 10 else 0), replay)
                    }
                    return null
                }
                sport = Sport.FUTBOL
                score = 100
                score += when (competition) {
                    "LaLiga", "Champions" -> 30
                    "Supercopa", "Selección" -> 20
                    "Copa del Rey" -> 15
                    "Europa League" -> 10
                    "Premier League", "Conference" -> 5
                    "Hypermotion", "Liga F" -> -15
                    null -> 0
                    else -> -5
                }
                if (bigTeams.any { has(t, it) }) score += 15
                if (isMatch) score += 10 else score -= 25 // programas sobre fútbol que no son partido
            }
        }

        if (favoriteTeams.any { it.isNotBlank() && has(t, norm(it.trim())) }) score += 60
        if (live) score += 10
        if (replay) score -= 90
        if (start > 0 && stop > 0) {
            if (now in start until stop) score += 25
            else if (start > now && start - now < 3 * 3600_000L) score += 10
            else if (stop < now) score -= 50
        }
        return Result(sport, competition, score, replay)
    }
}
