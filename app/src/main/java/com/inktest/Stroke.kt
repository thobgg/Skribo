package com.inktest

import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

enum class SmoothingAlgo { BEZIER, CATMULL_ROM, WMA }

class Stroke(
    val color: Int = Color.BLACK,
    val width: Float = 4f,
    val smoothingFactor: Float = 0.5f,
    val algo: SmoothingAlgo = SmoothingAlgo.BEZIER,
) {
    private var xs: FloatArray = FloatArray(INITIAL_CAPACITY)
    private var ys: FloatArray = FloatArray(INITIAL_CAPACITY)
    var size: Int = 0
        private set

    private val bbox = Rect()
    private var minX = Float.POSITIVE_INFINITY
    private var minY = Float.POSITIVE_INFINITY
    private var maxX = Float.NEGATIVE_INFINITY
    private var maxY = Float.NEGATIVE_INFINITY

    fun x(i: Int): Float = xs[i]
    fun y(i: Int): Float = ys[i]

    fun addPoint(rx: Float, ry: Float) {
        val sx: Float
        val sy: Float
        if (size == 0 || smoothingFactor <= 0f) {
            sx = rx
            sy = ry
        } else {
            // EMA prefilter: smoothingFactor=0 → raw, 1 → strong.
            val alpha = (1f - smoothingFactor * 0.85f).coerceIn(0.05f, 1f)
            val lx = xs[size - 1]
            val ly = ys[size - 1]
            sx = lx + alpha * (rx - lx)
            sy = ly + alpha * (ry - ly)
        }
        ensureCapacity(size + 1)
        xs[size] = sx
        ys[size] = sy
        size++

        if (sx < minX) minX = sx
        if (sx > maxX) maxX = sx
        if (sy < minY) minY = sy
        if (sy > maxY) maxY = sy
    }

    fun bounds(pad: Float, out: Rect = bbox): Rect {
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

    private fun ensureCapacity(min: Int) {
        if (min <= xs.size) return
        val newCap = maxOf(min, xs.size * 2)
        xs = xs.copyOf(newCap)
        ys = ys.copyOf(newCap)
    }

    fun buildPath(out: Path) {
        out.rewind()
        if (size == 0) return
        out.moveTo(xs[0], ys[0])
        if (size == 1) {
            out.lineTo(xs[0] + 0.01f, ys[0] + 0.01f)
            return
        }
        if (size == 2) {
            out.lineTo(xs[1], ys[1])
            return
        }
        when (algo) {
            SmoothingAlgo.BEZIER -> buildBezier(out)
            SmoothingAlgo.CATMULL_ROM -> buildCatmullRom(out)
            SmoothingAlgo.WMA -> buildWma(out)
        }
    }

    private fun buildBezier(out: Path) {
        for (i in 1 until size - 1) {
            val midX = (xs[i] + xs[i + 1]) * 0.5f
            val midY = (ys[i] + ys[i + 1]) * 0.5f
            out.quadTo(xs[i], ys[i], midX, midY)
        }
        out.lineTo(xs[size - 1], ys[size - 1])
    }

    private fun buildCatmullRom(out: Path) {
        // Uniform Catmull-Rom (τ = 0.5) expressed as cubic Bezier per segment:
        // for segment p1..p2, c1 = p1 + (p2 - p0)/6, c2 = p2 - (p3 - p1)/6.
        // Endpoints are duplicated so the curve still passes through the first
        // and last sample.
        for (i in 0 until size - 1) {
            val i0 = if (i - 1 < 0) i else i - 1
            val i3 = if (i + 2 >= size) i + 1 else i + 2
            val p0x = xs[i0]; val p0y = ys[i0]
            val p1x = xs[i];     val p1y = ys[i]
            val p2x = xs[i + 1]; val p2y = ys[i + 1]
            val p3x = xs[i3];    val p3y = ys[i3]
            val c1x = p1x + (p2x - p0x) / 6f
            val c1y = p1y + (p2y - p0y) / 6f
            val c2x = p2x - (p3x - p1x) / 6f
            val c2y = p2y - (p3y - p1y) / 6f
            out.cubicTo(c1x, c1y, c2x, c2y, p2x, p2y)
        }
    }

    private fun buildWma(out: Path) {
        // Trailing 4-tap WMA, linear weights [1,2,3,4]. For i < 3 the window
        // clamps to the first sample (so the curve still starts at p0).
        val w0 = 1f; val w1 = 2f; val w2 = 3f; val w3 = 4f
        val wsum = w0 + w1 + w2 + w3
        var i = 1
        while (i < size) {
            val i0 = maxOf(0, i - 3)
            val i1 = maxOf(0, i - 2)
            val i2 = maxOf(0, i - 1)
            val sx = (xs[i0] * w0 + xs[i1] * w1 + xs[i2] * w2 + xs[i] * w3) / wsum
            val sy = (ys[i0] * w0 + ys[i1] * w1 + ys[i2] * w2 + ys[i] * w3) / wsum
            out.lineTo(sx, sy)
            i++
        }
    }

    fun toJson(): JSONObject {
        val pts = JSONArray()
        for (i in 0 until size) {
            pts.put(xs[i].toDouble())
            pts.put(ys[i].toDouble())
        }
        return JSONObject().apply {
            put("color", color)
            put("width", width.toDouble())
            put("algo", algo.name)
            put("points", pts)
        }
    }

    companion object {
        const val INITIAL_CAPACITY = 128

        fun fromJson(j: JSONObject): Stroke {
            val color = j.getInt("color")
            val width = j.getDouble("width").toFloat()
            val algo = runCatching { SmoothingAlgo.valueOf(j.getString("algo")) }
                .getOrDefault(SmoothingAlgo.BEZIER)
            val pts = j.getJSONArray("points")
            val s = Stroke(color = color, width = width, smoothingFactor = 0f, algo = algo)
            var i = 0
            while (i + 1 < pts.length()) {
                s.addPoint(pts.getDouble(i).toFloat(), pts.getDouble(i + 1).toFloat())
                i += 2
            }
            return s
        }
    }
}
