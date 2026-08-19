package com.inktest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SectionTest {

    private fun section() = Section(name = "Analysis", color = Section.DEFAULT_COLOR)

    @Test
    fun `unterseiten stehen in anlege-reihenfolge unter der elternseite`() {
        val s = section()
        val parent = Page(title = "Ableitungsregeln")
        s.addRootPage(parent)

        s.addSubpageOf(parent, Page(title = "Beispiel"))
        s.addSubpageOf(parent, Page(title = "Übungsblatt"))

        assertEquals(
            listOf("Ableitungsregeln", "Beispiel", "Übungsblatt"),
            s.pages.map { it.title },
        )
    }

    @Test
    fun `unterseiten schieben sich nicht vor die naechste hauptseite`() {
        val s = section()
        val first = Page(title = "Erste")
        val second = Page(title = "Zweite")
        s.addRootPage(first)
        s.addRootPage(second)

        s.addSubpageOf(first, Page(title = "Unterseite"))

        assertEquals(listOf("Erste", "Unterseite", "Zweite"), s.pages.map { it.title })
    }

    @Test
    fun `seite loeschen nimmt ihre unterseiten mit`() {
        val s = section()
        val parent = Page(title = "Eltern")
        val other = Page(title = "Bleibt")
        s.addRootPage(parent)
        s.addRootPage(other)
        s.addSubpageOf(parent, Page(title = "Kind 1"))
        s.addSubpageOf(parent, Page(title = "Kind 2"))

        val removed = s.removePage(parent)

        assertEquals(3, removed.size)
        assertEquals(listOf("Bleibt"), s.pages.map { it.title })
    }

    @Test
    fun `tiefe der unterseite wird richtig bestimmt`() {
        val s = section()
        val parent = Page(title = "Eltern")
        val child = Page(title = "Kind")
        s.addRootPage(parent)
        s.addSubpageOf(parent, child)

        assertEquals(0, s.depthOf(parent))
        assertEquals(1, s.depthOf(child))
    }

    @Test
    fun `abschnitt ohne webdav-pfad bleibt lokal`() {
        val s = section()
        assertTrue(s.webdavPath == null)
    }
}
