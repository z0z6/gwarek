package org.gadka.aac.print

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintDialog(
    categoryName: String?,
    sentenceEmpty: Boolean,
    onDismiss: () -> Unit,
    onPrint: (PrintOptions) -> Unit,
) {
    var source by remember { mutableStateOf(PrintSource.CATEGORY) }
    var size by remember { mutableStateOf(CardSize.MEDIUM) }
    var label by remember { mutableStateOf(LabelPosition.BELOW) }
    var colored by remember { mutableStateOf(true) }
    var cutLines by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drukowanie kart") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Co wydrukować", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = source == PrintSource.CATEGORY,
                        onClick = { source = PrintSource.CATEGORY },
                        label = { Text("Kategoria: ${categoryName ?: "–"}") },
                    )
                    FilterChip(
                        selected = source == PrintSource.ALL,
                        onClick = { source = PrintSource.ALL },
                        label = { Text("Wszystkie kategorie") },
                    )
                    FilterChip(
                        selected = source == PrintSource.SENTENCE,
                        onClick = { source = PrintSource.SENTENCE },
                        enabled = !sentenceEmpty,
                        label = { Text("Ułożone zdanie / plan") },
                    )
                }
                Text("Wielkość kart", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardSize.entries.forEach { s ->
                        FilterChip(selected = size == s, onClick = { size = s }, label = { Text(s.label) })
                    }
                }
                Text("Podpis", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelPosition.entries.forEach { l ->
                        FilterChip(selected = label == l, onClick = { label = l }, label = { Text(l.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kolorowe tło (klucz Fitzgeralda)", modifier = Modifier.weight(1f))
                    Switch(checked = colored, onCheckedChange = { colored = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Linie cięcia", modifier = Modifier.weight(1f))
                    Switch(checked = cutLines, onCheckedChange = { cutLines = it })
                }
                Text(
                    "W następnym oknie wybierzesz drukarkę albo „Zapisz jako PDF”.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onPrint(PrintOptions(source, size, label, colored, cutLines)) }) {
                Text("Dalej")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
