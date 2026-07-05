package com.inktest

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PaperStyle { BLANK, LINED, GRID, DOTS, LEGAL }

enum class Tool { PEN, HIGHLIGHTER, LINE, TEXT, IMAGE, ERASER }

class TextBox(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var content: String,
    var fontSize: Float = 18f,
    var color: Int = 0xFF0F1729.toInt(),
) {
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
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var assetPath: String,
) {
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
    val strokes: MutableList<Stroke> = mutableListOf(),
    val textBoxes: MutableList<TextBox> = mutableListOf(),
    val imageBoxes: MutableList<ImageBox> = mutableListOf(),
) {
    val redoStack: ArrayDeque<Stroke> = ArrayDeque()

    fun addStroke(s: Stroke) {
        strokes.add(s)
        redoStack.clear()
    }

    fun undo(): Boolean {
        val s = strokes.removeLastOrNull() ?: return false
        redoStack.addLast(s)
        return true
    }

    fun redo(): Boolean {
        val s = redoStack.removeLastOrNull() ?: return false
        strokes.add(s)
        return true
    }

    fun clear() {
        strokes.clear()
        redoStack.clear()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("paperStyle", paperStyle.name)
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
    }

    companion object {
        fun fromJson(j: JSONObject): Page {
            val paper = runCatching { PaperStyle.valueOf(j.getString("paperStyle")) }
                .getOrDefault(PaperStyle.BLANK)
            val p = Page(
                id = j.getString("id"),
                title = j.optString("title", "Seite"),
                paperStyle = paper,
                parentId = if (j.has("parentId") && !j.isNull("parentId")) j.getString("parentId") else null,
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
        if (idx >= 0) pages.add(idx + 1, newPage) else pages.add(newPage)
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
