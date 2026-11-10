package org.gwarek.aac.creator

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gwarek.aac.data.Pictogram
import org.gwarek.aac.symbols.SvgRenderer
import org.gwarek.aac.ui.SymbolPickerDialog
import org.gwarek.aac.ui.theme.TileText
import java.io.File

private val PenColors = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835,
    0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFF6D4C41,
).map { it.toInt() }

private val PenWidths = listOf("Cienki" to 0.012f, "Średni" to 0.025f, "Gruby" to 0.05f)

private val Backgrounds = listOf(
    "Przezroczyste" to 0,
    "Białe" to 0xFFFFFFFF.toInt(),
    "Kremowe" to 0xFFFFF8E1.toInt(),
    "Czarne" to 0xFF000000.toInt(),
)

/**
 * Kreator piktogramów: zdjęcie (aparat/galeria) z kadrowaniem, do dwóch symboli
 * z biblioteki obok siebie, rysowanie palcem i znak przekreślenia („nie …”).
 * Wynik trafia do pliku PNG, którego ścieżkę dostaje [onDone].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PictogramCreatorDialog(
    initial: Pictogram,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { CreatorState() }
    var mode by remember { mutableStateOf(CreatorMode.DRAW) }
    var showPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { state.prefill(context, initial) }

    val cameraFile = remember { File(File(context.cacheDir, "camera").apply { mkdirs() }, "photo.jpg") }
    val cameraUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageLoader.loadScaled(context, cameraUri) }
            if (bmp != null) { state.setNewPhoto(bmp); mode = CreatorMode.MOVE }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageLoader.loadScaled(context, uri) }
            if (bmp != null) { state.setNewPhoto(bmp); mode = CreatorMode.MOVE }
            else Toast.makeText(context, "Nie udało się wczytać zdjęcia", Toast.LENGTH_SHORT).show()
        }
    }

    if (showPicker) {
        SymbolPickerDialog(
            initialQuery = "",
            onDismiss = { showPicker = false },
            onPick = { s ->
                showPicker = false
                scope.launch {
                    val bmp = withContext(Dispatchers.Default) {
                        SvgRenderer.render(context, s.assetPath, CREATOR_PX)
                    }
                    if (bmp != null) state.addSymbol(bmp)
                }
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Kreator piktogramu",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Anuluj") }
                    Button(
                        enabled = !saving && !state.isEmpty,
                        onClick = {
                            saving = true
                            scope.launch {
                                val path = withContext(Dispatchers.IO) { state.export(context) }
                                onDone(path)
                            }
                        },
                    ) { Text("Gotowe") }
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val wide = maxWidth > maxHeight
                    val side = if (wide) minOf(maxHeight, maxWidth * 0.6f) else minOf(maxWidth, maxHeight * 0.55f)
                    val tools: @Composable (Modifier) -> Unit = { m ->
                        CreatorTools(
                            state = state,
                            mode = mode,
                            onMode = { mode = it },
                            onCamera = {
                                try {
                                    takePicture.launch(cameraUri)
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, "Brak aplikacji aparatu", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onGallery = {
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onSymbol = { showPicker = true },
                            modifier = m,
                        )
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CreatorCanvas(state, mode, Modifier.size(side))
                            tools(Modifier.weight(1f))
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CreatorCanvas(state, mode, Modifier.size(side))
                            tools(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorCanvas(state: CreatorState, mode: CreatorMode, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color(0xFFE4E7EE))
            .border(2.dp, Color(0xFFB0B6C3), shape)
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(mode) {
                    val w = size.width.toFloat()
                    if (mode == CreatorMode.DRAW) {
                        detectDragGestures(
                            onDragStart = { o -> state.startStroke(o / w) },
                            onDrag = { change, _ ->
                                change.consume()
                                state.extendStroke(change.position / w)
                            },
                            onDragEnd = { state.endStroke() },
                            onDragCancel = { state.endStroke() },
                        )
                    } else {
                        detectTransformGestures { _, pan, zoom, _ ->
                            state.photoScale = (state.photoScale * zoom).coerceIn(0.3f, 8f)
                            state.photoOffset += pan / w
                        }
                    }
                }
        ) {
            drawIntoCanvas { state.render(it.nativeCanvas, size.width) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreatorTools(
    state: CreatorState,
    mode: CreatorMode,
    onMode: (CreatorMode) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSymbol: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("Obraz")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCamera) { Text("📷 Aparat") }
            OutlinedButton(onClick = onGallery) { Text("🖼️ Galeria") }
            OutlinedButton(onClick = onSymbol) {
                Text(if (state.symbols.size < 2) "📚 Dodaj symbol" else "📚 Zamień drugi symbol")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.photo != null) TextButton(onClick = { state.setNewPhoto(null) }) { Text("Usuń zdjęcie") }
            if (state.symbols.isNotEmpty()) TextButton(onClick = { state.symbols.clear() }) { Text("Usuń symbole") }
        }

        SectionTitle("Tryb")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == CreatorMode.DRAW,
                onClick = { onMode(CreatorMode.DRAW) },
                label = { Text("✏️ Rysowanie") },
            )
            FilterChip(
                selected = mode == CreatorMode.MOVE,
                onClick = { onMode(CreatorMode.MOVE) },
                enabled = state.photo != null,
                label = { Text("✋ Kadrowanie zdjęcia") },
            )
        }
        if (mode == CreatorMode.MOVE) {
            Text(
                "Przesuwaj zdjęcie palcem, powiększaj dwoma palcami.",
                color = TileText.copy(alpha = 0.6f),
            )
        }

        SectionTitle("Pisak")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PenColors.forEach { c ->
                val selected = state.penColor == c
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            if (selected) 4.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else Color(0xFF9E9E9E),
                            CircleShape,
                        )
                        .clickable { state.penColor = c; onMode(CreatorMode.DRAW) }
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PenWidths.forEach { (label, w) ->
                FilterChip(
                    selected = state.penWidth == w,
                    onClick = { state.penWidth = w },
                    label = { Text(label) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.undoStroke() }, enabled = state.strokes.isNotEmpty()) {
                Text("↶ Cofnij kreskę")
            }
            TextButton(onClick = { state.strokes.clear() }, enabled = state.strokes.isNotEmpty()) {
                Text("Wyczyść rysunek")
            }
        }

        SectionTitle("Tło")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Backgrounds.forEach { (label, c) ->
                FilterChip(
                    selected = state.background == c,
                    onClick = { state.background = c },
                    label = { Text(label) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Przekreślenie (zakaz, „nie …”)", modifier = Modifier.weight(1f))
            Switch(checked = state.crossOut, onCheckedChange = { state.crossOut = it })
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}
