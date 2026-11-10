package org.gwarek.aac.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gwarek.aac.ai.LlmStatus
import org.gwarek.aac.data.Settings
import java.util.Locale

@Composable
fun AiDialog(
    status: LlmStatus,
    modelSizeBytes: Long,
    importProgress: Float?,
    message: String?,
    settings: Settings,
    onSettings: (Settings) -> Unit,
    onImport: (Uri) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Asystent AI (offline)") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val statusText = when {
                    importProgress != null -> "Kopiowanie modelu…"
                    status == LlmStatus.READY -> "Gotowy · model %s".format(formatSize(modelSizeBytes))
                    status == LlmStatus.LOADING -> "Uruchamianie modelu…"
                    status == LlmStatus.ERROR -> "Model nie uruchomił się"
                    status == LlmStatus.UNSUPPORTED -> "To urządzenie nie obsługuje modelu (wymagany procesor ARM 64-bit)"
                    else -> "Brak modelu"
                }
                Text("Stan: $statusText", style = MaterialTheme.typography.titleSmall)
                if (importProgress != null) {
                    LinearProgressIndicator(progress = { importProgress }, modifier = Modifier.fillMaxWidth())
                }
                if (message != null) Text(message, color = MaterialTheme.colorScheme.primary)

                Text(
                    "Asystent działa wyłącznie na tym urządzeniu – aplikacja nie ma dostępu do internetu. " +
                        "Potrzebny jest plik modelu w formacie GGUF. Zalecany: Bielik-1.5B-v3.0-Instruct " +
                        "(polski model SpeakLeash, licencja Apache 2.0), wariant Q4_K_M, ok. 1 GB. " +
                        "Pobierz go raz na komputerze, skopiuj na tablet i wskaż poniżej.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        enabled = importProgress == null,
                    ) { Text(if (status == LlmStatus.NO_MODEL) "Wczytaj model z pliku" else "Zmień model") }
                    if (status != LlmStatus.NO_MODEL && importProgress == null) {
                        TextButton(onClick = { if (confirmRemove) onRemove() else confirmRemove = true }) {
                            Text(
                                if (confirmRemove) "Na pewno usunąć?" else "Usuń model",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                Text("Co robi asystent", style = MaterialTheme.typography.labelLarge)
                SettingSwitch(
                    "Układa poprawne zdanie przed odczytaniem („mama iść sklep” → „Mama idzie do sklepu.”)",
                    settings.smartSpeech,
                ) { onSettings(settings.copy(smartSpeech = it)) }
                SettingSwitch(
                    "Podpowiada kolejne piktogramy (uczy się z wyborów, działa też bez modelu)",
                    settings.showPredictions,
                ) { onSettings(settings.copy(showPredictions = it)) }
                Text(
                    "Gdy model jest gotowy, przy dodawaniu kategorii pojawi się też przycisk " +
                        "„Wypełnij piktogramami” – asystent zaproponuje słownictwo do tematu.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun SettingSwitch(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000_000L) String.format(Locale.forLanguageTag("pl-PL"), "%.1f GB", bytes / 1e9)
    else String.format(Locale.forLanguageTag("pl-PL"), "%d MB", bytes / 1_000_000L)
