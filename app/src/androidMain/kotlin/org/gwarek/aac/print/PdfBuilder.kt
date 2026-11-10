package org.gwarek.aac.print

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import org.gwarek.aac.data.Pictogram
import org.gwarek.aac.model.WordType
import org.gwarek.aac.symbols.ASSET_PREFIX
import org.gwarek.aac.symbols.SvgRenderer
import org.gwarek.aac.symbols.libraryAssetPath
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Składa karty piktogramów na stronach A4. Symbole z biblioteki trafiają do PDF
 * jako grafika wektorowa, więc drukują się ostro w każdym rozmiarze.
 */
class PdfBuilder(private val context: Context) {
    private val pageW = 595 // A4 w punktach (1/72 cala)
    private val pageH = 842
    private val margin = 28f
    private val footerH = 18f

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.BLACK
    }
    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 7f
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = 0xFF1B1B1B.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = 0xFFAAAAAA.toInt()
        pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** sections: (tytuł strony lub null, piktogramy). Każda sekcja zaczyna się od nowej strony. */
    fun build(sections: List<Pair<String?, List<Pictogram>>>, opt: PrintOptions, out: File) {
        val doc = PdfDocument()
        val card = opt.size.cm * 72f / 2.54f
        val gap = if (opt.cutLines) 10f else 8f
        val cols = max(1, ((pageW - 2 * margin + gap) / (card + gap)).toInt())
        val gridW = cols * card + (cols - 1) * gap
        val x0 = (pageW - gridW) / 2f
        val bottomLimit = pageH - margin - footerH

        var pageNo = 0
        var page: PdfDocument.Page? = null

        fun newPage(): Canvas {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            page = p
            drawFooter(p.canvas, pageNo)
            return p.canvas
        }

        for ((title, items) in sections) {
            if (items.isEmpty()) continue
            var canvas = newPage()
            var y = margin
            if (title != null) {
                canvas.drawText(title, margin, y + 18f, titlePaint)
                y += 34f
            }
            var col = 0
            for (p in items) {
                if (col == cols) {
                    col = 0
                    y += card + gap
                }
                if (y + card > bottomLimit) {
                    canvas = newPage()
                    y = margin
                    col = 0
                }
                val left = x0 + col * (card + gap)
                drawCard(canvas, p, RectF(left, y, left + card, y + card), opt, gap)
                col++
            }
        }
        page?.let { doc.finishPage(it) }
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
    }

    private fun drawFooter(canvas: Canvas, pageNo: Int) {
        canvas.drawText(
            "Symbole: Mulberry Symbols, OpenMoji – CC BY-SA 4.0 · wydrukowano w aplikacji Gwarek · strona $pageNo",
            pageW / 2f, pageH - margin + 4f, footerPaint,
        )
    }

    private fun drawCard(canvas: Canvas, p: Pictogram, r: RectF, opt: PrintOptions, gap: Float) {
        val type = WordType.from(p.wordType)
        val radius = r.width() * 0.06f

        if (opt.cutLines) {
            val h = gap / 2f
            canvas.drawRect(r.left - h, r.top - h, r.right + h, r.bottom + h, cutPaint)
        }
        fillPaint.color = if (opt.colored) type.background.toArgb() else Color.WHITE
        canvas.drawRoundRect(r, radius, radius, fillPaint)
        borderPaint.color = if (opt.colored) type.border.toArgb() else 0xFF555555.toInt()
        borderPaint.strokeWidth = max(1.5f, r.width() * 0.015f)
        canvas.drawRoundRect(r, radius, radius, borderPaint)

        val pad = r.width() * 0.07f
        val labelH = if (opt.label == LabelPosition.NONE) 0f else r.height() * 0.2f
        val imageArea = when (opt.label) {
            LabelPosition.ABOVE -> RectF(r.left + pad, r.top + labelH, r.right - pad, r.bottom - pad)
            else -> RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad - labelH)
        }
        val side = min(imageArea.width(), imageArea.height())
        val img = RectF(
            imageArea.centerX() - side / 2f, imageArea.centerY() - side / 2f,
            imageArea.centerX() + side / 2f, imageArea.centerY() + side / 2f,
        )
        drawImage(canvas, p, img)

        if (opt.label != LabelPosition.NONE) {
            val labelBox = if (opt.label == LabelPosition.ABOVE) {
                RectF(r.left + pad, r.top + pad * 0.5f, r.right - pad, r.top + labelH)
            } else {
                RectF(r.left + pad, r.bottom - pad * 0.5f - labelH, r.right - pad, r.bottom - pad * 0.5f)
            }
            drawLabel(canvas, p.label, labelBox)
        }
    }

    private fun drawImage(canvas: Canvas, p: Pictogram, r: RectF) {
        val asset = p.libraryAssetPath()
        if (asset != null) {
            SvgRenderer.load(context, asset)?.let { svg ->
                svg.renderToCanvas(canvas, r)
                return
            }
        }
        val path = p.imagePath
        if (path != null && !path.startsWith(ASSET_PREFIX)) {
            BitmapFactory.decodeFile(path)?.let { bmp ->
                canvas.drawBitmap(bmp, null, r, bitmapPaint)
                return
            }
        }
        val text = p.emoji?.takeIf { it.isNotBlank() } ?: p.label.take(1).uppercase()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = r.height() * 0.7f
            textAlign = Paint.Align.CENTER
            color = 0xFF1B1B1B.toInt()
        }
        val fm = paint.fontMetrics
        canvas.drawText(text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint)
    }

    private fun drawLabel(canvas: Canvas, text: String, box: RectF) {
        var size = box.height() * 0.75f
        labelPaint.textSize = size
        while (labelPaint.measureText(text) > box.width() && size > 6f) {
            size *= 0.92f
            labelPaint.textSize = size
        }
        val shown = TextUtils.ellipsize(text, labelPaint, box.width(), TextUtils.TruncateAt.END).toString()
        val fm = labelPaint.fontMetrics
        canvas.drawText(shown, box.centerX(), box.centerY() - (fm.ascent + fm.descent) / 2f, labelPaint)
    }
}
