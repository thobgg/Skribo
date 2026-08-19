package com.inktest

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sammelt die PathSink-Aufrufe als Text — so lässt sich die Kurvenform prüfen. */
private class RecordingSink : PathSink {
    val ops = mutableListOf<String>()
    override fun rewind() { ops += "rewind" }
    override fun moveTo(x: Float, y: Float) { ops += "moveTo" }
    override fun lineTo(x: Float, y: Float) { ops += "lineTo" }
    override fun quadTo(cx: Float, cy: Float, x: Float, y: Float) { ops += "quadTo" }
    override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
        ops += "cubicTo"
    }
}

class StrokeTest {

    @Test
    fun `json roundtrip erhaelt punkte und metadaten`() {
        val s = Stroke(color = 0xFFD13438.toInt(), width = 7.5f, smoothingFactor = 0f,
            algo = SmoothingAlgo.CATMULL_ROM)
        s.addPoint(10f, 20f)
        s.addPoint(30f, 40f)
        s.addPoint(50f, 60f)

        val back = Stroke.fromJson(JSONObject(s.toJson().toString()))

        assertEquals(s.color, back.color)
        assertEquals(s.width, back.width)
        assertEquals(s.algo, back.algo)
        assertEquals(s.size, back.size)
        for (i in 0 until s.size) {
            assertEquals(s.x(i), back.x(i), "x[$i]")
            assertEquals(s.y(i), back.y(i), "y[$i]")
        }
    }

    @Test
    fun `leerer strich verwirft den bisherigen pfad`() {
        // Ohne das rewind bliebe beim Seitenwechsel der alte Strich stehen.
        val sink = RecordingSink()
        Stroke().buildPath(sink)
        assertEquals(listOf("rewind"), sink.ops)
    }

    @Test
    fun `jeder glaettungsalgorithmus erzeugt eine kurve`() {
        for (algo in SmoothingAlgo.entries) {
            val s = Stroke(smoothingFactor = 0f, algo = algo)
            repeat(6) { i -> s.addPoint(i * 10f, i * i * 2f) }
            val sink = RecordingSink()
            s.buildPath(sink)
            assertEquals("rewind", sink.ops.first(), "$algo")
            assertEquals("moveTo", sink.ops[1], "$algo")
            assertTrue(sink.ops.size > 2, "$algo erzeugt keine Segmente")
        }
    }

    @Test
    fun `bounding box umschliesst alle punkte`() {
        val s = Stroke(smoothingFactor = 0f)
        s.addPoint(30f, 5f)
        s.addPoint(-10f, 80f)
        assertEquals(-10f, s.minX)
        assertEquals(5f, s.minY)
        assertEquals(30f, s.maxX)
        assertEquals(80f, s.maxY)
    }

    @Test
    fun `glaettung zieht punkte zum vorgaenger`() {
        val raw = Stroke(smoothingFactor = 0f)
        val smooth = Stroke(smoothingFactor = 1f)
        raw.addPoint(0f, 0f); smooth.addPoint(0f, 0f)
        raw.addPoint(100f, 0f); smooth.addPoint(100f, 0f)

        assertEquals(100f, raw.x(1))
        assertTrue(smooth.x(1) < raw.x(1), "geglätteter Punkt muss näher am Vorgänger liegen")
    }
}
