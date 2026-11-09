package org.gadka.aac.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.gadka.aac.data.Category
import org.gadka.aac.data.Pictogram
import org.gadka.aac.data.Settings
import java.util.Locale
import kotlin.math.roundToInt
import org.gadka.aac.model.WordType
import org.gadka.aac.symbols.ASSET_PREFIX
import org.gadka.aac.creator.PictogramCreatorDialog
import org.gadka.aac.symbols.SymbolLibrary

private val wideDialog = DialogProperties(usePlatformDefaultWidth = false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PictogramEditDialog(
    initial: Pictogram,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Pictogram) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var label by remember { mutableStateOf(initial.label) }
    var spoken by remember { mutableStateOf(initial.spokenText.orEmpty()) }
    var emoji by remember { mutableStateOf(initial.emoji.orEmpty()) }
    var type by remember { mutableStateOf(WordType.from(initial.wordType)) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var imagePath by remember { mutableStateOf(initial.imagePath) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showCreator by remember { mutableStateOf(false) }

    if (showCreator) {
        PictogramCreatorDialog(
            initial = initial.copy(emoji = emoji.ifBlank { null }, imagePath = imagePath),
            onDismiss = { showCreator = false },
            onDone = { path ->
                imagePath = path
                showCreator = false
            },
        )
    }

    if (showPicker) {
        SymbolPickerDialog(
            initialQuery = label,
            onDismiss = { showPicker = false },
            onPick = { s ->
                imagePath = ASSET_PREFIX + s.assetPath
                if (label.isBlank()) label = s.label
                type = s.type
                showPicker = false
            },
        )
    }

    // Na wąskich ekranach podgląd ląduje nad polami, a całość przewija się w pionie.
    val narrow = LocalConfiguration.current.screenWidthDp < 600

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = wideDialog,
        modifier = Modifier.fillMaxWidth(if (narrow) 0.95f else 0.8f),
        title = { Text(if (initial.id == 0L) "Nowy piktogram" else "Edytuj piktogram") },
        text = {
            FlowRow(
                modifier = if (narrow) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = if (narrow) 1 else 2,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PictogramTile(
                        p = initial.copy(
                            label = label.ifBlank { "?" },
                            emoji = emoji.ifBlank { null },
                            imagePath = imagePath,
                            wordType = type.name,
                        ),
                        onClick = { showPicker = true },
                        modifier = Modifier.size(170.dp),
                    )
                    Button(onClick = { showPicker = true }, modifier = Modifier.width(170.dp)) {
                        Text("Biblioteka")
                    }
                    OutlinedButton(onClick = { showCreator = true }, modifier = Modifier.width(170.dp)) {
                        Text("Kreator")
                    }
                    if (imagePath != null) {
                        TextButton(onClick = { imagePath = null }) { Text("Usuń obrazek") }
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .then(if (narrow) Modifier else Modifier.verticalScroll(rememberScrollState())),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Podpis") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = spoken,
                        onValueChange = { spoken = it },
                        label = { Text("Wypowiadany tekst (opcjonalnie)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Symbol (emoji)") },
                        supportingText = {
                            Text("Używane, gdy nie wybrano obrazka z biblioteki.")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Kolor (część mowy)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WordType.entries.forEach { t ->
                            FilterChip(
                                selected = t == type,
                                onClick = { type = t },
                                label = { Text(t.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = t.background,
                                    selectedContainerColor = t.border,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                    Text("Kategoria", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { c ->
                            FilterChip(
                                selected = c.id == categoryId,
                                onClick = { categoryId = c.id },
                                label = { Text("${c.emoji} ${c.name}") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = label.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            label = label.trim(),
                            spokenText = spoken.trim().ifBlank { null },
                            emoji = emoji.trim().ifBlank { null },
                            imagePath = imagePath,
                            wordType = type.name,
                            categoryId = categoryId,
                        )
                    )
                },
            ) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                        Text(
                            if (confirmDelete) "Na pewno usunąć?" else "Usuń",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        },
    )
}

@Composable
fun CategoryEditDialog(
    initial: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nowa kategoria" else "Edytuj kategorię") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("Symbol (emoji)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirmDelete) {
                    Text(
                        "Usunięcie kategorii usunie też wszystkie jej piktogramy.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(initial.copy(name = name.trim(), emoji = emoji.trim().ifBlank { "⭐" })) },
            ) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                        Text(
                            if (confirmDelete) "Tak, usuń" else "Usuń",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        },
    )
}

@Composable
fun SettingsDialog(
    initial: Settings,
    onDismiss: () -> Unit,
    onSave: (Settings) -> Unit,
) {
    var columns by remember { mutableIntStateOf(initial.columns) }
    var rate by remember { mutableFloatStateOf(initial.speechRate) }
    var speakOnTap by remember { mutableStateOf(initial.speakOnTap) }
    var showCredits by remember { mutableStateOf(false) }
    val symbolCount = SymbolLibrary.symbols.collectAsState().value.size

    if (showCredits) CreditsDialog(onDismiss = { showCredits = false })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ustawienia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Liczba kolumn: $columns")
                Slider(
                    value = columns.toFloat(),
                    onValueChange = { columns = it.roundToInt() },
                    valueRange = 3f..10f,
                    steps = 6,
                )
                Text("Tempo mowy: ${String.format(Locale.forLanguageTag("pl-PL"), "%.1f", rate)}×")
                Slider(
                    value = rate,
                    onValueChange = { rate = (it * 10).roundToInt() / 10f },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Czytaj piktogram po dotknięciu", modifier = Modifier.weight(1f))
                    Switch(checked = speakOnTap, onCheckedChange = { speakOnTap = it })
                }
                TextButton(onClick = { showCredits = true }) { Text("Licencje i autorzy symboli") }
                Text(
                    "Gadka 0.3 · biblioteka: $symbolCount symboli",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(initial.copy(columns = columns, speechRate = rate, speakOnTap = speakOnTap))
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val credits = remember {
        runCatching {
            context.assets.open("symbols/CREDITS.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("Biblioteka symboli nie jest dołączona do tej wersji.")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Licencje i autorzy") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Gadka – wolne oprogramowanie na licencji GNU GPL v3.\n")
                Text(credits)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}
