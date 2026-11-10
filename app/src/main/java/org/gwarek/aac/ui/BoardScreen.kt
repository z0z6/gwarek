package org.gwarek.aac.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.gwarek.aac.print.PrintDialog
import org.gwarek.aac.print.printPdf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.gwarek.aac.data.Category
import org.gwarek.aac.data.Pictogram
import org.gwarek.aac.tts.TtsStatus
import org.gwarek.aac.ai.LlmStatus
import org.gwarek.aac.ui.theme.TileText

/** Poniżej tej szerokości (telefony, tablety w pionie) układ jest kompaktowy. */
private val CompactWidth = 600.dp

/** Najmniejszy sensowny kafelek – mniejszych nie rysujemy, nawet gdy ustawiono dużo kolumn. */
private val MinTile = 88.dp

@Composable
fun BoardScreen(vm: MainViewModel) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selectedId by vm.selectedCategoryId.collectAsStateWithLifecycle()
    val pictograms by vm.pictograms.collectAsStateWithLifecycle()
    val sentence by vm.sentence.collectAsStateWithLifecycle()
    val editMode by vm.editMode.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ttsStatus by vm.ttsStatus.collectAsStateWithLifecycle()
    val predictions by vm.predictions.collectAsStateWithLifecycle()
    val correctedText by vm.correctedText.collectAsStateWithLifecycle()
    val assistantBusy by vm.assistantBusy.collectAsStateWithLifecycle()
    val llmStatus by vm.llmStatus.collectAsStateWithLifecycle()
    val importProgress by vm.modelImportProgress.collectAsStateWithLifecycle()
    val aiMessage by vm.aiMessage.collectAsStateWithLifecycle()
    var showAi by remember { mutableStateOf(false) }
    var showData by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    val dataBusy by vm.dataBusy.collectAsStateWithLifecycle()
    val dataMessage by vm.dataMessage.collectAsStateWithLifecycle()

    var editingPictogram by remember { mutableStateOf<Pictogram?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showPrint by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val onCategoryEdit: (Category) -> Unit = { editingCategory = it }
    val onCategoryAdd: () -> Unit = { editingCategory = Category(name = "", emoji = "⭐") }
    val onTap: (Pictogram) -> Unit = { p -> if (editMode) editingPictogram = p else vm.onPictogramTap(p) }
    val onAddPictogram: () -> Unit = {
        selectedId?.let { editingPictogram = Pictogram(categoryId = it, label = "") }
    }

    val tileStyle = TileStyle(settings.showLabels, settings.labelScale, settings.highContrast)
    CompositionLocalProvider(LocalTileStyle provides tileStyle) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < CompactWidth
            val railWidth = if (maxWidth < 840.dp) 180.dp else 220.dp
            val gap = if (compact) 8.dp else 12.dp
            Column(
                Modifier.fillMaxSize().padding(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                SentenceBar(
                    sentence = sentence,
                    editMode = editMode,
                    compact = compact,
                    onSpeak = vm::speakSentence,
                    onBackspace = vm::backspace,
                    onClear = vm::clearSentence,
                    onToggleEdit = {
                        when {
                            editMode -> vm.setEditMode(false)
                            settings.editPin.isNotEmpty() -> showPin = true
                            else -> vm.setEditMode(true)
                        }
                    },
                    onSettings = { showSettings = true },
                    onPrint = { showPrint = true },
                )

                if (ttsStatus == TtsStatus.MISSING_LANGUAGE || ttsStatus == TtsStatus.ERROR) {
                    TtsBanner(ttsStatus, onRecheck = vm::recheckVoice)
                }

                if (settings.smartSpeech && llmStatus == LlmStatus.READY && sentence.size >= 2) {
                    AssistantLine(
                        text = correctedText,
                        busy = assistantBusy,
                        onClick = { correctedText?.let { vm.speakText(it) } },
                    )
                }

                if (settings.showPredictions && !editMode && predictions.isNotEmpty()) {
                    PredictionRow(
                        predictions = predictions,
                        height = if (compact) 84.dp else 104.dp,
                        onTap = { vm.onPictogramTap(it) },
                    )
                }

                if (compact) {
                    CategoryStrip(
                        categories = categories,
                        selectedId = selectedId,
                        editMode = editMode,
                        onSelect = vm::selectCategory,
                        onEdit = onCategoryEdit,
                        onAdd = onCategoryAdd,
                    )
                    PictogramGrid(
                        pictograms = pictograms,
                        columns = settings.columns,
                        editMode = editMode,
                        onTap = onTap,
                        onAdd = onAddPictogram,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        CategoryRail(
                            categories = categories,
                            selectedId = selectedId,
                            editMode = editMode,
                            onSelect = vm::selectCategory,
                            onEdit = onCategoryEdit,
                            onAdd = onCategoryAdd,
                            modifier = Modifier
                                .width(railWidth)
                                .fillMaxHeight(),
                        )
                        PictogramGrid(
                            pictograms = pictograms,
                            columns = settings.columns,
                            editMode = editMode,
                            onTap = onTap,
                            onAdd = onAddPictogram,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }

    }

    editingPictogram?.let { p ->
        PictogramEditDialog(
            initial = p,
            categories = categories,
            onDismiss = { editingPictogram = null },
            onSave = { vm.savePictogram(it); editingPictogram = null },
            onDelete = if (p.id != 0L) {
                { vm.deletePictogram(p); editingPictogram = null }
            } else null,
        )
    }
    editingCategory?.let { c ->
        CategoryEditDialog(
            initial = c,
            onDismiss = { editingCategory = null },
            onSave = { vm.saveCategory(it); editingCategory = null },
            onDelete = if (c.id != 0L) {
                { vm.deleteCategory(c); editingCategory = null }
            } else null,
            onGenerate = if (c.id == 0L && llmStatus == LlmStatus.READY) {
                { name, emoji ->
                    editingCategory = null
                    Toast.makeText(context, "Asystent przygotowuje tablicę…", Toast.LENGTH_LONG).show()
                    scope.launch {
                        val n = vm.generateBoard(name, emoji)
                        Toast.makeText(
                            context,
                            if (n != null) "Dodano $n piktogramów – sprawdź je i popraw w razie potrzeby"
                            else "Nie udało się wygenerować tablicy",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } else null,
        )
    }
    if (showPrint) {
        PrintDialog(
            categoryName = categories.firstOrNull { it.id == selectedId }?.name,
            sentenceEmpty = sentence.isEmpty(),
            onDismiss = { showPrint = false },
            onPrint = { opt ->
                showPrint = false
                Toast.makeText(context, "Przygotowuję wydruk…", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val file = vm.buildPrintPdf(opt)
                    if (file == null) {
                        Toast.makeText(context, "Brak piktogramów do wydruku", Toast.LENGTH_SHORT).show()
                    } else {
                        printPdf(context, file, "Gwarek – karty")
                    }
                }
            },
        )
    }
    if (showSettings) {
        SettingsDialog(
            initial = settings,
            onDismiss = { showSettings = false },
            onSave = { vm.updateSettings(it); showSettings = false },
            onOpenAi = { showSettings = false; showAi = true },
            onOpenData = { showSettings = false; showData = true },
            onPinScreen = {
                showSettings = false
                vm.setEditMode(false)
                (context as? Activity)?.startLockTask()
            },
        )
    }
    if (showData) {
        DataDialog(
            busy = dataBusy,
            message = dataMessage,
            onExportBackup = vm::exportBackup,
            onImportBackup = vm::importBackup,
            onExportObz = vm::exportObz,
            onImportObf = vm::importObf,
            onDismiss = { showData = false },
        )
    }
    if (showPin) {
        PinDialog(
            pin = settings.editPin,
            onSuccess = { showPin = false; vm.setEditMode(true) },
            onDismiss = { showPin = false },
        )
    }
    if (showAi) {
        AiDialog(
            status = llmStatus,
            modelSizeBytes = remember(llmStatus, importProgress) { vm.modelSizeBytes() },
            importProgress = importProgress,
            message = aiMessage,
            settings = settings,
            onSettings = vm::updateSettings,
            onImport = vm::importModel,
            onRemove = vm::removeModel,
            onDismiss = { showAi = false },
        )
    }
}

@Composable
private fun SentenceBar(
    sentence: List<Pictogram>,
    editMode: Boolean,
    compact: Boolean,
    onSpeak: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onToggleEdit: () -> Unit,
    onSettings: () -> Unit,
    onPrint: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (compact) {
            // Telefon: zdanie na całą szerokość, przyciski w rzędzie pod spodem.
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SentenceStrip(sentence, onSpeak, Modifier.fillMaxWidth().height(84.dp))
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BarButton("🔊", "Mów", onSpeak, Modifier.weight(1.4f).fillMaxHeight(), true, compact = true)
                    BarButton("⌫", "Cofnij", onBackspace, Modifier.weight(1f).fillMaxHeight(), compact = true)
                    BarButton("✖", "Wyczyść", onClear, Modifier.weight(1f).fillMaxHeight(), compact = true)
                    if (editMode) {
                        RoundIconButton(onClick = onPrint, size = 52.dp) { Text("🖨️", fontSize = 22.sp) }
                        RoundIconButton(onClick = onSettings, size = 52.dp) {
                            Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                        }
                    }
                    LockButton(editMode = editMode, onToggle = onToggleEdit, size = 52.dp)
                }
            }
        } else {
            Row(
                Modifier.height(140.dp).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SentenceStrip(sentence, onSpeak, Modifier.weight(1f).fillMaxHeight())
                BarButton("🔊", "Mów", onSpeak, Modifier.width(120.dp).fillMaxHeight(), true)
                BarButton("⌫", "Cofnij", onBackspace, Modifier.width(96.dp).fillMaxHeight())
                BarButton("✖", "Wyczyść", onClear, Modifier.width(96.dp).fillMaxHeight())
                if (editMode) {
                    RoundIconButton(onClick = onPrint) { Text("🖨️", fontSize = 26.sp) }
                    RoundIconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                    }
                }
                LockButton(editMode = editMode, onToggle = onToggleEdit)
            }
        }
    }
}

@Composable
private fun SentenceStrip(sentence: List<Pictogram>, onSpeak: () -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(sentence.size) {
        if (sentence.isNotEmpty()) listState.animateScrollToItem(sentence.lastIndex)
    }
    // Dotknięcie paska zdania = przeczytaj całe zdanie (typowe w AAC).
    Box(modifier.clickable(onClick = onSpeak), contentAlignment = Alignment.CenterStart) {
        if (sentence.isEmpty()) {
            Text(
                "Dotknij piktogramu, aby ułożyć zdanie",
                color = TileText.copy(alpha = 0.45f),
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        } else {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(sentence) { _, p ->
                    PictogramTile(
                        p = p,
                        onClick = onSpeak,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarButton(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    highlighted: Boolean = false,
    compact: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary else Color(0xFFEDEFF4),
        contentColor = if (highlighted) Color.White else TileText,
        modifier = modifier,
    ) {
        if (compact) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(glyph, fontSize = 22.sp)
                if (highlighted) {
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(glyph, fontSize = 40.sp)
                Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, size: Dp = 64.dp, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFFEDEFF4),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Blokada edycji: wejście w tryb edycji wymaga przytrzymania,
 * żeby dziecko nie zmieniało tablic przypadkiem.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LockButton(editMode: Boolean, onToggle: () -> Unit, size: Dp = 64.dp) {
    val context = LocalContext.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (editMode) MaterialTheme.colorScheme.primary else Color(0xFFEDEFF4))
            .combinedClickable(
                onClick = {
                    if (editMode) onToggle()
                    else Toast.makeText(context, "Przytrzymaj, aby edytować", Toast.LENGTH_SHORT).show()
                },
                onLongClick = { if (!editMode) onToggle() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (editMode) Icons.Default.Check else Icons.Default.Lock,
            contentDescription = if (editMode) "Zakończ edycję" else "Edycja (przytrzymaj)",
            tint = if (editMode) Color.White else TileText,
        )
    }
}

@Composable
private fun TtsBanner(status: TtsStatus, onRecheck: () -> Unit) {
    val context = LocalContext.current
    Surface(
        color = Color(0xFFFFF3CD),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (status == TtsStatus.ERROR)
                    "Nie udało się uruchomić syntezatora mowy. Sprawdź ustawienia zamiany tekstu na mowę."
                else
                    "Brak polskiego głosu. Zainstaluj go, aby aplikacja mogła mówić bez internetu.",
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { openTtsInstall(context) }) { Text("Zainstaluj głos") }
            TextButton(onClick = onRecheck) { Text("Sprawdź ponownie") }
        }
    }
}

private fun openTtsInstall(context: Context) {
    try {
        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(context, "Otwórz Ustawienia → Zamiana tekstu na mowę", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun CategoryRail(
    categories: List<Category>,
    selectedId: Long?,
    editMode: Boolean,
    onSelect: (Long) -> Unit,
    onEdit: (Category) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories, key = { it.id }) { c ->
            val selected = c.id == selectedId
            Surface(
                onClick = { if (editMode && selected) onEdit(c) else onSelect(c.id) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                contentColor = if (selected) Color.White else TileText,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        c.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    if (editMode && selected) {
                        Icon(Icons.Default.Edit, contentDescription = "Edytuj kategorię")
                    }
                }
            }
        }
        if (editMode) {
            item(key = "add-category") {
                OutlinedButton(
                    onClick = onAdd,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Kategoria", fontSize = 18.sp)
                }
            }
        }
    }
}


/** Kategorie jako przewijany poziomo pasek – dla wąskich ekranów. */
@Composable
private fun CategoryStrip(
    categories: List<Category>,
    selectedId: Long?,
    editMode: Boolean,
    onSelect: (Long) -> Unit,
    onEdit: (Category) -> Unit,
    onAdd: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId, categories.size) {
        val index = categories.indexOfFirst { it.id == selectedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        items(categories, key = { it.id }) { c ->
            val selected = c.id == selectedId
            Surface(
                onClick = { if (editMode && selected) onEdit(c) else onSelect(c.id) },
                shape = RoundedCornerShape(28.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                contentColor = if (selected) Color.White else TileText,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.emoji, fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(c.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (editMode && selected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Edit, contentDescription = "Edytuj kategorię", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (editMode) {
            item(key = "add-category") {
                OutlinedButton(
                    onClick = onAdd,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Kategoria")
                }
            }
        }
    }
}

@Composable
private fun PictogramGrid(
    pictograms: List<Pictogram>,
    columns: Int,
    editMode: Boolean,
    onTap: (Pictogram) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pictograms.isEmpty() && !editMode) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Ta kategoria jest pusta.\nPrzytrzymaj kłódkę, aby dodać piktogramy.",
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
                color = TileText.copy(alpha = 0.5f),
            )
        }
        return
    }
    BoxWithConstraints(modifier) {
        val spacing = 10.dp
        // Ustawiona liczba kolumn to maksimum – na wąskim ekranie kafelki nie zejdą poniżej MinTile.
        val fitting = ((maxWidth + spacing) / (MinTile + spacing)).toInt().coerceAtLeast(2)
        val effectiveColumns = columns.coerceAtMost(fitting)
        LazyVerticalGrid(
            columns = GridCells.Fixed(effectiveColumns),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(pictograms, key = { it.id }) { p ->
                PictogramTile(
                    p = p,
                    onClick = { onTap(p) },
                    modifier = Modifier.aspectRatio(1f),
                    showEditBadge = editMode,
                )
            }
            if (editMode) {
                item(key = "add-pictogram") {
                    AddTile(onClick = onAdd, modifier = Modifier.aspectRatio(1f))
                }
            }
        }
    }
}

/** Zdanie ułożone przez asystenta – dotknięcie odczytuje je ponownie. */
@Composable
private fun AssistantLine(text: String?, busy: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFEFF3FF),
        contentColor = TileText,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✨", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            when {
                busy && text == null -> Text("Układam zdanie…", color = TileText.copy(alpha = 0.6f), fontSize = 18.sp)
                text != null -> Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                else -> Text("Nie udało się ułożyć zdania", color = TileText.copy(alpha = 0.6f), fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun PredictionRow(predictions: List<Pictogram>, height: Dp, onTap: (Pictogram) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("💡", fontSize = 22.sp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxHeight(),
        ) {
            items(predictions, key = { it.id }) { p ->
                PictogramTile(
                    p = p,
                    onClick = { onTap(p) },
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                )
            }
        }
    }
}
