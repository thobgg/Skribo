package com.inktest

import android.graphics.Path
import android.graphics.Rect

/**
 * Füllt ein `android.graphics.Path` aus der plattformfreien Strich-Mathematik
 * ([Stroke.buildPath]). Eine Instanz pro Pfad anlegen und wiederverwenden —
 * das läuft im latenzkritischen Ink-Pfad.
 */
class AndroidPathSink(val path: Path = Path()) : PathSink {
    override fun rewind() = path.rewind()

    override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)

    override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)

    override fun quadTo(cx: Float, cy: Float, x: Float, y: Float) = path.quadTo(cx, cy, x, y)

    override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
        path.cubicTo(c1x, c1y, c2x, c2y, x, y)
}

/**
 * Bounding-Box des Strichs als [Rect] — Grundlage der Damage-Rect-Invalidation.
 * Bei leerem Strich ein leeres Rect.
 */
fun Stroke.bounds(pad: Float, out: Rect): Rect {
    if (size == 0) {
        out.set(0, 0, 0, 0)
        return out
    }
    out.set(
        (minX - pad).toInt(),
        (minY - pad).toInt(),
        (maxX + pad).toInt() + 1,
        (maxY + pad).toInt() + 1,
    )
    return out
}
