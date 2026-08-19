package com.inktest

/**
 * Format einer Seite in **Punkt** (1/72 Zoll) — dieselbe Einheit, in der PDFs
 * ihre Seitengröße angeben.
 *
 * Das Format legt zugleich das Koordinatensystem der Seite fest: Striche,
 * Text- und Bildboxen werden in Punkt relativ zur linken oberen Seitenecke
 * gespeichert. Nur so landet eine am Board geschriebene Annotation am PC an
 * derselben Stelle — beide Clients skalieren die Seite lediglich auf ihre
 * jeweilige Anzeigefläche.
 *
 * [FREE] ist der unbegrenzte Canvas von schemaVersion 1: dort gibt es keine
 * Seitengrenzen, Koordinaten sind schlicht Weltkoordinaten.
 */
enum class PageFormat(val widthPt: Float, val heightPt: Float) {
    FREE(0f, 0f),
    A4_PORTRAIT(595.28f, 841.89f),
    A4_LANDSCAPE(841.89f, 595.28f),
    SLIDE_16_9(960f, 540f),
    SLIDE_4_3(960f, 720f);

    /** Ob die Seite feste Abmessungen hat (alles außer [FREE]). */
    val isBounded: Boolean get() = this != FREE

    val aspectRatio: Float get() = if (heightPt > 0f) widthPt / heightPt else 1f

    companion object {
        fun parse(name: String?): PageFormat =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FREE

        /**
         * Wählt das passende Format zu einer gerenderten Quelle (PDF-Seite,
         * Folie). Entscheidend ist das Seitenverhältnis — eine 16:9-Folie soll
         * nicht als A4 quer landen.
         */
        fun bestFit(widthPt: Float, heightPt: Float): PageFormat {
            if (widthPt <= 0f || heightPt <= 0f) return FREE
            val ratio = widthPt / heightPt
            return entries.filter { it.isBounded }
                .minByOrNull { kotlin.math.abs(it.aspectRatio - ratio) }
                ?: FREE
        }
    }
}

/**
 * Fest auf der Seite liegender Hintergrund — die gerenderte PDF-Seite bzw.
 * Präsentationsfolie, über die am Board geschrieben wird.
 *
 * Der Hintergrund gehört zur *Basis* der Seite (`base.json`), die Handschrift
 * darüber in die jahresbezogene Annotationsebene. Dieselbe Vorlage kann so in
 * mehreren Schuljahren neu annotiert werden.
 */
class PageBackground(
    /** Pfad relativ zum Dokumentwurzelverzeichnis, z. B. `assets/<id>.png`. */
    var assetPath: String,
    /** Ursprungsdatei, damit später nachvollziehbar bleibt, woher die Seite kam. */
    var sourceName: String? = null,
    /** 1-basierte Seitennummer in der Ursprungsdatei. */
    var sourcePage: Int? = null,
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("assetPath", assetPath)
        sourceName?.let { put("sourceName", it) }
        sourcePage?.let { put("sourcePage", it) }
    }

    companion object {
        fun fromJson(j: org.json.JSONObject): PageBackground = PageBackground(
            assetPath = j.getString("assetPath"),
            sourceName = if (j.has("sourceName") && !j.isNull("sourceName")) j.getString("sourceName") else null,
            sourcePage = if (j.has("sourcePage") && !j.isNull("sourcePage")) j.getInt("sourcePage") else null,
        )
    }
}
