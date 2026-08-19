package com.inktest.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.inktest.SkriboLog
import java.io.File
import javax.imageio.ImageIO

/**
 * Hält geladene Seitenhintergründe und Bilder im Speicher. Ohne Zwischenspeicher
 * würde jedes Neuzeichnen die PNG-Datei erneut dekodieren — bei einer
 * gerenderten A4-Seite spürbar.
 *
 * Fehlende oder kaputte Dateien liefern `null`; die Seite wird dann eben ohne
 * Hintergrund gezeigt, statt dass die App abbricht.
 */
class AssetCache(private val rootDir: File) {

    private val cache = mutableMapOf<String, ImageBitmap?>()

    /** [assetPath] ist relativ zum Dokumentwurzelverzeichnis, z. B. `assets/x.png`. */
    fun load(assetPath: String): ImageBitmap? = cache.getOrPut(assetPath) {
        val file = File(rootDir, assetPath)
        if (!file.exists()) {
            SkriboLog.w(TAG, "Asset fehlt: $assetPath")
            return@getOrPut null
        }
        runCatching { ImageIO.read(file)?.toComposeImageBitmap() }
            .onFailure { SkriboLog.w(TAG, "Asset unlesbar ($assetPath): $it") }
            .getOrNull()
    }

    /** Nach dem Ersetzen einer Datei aufrufen, damit nicht das alte Bild bleibt. */
    fun invalidate(assetPath: String) {
        cache.remove(assetPath)
    }

    private companion object {
        const val TAG = "AssetCache"
    }
}
