package com.inktest.desktop

import java.io.File

/**
 * Ablageort des lokalen Dokuments je Betriebssystem — bewusst an die jeweilige
 * Plattformkonvention gehalten, damit die App sich dort nicht wie ein Fremdkörper
 * verhält.
 */
object AppPaths {
    fun documentRoot(): File {
        // Erlaubt einen abweichenden Ablageort — etwa auf einem anderen
        // Laufwerk, für ein zweites Dokument oder zum gefahrlosen Ausprobieren.
        System.getenv(HOME_ENV)?.takeIf { it.isNotBlank() }?.let { return File(it) }

        val os = System.getProperty("os.name").lowercase()
        val home = File(System.getProperty("user.home"))
        val base = when {
            os.contains("win") ->
                System.getenv("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")
            os.contains("mac") ->
                File(home, "Library/Application Support")
            else ->
                System.getenv("XDG_DATA_HOME")?.let(::File) ?: File(home, ".local/share")
        }
        return File(base, "skribo")
    }

    /** Geräte-Einstellungen (zuletzt geöffneter Abschnitt/Seite, …). */
    fun settingsFile(): File = File(documentRoot(), "desktop.properties")

    /** Umgebungsvariable, die den Ablageort überschreibt. */
    const val HOME_ENV = "SKRIBO_HOME"
}
