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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.inktest.Page
import com.inktest.PaperStyle
import com.inktest.TextBox
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint as SkiaPaint
import org.jetbrains.skia.Typeface

/** Millimeter → Pixel bei angenommenen 96 dpi (Desktop-Referenz). */
private const val MM_TO_PX = 96f / 25.4f

/** Systemschrift; makeDefault als Rückfall, falls der FontMgr nichts liefert. */
private val defaultTypeface: Typeface by lazy {
    FontMgr.default.legacyMakeTypeface("", FontStyle.NORMAL) ?: Typeface.makeEmpty()
}

/**
 * Zeigt eine Seite so an, wie sie am Board aussieht: Papierraster, die am Board
 * geschriebenen Striche und die Textfelder. Die Striche laufen durch dieselbe
 * Glättungsmathematik wie auf Android ([ComposePathSink]).
 *
 * Handschrift entsteht am Board — hier wird geplant. Ein Klick auf eine freie
 * Stelle legt daher ein Textfeld an, ein Klick auf ein bestehendes öffnet es.
 *
 * [revision] wird nur gelesen, damit Compose nach Änderungen am (selbst nicht
 * beobachtbaren) Modell neu zeichnet.
 */
@Composable
fun PageCanvas(
    page: Page?,
    revision: Int,
    onEmptyClick: (Float, Float) -> Unit,
    onTextClick: (TextBox) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color(0xFFEFEFEF)).padding(24.dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .background(paperColor(page?.paperStyle))
                .pointerInput(page, revision) {
                    detectTapGestures { offset ->
                        val p = page ?: return@detectTapGestures
                        val hit = p.textBoxes.lastOrNull { bounds(it).contains(offset) }
                        if (hit != null) onTextClick(hit) else onEmptyClick(offset.x, offset.y)
                    }
                }
        ) {
            @Suppress("UNUSED_EXPRESSION") revision
            val p = page ?: return@Canvas
            drawPaper(p.paperStyle)
            drawStrokes(p)
            drawTextBoxes(p)
        }
    }
}

/**
 * Klickfläche eines Textfelds. x/y ist die linke obere Ecke; gezeichnet wird auf
 * der Grundlinie bei y + fontSize, deshalb reicht das Rechteck genau so weit.
 */
private fun bounds(box: TextBox): Rect {
    val width = Font(defaultTypeface, box.fontSize).measureTextWidth(box.content)
    return Rect(
        left = box.x,
        top = box.y,
        right = box.x + width.coerceAtLeast(box.fontSize),
        bottom = box.y + box.fontSize * 1.3f,
    )
}

private fun paperColor(style: PaperStyle?): Color =
    if (style == PaperStyle.LEGAL) Color(0xFFFFFDE7) else Color.White

private fun DrawScope.drawPaper(style: PaperStyle) {
    val line = Color(0xFFB0BEC5)
    when (style) {
        PaperStyle.BLANK -> Unit
        PaperStyle.LEGAL, PaperStyle.LINED -> {
            val step = 9f * MM_TO_PX
            var y = step
            while (y < size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }
        PaperStyle.GRID -> {
            val step = 5f * MM_TO_PX
            var x = step
            while (x < size.width) {
                drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }
        PaperStyle.DOTS -> {
            val step = 5f * MM_TO_PX
            var x = step
            while (x < size.width) {
                var y = step
                while (y < size.height) {
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
    if (page.textBoxes.isEmpty()) return
    page.textBoxes.forEach { tb ->
        val paint = SkiaPaint().apply { color = tb.color }
        val font = Font(defaultTypeface, tb.fontSize)
        drawContext.canvas.skiaCanvas.drawString(
            tb.content, tb.x, tb.y + tb.fontSize, font, paint,
        )
    }
}
