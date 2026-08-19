package com.inktest.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.inktest.DocumentStore
import com.inktest.SkriboLog

fun main() = application {
    SkriboLog.sink = SkriboLog.Sink { tag, message -> System.err.println("[$tag] $message") }

    val repository = remember { DesktopRepository(DocumentStore(AppPaths.documentRoot())) }
    val controller = remember {
        DocumentController(repository.load(), repository, DesktopPrefs(AppPaths.settingsFile()))
    }
    val assets = remember { AssetCache(repository.rootDir) }

    Window(
        onCloseRequest = {
            // Ausstehende Schreibvorgänge dürfen nicht mit dem Fenster verschwinden.
            controller.flush()
            exitApplication()
        },
        title = "Skribo — Unterrichtsplanung",
        state = rememberWindowState(
            size = DpSize(1280.dp, 840.dp),
            position = WindowPosition(Alignment.Center),
        ),
    ) {
        MaterialTheme {
            SkriboApp(controller, assets)
        }
    }
}
