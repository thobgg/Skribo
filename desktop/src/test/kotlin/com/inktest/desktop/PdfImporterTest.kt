package com.inktest.desktop

import com.inktest.Document
import com.inktest.DocumentStore
import com.inktest.PageFormat
import com.inktest.PaperStyle
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PdfImporterTest {

    private fun tempDir(prefix: String) = Files.createTempDirectory(prefix).toFile()

    /** Erzeugt ein PDF mit [pageCount] Seiten im gewünschten Format. */
    private fun makePdf(dir: File, name: String, pageCount: Int, box: PDRectangle): File {
        val file = File(dir, name)
        PDDocument().use { doc ->
            repeat(pageCount) { doc.addPage(PDPage(box)) }
            doc.save(file)
        }
        return file
    }

    @Test
    fun `jede pdf-seite wird zu einer eigenen seite mit hintergrund`() {
        val dir = tempDir("skribo-pdf")
        val assets = File(dir, "assets")
        val pdf = makePdf(dir, "Arbeitsblatt.pdf", 3, PDRectangle.A4)

        val pages = PdfImporter(assets).import(pdf, dpi = 36f)

        assertEquals(3, pages.size)
        pages.forEachIndexed { i, page ->
            assertEquals(PageFormat.A4_PORTRAIT, page.format)
            assertEquals(i + 1, page.background.sourcePage)
            assertEquals("Arbeitsblatt.pdf", page.background.sourceName)
            // Die gerenderte Datei muss wirklich existieren und lesbar sein.
            val asset = File(dir, page.background.assetPath)
            assertTrue(asset.exists(), "Asset fehlt: ${page.background.assetPath}")
            assertNotNull(ImageIO.read(asset))
        }
    }

    @Test
    fun `mehrseitiges pdf nummeriert die titel`() {
        val dir = tempDir("skribo-pdf-titles")
        val pdf = makePdf(dir, "Folien.pdf", 2, PDRectangle.A4)

        val pages = PdfImporter(File(dir, "assets")).import(pdf, dpi = 36f)

        assertEquals(listOf("Folien — S. 1", "Folien — S. 2"), pages.map { it.title })
    }

    @Test
    fun `einseitiges pdf behaelt schlicht seinen namen`() {
        val dir = tempDir("skribo-pdf-single")
        val pdf = makePdf(dir, "Hausaufgabe.pdf", 1, PDRectangle.A4)

        val pages = PdfImporter(File(dir, "assets")).import(pdf, dpi = 36f)

        assertEquals("Hausaufgabe", pages.single().title)
    }

    @Test
    fun `praesentationsfolie wird als folie erkannt nicht als a4 quer`() {
        val dir = tempDir("skribo-pdf-slide")
        // 16:9 in Punkt (etwa 33,87 × 19,05 cm — das PowerPoint-Standardformat).
        val slide = PDRectangle(960f, 540f)
        val pdf = makePdf(dir, "Praesentation.pdf", 1, slide)

        val pages = PdfImporter(File(dir, "assets")).import(pdf, dpi = 36f)

        assertEquals(PageFormat.SLIDE_16_9, pages.single().format)
    }

    @Test
    fun `querformat wird als a4 quer erkannt`() {
        val dir = tempDir("skribo-pdf-land")
        val pdf = makePdf(dir, "Quer.pdf", 1, PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width))

        val pages = PdfImporter(File(dir, "assets")).import(pdf, dpi = 36f)

        assertEquals(PageFormat.A4_LANDSCAPE, pages.single().format)
    }

    @Test
    fun `seitenzahl laesst sich ohne rendern bestimmen`() {
        val dir = tempDir("skribo-pdf-count")
        val pdf = makePdf(dir, "Buch.pdf", 12, PDRectangle.A4)

        assertEquals(12, PdfImporter(File(dir, "assets")).pageCount(pdf))
    }

    @Test
    fun `importierte seiten landen im dokument und ueberleben das speichern`() {
        val dir = tempDir("skribo-pdf-integration")
        val store = DocumentStore(dir)
        val repo = DesktopRepository(store)
        val c = DocumentController(Document.default(), repo)
        val pdf = makePdf(dir, "Vorlage.pdf", 2, PDRectangle.A4)

        val added = c.addImportedPages(PdfImporter(store.assetsDir).import(pdf, dpi = 36f))
        c.flush()

        assertEquals(2, added)
        val reloaded = DocumentStore(dir).load()
        val imported = reloaded.sections.first().pages.filter { it.background != null }
        assertEquals(2, imported.size)
        imported.forEach {
            assertEquals(PageFormat.A4_PORTRAIT, it.format)
            // Auf einer gerenderten Vorlage stört jedes Raster.
            assertEquals(PaperStyle.BLANK, it.paperStyle)
            assertEquals("Vorlage.pdf", it.background?.sourceName)
        }
    }

    @Test
    fun `importierte seiten haengen als unterseiten unter der aktiven seite`() {
        val dir = tempDir("skribo-pdf-nesting")
        val store = DocumentStore(dir)
        val c = DocumentController(Document.default(), DesktopRepository(store))
        val parent = c.activePage!!
        val pdf = makePdf(dir, "Anhang.pdf", 2, PDRectangle.A4)

        c.addImportedPages(PdfImporter(store.assetsDir).import(pdf, dpi = 36f))

        val subs = c.activeSection!!.pages.filter { it.parentId == parent.id }
        assertEquals(2, subs.size)
        assertEquals(listOf("Anhang — S. 1", "Anhang — S. 2"), subs.map { it.title })
    }
}
