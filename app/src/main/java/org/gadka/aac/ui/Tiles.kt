package org.gadka.aac.ui

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gadka.aac.data.Pictogram
import org.gadka.aac.model.WordType
import org.gadka.aac.ui.theme.TileText

@Composable
fun PictogramTile(
    p: Pictogram,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showEditBadge: Boolean = false,
) {
    val type = WordType.from(p.wordType)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = type.background,
        contentColor = TileText,
        border = BorderStroke(3.dp, type.border),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val labelSize = with(LocalDensity.current) { (maxWidth * 0.12f).toSp() }
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PictogramImage(p, Modifier.weight(1f).fillMaxWidth())
                Text(
                    text = p.label,
                    fontSize = labelSize,
                    lineHeight = labelSize * 1.1f,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
    val bitmap = remember(p.imagePath) {
        p.imagePath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        val glyphSize = with(LocalDensity.current) { (side * 0.62f).toSp() }
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = p.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            !p.emoji.isNullOrBlank() -> Text(
                text = p.emoji,
                fontSize = glyphSize,
                lineHeight = glyphSize,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            else -> Text(
                text = p.label.take(1).uppercase(),
                fontSize = glyphSize,
                lineHeight = glyphSize,
                fontWeight = FontWeight.Bold,
                color = TileText.copy(alpha = 0.5f),
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
