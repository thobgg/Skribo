package com.inktest

import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent

class MetricsTracker {
    private val windowMs = 1000L
    private val eventTimes = ArrayDeque<Long>()
    private val historySizes = ArrayDeque<Int>()
    private val frameTimes = ArrayDeque<Long>()

    var eventsPerSecond: Double = 0.0
        private set
    var avgHistorySize: Double = 0.0
        private set
    var lastPointerType: String = "NONE"
        private set
    var fps: Double = 0.0
        private set
    var downToFirstDrawMs: Long = -1L
        private set
    var strokeCount: Int = 0
        private set

    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var downTs: Long = 0L
    private var firstDrawSeen = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val now = SystemClock.uptimeMillis()
            frameTimes.addLast(now)
            while (frameTimes.isNotEmpty() && now - frameTimes.first() > windowMs) {
                frameTimes.removeFirst()
            }
            fps = frameTimes.size.toDouble()
            if (running) choreographer.postFrameCallback(this)
        }
    }

    fun record(event: MotionEvent) {
        val now = System.currentTimeMillis()
        eventTimes.addLast(now)
        historySizes.addLast(event.historySize)

        while (eventTimes.isNotEmpty() && now - eventTimes.first() > windowMs) {
            eventTimes.removeFirst()
            historySizes.removeFirst()
        }

        eventsPerSecond = eventTimes.size.toDouble()
        avgHistorySize = if (historySizes.isNotEmpty()) {
            historySizes.sum().toDouble() / historySizes.size
        } else 0.0

        lastPointerType = when (event.getToolType(0)) {
            MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
            MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
            MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
            MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
            else -> "UNKNOWN"
        }
    }

    fun onStrokeStart() {
        downTs = SystemClock.uptimeMillis()
        firstDrawSeen = false
        if (!running) {
            running = true
            frameTimes.clear()
            choreographer.postFrameCallback(frameCallback)
        }
    }

    fun onDraw() {
        if (!firstDrawSeen && downTs != 0L) {
            firstDrawSeen = true
            downToFirstDrawMs = SystemClock.uptimeMillis() - downTs
        }
    }

    fun onStrokeEnd(committed: Boolean) {
        if (committed) strokeCount++
        running = false
    }

    fun reset() {
        eventTimes.clear()
        historySizes.clear()
        frameTimes.clear()
        eventsPerSecond = 0.0
        avgHistorySize = 0.0
        lastPointerType = "NONE"
        fps = 0.0
        downToFirstDrawMs = -1L
        strokeCount = 0
        running = false
    }
}
