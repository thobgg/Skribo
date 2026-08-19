package com.inktest.desktop

import com.inktest.Document
import com.inktest.DocumentStore
import com.inktest.Page
import com.inktest.SkriboLog
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Desktop-Seite der Persistenz — Gegenstück zum `Repository` des Board-Clients:
 * Schreibvorgänge werden um 400 ms gebündelt, damit Tippen im Textfeld nicht
 * bei jedem Zeichen die Seite neu schreibt. Das eigentliche Lesen/Schreiben
 * macht der geteilte [DocumentStore].
 */
class DesktopRepository(private val store: DocumentStore) {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "skribo-save").apply { isDaemon = true }
    }
    private val pending = mutableMapOf<String, ScheduledFuture<*>>()

    fun load(): Document = store.load()

    fun saveDocumentStructure(doc: Document) = schedule("doc") {
        store.writeDocumentStructure(doc)
    }

    fun savePage(page: Page) = schedule("page-${page.id}") {
        store.writePage(page)
    }

    fun deletePage(page: Page) {
        synchronized(pending) { pending.remove("page-${page.id}")?.cancel(false) }
        store.deletePage(page)
    }

    /** Schreibt alles Ausstehende sofort — beim Schließen des Fensters. */
    fun flush() {
        val tasks = synchronized(pending) {
            pending.values.toList().also { pending.clear() }
        }
        // Ein abgebrochener Task würde seinen Schreibvorgang verlieren, deshalb
        // laufen lassen und nur auf den Abschluss warten.
        tasks.forEach { runCatching { it.get(5, TimeUnit.SECONDS) } }
    }

    private fun schedule(key: String, action: () -> Unit) {
        synchronized(pending) {
            pending.remove(key)?.cancel(false)
            pending[key] = scheduler.schedule({
                try {
                    action()
                } catch (t: Throwable) {
                    SkriboLog.w(TAG, "save [$key] failed: $t")
                } finally {
                    synchronized(pending) { pending.remove(key) }
                }
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
        }
    }

    private companion object {
        const val TAG = "DesktopRepository"
        const val DEBOUNCE_MS = 400L
    }
}
