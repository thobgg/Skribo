package com.inktest.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Dateiauswahl über den AWT-Dialog — der greift auf jeder Plattform auf den
 * systemeigenen Dialog zurück und fühlt sich dadurch weniger fremd an als ein
 * nachgebauter Swing-Dialog.
 */
object FilePicker {

    fun openPdf(parent: Frame?): File? =
        open(parent, "PDF öffnen", listOf("pdf"))

    fun openImage(parent: Frame?): File? =
        open(parent, "Bild öffnen", listOf("png", "jpg", "jpeg", "gif", "bmp"))

    private fun open(parent: Frame?, title: String, extensions: List<String>): File? {
        val dialog = FileDialog(parent, title, FileDialog.LOAD).apply {
            // Nur unter Linux/GTK wirksam; Windows und macOS ignorieren den
            // Filter still, weshalb die Endung unten trotzdem geprüft wird.
            setFilenameFilter { _, name ->
                extensions.any { name.lowercase().endsWith(".$it") }
            }
            isMultipleMode = false
            isVisible = true
        }
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        val file = File(dir, name)
        return if (extensions.any { name.lowercase().endsWith(".$it") }) file else null
    }
}
