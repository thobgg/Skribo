package com.inktest

import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pushes the local Inktest document to a WebDAV server, mapping the in-memory
 * structure to the Skribo on-disk schema:
 *
 *   <section.webdavPath>/<parent-page.title>/skribo/base.json
 *   <section.webdavPath>/<parent-page.title>/skribo/annotations/<year>.json
 *   <section.webdavPath>/<parent-page.title>/skribo/<subpage.title>/base.json
 *   <section.webdavPath>/<parent-page.title>/skribo/<subpage.title>/annotations/<year>.json
 *
 * Page titles become directory names — keep them filesystem-friendly. Strokes
 * land in the annotations/<year>.json layer only; base.json carries title + paper
 * metadata for now (richer base content comes with the data-model refactor).
 *
 * Pull is not yet implemented; this class is push-only.
 */
class SkriboSync(private val prefs: Prefs) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Sanity-checks the WebDAV server reachability and the configured credentials by
     * doing a single PROPFIND on the root. Throws IOException with a user-friendly
     * message on failure; returns silently on success.
     */
    @Throws(IOException::class)
    fun testConnection() {
        val server = prefs.webdavServer.trimEnd('/')
        if (server.isEmpty()) throw IOException("Server-URL nicht gesetzt")
        if (prefs.webdavUsername.isEmpty()) throw IOException("Benutzername nicht gesetzt")
        val auth = Credentials.basic(prefs.webdavUsername, prefs.webdavPassword)
        val req = Request.Builder()
            .url("$server/")
            .header("Authorization", auth)
            .header("Depth", "0")
            .method("PROPFIND", null)
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    200, 207 -> { /* ok */ }
                    401 -> throw IOException("Login fehlgeschlagen (401) — Benutzer/Passwort falsch")
                    403 -> throw IOException("Zugriff verweigert (403) — WebDAV-Berechtigung für $server prüfen")
                    404 -> throw IOException("Server-URL nicht erreichbar (404) — URL korrekt?")
                    405 -> throw IOException("WebDAV nicht aktiv auf $server (405)")
                    else -> throw IOException("Unerwartete Antwort: HTTP ${resp.code}")
                }
            }
        } catch (e: java.net.UnknownHostException) {
            throw IOException("Server unbekannt — DNS-Auflösung von $server fehlgeschlagen")
        } catch (e: java.net.SocketTimeoutException) {
            throw IOException("Server antwortet nicht (Timeout)")
        } catch (e: javax.net.ssl.SSLException) {
            throw IOException("Zertifikat-Problem: ${e.message}")
        }
    }

    @Throws(IOException::class)
    fun pushDocument(doc: Document): SyncResult {
        val server = prefs.webdavServer.trimEnd('/')
        if (server.isEmpty()) throw IOException("Server-URL nicht gesetzt")
        if (prefs.webdavUsername.isEmpty()) throw IOException("Benutzername nicht gesetzt")
        val auth = Credentials.basic(prefs.webdavUsername, prefs.webdavPassword)
        val year = prefs.activeSchoolYear

        var pageCount = 0
        val errors = mutableListOf<String>()

        for (section in doc.sections) {
            val sectionPath = section.webdavPath?.trim('/')
            if (sectionPath.isNullOrEmpty()) continue  // section without path is local-only

            val parentPages = section.pages.filter { it.parentId == null }
            for (parent in parentPages) {
                val parentTopicPath = "$sectionPath/${parent.title.trim()}/skribo"
                try {
                    pushPage(server, auth, parentTopicPath, parent, year)
                    pageCount++
                } catch (e: Exception) {
                    Log.w(TAG, "push parent '${parent.title}': ${e.message}")
                    errors += "${parent.title}: ${e.message}"
                }
                val subpages = section.pages.filter { it.parentId == parent.id }
                for (sub in subpages) {
                    val subPath = "$parentTopicPath/${sub.title.trim()}"
                    try {
                        pushPage(server, auth, subPath, sub, year)
                        pageCount++
                    } catch (e: Exception) {
                        Log.w(TAG, "push sub '${sub.title}': ${e.message}")
                        errors += "${sub.title}: ${e.message}"
                    }
                }
            }
        }
        prefs.lastSyncTime = System.currentTimeMillis()
        return SyncResult(pageCount, errors)
    }

    private fun pushPage(server: String, auth: String, basePath: String, page: Page, year: String) {
        ensureDirectory(server, auth, basePath)
        ensureDirectory(server, auth, "$basePath/annotations")
        putJson(server, auth, "$basePath/base.json", pageToBaseJson(page))
        putJson(server, auth, "$basePath/annotations/$year.json", pageToAnnotationsJson(page, year))
    }

    private fun pageToBaseJson(page: Page): JSONObject = JSONObject().apply {
        put("schemaVersion", 1)
        put("type", "skribo-base")
        put("id", page.id)
        put("title", page.title)
        put("paper", JSONObject().apply {
            put("type", page.paperStyle.name.lowercase())
        })
        put("strokes", JSONArray())
        val texts = JSONArray()
        page.textBoxes.forEach { tb ->
            texts.put(JSONObject().apply {
                put("id", tb.id)
                put("x", tb.x.toDouble())
                put("y", tb.y.toDouble())
                put("content", tb.content)
                put("fontSize", tb.fontSize.toDouble())
                put("color", String.format("#%06X", tb.color and 0xFFFFFF))
            })
        }
        put("texts", texts)
        val images = JSONArray()
        page.imageBoxes.forEach { ib ->
            images.put(JSONObject().apply {
                put("id", ib.id)
                put("x", ib.x.toDouble())
                put("y", ib.y.toDouble())
                put("width", ib.width.toDouble())
                put("height", ib.height.toDouble())
                put("src", ib.assetPath)
                // sha256 wird in Phase 3b ergänzt (Asset-Versionierung)
            })
        }
        put("images", images)
    }

    private fun pageToAnnotationsJson(page: Page, year: String): JSONObject = JSONObject().apply {
        put("schemaVersion", 1)
        put("type", "skribo-annotations")
        put("schoolYear", year)
        val strokes = JSONArray()
        page.strokes.forEachIndexed { idx, stroke ->
            strokes.put(strokeToSkriboJson(stroke, idx))
        }
        put("strokes", strokes)
        put("texts", JSONArray())
        put("images", JSONArray())
    }

    private fun strokeToSkriboJson(stroke: Stroke, index: Int): JSONObject {
        val pts = JSONArray()
        for (i in 0 until stroke.size) {
            val pt = JSONArray()
            pt.put(stroke.x(i).toDouble())
            pt.put(stroke.y(i).toDouble())
            pts.put(pt)
        }
        return JSONObject().apply {
            put("id", "s$index")
            put("tool", "pen")
            put("color", String.format("#%06X", stroke.color and 0xFFFFFF))
            put("width", stroke.width.toDouble())
            put("points", pts)
        }
    }

    private fun ensureDirectory(server: String, auth: String, path: String) {
        val parts = path.split('/').filter { it.isNotEmpty() }
        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current/$part"
            val url = "$server/${urlEncodePath(current)}/"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", auth)
                .method("MKCOL", null)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 401) {
                    throw IOException("Authentifizierung fehlgeschlagen (401)")
                }
                // 201 Created: ok; 405 Method Not Allowed (already exists): ok; others ignored
            }
        }
    }

    private fun putJson(server: String, auth: String, path: String, body: JSONObject) {
        val url = "$server/${urlEncodePath(path)}"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .put(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 201 && resp.code != 204) {
                throw IOException("PUT $path → HTTP ${resp.code}")
            }
        }
    }

    private fun urlEncodePath(path: String): String {
        return path.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
    }

    data class SyncResult(val pageCount: Int, val errors: List<String>)

    companion object {
        private const val TAG = "SkriboSync"
    }
}
