package com.inktest

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageFormatTest {

    @Test
    fun `a4 hoch entspricht 210 mal 297 millimeter`() {
        val mmPerPt = 25.4f / 72f
        assertEquals(210f, PageFormat.A4_PORTRAIT.widthPt * mmPerPt, 0.5f)
        assertEquals(297f, PageFormat.A4_PORTRAIT.heightPt * mmPerPt, 0.5f)
    }

    @Test
    fun `bestFit erkennt folie und a4 am seitenverhaeltnis`() {
        // 16:9-Folie darf nicht als A4 quer landen.
        assertEquals(PageFormat.SLIDE_16_9, PageFormat.bestFit(1920f, 1080f))
        assertEquals(PageFormat.SLIDE_4_3, PageFormat.bestFit(1024f, 768f))
        assertEquals(PageFormat.A4_PORTRAIT, PageFormat.bestFit(595f, 842f))
        assertEquals(PageFormat.A4_LANDSCAPE, PageFormat.bestFit(842f, 595f))
    }

    @Test
    fun `ungueltige groesse ergibt freien canvas`() {
        assertEquals(PageFormat.FREE, PageFormat.bestFit(0f, 0f))
        assertEquals(PageFormat.FREE, PageFormat.bestFit(-1f, 100f))
    }

    @Test
    fun `unbekannter formatname faellt auf frei zurueck`() {
        assertEquals(PageFormat.FREE, PageFormat.parse("din-a17"))
        assertEquals(PageFormat.FREE, PageFormat.parse(null))
        assertEquals(PageFormat.A4_PORTRAIT, PageFormat.parse("a4_portrait"))
    }

    @Test
    fun `nur der freie canvas ist unbegrenzt`() {
        assertTrue(!PageFormat.FREE.isBounded)
        PageFormat.entries.filter { it != PageFormat.FREE }.forEach {
            assertTrue(it.isBounded, "$it sollte feste Abmessungen haben")
        }
    }

    @Test
    fun `format und hintergrund ueberstehen den json-zyklus`() {
        val page = Page(
            title = "Arbeitsblatt",
            format = PageFormat.A4_PORTRAIT,
            background = PageBackground("assets/abc.png", "Arbeitsblatt.pdf", 3),
        )

        val back = Page.fromJson(JSONObject(page.toJson().toString()))

        assertEquals(PageFormat.A4_PORTRAIT, back.format)
        val bg = assertNotNull(back.background)
        assertEquals("assets/abc.png", bg.assetPath)
        assertEquals("Arbeitsblatt.pdf", bg.sourceName)
        assertEquals(3, bg.sourcePage)
    }

    @Test
    fun `seite aus schemaversion 1 bleibt lesbar`() {
        // Ohne format/background gelesen: freier Canvas, kein Hintergrund.
        val old = JSONObject(
            """{"id":"p1","title":"Alt","paperStyle":"LINED","strokes":[],"textBoxes":[],"imageBoxes":[]}"""
        )

        val page = Page.fromJson(old)

        assertEquals(PageFormat.FREE, page.format)
        assertNull(page.background)
        assertEquals("Alt", page.title)
    }
}

class LinkBoxTest {

    @Test
    fun `youtube-id wird aus den gaengigen linkformen gelesen`() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://youtu.be/$id",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/watch?list=PL123&v=$id",
        ).forEach { url ->
            assertEquals(id, LinkBox(x = 0f, y = 0f, url = url).youtubeId(), url)
        }
    }

    @Test
    fun `fremder link ist kein youtube-video`() {
        assertNull(LinkBox(x = 0f, y = 0f, url = "https://serlo.org/mathe").youtubeId())
        assertNull(LinkBox(x = 0f, y = 0f, url = "").youtubeId())
    }

    @Test
    fun `ohne titel wird die url angezeigt`() {
        assertEquals("https://x.test", LinkBox(x = 0f, y = 0f, url = "https://x.test").label)
        assertEquals(
            "Erklärvideo",
            LinkBox(x = 0f, y = 0f, url = "https://x.test", title = "Erklärvideo").label,
        )
    }

    @Test
    fun `links ueberstehen den json-zyklus`() {
        val page = Page(title = "Seite")
        page.applyAction(
            AddLinkBox(LinkBox(x = 12f, y = 34f, url = "https://youtu.be/dQw4w9WgXcQ", title = "Intro"))
        )

        val back = Page.fromJson(JSONObject(page.toJson().toString()))

        val link = back.linkBoxes.single()
        assertEquals("Intro", link.title)
        assertEquals("dQw4w9WgXcQ", link.youtubeId())
        assertEquals(12f, link.x)
    }

    @Test
    fun `link anlegen und loeschen ist rueckgaengig-machbar`() {
        val page = Page(title = "Seite")
        val box = LinkBox(x = 0f, y = 0f, url = "https://youtu.be/dQw4w9WgXcQ")

        page.applyAction(AddLinkBox(box))
        assertEquals(1, page.linkBoxes.size)

        page.applyAction(RemoveLinkBox(box, 0))
        assertTrue(page.linkBoxes.isEmpty())

        page.undo()
        assertEquals(1, page.linkBoxes.size)
    }
}
