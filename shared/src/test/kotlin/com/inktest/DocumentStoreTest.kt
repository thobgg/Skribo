package com.inktest

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentStoreTest {

    private fun tempStore() = DocumentStore(Files.createTempDirectory("skribo-test").toFile())

    @Test
    fun `leeres verzeichnis liefert standarddokument`() {
        val doc = tempStore().load()
        assertTrue(doc.sections.isNotEmpty(), "Standarddokument braucht mindestens einen Abschnitt")
    }

    @Test
    fun `dokument und seite ueberleben einen speicher-lade-zyklus`() {
        val store = tempStore()
        val doc = Document.default()
        val section = doc.sections.first()
        val page = section.pages.first()
        page.title = "Bruchrechnen"
        page.paperStyle = PaperStyle.GRID
        page.applyAction(AddTextBox(TextBox(x = 12f, y = 34f, content = "Hausaufgabe")))
        val stroke = Stroke(smoothingFactor = 0f).apply {
            addPoint(1f, 2f)
            addPoint(3f, 4f)
        }
        page.addStroke(stroke)

        store.writePage(page)
        store.writeDocumentStructure(doc)

        val loadedPage = store.load().sections.first().pages.first { it.id == page.id }
        assertEquals("Bruchrechnen", loadedPage.title)
        assertEquals(PaperStyle.GRID, loadedPage.paperStyle)
        assertEquals(1, loadedPage.strokes.size)
        assertEquals(2, loadedPage.strokes.first().size)
        assertEquals("Hausaufgabe", loadedPage.textBoxes.single().content)
    }

    @Test
    fun `geloeschte seite kommt nicht zurueck`() {
        val store = tempStore()
        val doc = Document.default()
        val section = doc.sections.first()
        val page = section.pages.first()
        store.writePage(page)
        store.writeDocumentStructure(doc)

        store.deletePage(page)
        section.pages.remove(page)
        store.writeDocumentStructure(doc)

        assertTrue(store.load().sections.first().pages.none { it.id == page.id })
    }
}
