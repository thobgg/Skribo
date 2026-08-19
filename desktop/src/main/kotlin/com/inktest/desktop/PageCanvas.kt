package com.inktest.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.unit.dp
import com.inktest.Page
import com.inktest.PaperStyle
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint as SkiaPaint

/** Millimeter → Pixel bei angenommenen 96 dpi (Desktop-Referenz). */
private const val MM_TO_PX = 96f / 25.4f

/**
 * Zeigt eine Seite so an, wie sie am Board aussieht: Papierraster, die am Board
 * geschriebenen Striche und die Textfelder. Das Rendering der Striche läuft über
 * dieselbe Glättungsmathematik wie auf Android ([ComposePathSink]).
 *
 * Bewusst nur Anzeige — Handschrift entsteht am Board, der Desktop plant.
 */
@Composable
fun PageCanvas(page: Page?, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFFEFEFEF)).padding(24.dp)) {
        Canvas(Modifier.fillMaxSize().background(paperColor(page?.paperStyle))) {
            val p = page ?: return@Canvas
            drawPaper(p.paperStyle)
            drawStrokes(p)
            drawTextBoxes(p)
        }
    }
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
                drawLine(line, androidx.compose.ui.geometry.Offset(0f, y),
                    androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                y += step
            }
        }
        PaperStyle.GRID -> {
            val step = 5f * MM_TO_PX
            var x = step
            while (x < size.width) {
                drawLine(line, androidx.compose.ui.geometry.Offset(x, 0f),
                    androidx.compose.ui.geometry.Offset(x, size.height), 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(line, androidx.compose.ui.geometry.Offset(0f, y),
                    androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                y += step
            }
        }
        PaperStyle.DOTS -> {
            val step = 5f * MM_TO_PX
            var x = step
            while (x < size.width) {
                var y = step
                while (y < size.height) {
                    drawCircle(line, 1.5f, androidx.compose.ui.geometry.Offset(x, y))
                    y += step
                }
                x += step
            }
        }
    }
}

private fun DrawScope.drawStrokes(page: Page) {
    // Ein Sink für alle Striche — reset() pro Strich, keine Allokation je Frame.
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
    val typeface = FontMgr.default.legacyMakeTypeface("", FontStyle.NORMAL)
    page.textBoxes.forEach { tb ->
        val paint = SkiaPaint().apply { color = tb.color }
        val font = Font(typeface, tb.fontSize)
        drawContext.canvas.skiaCanvas.drawString(
            tb.content, tb.x, tb.y + tb.fontSize, font, paint,
        )
    }
}
