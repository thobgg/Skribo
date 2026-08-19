package com.inktest.desktop

import com.inktest.ImageBox
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.min

/**
 * Kopiert ein Bild (jpg/png) in den Asset-Ordner und legt eine passende
 * [ImageBox] an. Die Datei wird bewusst kopiert statt verlinkt: sie muss mit
 * dem Dokument aufs Board synchronisiert werden.
 */
class ImageImporter(private val assetsDir: File) {

    fun import(source: File, maxWidthPt: Float = DEFAULT_MAX_WIDTH_PT): ImageBox {
        assetsDir.mkdirs()
        val image = ImageIO.read(source) ?: throw IOException("Kein lesbares Bildformat")

        val extension = source.extension.lowercase().ifEmpty { "png" }
        val assetId = UUID.randomUUID().toString()
        val target = File(assetsDir, "$assetId.$extension")
        source.copyTo(target, overwrite = true)

        // Auf eine brauchbare Größe bringen; das Seitenverhältnis bleibt erhalten.
        val scale = min(1f, maxWidthPt / image.width.toFloat())
        return ImageBox(
            x = MARGIN_PT,
            y = MARGIN_PT,
            width = image.width * scale,
            height = image.height * scale,
            assetPath = "assets/$assetId.$extension",
        )
    }

    private companion object {
        /** Gut die halbe A4-Breite — groß genug, ohne die Seite zu füllen. */
        const val DEFAULT_MAX_WIDTH_PT = 300f
        const val MARGIN_PT = 40f
    }
}
