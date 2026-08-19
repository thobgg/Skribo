package com.inktest.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.inktest.LinkBox
import com.inktest.Page
import com.inktest.PageFormat
import com.inktest.PaperStyle
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

/** Was auf der Seite angeklickt wurde. */
sealed interface CanvasTarget {
    data class Text(val box: TextBox) : CanvasTarget
    data class Link(val box: LinkBox) : CanvasTarget
    /** Freie Stelle — Koordinaten in Seitenpunkten. */
    data class Empty(val x: Float, val y: Float) : CanvasTarget
}

/**
 * Zeigt eine Seite so, wie sie am Board aussieht: Hintergrund (gerenderte
 * PDF-Seite/Folie) oder Papierraster, darüber Striche, Texte und Links.
 *
 * Seiten mit Format werden **als Ganzes eingepasst** und behalten ihr
 * Seitenverhältnis. Alle Inhalte liegen in Seitenpunkten, deshalb sitzt eine am
 * Board geschriebene Annotation hier an derselben Stelle — unabhängig von der
 * Fenstergröße.
 */
@Composable
fun PageCanvas(
    page: Page?,
    revision: Int,
    backgroundLoader: (String) -> ImageBitmap?,
    onTarget: (CanvasTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color(0xFFEFEFEF)).padding(24.dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(page, revision) {
                    detectTapGestures { offset ->
                        val p = page ?: return@detectTapGestures
                        val layout = layoutFor(p, size.width.toFloat(), size.height.toFloat())
                        val pt = layout.toPage(offset) ?: return@detectTapGestures
                        onTarget(hitTest(p, pt))
                    }
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
                    drawStrokes(p)
                    drawTextBoxes(p)
                    drawLinkBoxes(p)
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

private fun hitTest(page: Page, pt: Offset): CanvasTarget {
    // Von oben nach unten: zuletzt Angelegtes liegt oben.
    page.linkBoxes.lastOrNull { linkBounds(it).contains(pt) }?.let { return CanvasTarget.Link(it) }
    page.textBoxes.lastOrNull { textBounds(it).contains(pt) }?.let { return CanvasTarget.Text(it) }
    return CanvasTarget.Empty(pt.x, pt.y)
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

private const val LINK_FONT_SIZE = 15f
private const val LINK_PADDING = 8f
/** Platz links für das Abspiel-Dreieck. */
private const val LINK_TEXT_LEFT = 22f

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
            path = androidx.compose.ui.graphics.Path().apply {
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
