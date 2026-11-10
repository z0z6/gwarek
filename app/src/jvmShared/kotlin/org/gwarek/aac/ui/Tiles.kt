package org.gwarek.aac.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gwarek.aac.data.Pictogram
import org.gwarek.aac.model.WordType
import org.gwarek.aac.symbols.ASSET_PREFIX
import org.gwarek.aac.platform.loadFileBitmap
import org.gwarek.aac.symbols.SvgAssetImage
import org.gwarek.aac.symbols.SymbolLibrary
import org.gwarek.aac.ui.theme.TileText

@Composable
fun PictogramTile(
    p: Pictogram,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showEditBadge: Boolean = false,
) {
    val type = WordType.from(p.wordType)
    val style = LocalTileStyle.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = type.background,
        contentColor = TileText,
        border = if (style.highContrast) BorderStroke(6.dp, TileText) else BorderStroke(3.dp, type.border),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val labelSize = with(LocalDensity.current) { (maxWidth * 0.12f * style.labelScale).toSp() }
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PictogramImage(p, Modifier.weight(1f).fillMaxWidth())
                if (style.showLabels) {
                    Text(
                        text = p.label,
                        fontSize = labelSize,
                        lineHeight = labelSize * 1.1f,
                        fontWeight = if (style.highContrast) FontWeight.Bold else FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showEditBadge) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
                )
            }
        }
    }
}

@Composable
fun PictogramImage(p: Pictogram, modifier: Modifier = Modifier) {
    // Obserwujemy bibliotekę, żeby po jej wczytaniu emoji zamieniły się na obrazki OpenMoji.
    val library by SymbolLibrary.symbols.collectAsState()
    val path = p.imagePath
    val emoji = p.emoji
    val assetPath: String? = when {
        path != null && path.startsWith(ASSET_PREFIX) -> path.removePrefix(ASSET_PREFIX)
        path == null && !emoji.isNullOrBlank() && library.isNotEmpty() ->
            SymbolLibrary.forEmoji(emoji)?.assetPath
        else -> null
    }
    val filePath = path?.takeIf { !it.startsWith(ASSET_PREFIX) }
    val fileBitmap = remember(filePath) {
        filePath?.let { loadFileBitmap(it) }
    }

    when {
        assetPath != null -> SvgAssetImage(assetPath, p.label, modifier)
        fileBitmap != null -> Image(
            bitmap = fileBitmap,
            contentDescription = p.label,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
        else -> BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            val glyphSize = with(LocalDensity.current) { (side * 0.62f).toSp() }
            Text(
                text = if (!emoji.isNullOrBlank()) emoji else p.label.take(1).uppercase(),
                fontSize = glyphSize,
                lineHeight = glyphSize,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = if (!emoji.isNullOrBlank()) TileText else TileText.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun AddTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Dodaj piktogram",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
