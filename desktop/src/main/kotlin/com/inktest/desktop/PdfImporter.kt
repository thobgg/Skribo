package com.inktest.desktop

import com.inktest.PageBackground
import com.inktest.PageFormat
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Importiert ein PDF als Seitenfolge („Ausdruck"): jede PDF-Seite wird zu einem
 * Bild gerendert und als Hintergrund einer eigenen Skribo-Seite abgelegt.
 *
 * Damit braucht das Board **keinen PDF-Renderer** — es sieht nur Bilder und
 * schreibt darüber. Die Handschrift landet in der jahresbezogenen
 * Annotationsebene, der Hintergrund bleibt als Vorlage unangetastet.
 */
class PdfImporter(private val assetsDir: File) {

    /** Eine gerenderte PDF-Seite, bereit als Skribo-Seite angelegt zu werden. */
    data class ImportedPage(
        val title: String,
        val format: PageFormat,
        val background: PageBackground,
    )

    /**
     * Rendert [pdf] und gibt eine [ImportedPage] je PDF-Seite zurück.
     * Wirft [java.io.IOException], wenn die Datei nicht lesbar ist.
     */
    fun import(pdf: File, dpi: Float = RENDER_DPI): List<ImportedPage> {
        assetsDir.mkdirs()
        val baseName = pdf.nameWithoutExtension
        return Loader.loadPDF(pdf).use { document ->
            val renderer = PDFRenderer(document)
            (0 until document.numberOfPages).map { index ->
                val image = renderer.renderImageWithDPI(index, dpi, ImageType.RGB)
                val assetId = UUID.randomUUID().toString()
                val target = File(assetsDir, "$assetId.png")
                ImageIO.write(image, "png", target)

                val mediaBox = document.getPage(index).mediaBox
                ImportedPage(
                    // Einseitige PDFs behalten schlicht ihren Dateinamen.
                    title = if (document.numberOfPages == 1) baseName
                    else "$baseName — S. ${index + 1}",
                    format = PageFormat.bestFit(mediaBox.width, mediaBox.height),
                    background = PageBackground(
                        assetPath = "assets/$assetId.png",
                        sourceName = pdf.name,
                        sourcePage = index + 1,
                    ),
                )
            }
        }
    }

    /** Anzahl Seiten, ohne zu rendern — für Rückfragen vor großen Dateien. */
    fun pageCount(pdf: File): Int = Loader.loadPDF(pdf).use { it.numberOfPages }

    companion object {
        /**
         * 150 dpi: am Board (meist 4K auf ~86") noch scharf, hält eine A4-Seite
         * aber bei rund 1240×1754 px — klein genug fürs Synchronisieren.
         */
        const val RENDER_DPI = 150f

        /**
         * Ab dieser Seitenzahl wird nachgefragt. Die Vorlagen im Unterricht sind
         * typischerweise wenige Seiten; ein 200-Seiten-Buch wäre ein Versehen.
         */
        const val LARGE_DOCUMENT_PAGES = 20
    }
}
