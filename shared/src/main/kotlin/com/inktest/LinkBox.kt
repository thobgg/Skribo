package com.inktest

import org.json.JSONObject
import java.util.UUID

/**
 * Verweis auf ein Video im Netz (in der Praxis YouTube). Bewusst **nur ein
 * Link**, kein eingebetteter Player: Videos werden nicht mitsynchronisiert,
 * nicht dekodiert und belasten das Board nicht. Ein Tipp darauf öffnet den
 * Link im Browser.
 */
class LinkBox(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var url: String,
    /** Anzeigetext; leer ⇒ die URL selbst wird gezeigt. */
    var title: String = "",
) {
    /** Was auf der Seite steht. */
    val label: String get() = title.ifBlank { url }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("url", url)
        put("title", title)
    }

    /**
     * Erkennt die YouTube-Video-ID in den gängigen Link-Formen
     * (`watch?v=`, `youtu.be/`, `embed/`, `shorts/`). `null`, wenn es kein
     * YouTube-Link ist — dann bleibt es ein gewöhnlicher Verweis.
     */
    fun youtubeId(): String? = YOUTUBE_PATTERNS.firstNotNullOfOrNull { pattern ->
        pattern.find(url)?.groupValues?.getOrNull(1)?.takeIf { it.length == 11 }
    }

    companion object {
        private val YOUTUBE_PATTERNS = listOf(
            Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
            Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
            Regex("""youtube\.com/embed/([A-Za-z0-9_-]{11})"""),
            Regex("""youtube\.com/shorts/([A-Za-z0-9_-]{11})"""),
        )

        fun fromJson(j: JSONObject): LinkBox = LinkBox(
            id = j.optString("id", UUID.randomUUID().toString()),
            x = j.getDouble("x").toFloat(),
            y = j.getDouble("y").toFloat(),
            url = j.optString("url", ""),
            title = j.optString("title", ""),
        )
    }
}
