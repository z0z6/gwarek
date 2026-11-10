package org.gwarek.aac.symbols

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gwarek.aac.platform.AppAssets
import java.util.Collections

/** Pamięć podręczna wczytanych SVG (ostatnio używane). */
private val painters: MutableMap<String, Painter> = Collections.synchronizedMap(
    object : LinkedHashMap<String, Painter>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Painter>?): Boolean = size > 800
    }
)

/** Obrazek SVG z biblioteki symboli – wektorowo, ostry w każdym rozmiarze. */
@Composable
fun SvgAssetImage(assetPath: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val painter by produceState(initialValue = painters[assetPath], assetPath) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    AppAssets.open(assetPath)?.use { loadSvgPainter(it, density) }
                }.getOrNull()
            }?.also { painters[assetPath] = it }
        }
    }
    val p = painter
    if (p != null) {
        Image(painter = p, contentDescription = contentDescription, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        Box(modifier)
    }
}
