package com.inktest

import java.time.LocalDate

/**
 * Schuljahre in der Schreibweise `25-26`. Sie benennen die Annotationsebenen
 * (siehe [DocumentStore]) und tauchen so in Datei- und WebDAV-Pfaden auf —
 * deshalb bewusst kurz und ohne Sonderzeichen.
 */
object SchoolYear {

    /**
     * Das laufende Schuljahr. Der Wechsel wird auf den **1. August** gelegt:
     * Vorbereitung beginnt in den Sommerferien, und ab dann soll Neues in der
     * neuen Ebene landen.
     */
    fun current(today: LocalDate = LocalDate.now()): String {
        val startYear = if (today.monthValue >= 8) today.year else today.year - 1
        return format(startYear)
    }

    /** Das Schuljahr, das auf [year] folgt. */
    fun next(year: String): String = format(startYearOf(year) + 1)

    /** Zweistelliges Startjahr aus `25-26`; unlesbare Angaben ergeben das laufende. */
    fun startYearOf(year: String): Int {
        val part = year.substringBefore('-').trim().toIntOrNull()
            ?: return startYearOf(current())
        return if (part < 100) 2000 + part else part
    }

    private fun format(startYear: Int): String {
        val a = startYear % 100
        val b = (startYear + 1) % 100
        return "%02d-%02d".format(a, b)
    }
}
