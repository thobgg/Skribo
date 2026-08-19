package com.inktest

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PaperStyle { BLANK, LINED, GRID, DOTS, LEGAL }

enum class Tool { PEN, HIGHLIGHTER, LINE, TEXT, IMAGE, ERASER }

/**
 * Eine rückgängig-machbare Änderung an einer [Page]. `redo` wendet die Änderung an
 * (auch beim erstmaligen Ausführen über [Page.applyAction]), `undo` macht sie rückgängig.
 * Alle Seiten-Mutationen laufen über diese Aktionen, damit Zeichnen, Radieren,
 * Text/Bild und Papierwechsel einheitlich über eine Historie undo/redo-bar sind.
 */
sealed interface EditAction {
    fun redo(page: Page)
    fun undo(page: Page)
}

class AddStroke(private val stroke: Stroke) : EditAction {
    override fun redo(page: Page) { page.strokes.add(stroke) }
    override fun undo(page: Page) { page.strokes.remove(stroke) }
}

/** Radiert einen Stroke; merkt sich seine Position, um ihn beim Undo z-korrekt einzufügen. */
class EraseStroke(private val stroke: Stroke, private val index: Int) : EditAction {
    override fun redo(page: Page) { page.strokes.remove(stroke) }
    override fun undo(page: Page) {
        page.strokes.add(index.coerceIn(0, page.strokes.size), stroke)
    }
}

/** Leert alle Striche einer Seite (undo-bar). */
class ClearStrokes(private val removed: List<Stroke>) : EditAction {
    override fun redo(page: Page) { page.strokes.removeAll(removed.toSet()) }
    override fun undo(page: Page) { page.strokes.addAll(removed) }
}

class AddTextBox(private val box: TextBox) : EditAction {
    override fun redo(page: Page) { page.textBoxes.add(box) }
    override fun undo(page: Page) { page.textBoxes.remove(box) }
}

class RemoveTextBox(private val box: TextBox, private val index: Int) : EditAction {
    override fun redo(page: Page) { page.textBoxes.remove(box) }
    override fun undo(page: Page) {
        page.textBoxes.add(index.coerceIn(0, page.textBoxes.size), box)
    }
}

class EditTextBoxContent(
    private val box: TextBox,
    private val oldContent: String,
    private val newContent: String,
) : EditAction {
    override fun redo(page: Page) { box.content = newContent }
    override fun undo(page: Page) { box.content = oldContent }
}

class AddImageBox(private val box: ImageBox) : EditAction {
    override fun redo(page: Page) { page.imageBoxes.add(box) }
    override fun undo(page: Page) { page.imageBoxes.remove(box) }
}

class RemoveImageBox(private val box: ImageBox, private val index: Int) : EditAction {
    override fun redo(page: Page) { page.imageBoxes.remove(box) }
    override fun undo(page: Page) {
        page.imageBoxes.add(index.coerceIn(0, page.imageBoxes.size), box)
    }
}

class ChangePaperStyle(private val old: PaperStyle, private val new: PaperStyle) : EditAction {
    override fun redo(page: Page) { page.paperStyle = new }
    override fun undo(page: Page) { page.paperStyle = old }
}

class AddLinkBox(private val box: LinkBox) : EditAction {
    override fun redo(page: Page) { page.linkBoxes.add(box) }
    override fun undo(page: Page) { page.linkBoxes.remove(box) }
}

class RemoveLinkBox(private val box: LinkBox, private val index: Int) : EditAction {
    override fun redo(page: Page) { page.linkBoxes.remove(box) }
    override fun undo(page: Page) {
        page.linkBoxes.add(index.coerceIn(0, page.linkBoxes.size), box)
    }
}

class EditLinkBox(
    private val box: LinkBox,
    private val oldUrl: String,
    private val oldTitle: String,
    private val newUrl: String,
    private val newTitle: String,
) : EditAction {
    override fun redo(page: Page) { box.url = newUrl; box.title = newTitle }
    override fun undo(page: Page) { box.url = oldUrl; box.title = oldTitle }
}

class TextBox(
    val id: String = UUID.randomUUID().toString(),
    override var x: Float,
    override var y: Float,
    var content: String,
    var fontSize: Float = 18f,
    var color: Int = 0xFF0F1729.toInt(),
) : PositionedBox {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("content", content)
        put("fontSize", fontSize.toDouble())
        put("color", color)
    }

    companion object {
        fun fromJson(j: JSONObject): TextBox = TextBox(
            id = j.optString("id", UUID.randomUUID().toString()),
            x = j.getDouble("x").toFloat(),
            y = j.getDouble("y").toFloat(),
            content = j.optString("content", ""),
            fontSize = j.optDouble("fontSize", 18.0).toFloat(),
            color = j.optInt("color", 0xFF0F1729.toInt()),
        )
    }
}

class ImageBox(
    val id: String = UUID.randomUUID().toString(),
    override var x: Float,
    override var y: Float,
    var width: Float,
    var height: Float,
    var assetPath: String,
) : PositionedBox {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("width", width.toDouble())
        put("height", height.toDouble())
        put("assetPath", assetPath)
    }

    companion object {
        fun fromJson(j: JSONObject): ImageBox = ImageBox(
            id = j.optString("id", UUID.randomUUID().toString()),
            x = j.getDouble("x").toFloat(),
            y = j.getDouble("y").toFloat(),
            width = j.getDouble("width").toFloat(),
            height = j.getDouble("height").toFloat(),
            assetPath = j.optString("assetPath", ""),
        )
    }
}

class Page(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var paperStyle: PaperStyle = PaperStyle.BLANK,
    var parentId: String? = null,
    /**
     * Seitenformat und damit das Koordinatensystem der Seite (siehe [PageFormat]).
     * [PageFormat.FREE] entspricht dem unbegrenzten Canvas von schemaVersion 1.
     */
    var format: PageFormat = PageFormat.FREE,
    /** Gerenderte PDF-Seite bzw. Folie, über die geschrieben wird. */
    var background: PageBackground? = null,
    val strokes: MutableList<Stroke> = mutableListOf(),
    val textBoxes: MutableList<TextBox> = mutableListOf(),
    val imageBoxes: MutableList<ImageBox> = mutableListOf(),
    val linkBoxes: MutableList<LinkBox> = mutableListOf(),
) {
    private val undoStack = ArrayDeque<EditAction>()
    private val redoStack = ArrayDeque<EditAction>()

    /** Wendet eine Aktion an, legt sie auf den Undo-Stack und verwirft den Redo-Stack. */
    fun applyAction(action: EditAction) {
        action.redo(this)
        undoStack.addLast(action)
        redoStack.clear()
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
    }

    /** Bequemer Alias fürs Zeichnen. */
    fun addStroke(s: Stroke) = applyAction(AddStroke(s))

    /** Leert alle Striche (undo-bar). No-op, wenn schon leer. */
    fun clearStrokes() {
        if (strokes.isEmpty()) return
        applyAction(ClearStrokes(strokes.toList()))
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(): Boolean {
        val a = undoStack.removeLastOrNull() ?: return false
        a.undo(this)
        redoStack.addLast(a)
        return true
    }

    fun redo(): Boolean {
        val a = redoStack.removeLastOrNull() ?: return false
        a.redo(this)
        undoStack.addLast(a)
        return true
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("paperStyle", paperStyle.name)
        put("format", format.name)
        background?.let { put("background", it.toJson()) }
        if (parentId != null) put("parentId", parentId)
        val arr = JSONArray()
        strokes.forEach { arr.put(it.toJson()) }
        put("strokes", arr)
        val tArr = JSONArray()
        textBoxes.forEach { tArr.put(it.toJson()) }
        put("textBoxes", tArr)
        val iArr = JSONArray()
        imageBoxes.forEach { iArr.put(it.toJson()) }
        put("imageBoxes", iArr)
        val lArr = JSONArray()
        linkBoxes.forEach { lArr.put(it.toJson()) }
        put("linkBoxes", lArr)
    }

    companion object {
        const val MAX_HISTORY = 100

        fun fromJson(j: JSONObject): Page {
            val paper = runCatching { PaperStyle.valueOf(j.getString("paperStyle")) }
                .getOrDefault(PaperStyle.BLANK)
            val p = Page(
                id = j.getString("id"),
                title = j.optString("title", "Seite"),
                paperStyle = paper,
                parentId = if (j.has("parentId") && !j.isNull("parentId")) j.getString("parentId") else null,
                // Fehlt beides, ist es eine Seite aus schemaVersion 1: freier Canvas.
                format = PageFormat.parse(j.optString("format", null)),
                background = j.optJSONObject("background")
                    ?.let { runCatching { PageBackground.fromJson(it) }.getOrNull() },
            )
            j.optJSONArray("strokes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching { Stroke.fromJson(arr.getJSONObject(i)) }
                        .onSuccess { p.strokes.add(it) }
                }
            }
            j.optJSONArray("textBoxes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching { TextBox.fromJson(arr.getJSONObject(i)) }
                        .onSuccess { p.textBoxes.add(it) }
                }
            }
            j.optJSONArray("imageBoxes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching { ImageBox.fromJson(arr.getJSONObject(i)) }
                        .onSuccess { p.imageBoxes.add(it) }
                }
            }
            j.optJSONArray("linkBoxes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching { LinkBox.fromJson(arr.getJSONObject(i)) }
                        .onSuccess { p.linkBoxes.add(it) }
                }
            }
            return p
        }
    }
}

class Section(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var color: Int,
    var webdavPath: String? = null,
    val pages: MutableList<Page> = mutableListOf(),
) {
    fun depthOf(page: Page): Int {
        var d = 0
        var current: Page? = page
        val byId = pages.associateBy { it.id }
        while (current?.parentId != null) {
            current = byId[current.parentId]
            if (current == null) return d
            d++
        }
        return d
    }

    fun addRootPage(page: Page) {
        pages.add(page)
    }

    fun addSubpageOf(parent: Page, newPage: Page) {
        newPage.parentId = parent.id
        val idx = pages.indexOf(parent)
        if (idx < 0) {
            pages.add(newPage)
            return
        }
        // Hinter die bereits vorhandenen Unterseiten einsortieren. Direkt hinter
        // die Elternseite gesetzt, stünden neue Unterseiten sonst vor den älteren.
        val byId = pages.associateBy { it.id }
        var insert = idx + 1
        while (insert < pages.size && isDescendantOf(pages[insert], parent, byId)) insert++
        pages.add(insert, newPage)
    }

    fun removePage(page: Page): List<Page> {
        val byId = pages.associateBy { it.id }
        val removed = mutableListOf<Page>()
        val toRemove = pages.filter { isDescendantOf(it, page, byId) } + page
        removed.addAll(toRemove)
        pages.removeAll(toRemove.toSet())
        return removed
    }

    private fun isDescendantOf(p: Page, ancestor: Page, byId: Map<String, Page>): Boolean {
        var current: Page? = p
        while (current?.parentId != null) {
            if (current.parentId == ancestor.id) return true
            current = byId[current.parentId]
        }
        return false
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
        if (webdavPath != null) put("webdavPath", webdavPath)
        val ids = JSONArray()
        pages.forEach { ids.put(it.id) }
        put("pageIds", ids)
    }

    companion object {
        fun fromJson(j: JSONObject, pageStore: Map<String, Page>): Section {
            val section = Section(
                id = j.getString("id"),
                name = j.optString("name", "Abschnitt"),
                color = j.optInt("color", DEFAULT_COLOR),
                webdavPath = if (j.has("webdavPath") && !j.isNull("webdavPath")) j.getString("webdavPath") else null,
            )
            val ids = j.optJSONArray("pageIds") ?: return section
            for (i in 0 until ids.length()) {
                pageStore[ids.getString(i)]?.let { section.pages.add(it) }
            }
            return section
        }

        const val DEFAULT_COLOR: Int = 0xFF4A90E2.toInt()
    }
}

class Document(val sections: MutableList<Section> = mutableListOf()) {
    companion object {
        fun default(): Document {
            val d = Document()
            val s = Section(name = "Analysis", color = 0xFF4A90E2.toInt())
            s.pages.add(Page(title = "Seite 1", paperStyle = PaperStyle.LINED))
            d.sections.add(s)
            return d
        }
    }
}
