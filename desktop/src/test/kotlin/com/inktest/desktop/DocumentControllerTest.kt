package com.inktest.desktop

import com.inktest.Document
import com.inktest.DocumentStore
import com.inktest.PaperStyle
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DocumentControllerTest {

    private fun controller(): Pair<DocumentController, File> {
        val dir = Files.createTempDirectory("skribo-desktop-test").toFile()
        val repo = DesktopRepository(DocumentStore(dir))
        return DocumentController(Document.default(), repo) to dir
    }

    @Test
    fun `neuer abschnitt wird angelegt und aktiv`() {
        val (c, _) = controller()
        val before = c.document.sections.size

        c.addSection("Stochastik")

        assertEquals(before + 1, c.document.sections.size)
        assertEquals("Stochastik", c.activeSection?.name)
        // Ein leerer Abschnitt wäre eine Sackgasse — er startet mit einer Seite.
        assertEquals(1, c.activeSection?.pages?.size)
        assertSame(c.activeSection?.pages?.first(), c.activePage)
    }

    @Test
    fun `abschnitt loeschen setzt die auswahl auf den verbleibenden`() {
        val (c, _) = controller()
        val first = c.document.sections.first()
        c.addSection("Zweiter")

        c.deleteSection(c.activeSection!!)

        assertEquals(1, c.document.sections.size)
        assertSame(first, c.activeSection)
        assertNotNull(c.activePage)
    }

    @Test
    fun `letzten abschnitt loeschen laesst keine seite aktiv`() {
        val (c, _) = controller()

        c.deleteSection(c.document.sections.first())

        assertTrue(c.document.sections.isEmpty())
        assertNull(c.activeSection)
        assertNull(c.activePage)
    }

    @Test
    fun `unterseite haengt an der elternseite und steht direkt darunter`() {
        val (c, _) = controller()
        val parent = c.activePage!!
        c.addPage("Zweite Seite")

        c.addSubpage(parent, "Beispiel A")

        val pages = c.activeSection!!.pages
        val sub = pages.first { it.title == "Beispiel A" }
        assertEquals(parent.id, sub.parentId)
        assertEquals(pages.indexOf(parent) + 1, pages.indexOf(sub))
        assertSame(sub, c.activePage)
    }

    @Test
    fun `seite loeschen nimmt ihre unterseiten mit`() {
        val (c, _) = controller()
        val parent = c.activePage!!
        c.addSubpage(parent, "Unterseite 1")
        c.addSubpage(parent, "Unterseite 2")
        assertEquals(3, c.activeSection!!.pages.size)

        c.deletePage(parent)

        assertTrue(c.activeSection!!.pages.isEmpty())
        assertNull(c.activePage)
    }

    @Test
    fun `andere seite loeschen laesst die auswahl in ruhe`() {
        val (c, _) = controller()
        val keep = c.activePage!!
        c.addPage("Wegwerfseite")
        val throwaway = c.activePage!!
        c.selectPage(keep)

        c.deletePage(throwaway)

        assertSame(keep, c.activePage)
    }

    @Test
    fun `textfeld anlegen bearbeiten und rueckgaengig machen`() {
        val (c, _) = controller()
        val page = c.activePage!!

        c.addTextBox(page, 40f, 60f, "Hausaufgabe")
        val box = page.textBoxes.single()
        assertEquals("Hausaufgabe", box.content)

        c.editTextBox(page, box, "Hausaufgabe bis Freitag")
        assertEquals("Hausaufgabe bis Freitag", box.content)

        c.undo(page)
        assertEquals("Hausaufgabe", box.content)

        c.undo(page)
        assertTrue(page.textBoxes.isEmpty())

        c.redo(page)
        assertEquals("Hausaufgabe", page.textBoxes.single().content)
    }

    @Test
    fun `leerer webdav pfad bedeutet nur lokal`() {
        val (c, _) = controller()
        val section = c.activeSection!!

        c.setSectionWebdavPath(section, "  Schuljahr/Analysis  ")
        assertEquals("Schuljahr/Analysis", section.webdavPath)

        c.setSectionWebdavPath(section, "   ")
        assertNull(section.webdavPath)
    }

    @Test
    fun `bearbeitungen landen auf der platte und werden wieder geladen`() {
        val dir = Files.createTempDirectory("skribo-desktop-persist").toFile()
        val repo = DesktopRepository(DocumentStore(dir))
        val c = DocumentController(Document.default(), repo)

        c.addSection("Geometrie")
        val page = c.activePage!!
        c.renamePage(page, "Strahlensätze")
        c.setPaperStyle(page, PaperStyle.GRID)
        c.addTextBox(page, 10f, 20f, "Merksatz")
        c.flush()

        val reloaded = DocumentStore(dir).load()
        val section = reloaded.sections.first { it.name == "Geometrie" }
        val loadedPage = section.pages.single()
        assertEquals("Strahlensätze", loadedPage.title)
        assertEquals(PaperStyle.GRID, loadedPage.paperStyle)
        assertEquals("Merksatz", loadedPage.textBoxes.single().content)
    }

    @Test
    fun `app oeffnet wieder bei der zuletzt bearbeiteten seite`() {
        val dir = Files.createTempDirectory("skribo-desktop-prefs").toFile()
        val store = DocumentStore(dir)
        val repo = DesktopRepository(store)
        val prefs = DesktopPrefs(File(dir, "desktop.properties"))

        val first = DocumentController(Document.default(), repo, prefs)
        first.addSection("Stochastik")
        first.addPage("Bedingte Wahrscheinlichkeit")
        val expectedSection = first.activeSection!!.id
        val expectedPage = first.activePage!!.id
        first.flush()

        val reopened = DocumentController(
            store.load(), repo, DesktopPrefs(File(dir, "desktop.properties")),
        )

        assertEquals(expectedSection, reopened.activeSection?.id)
        assertEquals(expectedPage, reopened.activePage?.id)
    }

    @Test
    fun `verschwundene seite aus den einstellungen faellt auf die erste zurueck`() {
        val dir = Files.createTempDirectory("skribo-desktop-prefs2").toFile()
        val store = DocumentStore(dir)
        val prefs = DesktopPrefs(File(dir, "desktop.properties"))
        prefs.activeSectionId = "gibt-es-nicht"
        prefs.activePageId = "auch-nicht"

        val c = DocumentController(Document.default(), DesktopRepository(store), prefs)

        assertSame(c.document.sections.first(), c.activeSection)
        assertNotNull(c.activePage)
    }

    @Test
    fun `jede bearbeitung erhoeht die revision`() {
        // Ohne das würde Compose Modelländerungen nicht bemerken.
        val (c, _) = controller()
        val before = c.revision

        c.addPage("Neu")
        c.renamePage(c.activePage!!, "Umbenannt")

        assertEquals(before + 2, c.revision)
    }
}
