package com.inktest.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.inktest.ImageBox
import com.inktest.LinkBox
import com.inktest.Page
import com.inktest.PageFormat
import com.inktest.PaperStyle
import com.inktest.PositionedBox
import com.inktest.TextBox
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint as SkiaPaint
import org.jetbrains.skia.Typeface
import kotlin.math.min

/** Millimeter → Punkt (Seitenkoordinaten sind Punkt, 1/72 Zoll). */
private const val MM_TO_PT = 72f / 25.4f

private val defaultTypeface: Typeface by lazy {
    FontMgr.default.legacyMakeTypeface("", FontStyle.NORMAL) ?: Typeface.makeEmpty()
}

/**
 * Zeigt eine Seite so, wie sie am Board aussieht: Hintergrund (gerenderte
 * PDF-Seite/Folie) oder Papierraster, darüber Bilder, Striche, Texte und Links.
 *
 * Seiten mit Format werden **als Ganzes eingepasst** und behalten ihr
 * Seitenverhältnis. Alle Inhalte liegen in Seitenpunkten, deshalb sitzt eine am
 * Board geschriebene Annotation hier an derselben Stelle — unabhängig von der
 * Fenstergröße.
 *
 * Bedienung: Klick wählt aus, Ziehen verschiebt, der Griff unten rechts an
 * einem ausgewählten Bild ändert die Größe. Ein Klick ins Leere legt Text an.
 */
@Composable
fun PageCanvas(
    page: Page?,
    revision: Int,
    selected: PositionedBox?,
    controller: DocumentController,
    backgroundLoader: (String) -> ImageBitmap?,
    onSelect: (PositionedBox?) -> Unit,
    onOpen: (PositionedBox) -> Unit,
    onEmptyClick: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color(0xFFEFEFEF)).padding(24.dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Bewusst NUR auf `page` gekeyed: käme `revision` dazu, würde
                // jede Positionsänderung den Erkenner neu starten und die
                // laufende Zugbewegung abbrechen.
                .pointerInput(page) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val p = page ?: return@detectTapGestures
                            val layout = layoutFor(p, size.width.toFloat(), size.height.toFloat())
                            val pt = layout.toPage(offset) ?: return@detectTapGestures
                            hitBox(p, pt)?.let(onOpen)
                        },
                        onTap = { offset ->
                            val p = page ?: return@detectTapGestures
                            val layout = layoutFor(p, size.width.toFloat(), size.height.toFloat())
                            val pt = layout.toPage(offset) ?: return@detectTapGestures
                            val hit = hitBox(p, pt)
                            if (hit != null) onSelect(hit)
                            else {
                                onSelect(null)
                                onEmptyClick(pt.x, pt.y)
                            }
                        },
                    )
                }
                .pointerInput(page) {
                    var target: PositionedBox? = null
                    var resizing = false
                    var startX = 0f
                    var startY = 0f
                    var startWidth = 0f
                    var startHeight = 0f

                    detectDragGestures(
                        onDragStart = { offset ->
                            val p = page ?: return@detectDragGestures
                            val layout = layoutFor(p, size.width.toFloat(), size.height.toFloat())
                            val pt = layout.toPage(offset) ?: return@detectDragGestures

                            val sel = selected
                            resizing = sel is ImageBox && resizeHandle(sel, layout).contains(pt)
                            target = if (resizing) sel else hitBox(p, pt)

                            target?.let { box ->
                                startX = box.x
                                startY = box.y
                                if (box is ImageBox) {
                                    startWidth = box.width
                                    startHeight = box.height
                                }
                                if (!resizing) onSelect(box)
                            }
                        },
                        onDrag = { change, amount ->
                            val p = page ?: return@detectDragGestures
                            val box = target ?: return@detectDragGestures
                            change.consume()
                            val layout = layoutFor(p, size.width.toFloat(), size.height.toFloat())
                            val dx = amount.x / layout.scale
                            val dy = amount.y / layout.scale
                            if (resizing && box is ImageBox) {
                                controller.dragImageSize(
                                    box, box.x, box.y, box.width + dx, box.height + dy,
                                )
                            } else {
                                controller.dragBoxTo(box, box.x + dx, box.y + dy)
                            }
                        },
                        onDragEnd = {
                            val p = page
                            val box = target
                            if (p != null && box != null) {
                                if (resizing && box is ImageBox) {
                                    controller.commitImageResize(
                                        p, box, startX, startY, startWidth, startHeight,
                                    )
                                } else {
                                    controller.commitMove(p, box, startX, startY)
                                }
                            }
                            target = null
                            resizing = false
                        },
                        onDragCancel = {
                            target = null
                            resizing = false
                        },
                    )
                }
        ) {
            @Suppress("UNUSED_EXPRESSION") revision
            val p = page ?: return@Canvas
            val layout = layoutFor(p, size.width, size.height)

            drawRect(
                color = paperColor(p),
                topLeft = layout.origin,
                size = layout.sizePx,
            )

            // Innerhalb des Seitenrechtecks in Seitenkoordinaten zeichnen:
            // verschieben auf die Seitenecke, dann Punkt → Pixel skalieren.
            translate(layout.origin.x, layout.origin.y) {
                scale(layout.scale, Offset.Zero) {
                    val bg = p.background?.let { backgroundLoader(it.assetPath) }
                    if (bg != null) drawBackground(bg, layout) else drawPaper(p.paperStyle, layout)
                    drawImageBoxes(p, backgroundLoader)
                    drawStrokes(p)
                    drawTextBoxes(p)
                    drawLinkBoxes(p)
                    selected?.let { drawSelection(it, layout) }
                }
            }
        }
    }
}

// ---------------- Seitengeometrie ----------------

/**
 * Lage und Maßstab der Seite auf der Zeichenfläche. [scale] rechnet
 * Seitenpunkte in Pixel um.
 */
private class PageLayout(
    val origin: Offset,
    val sizePx: Size,
    val scale: Float,
    val widthPt: Float,
    val heightPt: Float,
) {
    /** Pixel-Koordinate → Seitenpunkt; null außerhalb der Seite. */
    fun toPage(p: Offset): Offset? {
        val x = (p.x - origin.x) / scale
        val y = (p.y - origin.y) / scale
        if (x < 0f || y < 0f || x > widthPt || y > heightPt) return null
        return Offset(x, y)
    }
}

/**
 * Passt eine Seite mit Format vollständig in die Fläche ein (mittig, Verhältnis
 * gewahrt). Der freie Canvas nutzt die Fläche unskaliert — dort sind
 * Seitenpunkte gleich Pixel wie in schemaVersion 1.
 */
private fun layoutFor(page: Page, availW: Float, availH: Float): PageLayout {
    if (!page.format.isBounded) {
        return PageLayout(Offset.Zero, Size(availW, availH), 1f, availW, availH)
    }
    val wPt = page.format.widthPt
    val hPt = page.format.heightPt
    val scale = min(availW / wPt, availH / hPt)
    val w = wPt * scale
    val h = hPt * scale
    return PageLayout(
        origin = Offset((availW - w) / 2f, (availH - h) / 2f),
        sizePx = Size(w, h),
        scale = scale,
        widthPt = wPt,
        heightPt = hPt,
    )
}

// ---------------- Treffer und Umrisse ----------------

/** Von oben nach unten prüfen: zuletzt Gezeichnetes liegt oben. */
private fun hitBox(page: Page, pt: Offset): PositionedBox? {
    page.linkBoxes.lastOrNull { linkBounds(it).contains(pt) }?.let { return it }
    page.textBoxes.lastOrNull { textBounds(it).contains(pt) }?.let { return it }
    page.imageBoxes.lastOrNull { imageBounds(it).contains(pt) }?.let { return it }
    return null
}

private fun boundsOf(box: PositionedBox): Rect = when (box) {
    is TextBox -> textBounds(box)
    is ImageBox -> imageBounds(box)
    is LinkBox -> linkBounds(box)
    else -> Rect(box.x, box.y, box.x, box.y)
}

private fun textBounds(box: TextBox): Rect {
    val width = Font(defaultTypeface, box.fontSize).measureTextWidth(box.content)
    return Rect(
        left = box.x,
        top = box.y,
        right = box.x + width.coerceAtLeast(box.fontSize),
        bottom = box.y + box.fontSize * 1.3f,
    )
}

private fun imageBounds(box: ImageBox): Rect =
    Rect(box.x, box.y, box.x + box.width, box.y + box.height)

private fun linkBounds(box: LinkBox): Rect {
    val font = Font(defaultTypeface, LINK_FONT_SIZE)
    val width = font.measureTextWidth(box.label)
    return Rect(
        left = box.x,
        top = box.y,
        right = box.x + LINK_TEXT_LEFT + width + LINK_PADDING,
        bottom = box.y + LINK_FONT_SIZE * 1.6f,
    )
}

/**
 * Griff unten rechts zum Ändern der Bildgröße. Er wird in Bildschirmpixeln
 * bemessen und zurückgerechnet, damit er bei jedem Zoom gleich gut greifbar
 * bleibt.
 */
private fun resizeHandle(box: ImageBox, layout: PageLayout): Rect {
    val size = HANDLE_PX / layout.scale
    val b = imageBounds(box)
    return Rect(b.right - size, b.bottom - size, b.right + size, b.bottom + size)
}

private const val LINK_FONT_SIZE = 15f
private const val LINK_PADDING = 8f
/** Platz links für das Abspiel-Dreieck. */
private const val LINK_TEXT_LEFT = 22f
private const val HANDLE_PX = 7f

// ---------------- Zeichnen ----------------

private fun paperColor(page: Page): Color =
    if (page.paperStyle == PaperStyle.LEGAL && page.background == null) Color(0xFFFFFDE7)
    else Color.White

private fun DrawScope.drawBackground(image: ImageBitmap, layout: PageLayout) {
    // Das Bild füllt die Seite genau — es wurde aus genau dieser Seite gerendert.
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(layout.widthPt.toInt(), layout.heightPt.toInt()),
    )
}

private fun DrawScope.drawImageBoxes(page: Page, loader: (String) -> ImageBitmap?) {
    page.imageBoxes.forEach { ib ->
        val bmp = loader(ib.assetPath)
        if (bmp == null) {
            // Platzhalter statt Lücke — sonst wirkt die Seite kaputt.
            drawRect(
                color = Color(0xFFE0E0E0),
                topLeft = Offset(ib.x, ib.y),
                size = Size(ib.width, ib.height),
            )
            return@forEach
        }
        drawImage(
            image = bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset(ib.x.toInt(), ib.y.toInt()),
            dstSize = IntSize(ib.width.toInt(), ib.height.toInt()),
        )
    }
}

private fun DrawScope.drawPaper(style: PaperStyle, layout: PageLayout) {
    val line = Color(0xFFB0BEC5)
    val w = layout.widthPt
    val h = layout.heightPt
    when (style) {
        PaperStyle.BLANK -> Unit
        PaperStyle.LEGAL, PaperStyle.LINED -> {
            val step = 9f * MM_TO_PT
            var y = step
            while (y < h) {
                drawLine(line, Offset(0f, y), Offset(w, y), 1f)
                y += step
            }
        }
        PaperStyle.GRID -> {
            val step = 5f * MM_TO_PT
            var x = step
            while (x < w) {
                drawLine(line, Offset(x, 0f), Offset(x, h), 1f)
                x += step
            }
            var y = step
            while (y < h) {
                drawLine(line, Offset(0f, y), Offset(w, y), 1f)
                y += step
            }
        }
        PaperStyle.DOTS -> {
            val step = 5f * MM_TO_PT
            var x = step
            while (x < w) {
                var y = step
                while (y < h) {
                    drawCircle(line, 1.5f, Offset(x, y))
                    y += step
                }
                x += step
            }
        }
    }
}

private fun DrawScope.drawStrokes(page: Page) {
    // Ein Sink für alle Striche — rewind() pro Strich, keine Allokation je Frame.
    val sink = ComposePathSink()
    page.strokes.forEach { stroke ->
        stroke.buildPath(sink)
        drawPath(
            path = sink.path,
            color = Color(stroke.color),
            style = DrawStroke(width = stroke.width),
        )
    }
}

private fun DrawScope.drawTextBoxes(page: Page) {
    page.textBoxes.forEach { tb ->
        val paint = SkiaPaint().apply { color = tb.color }
        val font = Font(defaultTypeface, tb.fontSize)
        drawContext.canvas.skiaCanvas.drawString(
            tb.content, tb.x, tb.y + tb.fontSize, font, paint,
        )
    }
}

private fun DrawScope.drawLinkBoxes(page: Page) {
    page.linkBoxes.forEach { lb ->
        val bounds = linkBounds(lb)
        val isVideo = lb.youtubeId() != null
        val accentArgb = if (isVideo) 0xFFC62828.toInt() else 0xFF1565C0.toInt()
        val accent = Color(accentArgb)

        // Videos sind Verweise, keine Player — deshalb als Chip statt als Rahmen.
        drawRect(
            color = if (isVideo) Color(0xFFFFEBEE) else Color(0xFFE3F2FD),
            topLeft = Offset(bounds.left, bounds.top),
            size = Size(bounds.width, bounds.height),
        )

        // Abspiel-Dreieck selbst zeichnen: ein ▶ aus der Schrift fehlt je nach
        // System und erscheint dann als leeres Kästchen.
        val cy = bounds.top + bounds.height / 2f
        val h = LINK_FONT_SIZE * 0.55f
        val left = bounds.left + LINK_PADDING
        drawPath(
            path = Path().apply {
                moveTo(left, cy - h / 2f)
                lineTo(left + h * 0.9f, cy)
                lineTo(left, cy + h / 2f)
                close()
            },
            color = accent,
        )

        val font = Font(defaultTypeface, LINK_FONT_SIZE)
        drawContext.canvas.skiaCanvas.drawString(
            lb.label,
            bounds.left + LINK_TEXT_LEFT,
            bounds.top + LINK_FONT_SIZE * 1.15f,
            font,
            SkiaPaint().apply { color = accentArgb },
        )
    }
}

/** Auswahlrahmen; bei Bildern zusätzlich der Griff zum Vergrößern. */
private fun DrawScope.drawSelection(box: PositionedBox, layout: PageLayout) {
    val b = boundsOf(box).inflate(3f)
    val accent = Color(0xFF1565C0)
    drawRect(
        color = accent,
        topLeft = Offset(b.left, b.top),
        size = Size(b.width, b.height),
        style = DrawStroke(
            width = 1.5f / layout.scale,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(6f / layout.scale, 4f / layout.scale)
            ),
        ),
    )
    if (box is ImageBox) {
        val h = resizeHandle(box, layout)
        drawRect(
            color = accent,
            topLeft = Offset(h.left, h.top),
            size = Size(h.width, h.height),
        )
    }
}
