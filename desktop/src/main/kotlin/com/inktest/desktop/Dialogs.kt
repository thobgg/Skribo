package com.inktest.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * Dialog für eine einzelne Texteingabe (Titel vergeben, Text setzen …).
 * Das Feld bekommt beim Öffnen den Fokus und der bestehende Text ist markiert,
 * damit Tippen ihn direkt ersetzt — sonst nervt jedes Umbenennen.
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirmLabel: String = "OK",
    onConfirm: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var value by remember {
        mutableStateOf(
            TextFieldValue(initial, selection = androidx.compose.ui.text.TextRange(0, initial.length))
        )
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val confirm = {
        val text = value.text.trim()
        if (text.isNotEmpty()) onConfirm(text) else onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
            }
        },
        confirmButton = { TextButton(onClick = confirm) { Text(confirmLabel) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Löschen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

/**
 * Dialog für einen Verweis (in der Praxis YouTube): URL plus optionaler
 * Anzeigetext. Leere URL heißt „löschen".
 */
@Composable
fun LinkDialog(
    initialUrl: String = "",
    initialTitle: String = "",
    onConfirm: (url: String, title: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var title by remember { mutableStateOf(initialTitle) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialUrl.isEmpty()) "Video verlinken" else "Verweis bearbeiten") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Adresse (z. B. YouTube-Link)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Beschriftung (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Das Video wird nicht heruntergeladen — auf der Seite steht ein " +
                        "Verweis, der im Browser geöffnet wird.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onConfirm(url.trim(), title.trim()) },
                enabled = url.isNotBlank(),
            ) { Text("Übernehmen") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Löschen") }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

/** Rückfrage vor dem Löschen — Seiten nehmen ihre Unterseiten mit. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Löschen",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/**
 * Die Einstellungen des Programms an einer Stelle: Zugangsdaten für den
 * Server, das aktive Schuljahr und der Ablageort des Dokuments.
 *
 * Die Zugangsdaten landen in `desktop.properties` neben dem Dokument — nicht
 * im Programm und nicht im Repository.
 */
@Composable
fun SettingsDialog(
    initialServer: String,
    initialUser: String,
    initialPassword: String,
    initialBasePath: String,
    schoolYear: String,
    documentPath: String,
    onTest: (String, String, String, String) -> Unit,
    testResult: String?,
    onConfirm: (server: String, user: String, password: String, basePath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var server by remember { mutableStateOf(initialServer) }
    var user by remember { mutableStateOf(initialUser) }
    var password by remember { mutableStateOf(initialPassword) }
    var basePath by remember { mutableStateOf(initialBasePath) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column {
                Text(
                    "Server (WebDAV)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Adresse, z. B. https://nas.example/skribo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Benutzername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = basePath,
                    onValueChange = { basePath = it },
                    label = { Text("Basis-Ordner auf dem Server, z. B. home/skribo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Darunter liegt je Notizbuch ein Ordner, darin je Abschnitt einer — " +
                        "niemand muss mehr Pfade je Abschnitt eintragen. Der Ordner muss " +
                        "innerhalb einer bestehenden Freigabe liegen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    testResult ?: "Die Angaben bleiben auf diesem Rechner — sie liegen " +
                        "in desktop.properties neben dem Dokument.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    "Schuljahr",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Zurzeit $schoolYear. Umschalten links oben neben den Abschnitten — " +
                        "ein Wechsel tauscht nur die Handschrift-Ebene, Vorlagen und " +
                        "Texte bleiben stehen.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "Ablage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    documentPath,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "Ein anderer Ort lässt sich über die Umgebungsvariable " +
                        "SKRIBO_HOME wählen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onTest(server.trim(), user.trim(), password, basePath.trim()) },
                    enabled = server.isNotBlank() && user.isNotBlank(),
                ) { Text("Verbindung testen") }
                TextButton(
                    onClick = { onConfirm(server.trim(), user.trim(), password, basePath.trim()) },
                ) { Text("Speichern") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/**
 * Abgleich-Fehler mit vollem Wortlaut: markierbar, scrollbar und per Knopf in
 * die Zwischenablage — geboren aus einem Abend, an dem die Fehler nur auf
 * stderr standen und der Sync wie erfolgreich aussah.
 */
@Composable
fun SyncErrorsDialog(
    summary: String,
    errors: List<String>,
    onClose: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val wortlaut = errors.joinToString("\n")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Abgleich: ${errors.size} Fehler") },
        text = {
            Column {
                Text(summary)
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        wortlaut,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(wortlaut))
                }) { Text("Kopieren") }
                TextButton(onClick = onClose) { Text("OK") }
            }
        },
    )
}
