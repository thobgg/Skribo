package com.inktest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lädt/speichert das Dokument und seine Seiten lokal unter
 *   getExternalFilesDir(null)/inktest/
 *     document.json        — Sektionen + Reihenfolge der Seiten-IDs
 *     pages/<uuid>.json    — Striche + Metadaten pro Seite
 *
 * Schreiben ist atomar (tmp-File + rename) und per 500 ms debounced,
 * damit kurz aufeinanderfolgende Striche nicht jeweils ein full-write triggern.
 */
class Repository(context: Context) {
    val rootDir: File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "inktest").also { it.mkdirs() }
    val assetsDir: File = File(rootDir, "assets").also { it.mkdirs() }
    private val pagesDir: File = File(rootDir, "pages").also { it.mkdirs() }
    private val documentFile: File = File(rootDir, "document.json")

    private val handler = Handler(Looper.getMainLooper())
    private val pendingSaves = mutableMapOf<String, Runnable>()
    private val debounceMs = 500L

    fun load(): Document {
        if (!documentFile.exists()) return Document.default()
        return try {
            val docJson = JSONObject(documentFile.readText())
            val pageStore = mutableMapOf<String, Page>()
            pagesDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { f ->
                runCatching { Page.fromJson(JSONObject(f.readText())) }
                    .onSuccess { pageStore[it.id] = it }
                    .onFailure { Log.w(TAG, "page ${f.name} invalid: $it") }
            }
            val doc = Document()
            val secs = docJson.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until secs.length()) {
                doc.sections.add(Section.fromJson(secs.getJSONObject(i), pageStore))
            }
            if (doc.sections.isEmpty()) Document.default() else doc
        } catch (t: Throwable) {
            Log.w(TAG, "load failed, returning default: $t")
            Document.default()
        }
    }

    fun saveDocumentStructure(doc: Document) = scheduleSave("doc") {
        val json = JSONObject().apply {
            put("sections", JSONArray().apply {
                doc.sections.forEach { put(it.toJson()) }
            })
        }
        writeAtomic(documentFile, json.toString())
    }

    fun savePage(page: Page) = scheduleSave("page-${page.id}") {
        writeAtomic(File(pagesDir, "${page.id}.json"), page.toJson().toString())
    }

    fun deletePage(page: Page) {
        pendingSaves.remove("page-${page.id}")?.let { handler.removeCallbacks(it) }
        File(pagesDir, "${page.id}.json").delete()
    }

    /** Erzwingt Ausführung aller noch ausstehenden Saves. */
    fun flush() {
        val runnables = pendingSaves.values.toList()
        pendingSaves.clear()
        runnables.forEach {
            handler.removeCallbacks(it)
            runCatching { it.run() }.onFailure { t -> Log.w(TAG, "flush failed: $t") }
        }
    }

    private fun scheduleSave(key: String, action: () -> Unit) {
        pendingSaves[key]?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            try { action() } catch (t: Throwable) { Log.w(TAG, "save [$key] failed: $t") }
            pendingSaves.remove(key)
        }
        pendingSaves[key] = r
        handler.postDelayed(r, debounceMs)
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
        const val TAG = "Repository"
    }
}
