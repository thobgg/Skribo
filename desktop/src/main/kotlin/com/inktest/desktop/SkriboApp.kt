package com.inktest.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inktest.LinkBox
import com.inktest.Page
import com.inktest.PageFormat
import com.inktest.PaperStyle
import com.inktest.Section
import com.inktest.TextBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OneNote-artige Grundstruktur: Abschnitte als Reiter oben, Seiten (mit
 * eingerückten Unterseiten) links, die Seite selbst rechts. Bearbeiten läuft
 * über die „+"-Schaltflächen und das Kontextmenü (Rechtsklick) — bewusst wie
 * in OneNote, wo Umbenennen/Löschen ebenfalls dort sitzen.
 */
@Composable
fun SkriboApp(controller: DocumentController, assets: AssetCache) {
    // Löst das Neuzeichnen aus, wenn sich am (Compose-freien) Modell etwas ändert.
    @Suppress("UNUSED_EXPRESSION") controller.revision

    var dialog by remember { mutableStateOf<AppDialog?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /** Import läuft im Hintergrund — Rendern eines PDFs dauert spürbar. */
    fun importPdf() {
        val file = FilePicker.openPdf(null) ?: return
        busy = "„${file.name}“ wird gerendert …"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { PdfImporter(controller.assetsDir).import(file) }
            }
            busy = null
            result
                .onSuccess { pages ->
                    if (pages.isEmpty()) dialog = AppDialog.Message("Das PDF enthält keine Seiten.")
                    else controller.addImportedPages(pages)
                }
                .onFailure { dialog = AppDialog.Message("PDF konnte nicht gelesen werden:\n${it.message}") }
        }
    }

    fun importImage() {
        val page = controller.activePage ?: return
        val file = FilePicker.openImage(null) ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ImageImporter(controller.assetsDir).import(file) }
            }
            result
                .onSuccess { controller.addImageBox(page, it) }
                .onFailure { dialog = AppDialog.Message("Bild konnte nicht gelesen werden:\n${it.message}") }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            SectionTabs(
                sections = controller.document.sections,
                active = controller.activeSection,
                onSelect = controller::selectSection,
                onAdd = { dialog = AppDialog.NewSection },
                onRename = { dialog = AppDialog.RenameSection(it) },
                onDelete = { dialog = AppDialog.DeleteSection(it) },
                onWebdavPath = { dialog = AppDialog.SectionWebdavPath(it) },
            )
            HorizontalDivider()
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(260.dp).fillMaxHeight()) {
                    PageList(
                        pages = controller.activeSection?.pages.orEmpty(),
                        active = controller.activePage,
                        onSelect = controller::selectPage,
                        onRename = { dialog = AppDialog.RenamePage(it) },
                        onDelete = { dialog = AppDialog.DeletePage(it) },
                        onAddSubpage = { dialog = AppDialog.NewSubpage(it) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    HorizontalDivider()
                    TextButton(
                        onClick = { dialog = AppDialog.NewPage },
                        enabled = controller.activeSection != null,
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                    ) { Text("+ Seite") }
                }
                VerticalDivider()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    PageToolbar(
                        page = controller.activePage,
                        onPaperStyle = { style ->
                            controller.activePage?.let { controller.setPaperStyle(it, style) }
                        },
                        onUndo = { controller.activePage?.let(controller::undo) },
                        onRedo = { controller.activePage?.let(controller::redo) },
                        onImportPdf = ::importPdf,
                        onImportImage = ::importImage,
                        onAddLink = { dialog = AppDialog.NewLink },
                    )
                    HorizontalDivider()
                    Box(Modifier.fillMaxSize()) {
                        PageCanvas(
                            page = controller.activePage,
                            revision = controller.revision,
                            backgroundLoader = assets::load,
                            onTarget = { target ->
                                dialog = when (target) {
                                    is CanvasTarget.Text -> AppDialog.EditText(target.box)
                                    is CanvasTarget.Link -> AppDialog.EditLink(target.box)
                                    is CanvasTarget.Empty -> AppDialog.NewText(target.x, target.y)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        busy?.let { message ->
                            Surface(
                                Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.inverseSurface,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    message,
                                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    AppDialogHost(dialog, controller) { dialog = null }
}

// ---------------- Dialog-Zustände ----------------

/** Welcher Dialog gerade offen ist — ein Zustand statt vieler Boolean-Flags. */
sealed interface AppDialog {
    data object NewSection : AppDialog
    data class RenameSection(val section: Section) : AppDialog
    data class DeleteSection(val section: Section) : AppDialog
    data class SectionWebdavPath(val section: Section) : AppDialog
    data object NewPage : AppDialog
    data class NewSubpage(val parent: Page) : AppDialog
    data class RenamePage(val page: Page) : AppDialog
    data class DeletePage(val page: Page) : AppDialog
    data class NewText(val x: Float, val y: Float) : AppDialog
    data class EditText(val box: TextBox) : AppDialog
    data object NewLink : AppDialog
    data class EditLink(val box: LinkBox) : AppDialog
    /** Reine Rückmeldung, etwa wenn ein Import fehlschlägt. */
    data class Message(val text: String) : AppDialog
}

@Composable
private fun AppDialogHost(
    dialog: AppDialog?,
    controller: DocumentController,
    onClose: () -> Unit,
) {
    when (dialog) {
        null -> Unit

        AppDialog.NewSection -> TextInputDialog(
            title = "Neuer Abschnitt", label = "Name", initial = "Abschnitt",
            confirmLabel = "Anlegen",
            onConfirm = { controller.addSection(it); onClose() }, onDismiss = onClose,
        )

        is AppDialog.RenameSection -> TextInputDialog(
            title = "Abschnitt umbenennen", label = "Name", initial = dialog.section.name,
            onConfirm = { controller.renameSection(dialog.section, it); onClose() },
            onDismiss = onClose,
        )

        is AppDialog.DeleteSection -> ConfirmDialog(
            title = "Abschnitt löschen",
            message = "„${dialog.section.name}“ wird mit allen " +
                "${dialog.section.pages.size} Seite(n) gelöscht. Das lässt sich nicht rückgängig machen.",
            onConfirm = { controller.deleteSection(dialog.section); onClose() },
            onDismiss = onClose,
        )

        is AppDialog.SectionWebdavPath -> TextInputDialog(
            title = "WebDAV-Pfad",
            label = "Pfad auf dem Server (leer = nur lokal)",
            initial = dialog.section.webdavPath.orEmpty(),
            confirmLabel = "Speichern",
            onConfirm = { controller.setSectionWebdavPath(dialog.section, it); onClose() },
            onDismiss = onClose,
        )

        AppDialog.NewPage -> TextInputDialog(
            title = "Neue Seite", label = "Titel", initial = "Neue Seite",
            confirmLabel = "Anlegen",
            onConfirm = { controller.addPage(it); onClose() }, onDismiss = onClose,
        )

        is AppDialog.NewSubpage -> TextInputDialog(
            title = "Neue Unterseite", label = "Titel", initial = "Unterseite",
            confirmLabel = "Anlegen",
            onConfirm = { controller.addSubpage(dialog.parent, it); onClose() },
            onDismiss = onClose,
        )

        is AppDialog.RenamePage -> TextInputDialog(
            title = "Seite umbenennen", label = "Titel", initial = dialog.page.title,
            onConfirm = { controller.renamePage(dialog.page, it); onClose() },
            onDismiss = onClose,
        )

        is AppDialog.DeletePage -> ConfirmDialog(
            title = "Seite löschen",
            message = "„${dialog.page.title}“ wird gelöscht — Unterseiten inklusive.",
            onConfirm = { controller.deletePage(dialog.page); onClose() },
            onDismiss = onClose,
        )

        is AppDialog.NewText -> TextInputDialog(
            title = "Text einfügen", label = "Text", confirmLabel = "Einfügen",
            onConfirm = { text ->
                controller.activePage?.let { controller.addTextBox(it, dialog.x, dialog.y, text) }
                onClose()
            },
            onDismiss = onClose,
        )

        is AppDialog.EditText -> TextInputDialog(
            title = "Text bearbeiten",
            label = "Text",
            initial = dialog.box.content,
            onConfirm = { text ->
                controller.activePage?.let { controller.editTextBox(it, dialog.box, text) }
                onClose()
            },
            onDelete = {
                controller.activePage?.let { controller.deleteTextBox(it, dialog.box) }
                onClose()
            },
            onDismiss = onClose,
        )

        AppDialog.NewLink -> LinkDialog(
            onConfirm = { url, title ->
                controller.activePage?.let { page ->
                    // Neue Verweise oben links, wo sie nicht im Weg sind.
                    controller.addLinkBox(page, LINK_DROP_X, LINK_DROP_Y, url, title)
                }
                onClose()
            },
            onDismiss = onClose,
        )

        is AppDialog.EditLink -> LinkDialog(
            initialUrl = dialog.box.url,
            initialTitle = dialog.box.title,
            onConfirm = { url, title ->
                controller.activePage?.let { controller.editLinkBox(it, dialog.box, url, title) }
                onClose()
            },
            onDelete = {
                controller.activePage?.let { controller.deleteLinkBox(it, dialog.box) }
                onClose()
            },
            onDismiss = onClose,
        )

        is AppDialog.Message -> ConfirmDialog(
            title = "Hinweis",
            message = dialog.text,
            confirmLabel = "OK",
            onConfirm = onClose,
            onDismiss = onClose,
        )
    }
}

private const val LINK_DROP_X = 40f
private const val LINK_DROP_Y = 40f

// ---------------- Abschnitts-Reiter ----------------

@Composable
private fun SectionTabs(
    sections: List<Section>,
    active: Section?,
    onSelect: (Section) -> Unit,
    onAdd: () -> Unit,
    onRename: (Section) -> Unit,
    onDelete: (Section) -> Unit,
    onWebdavPath: (Section) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sections.forEach { section ->
            val selected = section === active
            ContextMenuArea(items = {
                listOf(
                    ContextMenuItem("Umbenennen") { onRename(section) },
                    ContextMenuItem("WebDAV-Pfad …") { onWebdavPath(section) },
                    ContextMenuItem("Löschen") { onDelete(section) },
                )
            }) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(section) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            section.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        // Nur gesyncte Abschnitte haben einen WebDAV-Pfad.
                        if (section.webdavPath != null) {
                            Spacer(Modifier.width(6.dp))
                            Text("☁", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        TextButton(onClick = onAdd) { Text("+ Abschnitt") }
    }
}

// ---------------- Seitenliste ----------------

@Composable
private fun PageList(
    pages: List<Page>,
    active: Page?,
    onSelect: (Page) -> Unit,
    onRename: (Page) -> Unit,
    onDelete: (Page) -> Unit,
    onAddSubpage: (Page) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Unterseiten hängen über parentId an ihrer Elternseite und werden eingerückt
    // direkt darunter einsortiert — wie die Unterseiten-Ebene in OneNote.
    val ordered = buildList {
        pages.filter { it.parentId == null }.forEach { parent ->
            add(parent to false)
            pages.filter { it.parentId == parent.id }.forEach { add(it to true) }
        }
    }

    LazyColumn(modifier.background(MaterialTheme.colorScheme.surface)) {
        items(ordered) { (page, isSub) ->
            val selected = page === active
            ContextMenuArea(items = {
                buildList {
                    add(ContextMenuItem("Umbenennen") { onRename(page) })
                    if (!isSub) add(ContextMenuItem("Unterseite anlegen") { onAddSubpage(page) })
                    add(ContextMenuItem("Löschen") { onDelete(page) })
                }
            }) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { onSelect(page) }
                        .padding(
                            start = if (isSub) 32.dp else 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            bottom = 10.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        page.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.weight(1f))
                    if (page.strokes.isNotEmpty()) {
                        Text(
                            "${page.strokes.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Werkzeugleiste der Seite ----------------

@Composable
private fun PageToolbar(
    page: Page?,
    onPaperStyle: (PaperStyle) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onImportPdf: () -> Unit,
    onImportImage: () -> Unit,
    onAddLink: () -> Unit,
) {
    var paperMenuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column {
            Text(
                page?.title ?: "Keine Seite",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            page?.let {
                Text(
                    formatLabel(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))

        TextButton(onClick = onImportPdf) { Text("PDF …") }
        TextButton(onClick = onImportImage, enabled = page != null) { Text("Bild …") }
        TextButton(onClick = onAddLink, enabled = page != null) { Text("Video-Link …") }

        Spacer(Modifier.width(12.dp))
        TextButton(onClick = onUndo, enabled = page?.canUndo() == true) { Text("Rückgängig") }
        TextButton(onClick = onRedo, enabled = page?.canRedo() == true) { Text("Wiederholen") }

        Box {
            // Auf einer gerenderten Vorlage wäre ein Papierraster sinnlos.
            TextButton(
                onClick = { paperMenuOpen = true },
                enabled = page != null && page.background == null,
            ) {
                Text("Papier: ${page?.paperStyle?.let(::paperLabel) ?: "—"}")
            }
            DropdownMenu(paperMenuOpen, onDismissRequest = { paperMenuOpen = false }) {
                PaperStyle.entries.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(paperLabel(style)) },
                        onClick = { onPaperStyle(style); paperMenuOpen = false },
                    )
                }
            }
        }
    }
}

/** Kurzbeschreibung der Seite unter dem Titel — Format und Herkunft. */
private fun formatLabel(page: Page): String {
    val format = when (page.format) {
        PageFormat.FREE -> "Freie Fläche"
        PageFormat.A4_PORTRAIT -> "A4 hoch"
        PageFormat.A4_LANDSCAPE -> "A4 quer"
        PageFormat.SLIDE_16_9 -> "Folie 16:9"
        PageFormat.SLIDE_4_3 -> "Folie 4:3"
    }
    val bg = page.background ?: return format
    val source = bg.sourceName ?: return "$format · Vorlage"
    return bg.sourcePage?.let { "$format · $source, S. $it" } ?: "$format · $source"
}

private fun paperLabel(style: PaperStyle): String = when (style) {
    PaperStyle.BLANK -> "Blanko"
    PaperStyle.LINED -> "Liniert"
    PaperStyle.GRID -> "Kariert"
    PaperStyle.DOTS -> "Punkte"
    PaperStyle.LEGAL -> "Gelb liniert"
}
