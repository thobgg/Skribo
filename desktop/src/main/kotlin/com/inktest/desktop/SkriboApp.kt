package com.inktest.desktop

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inktest.Document
import com.inktest.Page
import com.inktest.Section

/**
 * OneNote-artige Grundstruktur: Abschnitte als Reiter oben, Seiten (mit
 * eingerückten Unterseiten) links, die Seite selbst rechts.
 */
@Composable
fun SkriboApp(document: Document) {
    var activeSection by remember { mutableStateOf(document.sections.firstOrNull()) }
    var activePage by remember { mutableStateOf(activeSection?.pages?.firstOrNull()) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            SectionTabs(
                sections = document.sections,
                active = activeSection,
                onSelect = { section ->
                    activeSection = section
                    activePage = section.pages.firstOrNull()
                },
            )
            HorizontalDivider()
            Row(Modifier.fillMaxSize()) {
                PageList(
                    pages = activeSection?.pages.orEmpty(),
                    active = activePage,
                    onSelect = { activePage = it },
                    modifier = Modifier.width(240.dp).fillMaxHeight(),
                )
                VerticalDivider()
                PageCanvas(activePage, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SectionTabs(
    sections: List<Section>,
    active: Section?,
    onSelect: (Section) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sections.forEach { section ->
            val selected = section === active
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
                Text(
                    section.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        if (sections.isEmpty()) {
            Text("Kein Abschnitt", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PageList(
    pages: List<Page>,
    active: Page?,
    onSelect: (Page) -> Unit,
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
