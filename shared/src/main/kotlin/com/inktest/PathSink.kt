package com.inktest

/**
 * Ziel für die Kurven, die [Stroke.buildPath] erzeugt — die Glättungsmathematik
 * (Bézier / Catmull-Rom / WMA) bleibt dadurch plattformfrei und wird von Board
 * und Desktop geteilt.
 *
 * Android füllt damit ein `android.graphics.Path`, der Desktop-Client später
 * einen Compose-/Skia-Pfad. Implementierungen sollten wiederverwendet statt
 * pro Frame neu angelegt werden: `buildPath` läuft im latenzkritischen Ink-Pfad.
 */
interface PathSink {
    /** Verwirft den bisher aufgebauten Pfad. */
    fun rewind()

    fun moveTo(x: Float, y: Float)

    fun lineTo(x: Float, y: Float)

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float)

    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float)
}
