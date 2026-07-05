package com.inktest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class InkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    // -------- Engine-Tunables (Einstellungen → Erweitert → Engine) --------
    var strokeWidth: Float = 4f
    var smoothingFactor: Float = 0.5f
    var strokeColor: Int = Color.BLACK
    var smoothingAlgo: SmoothingAlgo = SmoothingAlgo.BEZIER

    var predictionMode: PredictionMode = PredictionMode.OFF
    var useUnbufferedDispatch: Boolean = true

    /** Eingabe-Modus: Zeichnen (PEN) oder ganze Striche löschen (ERASER). */
    var tool: Tool = Tool.PEN

    var linedSpacingMm: Float = 9f
        set(v) {
            if (field == v) return
            field = v
            redrawAllCommitted()
            invalidate()
        }
    var gridSpacingMm: Float = 5f
        set(v) {
            if (field == v) return
            field = v
            redrawAllCommitted()
            invalidate()
        }
    var dotsSpacingMm: Float = 5f
        set(v) {
            if (field == v) return
            field = v
            redrawAllCommitted()
            invalidate()
        }

    var antialiased: Boolean = true
        set(v) {
            if (field == v) return
            field = v
            strokePaint.isAntiAlias = v
            redrawAllCommitted()
            invalidate()
        }

    var canvasClipping: Boolean = false
    var damageRectEnabled: Boolean = false

    var layerMode: LayerMode = LayerMode.NONE
        set(v) {
            if (field == v) return
            field = v
            when (v) {
                LayerMode.NONE -> setLayerType(LAYER_TYPE_NONE, null)
                LayerMode.HARDWARE -> setLayerType(LAYER_TYPE_HARDWARE, null)
                LayerMode.SOFTWARE -> setLayerType(LAYER_TYPE_SOFTWARE, null)
            }
            invalidate()
        }

    var bitmapMode: BitmapMode = BitmapMode.ARGB_8888
        set(v) {
            if (field == v) return
            field = v
            recreateBitmap()
            invalidate()
        }

    // -------- Seitenbezogener Zustand --------
    /**
     * Aktuell sichtbare Seite. Beim Setzen wird der Bitmap-Cache neu aufgebaut
     * (Hintergrund + alle Striche der neuen Seite). Auto-Save passiert über
     * den [onStrokeCommitted]-Callback — dieser Setter löst selbst keinen Save aus.
     */
    var page: Page? = null
        set(value) {
            if (field === value) return
            currentStroke = null
            predictedCount = 0
            field = value
            // Beim Page-Wechsel View zurücksetzen, sonst landet man irgendwo im Nirgendwo
            panX = 0f
            panY = 0f
            scale = 1f
            redrawAllCommitted()
            invalidate()
            onMetricsChanged?.invoke()
        }

    /** Ruft MainActivity → Repository.savePage nach jedem Commit/Undo/Redo/Clear. */
    var onStrokeCommitted: ((Page) -> Unit)? = null

    /** Tap-Callback fürs TEXT-Tool: existierende TextBox getroffen oder null + Welt-Koords. */
    var onTextBoxTap: ((TextBox?, Float, Float) -> Unit)? = null

    /** Tap-Callback fürs IMAGE-Tool: existierende ImageBox getroffen oder null + Welt-Koords. */
    var onImageBoxTap: ((ImageBox?, Float, Float) -> Unit)? = null

    val metrics: MetricsTracker = MetricsTracker()
    var onMetricsChanged: (() -> Unit)? = null

    val strokeCount: Int get() = metrics.strokeCount

    // -------- Preallocated hot-path objects --------
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val paperPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = false
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val imageBitmapCache = mutableMapOf<String, Bitmap>()
    /** Lookup-Function für ImageBox.assetPath → Bitmap. Wird von MainActivity gesetzt. */
    var imageLoader: ((String) -> Bitmap?)? = null
    private val reusablePath = Path()
    private val damageRectBuf = Rect()
    private val clipRectBuf = Rect()
    private val eraserHitBuf = Rect()
    private val predictedBuf = FloatArray(PREDICT_BUF_POINTS * 2)
    private var predictedCount = 0

    private var currentStroke: Stroke? = null
    private var committedBitmap: Bitmap? = null
    private var committedCanvas: Canvas? = null

    private val predictor: MotionEventPredictor by lazy {
        MotionEventPredictor.newInstance(this)
    }

    // -------- Pan / Zoom (unendlicher Canvas) --------
    /** Viewport-Translation in Pixeln (Bildschirm-Koordinaten). */
    var panX: Float = 0f
        private set
    var panY: Float = 0f
        private set
    /** Zoom-Faktor; 1.0 = 1:1, >1 = reingezoomt. */
    var scale: Float = 1f
        private set

    private val scaleDetector: ScaleGestureDetector by lazy {
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val factor = d.scaleFactor.coerceIn(0.5f, 2f)
                val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
                val effectiveFactor = newScale / scale
                if (effectiveFactor == 1f) return true
                // Zoom um den Fokuspunkt der Geste herum
                val fx = d.focusX
                val fy = d.focusY
                panX = fx - (fx - panX) * effectiveFactor
                panY = fy - (fy - panY) * effectiveFactor
                scale = newScale
                redrawAllCommitted()
                invalidate()
                return true
            }
        })
    }

    private var twoFingerActive: Boolean = false
    private var lastTwoFingerCenterX: Float = 0f
    private var lastTwoFingerCenterY: Float = 0f
    private var threeFingerUndoFired: Boolean = false

    // -------- Lineal --------
    private var lineStartX: Float = 0f
    private var lineStartY: Float = 0f

    private fun screenToWorldX(sx: Float): Float = (sx - panX) / scale
    private fun screenToWorldY(sy: Float): Float = (sy - panY) / scale

    fun resetView() {
        panX = 0f
        panY = 0f
        scale = 1f
        redrawAllCommitted()
        invalidate()
    }

    init {
        isFocusable = false
    }

    // -------- Lifecycle --------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recreateBitmap()
    }

    private fun recreateBitmap() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val config = when (bitmapMode) {
            BitmapMode.ARGB_8888 -> Bitmap.Config.ARGB_8888
            BitmapMode.RGB_565 -> Bitmap.Config.RGB_565
        }
        val old = committedBitmap
        val bmp = Bitmap.createBitmap(w, h, config)
        committedBitmap = bmp
        committedCanvas = Canvas(bmp)
        redrawAllCommitted()
        old?.recycle()
    }

    // -------- Touch handling --------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (page == null) return false

        // ScaleGestureDetector beobachtet alle Events (für Pinch).
        scaleDetector.onTouchEvent(event)

        // 3-Finger-Geste = Undo (feuert genau einmal pro Auflegen).
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && event.pointerCount == 3) {
            if (!threeFingerUndoFired) {
                threeFingerUndoFired = true
                if (currentStroke != null) cancelStroke()
                twoFingerActive = false
                undo()
            }
            return true
        }

        // 2-Finger-Modus: Pan + Zoom; Stift-Stroke wird abgebrochen.
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    if (currentStroke != null) cancelStroke()
                    twoFingerActive = true
                    lastTwoFingerCenterX = (event.getX(0) + event.getX(1)) / 2f
                    lastTwoFingerCenterY = (event.getY(0) + event.getY(1)) / 2f
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    twoFingerActive = false
                }
                if (event.pointerCount <= 1) {
                    threeFingerUndoFired = false
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                threeFingerUndoFired = false
            }
        }

        if (twoFingerActive) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount >= 2) {
                val cx = (event.getX(0) + event.getX(1)) / 2f
                val cy = (event.getY(0) + event.getY(1)) / 2f
                if (!scaleDetector.isInProgress) {
                    panX += cx - lastTwoFingerCenterX
                    panY += cy - lastTwoFingerCenterY
                    redrawAllCommitted()
                    invalidate()
                }
                lastTwoFingerCenterX = cx
                lastTwoFingerCenterY = cy
            }
            return true
        }

        if (tool == Tool.ERASER) return handleEraser(event)
        if (tool == Tool.LINE) return handleLine(event)
        if (tool == Tool.TEXT) return handleText(event)
        if (tool == Tool.IMAGE) return handleImage(event)

        if (predictionMode == PredictionMode.ANDROID) {
            runCatching { predictor.record(event) }
        }

        metrics.record(event)
        onMetricsChanged?.invoke()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (useUnbufferedDispatch) requestUnbufferedDispatch(event)
                startStroke(screenToWorldX(event.x), screenToWorldY(event.y))
                processHistoricalAndCurrent(event, skipCurrent = true)
                renderProgress()
            }
            MotionEvent.ACTION_MOVE -> {
                processHistoricalAndCurrent(event, skipCurrent = false)
                renderProgress()
            }
            MotionEvent.ACTION_UP -> {
                processHistoricalAndCurrent(event, skipCurrent = false)
                finishStroke()
            }
            MotionEvent.ACTION_CANCEL -> cancelStroke()
        }
        return true
    }

    private fun startStroke(x: Float, y: Float) {
        val s = Stroke(
            color = strokeColor,
            width = strokeWidth,
            smoothingFactor = smoothingFactor,
            algo = smoothingAlgo,
        )
        s.addPoint(x, y)
        currentStroke = s
        predictedCount = 0
        metrics.onStrokeStart()
    }

    private fun processHistoricalAndCurrent(event: MotionEvent, skipCurrent: Boolean) {
        val s = currentStroke ?: return
        val n = event.historySize
        for (i in 0 until n) {
            s.addPoint(
                screenToWorldX(event.getHistoricalX(i)),
                screenToWorldY(event.getHistoricalY(i)),
            )
        }
        if (!skipCurrent) s.addPoint(screenToWorldX(event.x), screenToWorldY(event.y))
    }

    private fun renderProgress() {
        updatePredictedTail()
        val s = currentStroke
        if (damageRectEnabled && s != null) {
            val pad = strokeWidth + 4f
            invalidate(s.bounds(pad, damageRectBuf))
        } else {
            invalidate()
        }
    }

    private fun updatePredictedTail() {
        predictedCount = 0
        when (predictionMode) {
            PredictionMode.OFF -> return
            PredictionMode.ANDROID -> {
                val p = try {
                    predictor.predict()
                } catch (t: Throwable) {
                    Log.w(TAG, "predict failed: $t")
                    return
                } ?: return
                try {
                    val count = (p.historySize + 1).coerceAtMost(PREDICT_BUF_POINTS)
                    for (i in 0 until count - 1) {
                        predictedBuf[i * 2] = p.getHistoricalX(i)
                        predictedBuf[i * 2 + 1] = p.getHistoricalY(i)
                    }
                    predictedBuf[(count - 1) * 2] = p.x
                    predictedBuf[(count - 1) * 2 + 1] = p.y
                    predictedCount = count
                } finally {
                    p.recycle()
                }
            }
            PredictionMode.LINEAR -> {
                val s = currentStroke ?: return
                if (s.size < 2) return
                val i1 = s.size - 1
                val i0 = (s.size - 3).coerceAtLeast(0)
                val dx = s.x(i1) - s.x(i0)
                val dy = s.y(i1) - s.y(i0)
                val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (len < 0.01f) return
                val k = LINEAR_PREDICT_PX / len
                predictedBuf[0] = s.x(i1) + dx * k
                predictedBuf[1] = s.y(i1) + dy * k
                predictedCount = 1
            }
        }
    }

    private fun finishStroke() {
        val s = currentStroke ?: return
        val p = page
        if (p == null) {
            cancelStroke()
            return
        }
        committedCanvas?.let { c ->
            val saved = c.save()
            c.translate(panX, panY)
            c.scale(scale, scale)
            drawStroke(c, s, clip = false)
            c.restoreToCount(saved)
        }
        p.addStroke(s)
        currentStroke = null
        predictedCount = 0
        metrics.onStrokeEnd(committed = true)
        invalidate()
        onStrokeCommitted?.invoke(p)
        onMetricsChanged?.invoke()
    }

    private fun cancelStroke() {
        currentStroke = null
        predictedCount = 0
        metrics.onStrokeEnd(committed = false)
        invalidate()
    }

    // -------- Drawing --------

    override fun onDraw(canvas: Canvas) {
        metrics.onDraw()
        val bmp = committedBitmap
        if (bmp != null) canvas.drawBitmap(bmp, 0f, 0f, null)
        val s = currentStroke ?: return
        val saved = canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scale, scale)
        drawStroke(canvas, s, clip = false)
        drawPredictedTail(canvas, s)
        canvas.restoreToCount(saved)
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke, clip: Boolean) {
        strokePaint.color = stroke.color
        strokePaint.strokeWidth = stroke.width
        if (clip) {
            val r = stroke.bounds(stroke.width + 4f, clipRectBuf)
            val saved = canvas.save()
            canvas.clipRect(r)
            stroke.buildPath(reusablePath)
            canvas.drawPath(reusablePath, strokePaint)
            canvas.restoreToCount(saved)
        } else {
            stroke.buildPath(reusablePath)
            canvas.drawPath(reusablePath, strokePaint)
        }
    }

    private fun drawPredictedTail(canvas: Canvas, stroke: Stroke) {
        val count = predictedCount
        if (count == 0 || stroke.size == 0) return
        strokePaint.color = stroke.color
        strokePaint.strokeWidth = stroke.width
        var prevX = stroke.x(stroke.size - 1)
        var prevY = stroke.y(stroke.size - 1)
        var i = 0
        while (i < count) {
            val x = predictedBuf[i * 2]
            val y = predictedBuf[i * 2 + 1]
            canvas.drawLine(prevX, prevY, x, y, strokePaint)
            prevX = x
            prevY = y
            i++
        }
    }

    private fun redrawAllCommitted() {
        val c = committedCanvas ?: return
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        drawPaperBackground(c)
        val p = page ?: return
        val saved = c.save()
        c.translate(panX, panY)
        c.scale(scale, scale)
        // Reihenfolge: Bilder unten, Strokes drüber, Text oben drauf.
        for (ib in p.imageBoxes) drawImageBox(c, ib)
        for (s in p.strokes) drawStroke(c, s, clip = false)
        for (tb in p.textBoxes) drawTextBox(c, tb)
        c.restoreToCount(saved)
    }

    private fun mm(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, v, resources.displayMetrics)

    private fun drawPaperBackground(c: Canvas) {
        val style = page?.paperStyle ?: PaperStyle.BLANK
        val bg = if (style == PaperStyle.LEGAL) 0xFFFCF8D8.toInt() else 0xFFFDFDFD.toInt()
        c.drawColor(bg)
        if (style == PaperStyle.BLANK) return

        // Welt-relative Linien / Punkte: Hintergrund zoomt und pannt mit.
        val saved = c.save()
        c.translate(panX, panY)
        c.scale(scale, scale)

        val cw = c.width.toFloat()
        val ch = c.height.toFloat()
        val worldLeft = -panX / scale
        val worldTop = -panY / scale
        val worldRight = (cw - panX) / scale
        val worldBottom = (ch - panY) / scale

        when (style) {
            PaperStyle.LINED -> {
                paperPaint.style = Paint.Style.STROKE
                paperPaint.strokeWidth = 1f / scale
                paperPaint.color = 0xFFB8C8E0.toInt()
                val step = mm(linedSpacingMm)
                var y = floor(worldTop / step) * step
                while (y < worldBottom) {
                    c.drawLine(worldLeft, y, worldRight, y, paperPaint)
                    y += step
                }
            }
            PaperStyle.GRID -> {
                paperPaint.style = Paint.Style.STROKE
                paperPaint.strokeWidth = 1f / scale
                paperPaint.color = 0xFFD0D0D0.toInt()
                val step = mm(gridSpacingMm)
                var y = floor(worldTop / step) * step
                while (y < worldBottom) {
                    c.drawLine(worldLeft, y, worldRight, y, paperPaint)
                    y += step
                }
                var x = floor(worldLeft / step) * step
                while (x < worldRight) {
                    c.drawLine(x, worldTop, x, worldBottom, paperPaint)
                    x += step
                }
            }
            PaperStyle.DOTS -> {
                paperPaint.style = Paint.Style.FILL
                paperPaint.color = 0xFFA8A8A8.toInt()
                val step = mm(dotsSpacingMm)
                val radius = max(mm(0.4f), 1.5f) / scale
                var y = (floor(worldTop / step) + 1f) * step
                while (y < worldBottom) {
                    var x = (floor(worldLeft / step) + 1f) * step
                    while (x < worldRight) {
                        c.drawCircle(x, y, radius, paperPaint)
                        x += step
                    }
                    y += step
                }
                paperPaint.style = Paint.Style.STROKE
            }
            PaperStyle.LEGAL -> {
                paperPaint.style = Paint.Style.STROKE
                paperPaint.strokeWidth = 1f / scale
                paperPaint.color = 0xFFB0B8D0.toInt()
                val step = mm(linedSpacingMm)
                var y = floor(worldTop / step) * step
                while (y < worldBottom) {
                    c.drawLine(worldLeft, y, worldRight, y, paperPaint)
                    y += step
                }
                paperPaint.color = 0x80D13438.toInt()
                paperPaint.strokeWidth = mm(0.5f) / scale
                val marginX = mm(30f)
                c.drawLine(marginX, worldTop, marginX, worldBottom, paperPaint)
            }
            else -> {}
        }
        c.restoreToCount(saved)
    }

    // -------- Eraser --------

    private fun handleEraser(event: MotionEvent): Boolean {
        val p = page ?: return false
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) return true
        val hit = findStrokeAt(p, screenToWorldX(event.x), screenToWorldY(event.y)) ?: return true
        p.strokes.remove(hit)
        p.redoStack.addLast(hit)
        redrawAllCommitted()
        invalidate()
        onStrokeCommitted?.invoke(p)
        onMetricsChanged?.invoke()
        return true
    }

    private fun handleLine(event: MotionEvent): Boolean {
        val p = page ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (useUnbufferedDispatch) requestUnbufferedDispatch(event)
                lineStartX = screenToWorldX(event.x)
                lineStartY = screenToWorldY(event.y)
                currentStroke = buildLineStroke(lineStartX, lineStartY, lineStartX, lineStartY)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                currentStroke = buildLineStroke(
                    lineStartX, lineStartY,
                    screenToWorldX(event.x), screenToWorldY(event.y),
                )
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val ex = screenToWorldX(event.x)
                val ey = screenToWorldY(event.y)
                val s = buildLineStroke(lineStartX, lineStartY, ex, ey)
                committedCanvas?.let { c ->
                    val saved = c.save()
                    c.translate(panX, panY)
                    c.scale(scale, scale)
                    drawStroke(c, s, clip = false)
                    c.restoreToCount(saved)
                }
                p.addStroke(s)
                currentStroke = null
                invalidate()
                onStrokeCommitted?.invoke(p)
                onMetricsChanged?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> {
                currentStroke = null
                invalidate()
            }
        }
        return true
    }

    private fun handleText(event: MotionEvent): Boolean {
        val p = page ?: return false
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        val wx = screenToWorldX(event.x)
        val wy = screenToWorldY(event.y)
        val hit = findTextBoxAt(p, wx, wy)
        onTextBoxTap?.invoke(hit, wx, wy)
        return true
    }

    private fun handleImage(event: MotionEvent): Boolean {
        val p = page ?: return false
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        val wx = screenToWorldX(event.x)
        val wy = screenToWorldY(event.y)
        val hit = findImageBoxAt(p, wx, wy)
        onImageBoxTap?.invoke(hit, wx, wy)
        return true
    }

    private fun findTextBoxAt(p: Page, x: Float, y: Float): TextBox? {
        for (i in p.textBoxes.indices.reversed()) {
            val tb = p.textBoxes[i]
            textPaint.textSize = tb.fontSize
            val lines = tb.content.split('\n')
            val width = (lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f).coerceAtLeast(40f)
            val lineHeight = tb.fontSize * 1.3f
            val height = lineHeight * lines.size.coerceAtLeast(1)
            if (x >= tb.x && x <= tb.x + width &&
                y >= tb.y && y <= tb.y + height) {
                return tb
            }
        }
        return null
    }

    private fun findImageBoxAt(p: Page, x: Float, y: Float): ImageBox? {
        for (i in p.imageBoxes.indices.reversed()) {
            val ib = p.imageBoxes[i]
            if (x >= ib.x && x <= ib.x + ib.width &&
                y >= ib.y && y <= ib.y + ib.height) {
                return ib
            }
        }
        return null
    }

    private fun drawTextBox(c: Canvas, tb: TextBox) {
        textPaint.color = tb.color
        textPaint.textSize = tb.fontSize
        val lineHeight = tb.fontSize * 1.3f
        val lines = tb.content.split('\n')
        var baselineY = tb.y + tb.fontSize
        for (line in lines) {
            c.drawText(line, tb.x, baselineY, textPaint)
            baselineY += lineHeight
        }
    }

    private fun drawImageBox(c: Canvas, ib: ImageBox) {
        val bmp = imageBitmapCache.getOrPut(ib.assetPath) {
            imageLoader?.invoke(ib.assetPath)
                ?: return  // Asset nicht ladbar → überspringen
        }
        val src = Rect(0, 0, bmp.width, bmp.height)
        val dst = Rect(ib.x.toInt(), ib.y.toInt(),
            (ib.x + ib.width).toInt(), (ib.y + ib.height).toInt())
        c.drawBitmap(bmp, src, dst, null)
    }

    fun invalidateImageCache() {
        imageBitmapCache.clear()
        redrawAllCommitted()
        invalidate()
    }

    /** Re-renders Page-Inhalt (Strokes, Texts, Images) ins committed-Bitmap und triggert View-Redraw. */
    fun refresh() {
        redrawAllCommitted()
        invalidate()
    }

    private fun buildLineStroke(x1: Float, y1: Float, x2: Float, y2: Float): Stroke {
        val s = Stroke(
            color = strokeColor,
            width = strokeWidth,
            smoothingFactor = 0f,
            algo = SmoothingAlgo.BEZIER,
        )
        s.addPoint(x1, y1)
        s.addPoint(x2, y2)
        return s
    }

    private fun findStrokeAt(p: Page, x: Float, y: Float): Stroke? {
        for (i in p.strokes.indices.reversed()) {
            val s = p.strokes[i]
            val threshold = s.width / 2f + 6f
            val bbox = s.bounds(threshold, eraserHitBuf)
            if (x < bbox.left || x > bbox.right || y < bbox.top || y > bbox.bottom) continue
            val t2 = threshold * threshold
            for (j in 0 until s.size) {
                val dx = s.x(j) - x
                val dy = s.y(j) - y
                if (dx * dx + dy * dy <= t2) return s
            }
        }
        return null
    }

    // -------- Public API (wird von MainActivity gerufen) --------

    fun setActivePaperStyle(style: PaperStyle) {
        val p = page ?: return
        if (p.paperStyle == style) return
        p.paperStyle = style
        redrawAllCommitted()
        invalidate()
        onStrokeCommitted?.invoke(p)
    }

    fun undo() {
        val p = page ?: return
        if (!p.undo()) return
        redrawAllCommitted()
        invalidate()
        onStrokeCommitted?.invoke(p)
        onMetricsChanged?.invoke()
    }

    fun redo() {
        val p = page ?: return
        if (!p.redo()) return
        redrawAllCommitted()
        invalidate()
        onStrokeCommitted?.invoke(p)
        onMetricsChanged?.invoke()
    }

    fun clear() {
        val p = page ?: return
        p.clear()
        currentStroke = null
        predictedCount = 0
        redrawAllCommitted()
        invalidate()
        metrics.reset()
        onStrokeCommitted?.invoke(p)
        onMetricsChanged?.invoke()
    }

    private companion object {
        const val TAG = "InkView"
        const val PREDICT_BUF_POINTS = 32
        const val LINEAR_PREDICT_PX = 20f
        const val MIN_SCALE = 0.25f
        const val MAX_SCALE = 6f
    }
}
