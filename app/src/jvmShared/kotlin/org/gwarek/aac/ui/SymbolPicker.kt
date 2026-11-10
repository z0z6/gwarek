package org.gwarek.aac.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gwarek.aac.symbols.SvgAssetImage
import org.gwarek.aac.symbols.Symbol
import org.gwarek.aac.symbols.SymbolLibrary
import org.gwarek.aac.ui.theme.TileText

@Composable
fun SymbolPickerDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onPick: (Symbol) -> Unit,
) {
    val all by SymbolLibrary.symbols.collectAsState()
    var query by remember { mutableStateOf(initialQuery) }
    var domain by remember { mutableStateOf<String?>(null) }
    val domains = remember(all) { all.map { it.domain }.distinct() }
    val results = remember(all, query, domain) { SymbolLibrary.search(all, query, domain) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Biblioteka symboli",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${results.size} z ${all.size}", color = TileText.copy(alpha = 0.6f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Zamknij") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Szukaj, np. pić, pies, szkoła, boli") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = domain == null,
                            onClick = { domain = null },
                            label = { Text("Wszystkie") },
                        )
                    }
                    items(domains) { d ->
                        FilterChip(
                            selected = domain == d,
                            onClick = { domain = if (domain == d) null else d },
                            label = { Text(d) },
                        )
                    }
                }
                when {
                    all.isEmpty() -> Message(
                        "Biblioteka symboli nie została dołączona do tej wersji aplikacji.\n" +
                            "Zbuduj ją ponownie (skrypt tools/fetch_symbols.py)."
                    )
                    results.isEmpty() -> Message("Nic nie znaleziono. Spróbuj innego słowa lub kategorii.")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(130.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results, key = { it.id }) { s ->
                            SymbolCell(s, onClick = { onPick(s) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, fontSize = 18.sp, color = TileText.copy(alpha = 0.6f))
    }
}

@Composable
private fun SymbolCell(s: Symbol, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = s.type.background,
        contentColor = TileText,
        border = BorderStroke(2.dp, s.type.border),
        modifier = Modifier.aspectRatio(0.85f),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SvgAssetImage(s.assetPath, s.label, Modifier.weight(1f).fillMaxWidth())
            Text(
                s.label,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
