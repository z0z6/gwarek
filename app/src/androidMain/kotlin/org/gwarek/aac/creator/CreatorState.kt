package org.gwarek.aac.creator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gwarek.aac.data.Pictogram
import org.gwarek.aac.symbols.ASSET_PREFIX
import org.gwarek.aac.symbols.SvgRenderer
import org.gwarek.aac.symbols.libraryAssetPath
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/** Rozdzielczość gotowego piktogramu – wystarcza do druku kart 8 cm. */
const val CREATOR_PX = 768

enum class CreatorMode { DRAW, MOVE }

/** Punkty i grubość są znormalizowane (0..1 względem boku obrazka). */
data class Stroke(val points: List<Offset>, val color: Int, val width: Float)

/**
 * Stan kreatora. Ta sama funkcja [render] rysuje podgląd na ekranie i gotowy plik,
 * więc to, co widać, jest dokładnie tym, co zostanie zapisane.
 */
class CreatorState {
    /** ARGB; 0 = przezroczyste (na kafelku prześwituje kolor części mowy). */
    var background by mutableIntStateOf(0)
    var photo by mutableStateOf<Bitmap?>(null)
        private set
    var photoScale by mutableFloatStateOf(1f)
    var photoOffset by mutableStateOf(Offset.Zero)
    val symbols = mutableStateListOf<Bitmap>()
    var crossOut by mutableStateOf(false)
    val strokes = mutableStateListOf<Stroke>()
    var current by mutableStateOf<Stroke?>(null)
        private set
    var penColor by mutableIntStateOf(android.graphics.Color.BLACK)
    var penWidth by mutableFloatStateOf(0.025f)

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFE53935.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    fun setNewPhoto(bitmap: Bitmap?) {
        photo = bitmap
        photoScale = 1f
        photoOffset = Offset.Zero
    }

    /** Najwyżej dwa symbole (obok siebie); trzeci zastępuje drugi. */
    fun addSymbol(bitmap: Bitmap) {
        if (symbols.size >= 2) symbols.removeAt(1)
        symbols.add(bitmap)
    }

    fun startStroke(p: Offset) {
        current = Stroke(listOf(p), penColor, penWidth)
    }

    fun extendStroke(p: Offset) {
        current = current?.let { it.copy(points = it.points + p) }
    }

    fun endStroke() {
        current?.let { strokes.add(it) }
        current = null
    }

    fun undoStroke() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
    }

    val isEmpty: Boolean
        get() = photo == null && symbols.isEmpty() && strokes.isEmpty() && !crossOut

    /** Wypełnia kreator obrazkiem edytowanego piktogramu. */
    suspend fun prefill(context: Context, p: Pictogram) {
        val asset = p.libraryAssetPath()
        val path = p.imagePath
        if (asset != null) {
            val b = withContext(Dispatchers.Default) { SvgRenderer.render(context, asset, CREATOR_PX) }
            if (b != null) addSymbol(b)
        } else if (path != null && !path.startsWith(ASSET_PREFIX)) {
            val b = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
            if (b != null) setNewPhoto(b)
        }
    }

    fun render(canvas: Canvas, size: Float) {
        if (background != 0) canvas.drawColor(background)

        photo?.let { bmp ->
            val s = max(size / bmp.width, size / bmp.height) * photoScale
            val w = bmp.width * s
            val h = bmp.height * s
            val left = (size - w) / 2f + photoOffset.x * size
            val top = (size - h) / 2f + photoOffset.y * size
            canvas.save()
            canvas.clipRect(0f, 0f, size, size)
            canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), bitmapPaint)
            canvas.restore()
        }

        val pad = size * 0.06f
        when (symbols.size) {
            1 -> canvas.drawBitmap(symbols[0], null, RectF(pad, pad, size - pad, size - pad), bitmapPaint)
            2 -> {
                val side = (size - 3 * pad) / 2f
                val top = (size - side) / 2f
                canvas.drawBitmap(symbols[0], null, RectF(pad, top, pad + side, top + side), bitmapPaint)
                canvas.drawBitmap(
                    symbols[1], null,
                    RectF(2 * pad + side, top, 2 * pad + 2 * side, top + side), bitmapPaint,
                )
            }
        }

        for (stroke in strokes) drawStroke(canvas, stroke, size)
        current?.let { drawStroke(canvas, it, size) }

        if (crossOut) {
            crossPaint.strokeWidth = size * 0.06f
            val r = size * 0.42f
            val c = size / 2f
            canvas.drawCircle(c, c, r, crossPaint)
            val d = r * 0.7071f
            canvas.drawLine(c - d, c - d, c + d, c + d, crossPaint)
        }
    }

    private fun drawStroke(canvas: Canvas, s: Stroke, size: Float) {
        if (s.points.isEmpty()) return
        val width = s.width * size
        if (s.points.size == 1) {
            dotPaint.color = s.color
            canvas.drawCircle(s.points[0].x * size, s.points[0].y * size, width / 2f, dotPaint)
            return
        }
        val path = Path()
        path.moveTo(s.points[0].x * size, s.points[0].y * size)
        for (i in 1 until s.points.size) path.lineTo(s.points[i].x * size, s.points[i].y * size)
        strokePaint.color = s.color
        strokePaint.strokeWidth = width
        canvas.drawPath(path, strokePaint)
    }

    /** Zapisuje gotowy piktogram jako PNG w pamięci aplikacji i zwraca ścieżkę. */
    fun export(context: Context): String {
        val bmp = Bitmap.createBitmap(CREATOR_PX, CREATOR_PX, Bitmap.Config.ARGB_8888)
        render(Canvas(bmp), CREATOR_PX.toFloat())
        val dir = File(context.filesDir, "pictograms").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }
}
