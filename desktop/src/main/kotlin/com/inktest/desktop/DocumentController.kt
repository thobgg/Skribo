package com.inktest.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.inktest.AddImageBox
import com.inktest.AddLinkBox
import com.inktest.AddTextBox
import com.inktest.Document
import com.inktest.EditLinkBox
import com.inktest.EditTextBoxContent
import com.inktest.ImageBox
import com.inktest.LinkBox
import com.inktest.MoveBox
import com.inktest.Page
import com.inktest.PaperStyle
import com.inktest.PositionedBox
import com.inktest.RemoveImageBox
import com.inktest.ResizeImageBox
import com.inktest.RemoveLinkBox
import com.inktest.RemoveTextBox
import com.inktest.SchoolYear
import com.inktest.SkriboSync
import com.inktest.Section
import com.inktest.TextBox

/**
 * Bindeglied zwischen dem geteilten (bewusst Compose-freien) Datenmodell und der
 * Oberfläche. Das Modell ist gewöhnlicher Kotlin-Zustand — Compose bemerkt
 * Änderungen daran nicht von selbst. Deshalb zählt [revision] jede Bearbeitung
 * hoch; Composables, die [revision] lesen, zeichnen sich daraufhin neu.
 *
 * Jede Bearbeitung geht durch [edit] und wird dadurch einheitlich gespeichert.
 */
class DocumentController(
    val document: Document,
    private val repository: DesktopRepository,
    private val prefs: DesktopPrefs? = null,
) {
    /** Zähler, der jede Modelländerung sichtbar macht. Nur lesen. */
    var revision by mutableStateOf(0)
        private set

    /** Schuljahr, dessen Annotationsebene gerade bearbeitet wird. */
    var schoolYear by mutableStateOf(prefs?.activeSchoolYear ?: repository.year)
        private set

    /** Schuljahre, für die schon Handschrift vorliegt — plus das laufende. */
    fun availableYears(): List<String> =
        (repository.listYears() + schoolYear + SchoolYear.current()).distinct().sortedDescending()

    /**
     * Wechselt die Annotationsebene: Die Vorlagen bleiben, die Handschrift wird
     * durch die des gewählten Jahres ersetzt. Für ein noch leeres Jahr heißt das
     * eine saubere Fläche über demselben Material.
     */
    fun switchYear(year: String) {
        if (year == schoolYear) return
        repository.flush()
        repository.year = year
        schoolYear = year
        prefs?.activeSchoolYear = year

        val reloaded = repository.load()
        document.sections.clear()
        document.sections.addAll(reloaded.sections)
        activeSection = document.sections.firstOrNull { it.id == activeSection?.id }
            ?: document.sections.firstOrNull()
        activePage = activeSection?.let { section ->
            section.pages.firstOrNull { it.id == activePage?.id } ?: section.pages.firstOrNull()
        }
        revision++
    }

    // Beim Start dort weitermachen, wo zuletzt gearbeitet wurde.
    var activeSection by mutableStateOf(
        document.sections.firstOrNull { it.id == prefs?.activeSectionId }
            ?: document.sections.firstOrNull()
    )
        private set

    var activePage by mutableStateOf(
        activeSection?.let { section ->
            section.pages.firstOrNull { it.id == prefs?.activePageId }
                ?: section.pages.firstOrNull()
        }
    )
        private set

    // ---------------- Auswahl ----------------

    fun selectSection(section: Section) {
        activeSection = section
        activePage = section.pages.firstOrNull()
        rememberSelection()
    }

    fun selectPage(page: Page) {
        activePage = page
        rememberSelection()
    }

    private fun rememberSelection() {
        prefs?.activeSectionId = activeSection?.id
        prefs?.activePageId = activePage?.id
    }

    // ---------------- Abschnitte ----------------

    fun addSection(name: String) = edit {
        val section = Section(name = name, color = Section.DEFAULT_COLOR)
        section.addRootPage(Page(title = "Seite 1", paperStyle = PaperStyle.LINED))
        document.sections.add(section)
        activeSection = section
        activePage = section.pages.first()
        section.pages.forEach(repository::savePage)
    }

    fun renameSection(section: Section, name: String) = edit {
        section.name = name
    }

    fun deleteSection(section: Section) = edit {
        section.pages.forEach(repository::deletePage)
        document.sections.remove(section)
        if (activeSection === section) {
            activeSection = document.sections.firstOrNull()
            activePage = activeSection?.pages?.firstOrNull()
        }
    }

    fun setSectionWebdavPath(section: Section, path: String) = edit {
        // Leer bedeutet laut Schema: Abschnitt bleibt lokal, wird nicht gesynct.
        section.webdavPath = path.trim().ifEmpty { null }
    }

    // ---------------- Seiten ----------------

    fun addPage(title: String) {
        val section = activeSection ?: return
        edit {
            val page = Page(title = title, paperStyle = PaperStyle.LINED)
            section.addRootPage(page)
            activePage = page
            repository.savePage(page)
        }
    }

    fun addSubpage(parent: Page, title: String) {
        val section = activeSection ?: return
        edit {
            val page = Page(title = title, paperStyle = parent.paperStyle)
            section.addSubpageOf(parent, page)
            activePage = page
            repository.savePage(page)
        }
    }

    fun renamePage(page: Page, title: String) = edit {
        page.title = title
        repository.savePage(page)
    }

    fun deletePage(page: Page) {
        val section = activeSection ?: return
        edit {
            // removePage nimmt die Unterseiten mit — die müssen auch von der Platte.
            val removed = section.removePage(page)
            removed.forEach(repository::deletePage)
            if (removed.any { it === activePage }) {
                activePage = section.pages.firstOrNull()
            }
        }
    }

    fun setPaperStyle(page: Page, style: PaperStyle) = edit {
        page.paperStyle = style
        repository.savePage(page)
    }

    // ---------------- Medien ----------------

    /**
     * Legt für jede Seite von [imported] eine Skribo-Seite an — der „Ausdruck".
     * Ist gerade eine Seite ausgewählt, hängen die neuen Seiten als Unterseiten
     * darunter, sonst landen sie auf oberster Ebene. Gibt die Anzahl zurück.
     */
    fun addImportedPages(imported: List<PdfImporter.ImportedPage>): Int {
        val section = activeSection ?: return 0
        if (imported.isEmpty()) return 0
        val parent = activePage?.takeIf { it.parentId == null }
        edit {
            imported.forEach { source ->
                val page = Page(
                    title = source.title,
                    // Auf einer gerenderten Vorlage stört jedes Raster.
                    paperStyle = PaperStyle.BLANK,
                    format = source.format,
                    background = source.background,
                )
                if (parent != null) section.addSubpageOf(parent, page) else section.addRootPage(page)
                repository.savePage(page)
                activePage = page
            }
        }
        return imported.size
    }

    fun addImageBox(page: Page, box: ImageBox) = edit {
        page.applyAction(AddImageBox(box))
        repository.savePage(page)
    }

    fun deleteImageBox(page: Page, box: ImageBox) = edit {
        val idx = page.imageBoxes.indexOf(box)
        if (idx >= 0) {
            page.applyAction(RemoveImageBox(box, idx))
            repository.savePage(page)
        }
    }

    // ---------------- Links (YouTube) ----------------

    fun addLinkBox(page: Page, x: Float, y: Float, url: String, title: String) = edit {
        page.applyAction(AddLinkBox(LinkBox(x = x, y = y, url = url, title = title)))
        repository.savePage(page)
    }

    fun editLinkBox(page: Page, box: LinkBox, url: String, title: String) = edit {
        page.applyAction(EditLinkBox(box, box.url, box.title, url, title))
        repository.savePage(page)
    }

    fun deleteLinkBox(page: Page, box: LinkBox) = edit {
        val idx = page.linkBoxes.indexOf(box)
        if (idx >= 0) {
            page.applyAction(RemoveLinkBox(box, idx))
            repository.savePage(page)
        }
    }

    // ---------------- Textfelder ----------------

    /** Gibt das neue Feld zurück, damit direkt hineingeschrieben werden kann. */
    fun addTextBox(page: Page, x: Float, y: Float, content: String): TextBox {
        val box = TextBox(x = x, y = y, content = content)
        edit {
            page.applyAction(AddTextBox(box))
            repository.savePage(page)
        }
        return box
    }

    fun editTextBox(page: Page, box: TextBox, content: String) = edit {
        page.applyAction(EditTextBoxContent(box, box.content, content))
        repository.savePage(page)
    }

    fun deleteTextBox(page: Page, box: TextBox) = edit {
        val idx = page.textBoxes.indexOf(box)
        if (idx >= 0) {
            page.applyAction(RemoveTextBox(box, idx))
            repository.savePage(page)
        }
    }

    fun undo(page: Page) = edit {
        if (page.undo()) repository.savePage(page)
    }

    fun redo(page: Page) = edit {
        if (page.redo()) repository.savePage(page)
    }

    // ---------------- Verschieben und Größe ändern ----------------

    /**
     * Setzt die Position während des Ziehens **ohne** Undo-Schritt. Erst
     * [commitMove] macht daraus eine Aktion — sonst läge nach einer einzigen
     * Zugbewegung ein Undo-Schritt je Mausbewegung auf dem Stapel.
     */
    fun dragBoxTo(box: PositionedBox, x: Float, y: Float) {
        box.x = x
        box.y = y
        revision++
    }

    /** Schließt eine Zugbewegung ab und macht sie als *ein* Schritt rückgängig-machbar. */
    fun commitMove(page: Page, box: PositionedBox, startX: Float, startY: Float) {
        if (box.x == startX && box.y == startY) return
        page.applyAction(MoveBox(box, startX, startY, box.x, box.y))
        revision++
        repository.savePage(page)
    }

    /** Größenänderung während des Ziehens, ebenfalls ohne Undo-Schritt. */
    fun dragImageSize(box: ImageBox, x: Float, y: Float, width: Float, height: Float) {
        box.x = x
        box.y = y
        box.width = width.coerceAtLeast(MIN_IMAGE_SIZE_PT)
        box.height = height.coerceAtLeast(MIN_IMAGE_SIZE_PT)
        revision++
    }

    fun commitImageResize(
        page: Page,
        box: ImageBox,
        startX: Float,
        startY: Float,
        startWidth: Float,
        startHeight: Float,
    ) {
        if (box.x == startX && box.y == startY &&
            box.width == startWidth && box.height == startHeight
        ) return
        page.applyAction(
            ResizeImageBox(
                box, startX, startY, startWidth, startHeight,
                box.x, box.y, box.width, box.height,
            )
        )
        revision++
        repository.savePage(page)
    }

    fun flush() = repository.flush()

    // ---------------- Synchronisierung ----------------

    val webdavConfigured: Boolean get() = prefs?.webdavConfigured == true

    val webdavServer: String get() = prefs?.webdavServer.orEmpty()
    val webdavUsername: String get() = prefs?.webdavUsername.orEmpty()
    val webdavPassword: String get() = prefs?.webdavPassword.orEmpty()

    fun setWebdav(server: String, user: String, password: String) {
        prefs?.webdavServer = server
        prefs?.webdavUsername = user
        prefs?.webdavPassword = password
        revision++
    }

    private fun sync(server: String, user: String, password: String) = SkriboSync(
        settings = {
            SkriboSync.SyncConfig(server, user, password, schoolYear)
        },
        assetRoot = repository.rootDir,
    )

    /** Prüft die Verbindung; gibt `null` zurück, wenn alles stimmt. */
    fun testConnection(server: String, user: String, password: String): String? =
        runCatching { sync(server, user, password).testConnection() }
            .fold(onSuccess = { null }, onFailure = { it.message ?: "Unbekannter Fehler" })

    /**
     * Schiebt alle Abschnitte mit WebDAV-Pfad auf den Server — Basis, die Ebene
     * des aktuellen Schuljahrs und die Assets. Vorher wird alles Ausstehende
     * geschrieben, sonst ginge die letzte Änderung nicht mit.
     */
    fun push(): SkriboSync.SyncResult {
        repository.flush()
        return sync(webdavServer, webdavUsername, webdavPassword).pushDocument(document)
    }

    /** Wo importierte Bilder und gerenderte PDF-Seiten liegen. */
    val assetsDir: java.io.File get() = repository.assetsDir
    val rootDir: java.io.File get() = repository.rootDir

    /**
     * Führt eine Modelländerung aus, meldet sie an Compose und schreibt die
     * Dokumentstruktur. Seiteninhalte speichern die Aufrufer selbst — nicht
     * jede Änderung betrifft eine Seite.
     */
    private companion object {
        /** Kleiner ließe sich ein Bild nicht mehr greifen. */
        const val MIN_IMAGE_SIZE_PT = 24f
    }

    private inline fun edit(block: () -> Unit) {
        block()
        revision++
        // Anlegen und Löschen verschieben die Auswahl mit — also hier festhalten.
        rememberSelection()
        repository.saveDocumentStructure(document)
    }
}
