package com.inktest.desktop

import com.inktest.Document
import com.inktest.ImageBox
import com.inktest.PositionedBox
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

private const val YEAR = "25-26"

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

        val reloaded = DocumentStore(dir).load(YEAR)
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
            store.load(YEAR), repo, DesktopPrefs(File(dir, "desktop.properties")),
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
    fun `eine zugbewegung ergibt genau einen undo-schritt`() {
        val (c, _) = controller()
        val page = c.activePage!!
        c.addTextBox(page, 100f, 100f, "Merksatz")
        val box = page.textBoxes.single()

        // So läuft es beim Ziehen: viele kleine Schritte, am Ende ein Commit.
        val startX = box.x
        val startY = box.y
        repeat(20) { c.dragBoxTo(box, box.x + 5f, box.y + 2f) }
        c.commitMove(page, box, startX, startY)

        assertEquals(200f, box.x)
        assertEquals(140f, box.y)

        // Ein einziges Undo muss die ganze Bewegung zurücknehmen.
        c.undo(page)
        assertEquals(100f, box.x)
        assertEquals(100f, box.y)

        c.redo(page)
        assertEquals(200f, box.x)
    }

    @Test
    fun `zugbewegung ohne ortsaenderung erzeugt keinen undo-schritt`() {
        val (c, _) = controller()
        val page = c.activePage!!
        c.addTextBox(page, 50f, 50f, "Text")
        val box = page.textBoxes.single()

        c.commitMove(page, box, box.x, box.y)

        // Nur das Anlegen darf auf dem Stapel liegen; ein Undo entfernt den Text.
        c.undo(page)
        assertTrue(page.textBoxes.isEmpty())
    }

    @Test
    fun `bildgroesse aendern ist als ein schritt rueckgaengig-machbar`() {
        val (c, _) = controller()
        val page = c.activePage!!
        val box = ImageBox(x = 10f, y = 20f, width = 200f, height = 100f, assetPath = "assets/a.png")
        c.addImageBox(page, box)

        val (sx, sy, sw, sh) = listOf(box.x, box.y, box.width, box.height)
        repeat(10) { c.dragImageSize(box, box.x, box.y, box.width + 10f, box.height + 5f) }
        c.commitImageResize(page, box, sx, sy, sw, sh)

        assertEquals(300f, box.width)
        assertEquals(150f, box.height)

        c.undo(page)
        assertEquals(200f, box.width)
        assertEquals(100f, box.height)
    }

    @Test
    fun `bild laesst sich nicht unter die mindestgroesse schrumpfen`() {
        val (c, _) = controller()
        val box = ImageBox(x = 0f, y = 0f, width = 200f, height = 100f, assetPath = "assets/a.png")

        c.dragImageSize(box, 0f, 0f, -500f, -500f)

        assertTrue(box.width > 0f, "Breite muss positiv bleiben")
        assertTrue(box.height > 0f, "Höhe muss positiv bleiben")
    }

    @Test
    fun `verschieben gilt fuer text bild und link gleichermassen`() {
        val (c, _) = controller()
        val page = c.activePage!!
        c.addTextBox(page, 0f, 0f, "T")
        c.addLinkBox(page, 0f, 0f, "https://youtu.be/dQw4w9WgXcQ", "V")
        c.addImageBox(page, ImageBox(x = 0f, y = 0f, width = 10f, height = 10f, assetPath = "a"))

        val boxes = listOf<PositionedBox>(
            page.textBoxes.single(), page.linkBoxes.single(), page.imageBoxes.single(),
        )
        boxes.forEach { c.dragBoxTo(it, 42f, 43f) }

        boxes.forEach {
            assertEquals(42f, it.x)
            assertEquals(43f, it.y)
        }
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
