package org.gadka.aac.symbols

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renderuje SVG z assets do bitmap (z pamięcią podręczną), offline, przez AndroidSVG. */
object SvgRenderer {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Rozmiary zaokrąglamy w górę do wielokrotności 64 px, żeby lepiej trafiać w cache. */
    fun bucket(px: Int): Int = (((px.coerceIn(64, 1024)) + 63) / 64) * 64

    fun cached(assetPath: String, sizePx: Int): Bitmap? = cache.get("$assetPath@$sizePx")

    fun render(context: Context, assetPath: String, sizePx: Int): Bitmap? {
        val key = "$assetPath@$sizePx"
        cache.get(key)?.let { return it }
        return try {
            val svg = context.assets.open(assetPath).use { SVG.getFromInputStream(it) }
            if (svg.documentViewBox == null && svg.documentWidth > 0 && svg.documentHeight > 0) {
                svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
            }
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp), RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()))
            cache.put(key, bmp)
            bmp
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun rememberSvgBitmap(assetPath: String, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val size = SvgRenderer.bucket(sizePx)
    val state by produceState(
        initialValue = SvgRenderer.cached(assetPath, size)?.asImageBitmap(),
        assetPath, size,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                SvgRenderer.render(context, assetPath, size)
            }?.asImageBitmap()
        }
    }
    return state
}

@Composable
fun SvgAssetImage(assetPath: String, contentDescription: String?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        val px = with(LocalDensity.current) { side.roundToPx() }
        val bitmap = rememberSvgBitmap(assetPath, px)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
