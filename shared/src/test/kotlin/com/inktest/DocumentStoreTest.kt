package com.inktest

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val YEAR = "25-26"

class DocumentStoreTest {

    private fun tempStore() = DocumentStore(Files.createTempDirectory("skribo-test").toFile())

    @Test
    fun `leeres verzeichnis liefert standarddokument`() {
        val doc = tempStore().load(YEAR)
        assertTrue(doc.allSections().isNotEmpty(), "Standarddokument braucht mindestens einen Abschnitt")
    }

    @Test
    fun `standarddokument gleicht sich von anfang an ab`() {
        // Sonst schreibt man auf einem frischen Gerät in den Standard-Abschnitt
        // und der Abgleich läuft still mit null Abschnitten — sah aus wie Erfolg.
        val section = Document.default().allSections().single()
        assertTrue(section.syncEnabled, "Der Standard-Abschnitt muss abgleichen")
        assertTrue(section.folderName != null, "Ordnername muss fest vergeben sein")
    }

    @Test
    fun `dokument und seite ueberleben einen speicher-lade-zyklus`() {
        val store = tempStore()
        val doc = Document.default()
        val section = doc.allSections().first()
        val page = section.pages.first()
        page.title = "Bruchrechnen"
        page.paperStyle = PaperStyle.GRID
        page.applyAction(AddTextBox(TextBox(x = 12f, y = 34f, content = "Hausaufgabe")))
        val stroke = Stroke(smoothingFactor = 0f).apply {
            addPoint(1f, 2f)
            addPoint(3f, 4f)
        }
        page.addStroke(stroke)

        store.writePage(page, YEAR)
        store.writeDocumentStructure(doc)

        val loadedPage = store.load(YEAR).allSections().first().pages.first { it.id == page.id }
        assertEquals("Bruchrechnen", loadedPage.title)
        assertEquals(PaperStyle.GRID, loadedPage.paperStyle)
        assertEquals(1, loadedPage.strokes.size)
        assertEquals(2, loadedPage.strokes.first().size)
        assertEquals("Hausaufgabe", loadedPage.textBoxes.single().content)
    }

    @Test
    fun `altes format ohne notizbuecher wird in ein standard-notizbuch migriert`() {
        val store = tempStore()
        // So sah document.json vor der Notizbuch-Ebene aus: Abschnitte direkt
        // im Dokument, Sync über einen festen Pfad je Abschnitt.
        java.io.File(store.rootDir, "document.json").writeText(
            """{"sections":[{"id":"s1","name":"Analysis 12","color":1,
               "webdavPath":"home/skribo-test/Analysis12","pageIds":[]}]}"""
        )

        val doc = store.load(YEAR)

        val nb = doc.notebooks.single()
        assertEquals(Notebook.DEFAULT_NAME, nb.name)
        val section = nb.sections.single()
        assertEquals("Analysis 12", section.name)
        // Der Altbestand gilt als „abgleichen an" und behält seinen festen Pfad.
        assertTrue(section.syncEnabled, "Abschnitt mit Pfad muss als syncEnabled migriert werden")
        assertEquals("home/skribo-test/Analysis12", section.webdavPath)
        // Ordnernamen sind ab jetzt fest vergeben.
        assertEquals("Analysis 12", section.folderName)
        assertEquals(Notebook.DEFAULT_NAME, nb.folderName)
    }

    @Test
    fun `gleichnamige abschnitte bekommen eindeutige ordnernamen`() {
        val store = tempStore()
        java.io.File(store.rootDir, "document.json").writeText(
            """{"sections":[
               {"id":"aaaaaaaa-1","name":"Übung","color":1,"pageIds":[]},
               {"id":"bbbbbbbb-2","name":"Übung","color":2,"pageIds":[]}]}"""
        )

        val sections = store.load(YEAR).allSections()

        assertEquals("Übung", sections[0].folderName)
        assertEquals("Übung (bbbbbb)", sections[1].folderName)
    }

    @Test
    fun `geloeschte seite kommt nicht zurueck`() {
        val store = tempStore()
        val doc = Document.default()
        val section = doc.allSections().first()
        val page = section.pages.first()
        store.writePage(page, YEAR)
        store.writeDocumentStructure(doc)

        store.deletePage(page)
        section.pages.remove(page)
        store.writeDocumentStructure(doc)

        assertTrue(store.load(YEAR).allSections().first().pages.none { it.id == page.id })
    }
}
