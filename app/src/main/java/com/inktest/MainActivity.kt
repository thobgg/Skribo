package com.inktest

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repository: Repository
    private lateinit var document: Document

    private lateinit var inkView: InkView
    private lateinit var metricsText: TextView
    private lateinit var appMenuPanel: View
    private lateinit var tuningPanel: View
    private lateinit var sectionTabsContainer: LinearLayout
    private lateinit var pageListContainer: LinearLayout
    private lateinit var paperBtn: Button
    private lateinit var toolPenBtn: Button
    private lateinit var toolHighlighterBtn: Button
    private lateinit var toolLineBtn: Button
    private lateinit var toolTextBtn: Button
    private lateinit var toolImageBtn: Button
    private lateinit var toolEraserBtn: Button
    private lateinit var widthThinBtn: Button
    private lateinit var widthMedBtn: Button
    private lateinit var widthThickBtn: Button
    private lateinit var colorPaletteContainer: LinearLayout
    private lateinit var menuMetrics: TextView
    private lateinit var rootLayout: FrameLayout
    private lateinit var toolBar: HorizontalScrollView
    private lateinit var toolBarDividerBottom: View
    private lateinit var dragHandle: Button
    private var dockedParent: LinearLayout? = null
    private var dockedIndex: Int = -1
    private var dockedParams: ViewGroup.LayoutParams? = null

    private var currentSection: Section? = null
    private var currentPage: Page? = null

    private var pendingImagePos: Pair<Float, Float>? = null
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val pos = pendingImagePos
        pendingImagePos = null
        if (uri != null && pos != null) {
            importImageFromUri(uri, pos.first, pos.second)
        }
    }

    private var lastMetricsUpdate = 0L

    private val penPalette = intArrayOf(
        0xFF000000.toInt(),
        0xFF4A4A4A.toInt(),
        0xFFD13438.toInt(),
        0xFFE0A82E.toInt(),
        0xFF107C10.toInt(),
        0xFF0078D4.toInt(),
        0xFF805AD5.toInt(),
        0xFF0EA5E9.toInt(),
    )
    private val widthPresets = floatArrayOf(1.5f, 4.0f, 9.0f)

    // ---------------- Lifecycle ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        repository = Repository(this)
        document = repository.load()

        inkView = findViewById(R.id.inkView)
        metricsText = findViewById(R.id.metricsText)
        appMenuPanel = findViewById(R.id.appMenuPanel)
        tuningPanel = findViewById(R.id.tuningPanel)
        sectionTabsContainer = findViewById(R.id.sectionTabsContainer)
        pageListContainer = findViewById(R.id.pageListContainer)
        paperBtn = findViewById(R.id.btnPaper)
        toolPenBtn = findViewById(R.id.btnToolPen)
        toolHighlighterBtn = findViewById(R.id.btnToolHighlighter)
        toolLineBtn = findViewById(R.id.btnToolLine)
        toolTextBtn = findViewById(R.id.btnToolText)
        toolImageBtn = findViewById(R.id.btnToolImage)
        toolEraserBtn = findViewById(R.id.btnToolEraser)
        widthThinBtn = findViewById(R.id.btnWidthThin)
        widthMedBtn = findViewById(R.id.btnWidthMed)
        widthThickBtn = findViewById(R.id.btnWidthThick)
        colorPaletteContainer = findViewById(R.id.colorPaletteContainer)
        menuMetrics = findViewById(R.id.menuMetrics)
        rootLayout = findViewById(R.id.root)
        toolBar = findViewById(R.id.toolBar)
        toolBarDividerBottom = findViewById(R.id.toolBarDividerBottom)
        dragHandle = findViewById(R.id.btnDragHandle)

        applyEngineSettingsToView()
        setupTopBarButtons()
        setupTools()
        setupColorPalette()
        setupWidthPresets()
        setupPaperButton()
        setupAppMenu()
        setupTuningPanel()
        setupMetrics()
        setupFloatingToolbar()

        inkView.onStrokeCommitted = { page -> repository.savePage(page) }
        inkView.onTextBoxTap = { existing, wx, wy ->
            if (existing != null) showTextBoxMenu(existing)
            else showTextBoxNewDialog(wx, wy)
        }
        inkView.onImageBoxTap = { existing, wx, wy ->
            if (existing != null) showImageBoxMenu(existing)
            else launchImagePicker(wx, wy)
        }
        inkView.imageLoader = { assetPath ->
            val file = java.io.File(repository.rootDir, assetPath)
            if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath) else null
        }

        activateInitial()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        currentPage?.let { repository.savePage(it) }
        repository.saveDocumentStructure(document)
        repository.flush()
        super.onPause()
    }

    @Deprecated("Handled explicitly — schließt offene Drawer bevor Activity verlassen wird")
    override fun onBackPressed() {
        if (appMenuPanel.visibility == View.VISIBLE) {
            appMenuPanel.visibility = View.GONE
            return
        }
        if (tuningPanel.visibility == View.VISIBLE) {
            tuningPanel.visibility = View.GONE
            prefs.tuningVisible = false
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // ---------------- Navigation ----------------

    private fun activateInitial() {
        val sec = document.sections.firstOrNull { it.id == prefs.activeSectionId }
            ?: document.sections.firstOrNull()
        currentSection = sec
        val page = sec?.pages?.firstOrNull { it.id == prefs.activePageId }
            ?: sec?.pages?.firstOrNull()
        currentPage = page
        inkView.page = page
        updatePaperButton()
        rebuildSectionTabs()
        rebuildPageList()
    }

    private fun switchToSection(section: Section) {
        if (section === currentSection) return
        currentPage?.let { repository.savePage(it) }
        currentSection = section
        prefs.activeSectionId = section.id
        val page = section.pages.firstOrNull()
        currentPage = page
        inkView.page = page
        prefs.activePageId = page?.id
        updatePaperButton()
        rebuildSectionTabs()
        rebuildPageList()
    }

    private fun switchToPage(page: Page?) {
        if (page === currentPage) return
        currentPage?.let { repository.savePage(it) }
        currentPage = page
        inkView.page = page
        prefs.activePageId = page?.id
        updatePaperButton()
        rebuildPageList()
    }

    // ---------------- Section tabs rendering ----------------

    private fun rebuildSectionTabs() {
        sectionTabsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val activeId = currentSection?.id
        for (section in document.sections) {
            sectionTabsContainer.addView(buildSectionTab(section, section.id == activeId, density))
        }
    }

    private fun buildSectionTab(section: Section, isActive: Boolean, density: Float): View {
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            if (isActive) setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            setOnClickListener { switchToSection(section) }
            setOnLongClickListener { showSectionMenu(section); true }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = (14 * density).toInt()
            setPadding(hPad, 0, hPad, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val dot = View(this).apply {
            val sz = (10 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(section.color)
            }
        }
        val name = TextView(this).apply {
            text = section.name
            textSize = 13f
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isActive) R.color.nav_text else R.color.nav_muted,
                ),
            )
            setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            val pad = (8 * density).toInt()
            setPadding(pad, 0, 0, 0)
        }
        row.addView(dot)
        row.addView(name)
        frame.addView(row)
        if (isActive) {
            val bar = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (2 * density).toInt(),
                    Gravity.BOTTOM,
                )
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.nav_accent))
            }
            frame.addView(bar)
        }
        return frame
    }

    // ---------------- Page list rendering ----------------

    private fun rebuildPageList() {
        pageListContainer.removeAllViews()
        val sec = currentSection ?: return
        val density = resources.displayMetrics.density
        val activeId = currentPage?.id
        for (page in sec.pages) {
            val depth = sec.depthOf(page)
            pageListContainer.addView(buildPageItem(page, depth, page.id == activeId, density))
        }
    }

    private fun buildPageItem(page: Page, depth: Int, isActive: Boolean, density: Float): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            if (isActive) setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            setOnClickListener { switchToPage(page) }
            setOnLongClickListener { showPageMenu(page); true }
        }
        val indicatorWidth = (3 * density).toInt()
        val indicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                indicatorWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            minimumHeight = (34 * density).toInt()
            if (isActive) {
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.nav_accent))
            }
        }
        val title = TextView(this).apply {
            text = page.title
            textSize = 12f
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isActive) R.color.nav_text else R.color.nav_muted,
                ),
            )
            setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            val leftPad = ((12 + depth * 18) * density).toInt() - indicatorWidth
            val rightPad = (12 * density).toInt()
            val vPad = (8 * density).toInt()
            setPadding(leftPad.coerceAtLeast(0), vPad, rightPad, vPad)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(indicator)
        container.addView(title)
        return container
    }

    // ---------------- Top-bar button wiring ----------------

    private fun setupTopBarButtons() {
        findViewById<Button>(R.id.btnAddSection).setOnClickListener { addSection() }
        findViewById<Button>(R.id.btnAddPage).setOnClickListener { addPage() }
        findViewById<Button>(R.id.btnAddSubpage).setOnClickListener { addSubpage() }

        findViewById<Button>(R.id.btnUndo).setOnClickListener { inkView.undo() }
        findViewById<Button>(R.id.btnRedo).setOnClickListener { inkView.redo() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { confirmClearPage() }
    }

    // ---------------- Tools ----------------

    private fun setupTools() {
        toolPenBtn.setOnClickListener { setTool(Tool.PEN) }
        toolHighlighterBtn.setOnClickListener { setTool(Tool.HIGHLIGHTER) }
        toolLineBtn.setOnClickListener { setTool(Tool.LINE) }
        toolTextBtn.setOnClickListener { setTool(Tool.TEXT) }
        toolImageBtn.setOnClickListener { setTool(Tool.IMAGE) }
        toolEraserBtn.setOnClickListener { setTool(Tool.ERASER) }
        updateToolButtons()
    }

    private fun setTool(t: Tool) {
        inkView.tool = t
        prefs.tool = t
        applyToolColor(t)
        inkView.strokeWidth = when (t) {
            Tool.HIGHLIGHTER -> 25f
            else -> prefs.strokeWidth
        }
        updateToolButtons()
    }

    private fun applyToolColor(t: Tool) {
        inkView.strokeColor = when (t) {
            Tool.HIGHLIGHTER -> (prefs.color and 0x00FFFFFF) or 0x66000000
            else -> prefs.color or 0xFF000000.toInt()
        }
    }

    private fun updateToolButtons() {
        val soft = ContextCompat.getColor(this, R.color.nav_accent_soft)
        val transparent = Color.TRANSPARENT
        toolPenBtn.setBackgroundColor(if (inkView.tool == Tool.PEN) soft else transparent)
        toolHighlighterBtn.setBackgroundColor(if (inkView.tool == Tool.HIGHLIGHTER) soft else transparent)
        toolLineBtn.setBackgroundColor(if (inkView.tool == Tool.LINE) soft else transparent)
        toolTextBtn.setBackgroundColor(if (inkView.tool == Tool.TEXT) soft else transparent)
        toolImageBtn.setBackgroundColor(if (inkView.tool == Tool.IMAGE) soft else transparent)
        toolEraserBtn.setBackgroundColor(if (inkView.tool == Tool.ERASER) soft else transparent)
    }

    // ---------------- Color palette ----------------

    private fun setupColorPalette() {
        rebuildColorPalette()
    }

    private fun rebuildColorPalette() {
        colorPaletteContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val active = prefs.color
        for (c in penPalette) {
            colorPaletteContainer.addView(buildColorChip(c, c == active, density))
        }
    }

    private fun buildColorChip(color: Int, isActive: Boolean, density: Float): View {
        val sz = (26 * density).toInt()
        val margin = (3 * density).toInt()
        val chip = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                leftMargin = margin
                rightMargin = margin
                gravity = Gravity.CENTER_VERTICAL
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (isActive) {
                    setStroke(
                        (3 * density).toInt(),
                        ContextCompat.getColor(this@MainActivity, R.color.nav_accent),
                    )
                } else {
                    setStroke(
                        (1 * density).toInt(),
                        ContextCompat.getColor(this@MainActivity, R.color.nav_border),
                    )
                }
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { selectColor(color) }
        }
        return chip
    }

    private fun selectColor(color: Int) {
        prefs.color = color
        rebuildColorPalette()
        if (inkView.tool == Tool.ERASER) {
            setTool(Tool.PEN)
        } else {
            applyToolColor(inkView.tool)
        }
    }

    // ---------------- Width presets ----------------

    private fun setupWidthPresets() {
        widthThinBtn.setOnClickListener { selectWidth(widthPresets[0]) }
        widthMedBtn.setOnClickListener { selectWidth(widthPresets[1]) }
        widthThickBtn.setOnClickListener { selectWidth(widthPresets[2]) }
        updateWidthButtons()
    }

    private fun selectWidth(w: Float) {
        inkView.strokeWidth = w
        prefs.strokeWidth = w
        findViewById<SeekBar>(R.id.sliderWidth)?.progress =
            ((w - 1.0f) * 10f).toInt().coerceIn(0, 190)
        findViewById<TextView>(R.id.labelWidth)?.text = widthLabelText(w)
        updateWidthButtons()
    }

    private fun updateWidthButtons() {
        val soft = ContextCompat.getColor(this, R.color.nav_accent_soft)
        val transparent = Color.TRANSPARENT
        val active = prefs.strokeWidth
        widthThinBtn.setBackgroundColor(
            if (kotlin.math.abs(active - widthPresets[0]) < 0.01f) soft else transparent,
        )
        widthMedBtn.setBackgroundColor(
            if (kotlin.math.abs(active - widthPresets[1]) < 0.01f) soft else transparent,
        )
        widthThickBtn.setBackgroundColor(
            if (kotlin.math.abs(active - widthPresets[2]) < 0.01f) soft else transparent,
        )
    }

    // ---------------- Paper dialog (Stil + Abstand) ----------------

    private fun setupPaperButton() {
        paperBtn.setOnClickListener { showPaperDialog() }
        updatePaperButton()
    }

    private fun presetsFor(style: PaperStyle): FloatArray = when (style) {
        PaperStyle.LINED, PaperStyle.LEGAL -> floatArrayOf(7f, 9f, 12f)
        PaperStyle.GRID -> floatArrayOf(4f, 5f, 7f, 10f)
        PaperStyle.DOTS -> floatArrayOf(5f, 8f)
        else -> floatArrayOf()
    }

    private fun showPaperDialog() {
        val density = resources.displayMetrics.density
        val accent = ContextCompat.getColor(this, R.color.nav_accent)
        val accentSoft = ContextCompat.getColor(this, R.color.nav_accent_soft)
        val muted = ContextCompat.getColor(this, R.color.nav_muted)
        val text = ContextCompat.getColor(this, R.color.nav_text)
        val border = ContextCompat.getColor(this, R.color.nav_border)
        val current = currentPage?.paperStyle ?: PaperStyle.BLANK

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            val h = (22 * density).toInt()
            val v = (18 * density).toInt()
            setPadding(h, v, h, v)
        }

        // ---- Stil ----
        container.addView(sectionLabel("Stil", muted, density))

        val styleList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val m = (6 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = m; bottomMargin = (14 * density).toInt() }
        }
        container.addView(styleList)

        // ---- Abstand ----
        val presetLabel = sectionLabel("Abstand", muted, density)
        container.addView(presetLabel)

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val m = (6 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = m; bottomMargin = (4 * density).toInt() }
        }
        container.addView(presetRow)

        var selectedStyle: PaperStyle = current
        var selectedSpacing: Float = when (current) {
            PaperStyle.LINED, PaperStyle.LEGAL -> prefs.linedSpacingMm
            PaperStyle.GRID -> prefs.gridSpacingMm
            PaperStyle.DOTS -> prefs.dotsSpacingMm
            else -> 0f
        }

        val choices = listOf(
            PaperStyle.BLANK to getString(R.string.paper_blank),
            PaperStyle.LINED to getString(R.string.paper_lined),
            PaperStyle.GRID to getString(R.string.paper_grid),
            PaperStyle.DOTS to getString(R.string.paper_dots),
            PaperStyle.LEGAL to getString(R.string.paper_legal),
        )

        lateinit var rebuildStyleListRef: () -> Unit
        lateinit var rebuildPresetRowRef: () -> Unit

        fun rebuildStyleList() {
            styleList.removeAllViews()
            choices.forEach { (style, name) ->
                styleList.addView(
                    buildStyleRow(
                        name,
                        style == selectedStyle,
                        accent,
                        accentSoft,
                        text,
                        density,
                    ) {
                        if (selectedStyle == style) return@buildStyleRow
                        selectedStyle = style
                        selectedSpacing = when (style) {
                            PaperStyle.LINED, PaperStyle.LEGAL -> prefs.linedSpacingMm
                            PaperStyle.GRID -> prefs.gridSpacingMm
                            PaperStyle.DOTS -> prefs.dotsSpacingMm
                            else -> 0f
                        }
                        rebuildStyleListRef()
                        rebuildPresetRowRef()
                    },
                )
            }
        }
        rebuildStyleListRef = ::rebuildStyleList

        fun rebuildPresetRow() {
            presetRow.removeAllViews()
            val presets = presetsFor(selectedStyle)
            val vis = if (presets.isNotEmpty()) View.VISIBLE else View.GONE
            presetLabel.visibility = vis
            presetRow.visibility = vis
            if (presets.isEmpty()) return
            if (presets.none { kotlin.math.abs(it - selectedSpacing) < 0.01f }) {
                selectedSpacing = presets[0]
            }
            presets.forEach { mmValue ->
                presetRow.addView(
                    buildPresetPill(
                        "${mmValue.toInt()} mm",
                        kotlin.math.abs(mmValue - selectedSpacing) < 0.01f,
                        accent,
                        text,
                        border,
                        density,
                    ) {
                        selectedSpacing = mmValue
                        rebuildPresetRowRef()
                    },
                )
            }
        }
        rebuildPresetRowRef = ::rebuildPresetRow

        rebuildStyleList()
        rebuildPresetRow()

        MaterialAlertDialogBuilder(this)
            .setCustomTitle(buildDialogTitle("Papier", accent, density))
            .setView(container)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                when (selectedStyle) {
                    PaperStyle.LINED, PaperStyle.LEGAL -> {
                        prefs.linedSpacingMm = selectedSpacing
                        inkView.linedSpacingMm = selectedSpacing
                    }
                    PaperStyle.GRID -> {
                        prefs.gridSpacingMm = selectedSpacing
                        inkView.gridSpacingMm = selectedSpacing
                    }
                    PaperStyle.DOTS -> {
                        prefs.dotsSpacingMm = selectedSpacing
                        inkView.dotsSpacingMm = selectedSpacing
                    }
                    else -> Unit
                }
                inkView.setActivePaperStyle(selectedStyle)
                updatePaperButton()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ---------------- Dialog-Styling-Helper (OneNote-Optik) ----------------

    private fun buildDialogTitle(title: String, accent: Int, density: Float): TextView {
        return TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(accent)
            setTypeface(null, Typeface.BOLD)
            val h = (24 * density).toInt()
            val top = (22 * density).toInt()
            val bot = (4 * density).toInt()
            setPadding(h, top, h, bot)
            setBackgroundColor(Color.WHITE)
        }
    }

    private fun sectionLabel(text: String, muted: Int, density: Float): TextView {
        return TextView(this).apply {
            this.text = text.uppercase()
            textSize = 10f
            letterSpacing = 0.14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(muted)
        }
    }

    private fun buildStyleRow(
        name: String,
        isActive: Boolean,
        accent: Int,
        accentSoft: Int,
        textColor: Int,
        density: Float,
        onClick: () -> Unit,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (isActive) accentSoft else Color.TRANSPARENT)
            val vPad = (10 * density).toInt()
            setPadding(0, vPad, 0, vPad)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val indicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (3 * density).toInt(),
                (22 * density).toInt(),
            )
            setBackgroundColor(if (isActive) accent else Color.TRANSPARENT)
        }
        val label = TextView(this).apply {
            text = name
            textSize = 15f
            setTextColor(if (isActive) accent else textColor)
            setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            val p = (12 * density).toInt()
            setPadding(p, 0, 0, 0)
        }
        row.addView(indicator)
        row.addView(label)
        return row
    }

    private fun buildPresetPill(
        label: String,
        isActive: Boolean,
        accent: Int,
        textColor: Int,
        border: Int,
        density: Float,
        onClick: () -> Unit,
    ): View {
        val pill = TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(
                (16 * density).toInt(),
                (8 * density).toInt(),
                (16 * density).toInt(),
                (8 * density).toInt(),
            )
            val m = (4 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { leftMargin = m; rightMargin = m }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                if (isActive) {
                    setColor(accent)
                    setStroke((1 * density).toInt(), accent)
                } else {
                    setColor(Color.WHITE)
                    setStroke((1 * density).toInt(), border)
                }
            }
            setTextColor(if (isActive) Color.WHITE else textColor)
            if (isActive) setTypeface(null, Typeface.BOLD)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        return pill
    }

    private fun updatePaperButton() {
        paperBtn.text = when (currentPage?.paperStyle) {
            PaperStyle.LINED -> getString(R.string.paper_lined)
            PaperStyle.GRID -> getString(R.string.paper_grid)
            PaperStyle.DOTS -> getString(R.string.paper_dots)
            PaperStyle.LEGAL -> getString(R.string.paper_legal)
            else -> getString(R.string.paper_blank)
        }
    }

    // ---------------- App menu (Hamburger-Drawer) ----------------

    private fun setupAppMenu() {
        findViewById<Button>(R.id.btnMenu).setOnClickListener {
            appMenuPanel.visibility =
                if (appMenuPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<Button>(R.id.btnAppMenuClose).setOnClickListener {
            appMenuPanel.visibility = View.GONE
        }
        findViewById<Button>(R.id.btnTuningClose).setOnClickListener {
            tuningPanel.visibility = View.GONE
            prefs.tuningVisible = false
        }
        findViewById<TextView>(R.id.menuPaper).setOnClickListener {
            appMenuPanel.visibility = View.GONE
            showPaperDialog()
        }
        menuMetrics.setOnClickListener {
            val nowVisible = metricsText.visibility != View.VISIBLE
            metricsText.visibility = if (nowVisible) View.VISIBLE else View.GONE
            prefs.metricsVisible = nowVisible
            updateMenuMetricsLabel()
        }
        findViewById<TextView>(R.id.menuTuning).setOnClickListener {
            appMenuPanel.visibility = View.GONE
            val nowVisible = tuningPanel.visibility != View.VISIBLE
            tuningPanel.visibility = if (nowVisible) View.VISIBLE else View.GONE
            prefs.tuningVisible = nowVisible
        }
        findViewById<TextView>(R.id.menuReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Auf Defaults zurücksetzen?")
                .setMessage("Alle Engine- und Darstellungs-Einstellungen werden verworfen. Notizen bleiben erhalten.")
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                    prefs.resetAll()
                    recreate()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
        findViewById<TextView>(R.id.menuSyncNow).setOnClickListener {
            appMenuPanel.visibility = View.GONE
            triggerSync()
        }
        findViewById<TextView>(R.id.menuSyncSettings).setOnClickListener {
            appMenuPanel.visibility = View.GONE
            showWebdavSettingsDialog()
        }
        findViewById<TextView>(R.id.menuSchoolYear).setOnClickListener {
            appMenuPanel.visibility = View.GONE
            showSchoolYearDialog()
        }
        findViewById<TextView>(R.id.menuFloatToolbar).setOnClickListener {
            appMenuPanel.visibility = View.GONE
            setToolbarFloating(!prefs.toolbarFloating)
        }
        updateMenuMetricsLabel()
        updateMenuFloatLabel()
    }

    private fun updateMenuFloatLabel() {
        findViewById<TextView>(R.id.menuFloatToolbar)?.text =
            if (prefs.toolbarFloating) "Werkzeugleiste andocken" else "Werkzeugleiste freistellen"
    }

    // ---------------- Floating Toolbar ----------------

    private fun setupFloatingToolbar() {
        dockedParent = toolBar.parent as? LinearLayout
        dockedIndex = dockedParent?.indexOfChild(toolBar) ?: -1
        dockedParams = toolBar.layoutParams

        var startRawX = 0f
        var startRawY = 0f
        var startTransX = 0f
        var startTransY = 0f
        dragHandle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = ev.rawX
                    startRawY = ev.rawY
                    startTransX = toolBar.translationX
                    startTransY = toolBar.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - startRawX
                    val dy = ev.rawY - startRawY
                    val maxX = (rootLayout.width - toolBar.width).toFloat().coerceAtLeast(0f)
                    val maxY = (rootLayout.height - toolBar.height).toFloat().coerceAtLeast(0f)
                    toolBar.translationX = (startTransX + dx).coerceIn(0f, maxX)
                    toolBar.translationY = (startTransY + dy).coerceIn(0f, maxY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.toolbarX = toolBar.translationX
                    prefs.toolbarY = toolBar.translationY
                    true
                }
                else -> false
            }
        }

        if (prefs.toolbarFloating) setToolbarFloating(true)
    }

    private fun setToolbarFloating(floating: Boolean) {
        val density = resources.displayMetrics.density
        val barHeight = (52 * density).toInt()
        if (floating) {
            (toolBar.parent as? ViewGroup)?.removeView(toolBar)
            toolBarDividerBottom.visibility = View.GONE
            val flp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                barHeight,
            )
            flp.gravity = Gravity.TOP or Gravity.START
            toolBar.layoutParams = flp
            toolBar.elevation = 12f * density
            toolBar.translationX = prefs.toolbarX
            toolBar.translationY = prefs.toolbarY
            rootLayout.addView(toolBar)
            dragHandle.visibility = View.VISIBLE
        } else {
            (toolBar.parent as? ViewGroup)?.removeView(toolBar)
            toolBar.translationX = 0f
            toolBar.translationY = 0f
            toolBar.elevation = 0f
            toolBar.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                barHeight,
            )
            dockedParent?.addView(toolBar, dockedIndex)
            toolBarDividerBottom.visibility = View.VISIBLE
            dragHandle.visibility = View.GONE
        }
        prefs.toolbarFloating = floating
        updateMenuFloatLabel()
    }

    // ---------------- Sync settings ----------------

    private fun showWebdavSettingsDialog() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val urlEdit = EditText(this).apply {
            hint = "https://skribo.bgg-home.de"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.webdavServer)
        }
        val userEdit = EditText(this).apply {
            hint = "Benutzername (z.B. skribo)"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.webdavUsername)
        }
        val pwEdit = EditText(this).apply {
            hint = "Passwort"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.webdavPassword)
        }
        listOf(
            "Server-URL" to urlEdit,
            "Benutzername" to userEdit,
            "Passwort" to pwEdit,
        ).forEach { (label, edit) ->
            container.addView(TextView(this).apply { text = label; setPadding(0, pad / 2, 0, 0) })
            container.addView(edit)
        }
        AlertDialog.Builder(this)
            .setTitle("WebDAV-Einstellungen")
            .setView(container)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                prefs.webdavServer = urlEdit.text.toString().trim().trimEnd('/')
                prefs.webdavUsername = userEdit.text.toString().trim()
                prefs.webdavPassword = pwEdit.text.toString()
                Toast.makeText(this, "Verbindung wird getestet …", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        SkriboSync(prefs).testConnection()
                        runOnUiThread {
                            Toast.makeText(this, "✓ Verbindung OK", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this, "✗ ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showSchoolYearDialog() {
        val edit = EditText(this).apply {
            hint = "z.B. 25-26"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.activeSchoolYear)
        }
        AlertDialog.Builder(this)
            .setTitle("Aktives Schuljahr")
            .setMessage("Striche landen in annotations/<schuljahr>.json. Beim Wechsel wird automatisch eine neue, leere Schicht angelegt.")
            .setView(edit)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val year = edit.text.toString().trim()
                if (year.isNotEmpty()) {
                    prefs.activeSchoolYear = year
                    Toast.makeText(this, "Schuljahr: $year", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun triggerSync() {
        if (prefs.webdavServer.isBlank() || prefs.webdavUsername.isBlank()) {
            Toast.makeText(this, "Erst WebDAV-Einstellungen ausfüllen", Toast.LENGTH_LONG).show()
            return
        }
        repository.flush()
        Toast.makeText(this, "Sync läuft …", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val result = SkriboSync(prefs).pushDocument(document)
                runOnUiThread {
                    val msg = buildString {
                        append("Sync fertig — ${result.pageCount} Seite(n) gepusht")
                        if (result.errors.isNotEmpty()) {
                            append(" · ${result.errors.size} Fehler:\n")
                            append(result.errors.take(3).joinToString("\n"))
                        }
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Sync fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun updateMenuMetricsLabel() {
        menuMetrics.text = if (metricsText.visibility == View.VISIBLE) {
            "Metrics-Overlay ausblenden"
        } else {
            "Metrics-Overlay anzeigen"
        }
    }

    // ---------------- Navigation actions ----------------

    private fun addSection() {
        val s = Section(
            name = getString(R.string.default_section_name),
            color = nextSectionColor(),
        )
        val firstPage = Page(
            title = getString(R.string.default_page_name),
            paperStyle = PaperStyle.LINED,
        )
        s.pages.add(firstPage)
        document.sections.add(s)
        repository.savePage(firstPage)
        repository.saveDocumentStructure(document)
        switchToSection(s)
    }

    private fun nextSectionColor(): Int {
        val palette = intArrayOf(
            0xFF4A90E2.toInt(),
            0xFFE0A82E.toInt(),
            0xFFD13438.toInt(),
            0xFF107C10.toInt(),
            0xFF805AD5.toInt(),
            0xFF0EA5E9.toInt(),
            0xFFF48120.toInt(),
        )
        val used = document.sections.map { it.color }.toSet()
        return palette.firstOrNull { it !in used } ?: palette.random()
    }

    private fun addPage() {
        val sec = currentSection ?: return
        val newPage = Page(
            title = getString(R.string.default_page_name),
            paperStyle = currentPage?.paperStyle ?: PaperStyle.LINED,
        )
        sec.addRootPage(newPage)
        repository.savePage(newPage)
        repository.saveDocumentStructure(document)
        switchToPage(newPage)
    }

    private fun addSubpage() {
        val sec = currentSection ?: return
        val parent = currentPage ?: return addPage()
        val newPage = Page(
            title = getString(R.string.default_page_name),
            paperStyle = parent.paperStyle,
        )
        sec.addSubpageOf(parent, newPage)
        repository.savePage(newPage)
        repository.saveDocumentStructure(document)
        switchToPage(newPage)
    }

    private fun confirmClearPage() {
        val p = currentPage ?: return
        AlertDialog.Builder(this)
            .setTitle("Seite leeren?")
            .setMessage("Alle Striche auf \"${p.title}\" entfernen.")
            .setPositiveButton("Leeren") { _: DialogInterface, _: Int -> inkView.clear() }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ---------------- Long-press menus ----------------

    private fun showPageMenu(page: Page) {
        AlertDialog.Builder(this)
            .setTitle(page.title)
            .setItems(arrayOf("Umbenennen", "Löschen")) { _, which ->
                when (which) {
                    0 -> showRenamePageDialog(page)
                    1 -> confirmDeletePage(page)
                }
            }
            .show()
    }

    private fun showRenamePageDialog(page: Page) {
        val edit = EditText(this).apply {
            setText(page.title)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_rename_page)
            .setView(edit)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val newTitle = edit.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    page.title = newTitle
                    repository.savePage(page)
                    rebuildPageList()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun confirmDeletePage(page: Page) {
        val sec = currentSection ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_page_title)
            .setMessage(getString(R.string.dialog_delete_page_msg, page.title))
            .setPositiveButton(R.string.dialog_delete) { _, _ ->
                val removed = sec.removePage(page)
                removed.forEach { repository.deletePage(it) }
                repository.saveDocumentStructure(document)
                if (currentPage != null && removed.any { it.id == currentPage!!.id }) {
                    switchToPage(sec.pages.firstOrNull())
                } else {
                    rebuildPageList()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ---------------- Text + Image content menus ----------------

    private fun showTextBoxNewDialog(wx: Float, wy: Float) {
        val page = currentPage ?: return
        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            hint = "Text eingeben"
        }
        AlertDialog.Builder(this)
            .setTitle("Neuer Text")
            .setView(edit)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val content = edit.text.toString().trim()
                if (content.isNotEmpty()) {
                    val tb = TextBox(x = wx, y = wy, content = content)
                    page.textBoxes.add(tb)
                    repository.savePage(page)
                    inkView.refresh()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showTextBoxMenu(tb: TextBox) {
        AlertDialog.Builder(this)
            .setTitle("Text")
            .setItems(arrayOf("Bearbeiten", "Löschen")) { _, which ->
                when (which) {
                    0 -> showTextBoxEditDialog(tb)
                    1 -> {
                        currentPage?.textBoxes?.remove(tb)
                        currentPage?.let { repository.savePage(it) }
                        inkView.refresh()
                    }
                }
            }
            .show()
    }

    private fun showTextBoxEditDialog(tb: TextBox) {
        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            setText(tb.content)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Text bearbeiten")
            .setView(edit)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                tb.content = edit.text.toString()
                currentPage?.let { repository.savePage(it) }
                inkView.refresh()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showImageBoxMenu(ib: ImageBox) {
        AlertDialog.Builder(this)
            .setTitle("Bild")
            .setItems(arrayOf("Löschen")) { _, which ->
                when (which) {
                    0 -> {
                        currentPage?.imageBoxes?.remove(ib)
                        currentPage?.let { repository.savePage(it) }
                        inkView.refresh()
                    }
                }
            }
            .show()
    }

    private fun launchImagePicker(wx: Float, wy: Float) {
        pendingImagePos = wx to wy
        pickImageLauncher.launch("image/*")
    }

    private fun importImageFromUri(uri: Uri, wx: Float, wy: Float) {
        val page = currentPage ?: return
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
            if (bitmap == null) {
                Toast.makeText(this, "Bild konnte nicht geladen werden", Toast.LENGTH_LONG).show()
                return
            }
            val assetId = UUID.randomUUID().toString()
            val assetFile = File(repository.assetsDir, "$assetId.png")
            FileOutputStream(assetFile).use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val maxDim = 600f
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val w: Float
            val h: Float
            if (ratio >= 1f) {
                w = minOf(maxDim, bitmap.width.toFloat())
                h = w / ratio
            } else {
                h = minOf(maxDim, bitmap.height.toFloat())
                w = h * ratio
            }
            val ib = ImageBox(
                x = wx, y = wy, width = w, height = h,
                assetPath = "assets/$assetId.png",
            )
            page.imageBoxes.add(ib)
            repository.savePage(page)
            inkView.refresh()
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSectionMenu(section: Section) {
        AlertDialog.Builder(this)
            .setTitle(section.name)
            .setItems(arrayOf("Umbenennen", "WebDAV-Pfad…", "Löschen")) { _, which ->
                when (which) {
                    0 -> showRenameSectionDialog(section)
                    1 -> showWebdavPathDialog(section)
                    2 -> confirmDeleteSection(section)
                }
            }
            .show()
    }

    private fun showWebdavPathDialog(section: Section) {
        val edit = EditText(this).apply {
            setText(section.webdavPath ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "z.B. Mathematik/Sek.I/Mathe9"
        }
        AlertDialog.Builder(this)
            .setTitle("WebDAV-Pfad für \"${section.name}\"")
            .setMessage("Pfad innerhalb des WebDAV-Roots, ohne führenden / und ohne /skribo am Ende. Leer lassen = kein Sync für diese Section.")
            .setView(edit)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val path = edit.text.toString().trim().trim('/')
                section.webdavPath = if (path.isEmpty()) null else path
                repository.saveDocumentStructure(document)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showRenameSectionDialog(section: Section) {
        val edit = EditText(this).apply {
            setText(section.name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_rename_section)
            .setView(edit)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val newName = edit.text.toString().trim()
                if (newName.isNotEmpty()) {
                    section.name = newName
                    repository.saveDocumentStructure(document)
                    rebuildSectionTabs()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun confirmDeleteSection(section: Section) {
        AlertDialog.Builder(this)
            .setTitle("Abschnitt löschen?")
            .setMessage("\"${section.name}\" und alle enthaltenen Seiten werden entfernt.")
            .setPositiveButton(R.string.dialog_delete) { _, _ ->
                section.pages.forEach { repository.deletePage(it) }
                document.sections.remove(section)
                if (document.sections.isEmpty()) {
                    val d = Document.default()
                    document.sections.addAll(d.sections)
                    d.sections.forEach { s -> s.pages.forEach { repository.savePage(it) } }
                }
                repository.saveDocumentStructure(document)
                val nextSection = document.sections.first()
                currentSection = nextSection
                prefs.activeSectionId = nextSection.id
                val nextPage = nextSection.pages.firstOrNull()
                currentPage = nextPage
                inkView.page = nextPage
                prefs.activePageId = nextPage?.id
                updatePaperButton()
                rebuildSectionTabs()
                rebuildPageList()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ---------------- Engine settings (Tuning panel) ----------------

    private fun applyEngineSettingsToView() {
        inkView.strokeWidth = prefs.strokeWidth
        inkView.smoothingFactor = prefs.smoothingFactor
        inkView.strokeColor = prefs.color
        inkView.smoothingAlgo = prefs.smoothingAlgo
        inkView.predictionMode = prefs.predictionMode
        inkView.layerMode = prefs.layerMode
        inkView.bitmapMode = prefs.bitmapMode
        inkView.antialiased = prefs.antialias
        inkView.canvasClipping = prefs.canvasClipping
        inkView.damageRectEnabled = prefs.damageRect
        inkView.useUnbufferedDispatch = prefs.unbufferedDispatch
        inkView.tool = prefs.tool
        inkView.linedSpacingMm = prefs.linedSpacingMm
        inkView.gridSpacingMm = prefs.gridSpacingMm
        inkView.dotsSpacingMm = prefs.dotsSpacingMm

        tuningPanel.visibility = if (prefs.tuningVisible) View.VISIBLE else View.GONE
        metricsText.visibility = if (prefs.metricsVisible) View.VISIBLE else View.GONE
    }

    private fun setupTuningPanel() {
        setupWidthSlider()
        setupSmoothSlider()
        setupAlgoGroup()
        setupPredictionGroup()
        setupLayerGroup()
        setupBitmapGroup()

        bindCheck(R.id.toggleAntialias, prefs.antialias) {
            inkView.antialiased = it
            prefs.antialias = it
        }
        bindCheck(R.id.toggleClipping, prefs.canvasClipping) {
            inkView.canvasClipping = it
            prefs.canvasClipping = it
        }
        bindCheck(R.id.toggleDamageRect, prefs.damageRect) {
            inkView.damageRectEnabled = it
            prefs.damageRect = it
        }
        bindCheck(R.id.toggleUnbuffered, prefs.unbufferedDispatch) {
            inkView.useUnbufferedDispatch = it
            prefs.unbufferedDispatch = it
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            prefs.resetAll()
            recreate()
        }
    }

    private fun setupWidthSlider() {
        val slider = findViewById<SeekBar>(R.id.sliderWidth)
        val label = findViewById<TextView>(R.id.labelWidth)
        slider.max = 190
        slider.progress = ((prefs.strokeWidth - 1.0f) * 10f).toInt().coerceIn(0, 190)
        label.text = widthLabelText(prefs.strokeWidth)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = 1.0f + progress / 10f
                inkView.strokeWidth = v
                prefs.strokeWidth = v
                label.text = widthLabelText(v)
                updateWidthButtons()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })
    }

    private fun setupSmoothSlider() {
        val slider = findViewById<SeekBar>(R.id.sliderSmooth)
        val label = findViewById<TextView>(R.id.labelSmooth)
        slider.max = 100
        slider.progress = (prefs.smoothingFactor * 100).toInt().coerceIn(0, 100)
        label.text = smoothLabelText(prefs.smoothingFactor)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress / 100f
                inkView.smoothingFactor = v
                prefs.smoothingFactor = v
                label.text = smoothLabelText(v)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })
    }

    private fun setupAlgoGroup() {
        val group = findViewById<RadioGroup>(R.id.algoGroup)
        group.check(
            when (prefs.smoothingAlgo) {
                SmoothingAlgo.CATMULL_ROM -> R.id.algoCatmull
                SmoothingAlgo.WMA -> R.id.algoWma
                SmoothingAlgo.BEZIER -> R.id.algoBezier
            },
        )
        group.setOnCheckedChangeListener { _, id ->
            val v = when (id) {
                R.id.algoCatmull -> SmoothingAlgo.CATMULL_ROM
                R.id.algoWma -> SmoothingAlgo.WMA
                else -> SmoothingAlgo.BEZIER
            }
            inkView.smoothingAlgo = v
            prefs.smoothingAlgo = v
        }
    }

    private fun setupPredictionGroup() {
        val group = findViewById<RadioGroup>(R.id.predictionGroup)
        group.check(
            when (prefs.predictionMode) {
                PredictionMode.ANDROID -> R.id.predictionAndroid
                PredictionMode.LINEAR -> R.id.predictionLinear
                PredictionMode.OFF -> R.id.predictionOff
            },
        )
        group.setOnCheckedChangeListener { _, id ->
            val v = when (id) {
                R.id.predictionAndroid -> PredictionMode.ANDROID
                R.id.predictionLinear -> PredictionMode.LINEAR
                else -> PredictionMode.OFF
            }
            inkView.predictionMode = v
            prefs.predictionMode = v
        }
    }

    private fun setupLayerGroup() {
        val group = findViewById<RadioGroup>(R.id.layerGroup)
        group.check(
            when (prefs.layerMode) {
                LayerMode.HARDWARE -> R.id.layerHardware
                LayerMode.SOFTWARE -> R.id.layerSoftware
                LayerMode.NONE -> R.id.layerNone
            },
        )
        group.setOnCheckedChangeListener { _, id ->
            val v = when (id) {
                R.id.layerHardware -> LayerMode.HARDWARE
                R.id.layerSoftware -> LayerMode.SOFTWARE
                else -> LayerMode.NONE
            }
            inkView.layerMode = v
            prefs.layerMode = v
        }
    }

    private fun setupBitmapGroup() {
        val group = findViewById<RadioGroup>(R.id.bitmapGroup)
        group.check(
            when (prefs.bitmapMode) {
                BitmapMode.RGB_565 -> R.id.bitmapRgb
                BitmapMode.ARGB_8888 -> R.id.bitmapArgb
            },
        )
        group.setOnCheckedChangeListener { _, id ->
            val v = when (id) {
                R.id.bitmapRgb -> BitmapMode.RGB_565
                else -> BitmapMode.ARGB_8888
            }
            inkView.bitmapMode = v
            prefs.bitmapMode = v
        }
    }

    private fun bindCheck(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val cb = findViewById<CheckBox>(id)
        cb.isChecked = initial
        cb.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    // ---------------- Metrics ----------------

    private fun setupMetrics() {
        inkView.onMetricsChanged = {
            val now = SystemClock.uptimeMillis()
            if (now - lastMetricsUpdate >= METRICS_THROTTLE_MS) {
                lastMetricsUpdate = now
                renderMetrics()
            }
        }
        renderMetrics()
    }

    private fun renderMetrics() {
        val m = inkView.metrics
        val latency = if (m.downToFirstDrawMs < 0) "-" else m.downToFirstDrawMs.toString() + " ms"
        metricsText.text = buildString {
            append("Events/s:        ").append(format1(m.eventsPerSecond)).append('\n')
            append("avg historySize: ").append(format2(m.avgHistorySize)).append('\n')
            append("pointer:         ").append(m.lastPointerType).append('\n')
            append("FPS (stroke):    ").append(format1(m.fps)).append('\n')
            append("DOWN->onDraw:    ").append(latency).append('\n')
            append("strokes:         ").append(m.strokeCount)
        }
    }

    private fun widthLabelText(v: Float) = "Stroke Width: ${format1(v.toDouble())} px"
    private fun smoothLabelText(v: Float) = "Smoothing: ${format2(v.toDouble())}"
    private fun format1(v: Double) = "%.1f".format(v)
    private fun format2(v: Double) = "%.2f".format(v)

    private companion object {
        const val METRICS_THROTTLE_MS = 66L
    }
}
