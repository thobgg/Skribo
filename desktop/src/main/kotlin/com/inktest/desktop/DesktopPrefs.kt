package com.inktest.desktop

import com.inktest.SchoolYear
import com.inktest.SkriboLog
import java.io.File
import java.util.Properties

/**
 * Geräte-Einstellungen des Desktop-Clients — bewusst eine schlichte
 * Properties-Datei neben dem Dokument statt einer versteckten Registry:
 * lesbar, sicherbar, löschbar.
 *
 * Das Pendant am Board ist `Prefs` (SharedPreferences).
 */
class DesktopPrefs(private val file: File) {

    private val props = Properties().apply {
        if (file.exists()) {
            runCatching { file.inputStream().use { load(it) } }
                .onFailure { SkriboLog.w(TAG, "Einstellungen unlesbar, Standardwerte: $it") }
        }
    }

    var activeSectionId: String?
        get() = props.getProperty(KEY_SECTION)?.ifEmpty { null }
        set(v) = set(KEY_SECTION, v)

    var activePageId: String?
        get() = props.getProperty(KEY_PAGE)?.ifEmpty { null }
        set(v) = set(KEY_PAGE, v)

    /** Schuljahr, dessen Annotationsebene gerade bearbeitet wird. */
    var activeSchoolYear: String
        get() = props.getProperty(KEY_YEAR)?.ifEmpty { null } ?: SchoolYear.current()
        set(v) = set(KEY_YEAR, v)

    private fun set(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        save()
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "Skribo Desktop") }
        }.onFailure { SkriboLog.w(TAG, "Einstellungen nicht speicherbar: $it") }
    }

    private companion object {
        const val TAG = "DesktopPrefs"
        const val KEY_SECTION = "activeSectionId"
        const val KEY_PAGE = "activePageId"
        const val KEY_YEAR = "activeSchoolYear"
    }
}
