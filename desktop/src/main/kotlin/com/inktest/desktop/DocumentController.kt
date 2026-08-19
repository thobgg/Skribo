package com.inktest.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.inktest.AddTextBox
import com.inktest.Document
import com.inktest.EditTextBoxContent
import com.inktest.Page
import com.inktest.PaperStyle
import com.inktest.RemoveTextBox
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

    // ---------------- Textfelder ----------------

    fun addTextBox(page: Page, x: Float, y: Float, content: String) = edit {
        page.applyAction(AddTextBox(TextBox(x = x, y = y, content = content)))
        repository.savePage(page)
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

    fun flush() = repository.flush()

    /**
     * Führt eine Modelländerung aus, meldet sie an Compose und schreibt die
     * Dokumentstruktur. Seiteninhalte speichern die Aufrufer selbst — nicht
     * jede Änderung betrifft eine Seite.
     */
    private inline fun edit(block: () -> Unit) {
        block()
        revision++
        // Anlegen und Löschen verschieben die Auswahl mit — also hier festhalten.
        rememberSelection()
        repository.saveDocumentStructure(document)
    }
}
