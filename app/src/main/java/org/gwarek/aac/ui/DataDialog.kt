package org.gwarek.aac.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kopie zapasowe oraz wymiana tablic z innymi aplikacjami AAC (Open Board Format). */
@Composable
fun DataDialog(
    busy: Boolean,
    message: String?,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onExportObz: (Uri) -> Unit,
    onImportObf: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        if (it != null) onExportBackup(it)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) pendingRestore = it
    }
    val saveObz = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        if (it != null) onExportObz(it)
    }
    val openObf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) onImportObf(it)
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Przywrócić kopię?") },
            text = { Text("Wszystkie obecne tablice i piktogramy zostaną zastąpione zawartością kopii.") },
            confirmButton = {
                TextButton(onClick = { onImportBackup(uri); pendingRestore = null }) {
                    Text("Przywróć", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Anuluj") } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kopie zapasowe i wymiana tablic") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message != null) Text(message, color = MaterialTheme.colorScheme.primary)

                Text("Kopia zapasowa Gwarka", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Wszystkie tablice, własne zdjęcia i rysunki, historia podpowiedzi i ustawienia w jednym pliku. " +
                        "Zapisz go np. na karcie pamięci, w chmurze albo wyślij sobie mailem.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { saveBackup.launch("gwarek-kopia-$today.zip") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Zapisz kopię zapasową") }
                OutlinedButton(
                    onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Przywróć z kopii…") }

                Text("Open Board Format", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Otwarty format wymiany tablic AAC. Pozwala przenieść tablice do innych aplikacji " +
                        "i wczytać tablice przygotowane gdzie indziej (pliki .obf i .obz).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { saveObz.launch("gwarek-tablice-$today.obz") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Eksportuj tablice (.obz)") }
                OutlinedButton(
                    onClick = { openObf.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Importuj tablice (.obf / .obz)") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}
