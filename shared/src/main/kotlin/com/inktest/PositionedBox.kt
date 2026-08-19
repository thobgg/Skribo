package com.inktest

/**
 * Gemeinsame Grundlage aller frei platzierten Seitenelemente (Text, Bild,
 * Verweis). Koordinaten sind Seitenpunkte relativ zur linken oberen Ecke —
 * siehe [PageFormat].
 *
 * Damit kommt das Verschieben mit *einer* Aktion für alle Elementarten aus.
 */
interface PositionedBox {
    var x: Float
    var y: Float
}

/**
 * Verschiebt ein Element. Beim Ziehen wird die Position laufend direkt
 * verändert; erst beim Loslassen entsteht diese Aktion — sonst läge nach einer
 * Zugbewegung ein Undo-Schritt je Mausbewegung auf dem Stapel.
 */
class MoveBox(
    private val box: PositionedBox,
    private val oldX: Float,
    private val oldY: Float,
    private val newX: Float,
    private val newY: Float,
) : EditAction {
    override fun redo(page: Page) {
        box.x = newX
        box.y = newY
    }

    override fun undo(page: Page) {
        box.x = oldX
        box.y = oldY
    }
}

/** Ändert Position und Größe eines Bildes (Ziehen an einem Eckgriff). */
class ResizeImageBox(
    private val box: ImageBox,
    private val oldX: Float,
    private val oldY: Float,
    private val oldWidth: Float,
    private val oldHeight: Float,
    private val newX: Float,
    private val newY: Float,
    private val newWidth: Float,
    private val newHeight: Float,
) : EditAction {
    override fun redo(page: Page) {
        box.x = newX
        box.y = newY
        box.width = newWidth
        box.height = newHeight
    }

    override fun undo(page: Page) {
        box.x = oldX
        box.y = oldY
        box.width = oldWidth
        box.height = oldHeight
    }
}
