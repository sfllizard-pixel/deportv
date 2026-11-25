package com.deportv.app.data

import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parser en streaming del XMLTV de Xtream (xmltv.php). Puede pesar decenas de MB,
 * así que no se carga entero: solo se guardan los programas de los canales que
 * nos interesan y dentro de la ventana de tiempo.
 */
object EpgParser {

    fun parse(
        input: InputStream,
        parser: XmlPullParser,
        wantedEpgIds: Set<String>,
        from: Long,
        to: Long,
        keep: (Program) -> Boolean = { true }
    ): List<Program> {
        val out = ArrayList<Program>()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val wantedLower = wantedEpgIds.map { it.lowercase() }.toHashSet()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val ch = parser.getAttributeValue(null, "channel") ?: ""
                val start = parseTime(parser.getAttributeValue(null, "start"))
                val stop = parseTime(parser.getAttributeValue(null, "stop"))
                val relevant = ch.lowercase() in wantedLower &&
                    start != null && stop != null && stop > from && start < to
                if (relevant) {
                    var title = ""
                    var desc = ""
                    val depth = parser.depth
                    while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
                        if (parser.eventType == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "title" -> if (title.isEmpty()) title = readText(parser)
                                "desc" -> if (desc.isEmpty()) desc = readText(parser)
                            }
                        }
                        if (parser.eventType == XmlPullParser.END_DOCUMENT) break
                    }
                    val p = Program(ch, start!!, stop!!, title.trim(), desc.trim())
                    if (p.title.isNotEmpty() && keep(p)) out.add(p)
                } else {
                    skip(parser)
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.TEXT) sb.append(p.text)
            if ((e == XmlPullParser.END_TAG && p.depth == depth) || e == XmlPullParser.END_DOCUMENT) break
        }
        return sb.toString()
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private val fmtZone = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    }
    private val fmtUtc = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

    /** Formatos XMLTV: "20261125200000 +0100" o "20261125200000" (UTC). */
    fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        return try {
            if (t.length > 14 && (t.contains('+') || t.contains('-'))) {
                val digits = t.substring(0, 14)
                val zone = t.substring(14).trim()
                fmtZone.get()!!.parse("$digits $zone")?.time
            } else {
                fmtUtc.get()!!.parse(t.take(14))?.time
            }
        } catch (e: Exception) {
            null
        }
    }
}
