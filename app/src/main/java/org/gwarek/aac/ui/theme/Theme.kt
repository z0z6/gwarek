package org.gwarek.aac.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TileText = Color(0xFF1B1B1B)

private val colors = lightColorScheme(
    primary = Color(0xFF2B5FD9),
    onPrimary = Color.White,
    background = Color(0xFFF3F5F9),
    surface = Color.White,
    onSurface = TileText,
)

@Composable
fun GwarekTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
