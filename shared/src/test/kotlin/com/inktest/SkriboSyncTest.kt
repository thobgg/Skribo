package com.inktest

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PropfindParsingTest {

    /** So antwortet ein DSM-WebDAV auf PROPFIND mit Depth 1. */
    private val antwort = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:">
          <D:response>
            <D:href>/skribo/Schuljahr/Analysis12/</D:href>
            <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
          </D:response>
          <D:response>
            <D:href>/skribo/Schuljahr/Analysis12/Ableitungsregeln/</D:href>
            <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
          </D:response>
          <D:response>
            <D:href>/skribo/Schuljahr/Analysis12/Beispiel%20Produktregel/</D:href>
            <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
          </D:response>
          <D:response>
            <D:href>/skribo/Schuljahr/Analysis12/hinweis.txt</D:href>
            <D:propstat><D:prop><D:resourcetype/></D:prop></D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    @Test
    fun `liest die unterordner und laesst den eigenen aus`() {
        val dirs = SkriboSync.parseDirectoryNames(antwort, "Schuljahr/Analysis12")

        assertEquals(listOf("Ableitungsregeln", "Beispiel Produktregel"), dirs)
    }

    @Test
    fun `dateien zaehlen nicht als ordner`() {
        val dirs = SkriboSync.parseDirectoryNames(antwort, "Schuljahr/Analysis12")

        assertTrue(dirs.none { it.contains("hinweis") }, "Dateien gehören nicht dazu")
    }

    @Test
    fun `prozentkodierte namen werden zurueckuebersetzt`() {
        val dirs = SkriboSync.parseDirectoryNames(antwort, "Schuljahr/Analysis12")

        assertTrue("Beispiel Produktregel" in dirs, "Leerzeichen muss dekodiert werden")
    }

    @Test
    fun `leere antwort ergibt leere liste`() {
        assertTrue(SkriboSync.parseDirectoryNames("", "egal").isEmpty())
    }

    @Test
    fun `tiefer liegende ordner werden nicht mitgezaehlt`() {
        val tief = """
            <D:multistatus xmlns:D="DAV:">
              <D:response><D:href>/a/b/</D:href></D:response>
              <D:response><D:href>/a/b/seite/</D:href></D:response>
              <D:response><D:href>/a/b/seite/skribo/</D:href></D:response>
            </D:multistatus>
        """.trimIndent()

        assertEquals(listOf("seite"), SkriboSync.parseDirectoryNames(tief, "a/b"))
    }
}

class BaseJsonRoundTripTest {

    @Test
    fun `was hochgeladen wird laesst sich wieder einlesen`() {
        // Push und Pull benutzen dieselbe Darstellung — sonst bräuchte es zwei
        // Übersetzer, die man bei jeder Schemaänderung doppelt nachziehen muss.
        val page = Page(
            title = "Arbeitsblatt",
            paperStyle = PaperStyle.BLANK,
            format = PageFormat.A4_PORTRAIT,
            background = PageBackground("assets/v.png", "Arbeitsblatt.pdf", 1, "assets/q.pdf"),
        )
        page.applyAction(AddTextBox(TextBox(x = 40f, y = 60f, content = "Merksatz")))
        page.applyAction(AddLinkBox(LinkBox(x = 10f, y = 20f, url = "https://youtu.be/dQw4w9WgXcQ")))
        page.addStroke(Stroke(smoothingFactor = 0f).apply { addPoint(5f, 6f); addPoint(7f, 8f) })

        val base = page.toBaseJson().apply {
            put("schemaVersion", SkriboSync.SCHEMA_VERSION)
            put("type", "skribo-base")
        }
        val annotations = page.toAnnotationsJson("25-26")

        val zurueck = Page.fromJson(JSONObject(base.toString()))
        zurueck.applyAnnotations(JSONObject(annotations.toString()))

        assertEquals("Arbeitsblatt", zurueck.title)
        assertEquals(PageFormat.A4_PORTRAIT, zurueck.format)
        assertEquals("Merksatz", zurueck.textBoxes.single().content)
        assertEquals("dQw4w9WgXcQ", zurueck.linkBoxes.single().youtubeId())
        assertEquals(1, zurueck.strokes.size)
        val bg = assertNotNull(zurueck.background)
        assertEquals("assets/q.pdf", bg.sourceAssetPath)
        // Die Basis darf keine Handschrift enthalten — sonst schlüge das Vorjahr durch.
        assertTrue(!base.has("strokes"))
    }
}
