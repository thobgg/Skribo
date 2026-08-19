package com.inktest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Android-Seite der Persistenz: wählt das Verzeichnis
 * (`getExternalFilesDir(null)/inktest/`) und debounced Schreibvorgänge um
 * 500 ms, damit kurz aufeinanderfolgende Striche nicht jeweils ein full-write
 * triggern. Das eigentliche Lesen/Schreiben macht der plattformfreie
 * [DocumentStore] — dieselbe Logik nutzt später der Desktop-Client.
 */
class Repository(context: Context) {
    private val store = DocumentStore(
        File(context.getExternalFilesDir(null) ?: context.filesDir, "inktest")
    )

    val rootDir: File get() = store.rootDir
    val assetsDir: File get() = store.assetsDir

    private val handler = Handler(Looper.getMainLooper())
    private val pendingSaves = mutableMapOf<String, Runnable>()
    private val debounceMs = 500L

    init {
        // Warnungen aus dem Kern in Logcat sichtbar machen.
        SkriboLog.sink = SkriboLog.Sink { tag, message -> Log.w(tag, message) }
    }

    fun load(): Document = store.load()

    fun saveDocumentStructure(doc: Document) = scheduleSave("doc") {
        store.writeDocumentStructure(doc)
    }

    fun savePage(page: Page) = scheduleSave("page-${page.id}") {
        store.writePage(page)
    }

    fun deletePage(page: Page) {
        pendingSaves.remove("page-${page.id}")?.let { handler.removeCallbacks(it) }
        store.deletePage(page)
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

    private companion object {
        const val TAG = "Repository"
    }
}
