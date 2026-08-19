package com.inktest

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Plattformfreie lokale Persistenz des Dokuments unter [rootDir]:
 *
 *     document.json                    — Sektionen + Reihenfolge der Seiten-IDs
 *     pages/<uuid>.json                — die *stabile Basis* einer Seite
 *     annotations/<schuljahr>/<uuid>.json — die Handschrift dieses Schuljahrs
 *     assets/                          — Vorlagen, Bilder, Original-PDFs
 *
 * Die Trennung von Basis und Annotationen ist der Kern des Wiederverwendens:
 * Dieselbe Vorlage lässt sich Jahr für Jahr neu beschreiben, das Vorjahr bleibt
 * daneben erhalten und nachschlagbar.
 *
 * Schreiben ist atomar (tmp-File + rename). Das Debouncing und die Wahl des
 * Verzeichnisses sind bewusst *nicht* hier, sondern beim jeweiligen Client:
 * Android debounced über einen Main-Looper-Handler, der Desktop-Client später
 * über seinen eigenen Scheduler.
 */
class DocumentStore(val rootDir: File) {
    val assetsDir: File = File(rootDir, "assets")
    private val pagesDir: File = File(rootDir, "pages")
    private val annotationsDir: File = File(rootDir, "annotations")
    private val documentFile: File = File(rootDir, "document.json")

    init {
        rootDir.mkdirs()
        assetsDir.mkdirs()
        pagesDir.mkdirs()
    }

    private fun yearDir(year: String): File = File(annotationsDir, year)

    /** Alle Schuljahre, für die Annotationen vorliegen — neuestes zuerst. */
    fun listYears(): List<String> =
        annotationsDir.listFiles { f: File -> f.isDirectory }
            ?.map { it.name }?.sortedDescending().orEmpty()

    /**
     * Lädt das Dokument mit der Annotationsebene von [year]. Fehlt sie, kommt
     * die Seite ohne Handschrift — die Vorlage ist dann bereit für ein frisches
     * Schuljahr.
     */
    fun load(year: String): Document {
        if (!documentFile.exists()) return Document.default()
        return try {
            val docJson = JSONObject(documentFile.readText())
            val pageStore = mutableMapOf<String, Page>()
            val annotations = yearDir(year)
            pagesDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { f ->
                runCatching { Page.fromJson(JSONObject(f.readText())) }
                    .onSuccess { page ->
                        val layer = File(annotations, "${page.id}.json")
                        if (layer.exists()) {
                            runCatching { page.applyAnnotations(JSONObject(layer.readText())) }
                                .onFailure { SkriboLog.w(TAG, "annotations ${layer.name} invalid: $it") }
                        }
                        // Sonst bleiben etwaige Striche aus der Basis stehen —
                        // so wandern Dokumente aus der Zeit vor den Jahresebenen
                        // beim nächsten Speichern von selbst in die neue Ablage.
                        page.clearHistory()
                        pageStore[page.id] = page
                    }
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

    /** Schreibt die Basis und die Handschrift von [year] getrennt. */
    fun writePage(page: Page, year: String) {
        writeAtomic(File(pagesDir, "${page.id}.json"), page.toBaseJson().toString())
        val dir = yearDir(year)
        dir.mkdirs()
        writeAtomic(File(dir, "${page.id}.json"), page.toAnnotationsJson(year).toString())
    }

    /** Löscht die Basis **und** die Annotationen aller Schuljahre. */
    fun deletePage(page: Page) {
        File(pagesDir, "${page.id}.json").delete()
        annotationsDir.listFiles { f: File -> f.isDirectory }?.forEach { dir ->
            File(dir, "${page.id}.json").delete()
        }
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
