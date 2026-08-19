package com.inktest

/**
 * Minimaler Logging-Seam für den plattformfreien Kern: Android hängt hier
 * `android.util.Log` ein, der Desktop-Client seine eigene Ausgabe. Ohne
 * gesetzten Sink bleibt das Logging still — Bibliothekscode soll nicht
 * ungefragt auf die Konsole schreiben.
 */
object SkriboLog {
    fun interface Sink {
        fun warn(tag: String, message: String)
    }

    @Volatile
    var sink: Sink? = null

    fun w(tag: String, message: String) {
        sink?.warn(tag, message)
    }
}
