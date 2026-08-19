package com.inktest

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Plattformfreie lokale Persistenz des Dokuments unter [rootDir]:
 *
 *     document.json        — Sektionen + Reihenfolge der Seiten-IDs
 *     pages/<uuid>.json    — Striche + Metadaten pro Seite
 *     assets/              — eingebettete Bilder (und ab schemaVersion 2 weitere Medien)
 *
 * Schreiben ist atomar (tmp-File + rename). Das Debouncing und die Wahl des
 * Verzeichnisses sind bewusst *nicht* hier, sondern beim jeweiligen Client:
 * Android debounced über einen Main-Looper-Handler, der Desktop-Client später
 * über seinen eigenen Scheduler.
 */
class DocumentStore(val rootDir: File) {
    val assetsDir: File = File(rootDir, "assets")
    private val pagesDir: File = File(rootDir, "pages")
    private val documentFile: File = File(rootDir, "document.json")

    init {
        rootDir.mkdirs()
        assetsDir.mkdirs()
        pagesDir.mkdirs()
    }

    fun load(): Document {
        if (!documentFile.exists()) return Document.default()
        return try {
            val docJson = JSONObject(documentFile.readText())
            val pageStore = mutableMapOf<String, Page>()
            pagesDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { f ->
                runCatching { Page.fromJson(JSONObject(f.readText())) }
                    .onSuccess { pageStore[it.id] = it }
                    .onFailure { SkriboLog.w(TAG, "page ${f.name} invalid: $it") }
            }
            val doc = Document()
            val secs = docJson.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until secs.length()) {
                doc.sections.add(Section.fromJson(secs.getJSONObject(i), pageStore))
            }
            if (doc.sections.isEmpty()) Document.default() else doc
        } catch (t: Throwable) {
            SkriboLog.w(TAG, "load failed, returning default: $t")
            Document.default()
        }
    }

    fun writeDocumentStructure(doc: Document) {
        val json = JSONObject().apply {
            put("sections", JSONArray().apply {
                doc.sections.forEach { put(it.toJson()) }
            })
        }
        writeAtomic(documentFile, json.toString())
    }

    fun writePage(page: Page) {
        writeAtomic(File(pagesDir, "${page.id}.json"), page.toJson().toString())
    }

    fun deletePage(page: Page) {
        File(pagesDir, "${page.id}.json").delete()
    }

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            // Fallback: direct write if rename fails (shouldn't on internal/external storage).
            target.writeText(content)
            tmp.delete()
        }
    }

    private companion object {
        const val TAG = "DocumentStore"
    }
}
