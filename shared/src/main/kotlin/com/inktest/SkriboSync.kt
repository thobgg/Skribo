package com.inktest

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
class SkriboSync(
    private val settings: () -> SyncConfig,
    /**
     * Wurzel des lokalen Dokuments. Ist sie gesetzt, wandern auch die Assets
     * mit — gerenderte Vorlagen, Bilder und Original-PDFs. Ohne sie stünde am
     * Board eine leere Seite, weil die Vorlage fehlt.
     */
    private val assetRoot: java.io.File? = null,
) {

    /**
     * Verbindungsdaten für den WebDAV-Server. Wird bei jedem Aufruf frisch
     * geholt, damit Änderungen in den Einstellungen sofort greifen — auf Android
     * aus `Prefs`, auf dem Desktop aus dessen eigener Konfiguration.
     */
    data class SyncConfig(
        val server: String,
        val username: String,
        val password: String,
        val schoolYear: String,
    )

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
        val cfg = settings()
        val server = cfg.server.trimEnd('/')
        if (server.isEmpty()) throw IOException("Server-URL nicht gesetzt")
        if (cfg.username.isEmpty()) throw IOException("Benutzername nicht gesetzt")
        val auth = Credentials.basic(cfg.username, cfg.password)
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
        val cfg = settings()
        val server = cfg.server.trimEnd('/')
        if (server.isEmpty()) throw IOException("Server-URL nicht gesetzt")
        if (cfg.username.isEmpty()) throw IOException("Benutzername nicht gesetzt")
        val auth = Credentials.basic(cfg.username, cfg.password)
        val year = cfg.schoolYear

        var pageCount = 0
        val errors = mutableListOf<String>()

        for (section in doc.sections) {
            val sectionPath = section.webdavPath?.trim('/')
            if (sectionPath.isNullOrEmpty()) continue  // section without path is local-only

            // Ordnernamen kommen aus den Titeln — die sind aber nicht eindeutig.
            // Zwei Seiten „Übung" landeten sonst im selben Ordner und
            // überschrieben sich gegenseitig.
            val used = mutableSetOf<String>()

            val parentPages = section.pages.filter { it.parentId == null }
            for (parent in parentPages) {
                val parentTopicPath = "$sectionPath/${uniqueSegment(parent, used)}/skribo"
                try {
                    pushPage(server, auth, parentTopicPath, parent, year)
                    pageCount++
                } catch (e: Exception) {
                    SkriboLog.w(TAG, "push parent '${parent.title}': ${e.message}")
                    errors += "${parent.title}: ${e.message}"
                }
                val subUsed = mutableSetOf<String>()
                val subpages = section.pages.filter { it.parentId == parent.id }
                for (sub in subpages) {
                    val subPath = "$parentTopicPath/${uniqueSegment(sub, subUsed)}"
                    try {
                        pushPage(server, auth, subPath, sub, year)
                        pageCount++
                    } catch (e: Exception) {
                        SkriboLog.w(TAG, "push sub '${sub.title}': ${e.message}")
                        errors += "${sub.title}: ${e.message}"
                    }
                }
            }
        }
        // Zeitstempel setzt der Aufrufer — er kennt seinen Einstellungs-Speicher.
        return SyncResult(pageCount, errors)
    }

    /**
     * Lädt eine Seite hoch — **außer die Fassung auf dem Server ist neuer**.
     * Ohne diese Prüfung überschriebe das zuletzt abgleichende Gerät die Arbeit
     * des anderen: Wer am PC nichts geändert hat und danach abgleicht, löschte
     * sonst die Handschrift, die inzwischen am Board entstanden ist.
     */
    private fun pushPage(server: String, auth: String, basePath: String, page: Page, year: String) {
        val annotationsPath = "$basePath/annotations/$year.json"
        if (remoteIsNewer(server, auth, annotationsPath, page.modifiedAt)) {
            SkriboLog.w(TAG, "Server ist neuer, nicht überschrieben: ${page.title}")
            return
        }
        ensureDirectory(server, auth, basePath)
        ensureDirectory(server, auth, "$basePath/annotations")
        pushAssets(server, auth, basePath, page)
        putJson(server, auth, "$basePath/base.json", pageToBaseJson(page))
        putJson(server, auth, annotationsPath, page.toAnnotationsJson(year))
    }

    /** Vergleicht den Änderungszeitpunkt der Serverfassung mit dem lokalen. */
    private fun remoteIsNewer(
        server: String,
        auth: String,
        path: String,
        localModifiedAt: Long,
    ): Boolean {
        val remote = runCatching { getJson(server, auth, path) }.getOrNull() ?: return false
        val remoteModified = remote.optLong("modifiedAt", 0L)
        return remoteModified > localModifiedAt
    }

    /** Alle Dateien, auf die eine Seite verweist — relativ zur Dokumentwurzel. */
    private fun assetsOf(page: Page): List<String> = buildList {
        page.background?.let { bg ->
            add(bg.assetPath)
            bg.sourceAssetPath?.let { add(it) }
        }
        page.imageBoxes.forEach { add(it.assetPath) }
    }.filter { it.isNotBlank() }.distinct()

    /**
     * Lädt fehlende Assets hoch. Vorhandene werden übersprungen — ein
     * Original-PDF jedes Mal neu zu schicken wäre reine Verschwendung, und die
     * Dateien ändern sich nicht (jede bekommt beim Import eine neue Kennung).
     */
    private fun pushAssets(server: String, auth: String, basePath: String, page: Page) {
        val root = assetRoot ?: return
        val assets = assetsOf(page)
        if (assets.isEmpty()) return
        ensureDirectory(server, auth, "$basePath/assets")
        assets.forEach { rel ->
            val file = java.io.File(root, rel)
            if (!file.exists()) {
                SkriboLog.w(TAG, "Asset fehlt lokal, wird nicht hochgeladen: $rel")
                return@forEach
            }
            val remote = "$basePath/${rel.substringAfterLast('/').let { "assets/$it" }}"
            if (exists(server, auth, remote)) return@forEach
            putBytes(server, auth, remote, file)
        }
    }

    private fun exists(server: String, auth: String, path: String): Boolean {
        val req = Request.Builder()
            .url("$server/${urlEncodePath(path)}")
            .header("Authorization", auth)
            .head()
            .build()
        return runCatching {
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun putBytes(server: String, auth: String, path: String, file: java.io.File) {
        val type = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
        val req = Request.Builder()
            .url("$server/${urlEncodePath(path)}")
            .header("Authorization", auth)
            .put(file.readBytes().toRequestBody(type.toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 201 && resp.code != 204) {
                throw IOException("PUT $path → HTTP ${resp.code}")
            }
        }
    }

    /**
     * Die Seitenbasis fürs Netz — **dieselbe Darstellung wie lokal**, nur um
     * Typ und Schemaversion ergänzt. Zwei verschiedene Darstellungen zu pflegen
     * hieße, für den Pull einen zweiten Übersetzer zu bauen und jede
     * Schemaänderung doppelt nachzuziehen.
     */
    private fun pageToBaseJson(page: Page): JSONObject = page.toBaseJson().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("type", "skribo-base")
    }

    // ---------------- Herunterladen ----------------

    /**
     * Holt die Abschnitte mit WebDAV-Pfad vom Server und mischt sie ins lokale
     * Dokument. Die Zuordnung läuft über die **Seiten-Kennung** aus `base.json`,
     * nicht über den Ordnernamen — sonst würde eine umbenannte Seite als neue
     * gelten.
     *
     * Zusammenführung nach Ebenen: Die *Basis* kommt vom Server (dort steht die
     * am PC gepflegte Vorlage), die *Annotationen* ersetzen die lokalen des
     * gewählten Schuljahrs. Weil Basis und Handschrift getrennte Dateien sind,
     * können sie gar nicht erst kollidieren.
     *
     * Lokal vorhandene Seiten, die der Server nicht kennt, bleiben unangetastet —
     * ohne Löschmerkmal ließe sich „gelöscht" nicht von „neu hier" unterscheiden.
     */
    @Throws(IOException::class)
    fun pullDocument(doc: Document, onPage: (Page) -> Unit = {}): PullResult {
        val cfg = settings()
        val server = cfg.server.trimEnd('/')
        if (server.isEmpty()) throw IOException("Server-URL nicht gesetzt")
        if (cfg.username.isEmpty()) throw IOException("Benutzername nicht gesetzt")
        val auth = Credentials.basic(cfg.username, cfg.password)
        val year = cfg.schoolYear

        var added = 0
        var updated = 0
        val errors = mutableListOf<String>()

        for (section in doc.sections) {
            val sectionPath = section.webdavPath?.trim('/')
            if (sectionPath.isNullOrEmpty()) continue
            val pageDirs = runCatching { listDirectories(server, auth, sectionPath) }
                .getOrElse {
                    errors += "${section.name}: ${it.message}"
                    continue
                }
            for (dir in pageDirs) {
                val topic = "$sectionPath/$dir/skribo"
                fetchPage(server, auth, topic, year, section, null, errors)?.let { (page, isNew) ->
                    if (isNew) added++ else updated++
                    onPage(page)
                    // Unterseiten liegen als weitere Ordner neben annotations/ und assets/.
                    listDirectories(server, auth, topic)
                        .filter { it != "annotations" && it != "assets" }
                        .forEach { sub ->
                            fetchPage(server, auth, "$topic/$sub", year, section, page, errors)
                                ?.let { (subPage, subIsNew) ->
                                    if (subIsNew) added++ else updated++
                                    onPage(subPage)
                                }
                        }
                }
            }
        }
        return PullResult(added, updated, errors)
    }

    /**
     * Lädt eine Seite und mischt sie ein. Gibt die Seite zurück und ob sie neu
     * angelegt wurde; `null`, wenn dort keine Seite liegt.
     */
    private fun fetchPage(
        server: String,
        auth: String,
        basePath: String,
        year: String,
        section: Section,
        parent: Page?,
        errors: MutableList<String>,
    ): Pair<Page, Boolean>? {
        val baseJson = runCatching { getJson(server, auth, "$basePath/base.json") }
            .getOrElse {
                errors += "$basePath: ${it.message}"
                return null
            } ?: return null

        val remote = runCatching { Page.fromJson(baseJson) }.getOrElse {
            errors += "$basePath/base.json unlesbar: ${it.message}"
            return null
        }
        getJson(server, auth, "$basePath/annotations/$year.json")?.let { annotations ->
            runCatching { remote.applyAnnotations(annotations) }
        }
        downloadAssets(server, auth, basePath, remote, errors)

        val existing = section.pages.firstOrNull { it.id == remote.id }
        return if (existing == null) {
            remote.parentId = parent?.id
            if (parent != null) section.addSubpageOf(parent, remote) else section.addRootPage(remote)
            remote to true
        } else {
            merge(into = existing, from = remote)
            existing to false
        }
    }

    /**
     * Übernimmt die Serverfassung — **nur wenn sie neuer ist**. Sonst bleibt
     * das Lokale stehen; es wandert beim nächsten Senden nach oben.
     */
    private fun merge(into: Page, from: Page) {
        if (from.modifiedAt < into.modifiedAt) {
            SkriboLog.w(TAG, "Lokal ist neuer, Serverfassung verworfen: ${into.title}")
            return
        }
        into.title = from.title
        into.paperStyle = from.paperStyle
        into.format = from.format
        into.background = from.background
        into.textBoxes.clear(); into.textBoxes.addAll(from.textBoxes)
        into.imageBoxes.clear(); into.imageBoxes.addAll(from.imageBoxes)
        into.linkBoxes.clear(); into.linkBoxes.addAll(from.linkBoxes)
        into.strokes.clear(); into.strokes.addAll(from.strokes)
        into.modifiedAt = from.modifiedAt
        // Die Historie gehörte zum vorherigen Inhalt und passt nicht mehr.
        into.clearHistory()
    }

    /** Holt fehlende Vorlagen, Bilder und Original-PDFs in die lokale Ablage. */
    private fun downloadAssets(
        server: String,
        auth: String,
        basePath: String,
        page: Page,
        errors: MutableList<String>,
    ) {
        val root = assetRoot ?: return
        assetsOf(page).forEach { rel ->
            val target = java.io.File(root, rel)
            if (target.exists()) return@forEach
            val remote = "$basePath/assets/${rel.substringAfterLast('/')}"
            runCatching {
                getBytes(server, auth, remote)?.let { bytes ->
                    target.parentFile?.mkdirs()
                    target.writeBytes(bytes)
                }
            }.onFailure { errors += "Asset $rel: ${it.message}" }
        }
    }

    /** Ordnernamen direkt unterhalb von [path]. */
    private fun listDirectories(server: String, auth: String, path: String): List<String> {
        val req = Request.Builder()
            .url("$server/${urlEncodePath(path)}/")
            .header("Authorization", auth)
            .header("Depth", "1")
            .method("PROPFIND", null)
            .build()
        val body = client.newCall(req).execute().use { resp ->
            when (resp.code) {
                200, 207 -> resp.body?.string().orEmpty()
                404 -> return emptyList()
                401 -> throw IOException("Authentifizierung fehlgeschlagen (401)")
                405 -> throw IOException(explain405(path))
                else -> throw IOException("PROPFIND $path → HTTP ${resp.code}")
            }
        }
        return parseDirectoryNames(body, path)
    }

    private fun getJson(server: String, auth: String, path: String): JSONObject? =
        getBytes(server, auth, path)?.let { JSONObject(String(it, Charsets.UTF_8)) }

    private fun getBytes(server: String, auth: String, path: String): ByteArray? {
        val req = Request.Builder()
            .url("$server/${urlEncodePath(path)}")
            .header("Authorization", auth)
            .get()
            .build()
        return client.newCall(req).execute().use { resp ->
            when {
                resp.isSuccessful -> resp.body?.bytes()
                resp.code == 404 -> null
                else -> throw IOException("GET $path → HTTP ${resp.code}")
            }
        }
    }

    private fun ensureDirectory(server: String, auth: String, path: String) {
        val parts = path.split('/').filter { it.isNotEmpty() }
        var current = ""
        var topLevelRefused = false
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
                // 201 = angelegt, 405 = gibt es schon — beides in Ordnung.
                // 405 kann aber auch heißen: hier *darf* nicht angelegt werden.
                // Auf einer Synology etwa listet die Wurzel die Freigaben, und
                // neue Freigaben lassen sich per WebDAV nicht erzeugen. Das
                // merken wir uns für eine brauchbare Meldung weiter unten.
                if (resp.code == 405 && current == parts.first()) topLevelRefused = true
            }
        }
    }

    /** Erklärt einen 405 beim Schreiben, statt nur die Zahl zu nennen. */
    private fun explain405(path: String): String =
        "Der Ordner \u201E${path.substringBefore('/')}\u201C lässt sich nicht anlegen (405). " +
            "Zeigt die Server-Adresse auf die Freigabe-Ebene? Dort dürfen keine " +
            "neuen Ordner entstehen — trag den WebDAV-Pfad des Abschnitts " +
            "innerhalb einer bestehenden Freigabe ein, z. B. \u201Ehome/skribo\u201C."

    private fun putJson(server: String, auth: String, path: String, body: JSONObject) {
        val url = "$server/${urlEncodePath(path)}"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .put(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 201 && resp.code != 204) {
                if (resp.code == 405) throw IOException(explain405(path))
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

    data class PullResult(val added: Int, val updated: Int, val errors: List<String>)

    companion object {
        private const val TAG = "SkriboSync"

        /**
         * 2 seit der Medien-Erweiterung: Seitenformat, Hintergrund (gerenderte
         * PDF-Seite/Folie) und Links. Version 1 bleibt lesbar — dort fehlende
         * Felder bedeuten „freier Canvas, kein Hintergrund".
         */
        const val SCHEMA_VERSION = 2

        /**
         * Macht aus einem Seitentitel einen Ordnernamen, den auch ein
         * Windows-verträgliches Dateisystem annimmt — und eine Synology ist eines.
         *
         * Nötig geworden durch einen Fund am echten Server: Der Titel
         * „Stundenentwurf 20.08." endet auf einen Punkt; DSM benannte den Ordner
         * daraufhin selbsttätig um („TailCharacterConflict") und legte einen
         * zweiten an. Datumsangaben in dieser Schreibweise sind im Unterricht die
         * Regel, nicht die Ausnahme.
         */
            internal fun safeSegment(title: String): String {
            val cleaned = title.trim()
                .map { if (it in ILLEGAL_IN_NAMES || it.code < 32) '-' else it }
                .joinToString("")
                // Punkte und Leerzeichen am Ende sind das eigentliche Problem.
                .trimEnd('.', ' ')
                .trim()
            return cleaned.ifEmpty { "Seite" }
        }

        /**
         * Ordnername für eine Seite, eindeutig innerhalb von [used]. Bei
         * gleichem Titel bekommt die zweite Seite ein Kürzel ihrer Kennung
         * angehängt — lesbar bleibt es trotzdem.
         */
        internal fun uniqueSegment(page: Page, used: MutableSet<String>): String {
            val base = safeSegment(page.title)
            val name = if (used.add(base)) base
            else "$base (${page.id.take(6)})".also { used.add(it) }
            return name
        }

        /** Zeichen, die Windows und damit auch DSM in Namen nicht zulassen. */
        private const val ILLEGAL_IN_NAMES = "\\/:*?\"<>|"

        /**
         * Liest die Ordnernamen aus einer PROPFIND-Antwort. Bewusst über einen
         * Ausdruck statt über einen XML-Parser: Die Antwort ist maschinell
         * erzeugt und flach, und der Kern soll ohne XML-Bibliothek auskommen.
         *
         * [parentPath] ist der abgefragte Pfad; sein eigener Eintrag und alle
         * Nicht-Ordner werden übersprungen.
         */
        fun parseDirectoryNames(xml: String, parentPath: String): List<String> {
            val parent = parentPath.trim('/')
            val hrefs = Regex("""<[^>]*href[^>]*>([^<]*)</[^>]*href>""", RegexOption.IGNORE_CASE)
                .findAll(xml).map { it.groupValues[1].trim() }
            return hrefs.mapNotNull { raw ->
                // Nur Ordner: WebDAV hängt ihnen einen Schrägstrich an.
                if (!raw.endsWith("/")) return@mapNotNull null
                val decoded = runCatching {
                    java.net.URLDecoder.decode(raw, "UTF-8")
                }.getOrDefault(raw).trim('/')
                val idx = decoded.indexOf(parent)
                if (parent.isNotEmpty() && idx < 0) return@mapNotNull null
                val rest = if (parent.isEmpty()) decoded
                else decoded.substring(idx + parent.length).trim('/')
                // Genau eine Ebene tiefer; der Ordner selbst ergibt "".
                rest.takeIf { it.isNotEmpty() && !it.contains('/') }
            }.distinct().toList()
        }
    }
}
