package com.deportv.app

import com.deportv.app.data.*
import org.junit.Assert.*
import org.junit.Test
import org.kxml2.io.KXmlParser

class CoreTest {
    // 25/11/2026 18:00 hora de Madrid (17:00 UTC)
    private val now = EpgParser.parseTime("20261125180000 +0100")!!

    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<tv>
 <channel id="dazn.laliga.es"><display-name>DAZN LaLiga</display-name></channel>
 <programme start="20261125210000 +0100" stop="20261125230000 +0100" channel="dazn.laliga.es">
   <title lang="es">LaLiga EA Sports: Real Madrid - Girona (Directo)</title><desc>Jornada 14</desc><category>Fútbol</category>
 </programme>
 <programme start="20261125180000 +0100" stop="20261125190000 +0100" channel="dazn.laliga.es">
   <title>Resumen LaLiga: Jornada 13</title>
 </programme>
 <programme start="20261125210000 +0100" stop="20261125230000 +0100" channel="mlaliga.es">
   <title>LaLiga EA Sports: Real Madrid - Girona</title>
 </programme>
 <programme start="20261129140000 +0100" stop="20261129160000 +0100" channel="dazn.f1.es">
   <title>Fórmula 1: GP de Qatar - Carrera</title>
 </programme>
 <programme start="20261125200000 +0100" stop="20261125210000 +0100" channel="dazn.f1.es">
   <title>Fórmula 1: GP de Qatar - Libres 1 (R)</title>
 </programme>
 <programme start="20261125190000 +0100" stop="20261125200000 +0100" channel="otro.es">
   <title>Telediario</title>
 </programme>
 <programme start="20261201190000 +0100" stop="20261201200000 +0100" channel="dazn.laliga.es">
   <title>Fuera de ventana</title>
 </programme>
</tv>"""

    private val channels = listOf(
        Channel(1, "ES: DAZN LALIGA FHD", null, "dazn.laliga.es", "10", "ESPAÑA | DEPORTES"),
        Channel(2, "ES: DAZN LALIGA 4K", null, "dazn.laliga.es", "10", "ESPAÑA | DEPORTES"),
        Channel(3, "ES: M+ LALIGA HD", null, "mlaliga.es", "10", "ESPAÑA | DEPORTES"),
        Channel(4, "ES: DAZN F1 HD", null, "dazn.f1.es", "10", "ESPAÑA | DEPORTES"),
        Channel(5, "ES: LA 1 HD", null, "otro.es", "20", "ESPAÑA | GENERALISTAS")
    )

    @Test fun parsesTimesWithOffset() {
        val utc = EpgParser.parseTime("20261125170000")!!
        assertEquals(now, utc)
    }

    @Test fun parsesAndFilters() {
        val progs = EpgParser.parse(xml.byteInputStream(), KXmlParser(),
            setOf("dazn.laliga.es", "mlaliga.es", "dazn.f1.es"), now - 3600_000, now + 5 * 86_400_000L)
        assertEquals(5, progs.size) // excluye otro.es y el de fuera de ventana
        assertEquals("LaLiga EA Sports: Real Madrid - Girona (Directo)", progs[0].title)
        assertEquals("Jornada 14", progs[0].desc)
    }

    @Test fun buildsGroupedAndRanked() {
        val progs = EpgParser.parse(xml.byteInputStream(), KXmlParser(),
            channels.mapNotNull { it.epgId }.toSet(), now - 3600_000, now + 5 * 86_400_000L)
        val events = EventBuilder.rescore(EventBuilder.build(progs, channels, emptyList(), now), emptyList(), now)
        events.forEach { println("${it.score}\t${it.sport}\t${it.competition}\treplay=${it.isReplay}\t${it.title}\t${it.channels.map { c -> c.name }}") }

        val madrid = events.first()
        assertEquals(Sport.FUTBOL, madrid.sport)
        assertEquals("LaLiga", madrid.competition)
        // Mismo partido en DAZN (FHD y 4K) y M+ agrupado en un único evento, FHD primero
        assertEquals(3, madrid.channels.size)
        assertEquals(1, madrid.channels.first().streamId)

        val f1Race = events.first { it.sport == Sport.F1 && !it.isReplay }
        assertEquals("Fórmula 1", f1Race.competition)
        assertTrue(events.first { it.title.contains("Libres") }.isReplay)
        assertTrue(events.first { it.title.startsWith("Resumen") }.isReplay)
        assertNull(events.firstOrNull { it.title == "Telediario" })
    }

    @Test fun favoritesBoost() {
        val base = Relevance.classify("Premier League: Arsenal - Chelsea", "", "DAZN", emptyList())!!
        val fav = Relevance.classify("Premier League: Arsenal - Chelsea", "", "DAZN", listOf("arsenal"))!!
        assertEquals(60, fav.score - base.score)
    }

    @Test fun classifiesMotorAndIgnoresNonSport() {
        assertEquals(Sport.MOTOGP, Relevance.classify("MotoGP: GP de Valencia - Carrera", "", "DAZN MotoGP")!!.sport)
        assertEquals("Moto2", Relevance.classify("Moto2: Carrera", "", "DAZN MotoGP")!!.competition)
        assertEquals(Sport.F1, Relevance.classify("GP de Abu Dabi: Clasificación", "", "DAZN F1")!!.sport)
        assertNull(Relevance.classify("La Revuelta", "Programa de humor", "La 1"))
        assertNull(Relevance.classify("Telediario 2", "", "La 1"))
        // Partido en canal generalista
        assertEquals(Sport.FUTBOL, Relevance.classify("Copa del Rey: Valencia - Betis", "", "La 1")!!.sport)
    }

    @Test fun sportCategoryDetection() {
        assertTrue(EventBuilder.isSportName("ESPAÑA | DEPORTES"))
        assertTrue(EventBuilder.isSportName("ES: DAZN LALIGA"))
        assertFalse(EventBuilder.isSportName("ESPAÑA | GENERALISTAS"))
        assertFalse(EventBuilder.isSportName("CINE"))
    }

    @Test fun versionCompare() {
        assertTrue(com.deportv.app.update.Updater.isNewer("0.2.0", "0.1.0"))
        assertTrue(com.deportv.app.update.Updater.isNewer("0.10.0", "0.9.5"))
        assertTrue(com.deportv.app.update.Updater.isNewer("1.0", "0.9.9"))
        assertFalse(com.deportv.app.update.Updater.isNewer("0.2.0", "0.2.0"))
        assertFalse(com.deportv.app.update.Updater.isNewer("0.1.9", "0.2.0"))
    }
}
