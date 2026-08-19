package com.inktest

import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchoolYearTest {

    @Test
    fun `schuljahr wechselt zum ersten august`() {
        assertEquals("24-25", SchoolYear.current(LocalDate.of(2025, 7, 31)))
        assertEquals("25-26", SchoolYear.current(LocalDate.of(2025, 8, 1)))
        assertEquals("25-26", SchoolYear.current(LocalDate.of(2026, 5, 20)))
        assertEquals("26-27", SchoolYear.current(LocalDate.of(2026, 8, 19)))
    }

    @Test
    fun `jahrhundertwechsel bleibt zweistellig`() {
        assertEquals("99-00", SchoolYear.current(LocalDate.of(1999, 9, 1)))
        assertEquals("00-01", SchoolYear.next("99-00"))
    }

    @Test
    fun `folgejahr und startjahr passen zusammen`() {
        assertEquals("26-27", SchoolYear.next("25-26"))
        assertEquals(2025, SchoolYear.startYearOf("25-26"))
    }
}

class AnnotationLayerTest {

    private fun store() = DocumentStore(Files.createTempDirectory("skribo-jahr").toFile())

    private fun strokeAt(x: Float) = Stroke(smoothingFactor = 0f).apply {
        addPoint(x, x)
        addPoint(x + 10f, x + 10f)
    }

    @Test
    fun `neues schuljahr startet mit leerer flaeche auf derselben vorlage`() {
        val s = store()
        val doc = Document.default()
        val page = doc.sections.first().pages.first()
        page.title = "Arbeitsblatt"
        page.format = PageFormat.A4_PORTRAIT
        page.background = PageBackground("assets/vorlage.png", "Arbeitsblatt.pdf", 1)
        page.addStroke(strokeAt(10f))
        s.writePage(page, "25-26")
        s.writeDocumentStructure(doc)

        val neuesJahr = s.load("26-27").sections.first().pages.first()

        // Vorlage steht, Handschrift des Vorjahres ist weg.
        assertEquals("Arbeitsblatt", neuesJahr.title)
        assertEquals(PageFormat.A4_PORTRAIT, neuesJahr.format)
        assertEquals("assets/vorlage.png", neuesJahr.background?.assetPath)
        assertTrue(neuesJahr.strokes.isEmpty(), "Das Vorjahr darf nicht durchschlagen")
    }

    @Test
    fun `das vorjahr bleibt nachschlagbar`() {
        val s = store()
        val doc = Document.default()
        val page = doc.sections.first().pages.first()
        page.addStroke(strokeAt(10f))
        s.writePage(page, "25-26")
        s.writeDocumentStructure(doc)

        // Im neuen Jahr etwas anderes schreiben …
        val neu = s.load("26-27").sections.first().pages.first()
        neu.addStroke(strokeAt(50f))
        neu.addStroke(strokeAt(60f))
        s.writePage(neu, "26-27")

        // … beide Jahre existieren nebeneinander.
        assertEquals(1, s.load("25-26").sections.first().pages.first().strokes.size)
        assertEquals(2, s.load("26-27").sections.first().pages.first().strokes.size)
        assertEquals(listOf("26-27", "25-26"), s.listYears())
    }

    @Test
    fun `die basis enthaelt keine striche`() {
        val page = Page(title = "Seite")
        page.applyAction(AddTextBox(TextBox(x = 1f, y = 2f, content = "bleibt")))
        page.addStroke(strokeAt(5f))

        val base = page.toBaseJson()

        assertTrue(!base.has("strokes"), "Striche gehören in die Jahresebene, nicht in die Basis")
        assertEquals(1, base.getJSONArray("textBoxes").length())
    }

    @Test
    fun `dokument aus der zeit vor den jahresebenen behaelt seine striche`() {
        val s = store()
        val doc = Document.default()
        val page = doc.sections.first().pages.first()
        page.addStroke(strokeAt(10f))
        // So sah es früher aus: alles in einer Datei, keine annotations/-Ablage.
        s.writeDocumentStructure(doc)
        java.io.File(s.rootDir, "pages/${page.id}.json").writeText(page.toJson().toString())

        val geladen = s.load("25-26").sections.first().pages.first()

        assertEquals(1, geladen.strokes.size, "Alte Striche dürfen nicht verlorengehen")

        // Und beim nächsten Speichern wandern sie in die Jahresebene.
        s.writePage(geladen, "25-26")
        assertTrue(java.io.File(s.rootDir, "annotations/25-26/${page.id}.json").exists())
    }

    @Test
    fun `seite loeschen raeumt alle jahresebenen mit ab`() {
        val s = store()
        val doc = Document.default()
        val page = doc.sections.first().pages.first()
        page.addStroke(strokeAt(1f))
        s.writePage(page, "24-25")
        s.writePage(page, "25-26")

        s.deletePage(page)

        assertTrue(!java.io.File(s.rootDir, "annotations/24-25/${page.id}.json").exists())
        assertTrue(!java.io.File(s.rootDir, "annotations/25-26/${page.id}.json").exists())
        assertTrue(!java.io.File(s.rootDir, "pages/${page.id}.json").exists())
    }

    @Test
    fun `nach dem jahreswechsel gilt die historie des vorjahres nicht mehr`() {
        val s = store()
        val doc = Document.default()
        val page = doc.sections.first().pages.first()
        page.addStroke(strokeAt(10f))
        s.writePage(page, "25-26")
        s.writeDocumentStructure(doc)

        val neu = s.load("26-27").sections.first().pages.first()

        // Sonst würde ein Undo Striche des Vorjahres zurückholen.
        assertTrue(!neu.canUndo(), "Die Historie muss beim Jahreswechsel zurückgesetzt sein")
    }
}
