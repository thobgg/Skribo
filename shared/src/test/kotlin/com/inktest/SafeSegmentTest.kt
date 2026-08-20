package com.inktest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Seitentitel werden zu Ordnernamen. Am echten Synology-Server fiel auf, dass
 * ein Titel mit Punkt am Ende — etwa ein Datum wie „20.08." — den Ordner
 * eigenmächtig umbenennen lässt und dabei einen zweiten anlegt.
 */
class SafeSegmentTest {

    private fun seg(s: String) = SkriboSync.safeSegment(s)

    @Test
    fun `punkt am ende faellt weg`() {
        assertEquals("Stundenentwurf 20.08", seg("Stundenentwurf 20.08."))
        assertEquals("Übung", seg("Übung..."))
    }

    @Test
    fun `punkte in der mitte bleiben`() {
        assertEquals("20.08.2026 Analysis", seg("20.08.2026 Analysis"))
    }

    @Test
    fun `verbotene zeichen werden ersetzt`() {
        assertEquals("f(x) - g(x)", seg("f(x) / g(x)"))
        assertEquals("Aufgabe 3-4", seg("Aufgabe 3:4"))
        assertTrue(seg("a*b?c<d>e|f").none { it in "*?<>|" })
    }

    @Test
    fun `leerzeichen am rand verschwinden`() {
        assertEquals("Kettenregel", seg("  Kettenregel  "))
    }

    @Test
    fun `ein titel aus lauter punkten ergibt trotzdem einen namen`() {
        // Sonst entstünde ein leerer Pfadabschnitt und der Upload liefe ins Leere.
        assertEquals("Seite", seg("..."))
        assertEquals("Seite", seg("   "))
    }

    @Test
    fun `umlaute bleiben erhalten`() {
        assertEquals("Größen und Maße", seg("Größen und Maße"))
    }
}

/**
 * Titel sind nicht eindeutig — zwei Seiten „Übung" in einem Abschnitt sind
 * völlig normal. Ohne Unterscheidung landeten sie im selben Serverordner und
 * überschrieben sich gegenseitig; beim Zurückholen entstanden Doppelgänger.
 */
class UniqueSegmentTest {

    @Test
    fun `gleiche titel bekommen unterscheidbare ordner`() {
        val used = mutableSetOf<String>()
        val a = Page(id = "aaaaaaaa-1111", title = "Übung")
        val b = Page(id = "bbbbbbbb-2222", title = "Übung")

        val nameA = SkriboSync.uniqueSegment(a, used)
        val nameB = SkriboSync.uniqueSegment(b, used)

        assertEquals("Übung", nameA)
        assertTrue(nameA != nameB, "Zweite Seite braucht einen eigenen Ordner")
        assertTrue("aaaaaa" !in nameA, "Die erste bleibt schlicht lesbar")
        assertTrue("bbbbbb" in nameB, "Die zweite wird über ihre Kennung unterschieden")
    }

    @Test
    fun `verschiedene titel bleiben unangetastet`() {
        val used = mutableSetOf<String>()
        assertEquals("Einstieg", SkriboSync.uniqueSegment(Page(title = "Einstieg"), used))
        assertEquals("Vertiefung", SkriboSync.uniqueSegment(Page(title = "Vertiefung"), used))
    }
}
