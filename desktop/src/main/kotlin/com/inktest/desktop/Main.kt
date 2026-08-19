package com.inktest.desktop

import androidx.compose.material3.MaterialTheme
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

    val store = DocumentStore(AppPaths.documentRoot())
    val document = store.load()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Skribo — Unterrichtsplanung",
        state = rememberWindowState(
            size = DpSize(1280.dp, 840.dp),
            position = WindowPosition(androidx.compose.ui.Alignment.Center),
        ),
    ) {
        MaterialTheme {
            SkriboApp(document)
        }
    }
}
