package com.inktest.desktop

import androidx.compose.ui.graphics.Path
import com.inktest.PathSink

/**
 * Gegenstück zu `AndroidPathSink` auf der Desktop-Seite: füllt einen
 * Compose-/Skia-Pfad aus derselben Strich-Mathematik in `:shared`. Dadurch
 * sehen am Board gezeichnete Striche am PC exakt gleich aus — es gibt nur
 * eine Implementierung der Glättung.
 */
class ComposePathSink(val path: Path = Path()) : PathSink {
    override fun rewind() = path.reset()

    override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)

    override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)

    override fun quadTo(cx: Float, cy: Float, x: Float, y: Float) =
        path.quadraticTo(cx, cy, x, y)

    override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
        path.cubicTo(c1x, c1y, c2x, c2y, x, y)
}
