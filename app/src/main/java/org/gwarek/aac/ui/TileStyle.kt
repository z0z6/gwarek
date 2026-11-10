package org.gwarek.aac.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Ustawienia wyglądu kafelków (dostępność), przekazywane w dół drzewa Compose. */
data class TileStyle(
    val showLabels: Boolean = true,
    val labelScale: Float = 1f,
    val highContrast: Boolean = false,
)

val LocalTileStyle = staticCompositionLocalOf { TileStyle() }
