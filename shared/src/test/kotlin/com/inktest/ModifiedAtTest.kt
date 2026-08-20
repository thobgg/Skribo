package com.inktest

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Zeitstempel entscheidet beim Abgleich, welche Fassung gewinnt.
 *
 * Anlass war echter Datenverlust: Der Desktop hatte an einer Seite nichts
 * geändert, glich ab — und überschrieb damit sechs Striche, die inzwischen am
 * Tablet entstanden waren. „Erst senden, dann holen" allein reicht nicht.
 */
class ModifiedAtTest {

    private fun stroke() = Stroke(smoothingFactor = 0f).apply { addPoint(1f, 2f); addPoint(3f, 4f) }

    @Test
    fun `jede bearbeitung setzt den zeitstempel`() {
        val page = Page(title = "Seite")
        assertEquals(0L, page.modifiedAt)

        page.addStroke(stroke())

        assertTrue(page.modifiedAt > 0L, "Zeichnen muss den Zeitstempel setzen")
    }

    @Test
    fun `auch rueckgaengig gilt als aenderung`() {
        // Sonst wäre eine zurückgenommene Änderung für den Abgleich unsichtbar.
        val page = Page(title = "Seite")
        page.addStroke(stroke())
        page.touch(1000L)

        page.undo()

        assertTrue(page.modifiedAt > 1000L)
    }

    @Test
    fun `der zeitstempel ueberlebt den json-zyklus`() {
        val page = Page(title = "Seite")
        page.touch(1_700_000_000_000L)
        page.addStroke(stroke())
        page.touch(1_700_000_000_000L)

        val base = Page.fromJson(JSONObject(page.toBaseJson().toString()))
        assertEquals(1_700_000_000_000L, base.modifiedAt)

        val annotations = JSONObject(page.toAnnotationsJson("25-26").toString())
        assertEquals(1_700_000_000_000L, annotations.getLong("modifiedAt"))
    }

    @Test
    fun `annotationen setzen den zeitstempel beim laden mit`() {
        val quelle = Page(title = "Seite")
        quelle.addStroke(stroke())
        quelle.touch(1_800_000_000_000L)

        val ziel = Page(title = "Seite")
        ziel.applyAnnotations(JSONObject(quelle.toAnnotationsJson("25-26").toString()))

        assertEquals(1_800_000_000_000L, ziel.modifiedAt)
        assertEquals(1, ziel.strokes.size)
    }

    @Test
    fun `eine seite ohne zeitstempel gilt als aeltestmoeglich`() {
        // Dokumente aus der Zeit vor den Zeitstempeln dürfen nichts überschreiben.
        val alt = Page.fromJson(
            JSONObject("""{"id":"p","title":"Alt","paperStyle":"BLANK","textBoxes":[],"imageBoxes":[]}""")
        )
        assertEquals(0L, alt.modifiedAt)
    }
}
