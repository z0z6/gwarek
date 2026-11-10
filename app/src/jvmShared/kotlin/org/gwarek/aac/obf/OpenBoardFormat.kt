package org.gwarek.aac.obf

import androidx.compose.ui.graphics.toArgb
import org.gwarek.aac.backup.objects
import org.gwarek.aac.data.AppDatabase
import org.gwarek.aac.data.Category
import org.gwarek.aac.data.Pictogram
import org.gwarek.aac.model.WordType
import org.gwarek.aac.platform.AppAssets
import org.gwarek.aac.platform.saveImageAsPng
import org.gwarek.aac.platform.transaction
import org.gwarek.aac.symbols.ASSET_PREFIX
import org.gwarek.aac.symbols.libraryAssetPath
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.max

/**
 * Open Board Format (https://www.openboardformat.org) – otwarty format wymiany tablic AAC.
 * Eksport: plik .obz (zip z tablicami .obf i obrazkami). Import: .obz albo pojedynczy .obf.
 */
class OpenBoardFormat(private val db: AppDatabase, private val dataDir: File) {
    private val dao = db.boardDao()

    data class ImportResult(val boards: Int, val buttons: Int, val missingImages: Int)

    // ---------------- eksport ----------------

    suspend fun export(out: OutputStream, columns: Int) {
        val categories = dao.categoriesOnce()
        val files = LinkedHashMap<String, ByteArray>()
        val boardPaths = JSONObject()
        val imagePaths = JSONObject()

        for (c in categories) {
            val pictograms = dao.pictogramsOnce(c.id)
            val buttons = JSONArray()
            val images = JSONArray()
            val seenImages = HashSet<String>()
            for (p in pictograms) {
                val b = JSONObject()
                    .put("id", p.id.toString())
                    .put("label", p.label)
                    .put("background_color", rgb(WordType.from(p.wordType).background.toArgb()))
                    .put("border_color", rgb(WordType.from(p.wordType).border.toArgb()))
                p.spokenText?.let { b.put("vocalization", it) }
                val image = exportImage(p, files)
                if (image != null) {
                    b.put("image_id", image.getString("id"))
                    if (seenImages.add(image.getString("id"))) {
                        images.put(image)
                        imagePaths.put(image.getString("id"), image.getString("path"))
                    }
                }
                buttons.put(b)
            }
            val cols = columns.coerceIn(1, 12)
            val rows = max(1, ceil(pictograms.size / cols.toDouble()).toInt())
            val order = JSONArray()
            for (r in 0 until rows) {
                val row = JSONArray()
                for (col in 0 until cols) {
                    val i = r * cols + col
                    row.put(if (i < pictograms.size) pictograms[i].id.toString() else JSONObject.NULL)
                }
                order.put(row)
            }
            val board = JSONObject()
                .put("format", "open-board-0.1")
                .put("id", c.id.toString())
                .put("locale", "pl")
                .put("name", c.name)
                .put("ext_gwarek_emoji", c.emoji)
                .put("buttons", buttons)
                .put("images", images)
                .put("grid", JSONObject().put("rows", rows).put("columns", cols).put("order", order))
            val path = "boards/${c.id}.obf"
            files[path] = board.toString(2).toByteArray(Charsets.UTF_8)
            boardPaths.put(c.id.toString(), path)
        }

        val manifest = JSONObject()
            .put("format", "open-board-0.1")
            .put("root", categories.firstOrNull()?.let { "boards/${it.id}.obf" } ?: JSONObject.NULL)
            .put("paths", JSONObject().put("boards", boardPaths).put("images", imagePaths))

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun exportImage(p: Pictogram, files: MutableMap<String, ByteArray>): JSONObject? {
        val asset = p.libraryAssetPath()
        if (asset != null) {
            val id = asset.substringAfter("symbols/").replace('/', '_').substringBeforeLast('.')
            val path = "images/$id.svg"
            if (path !in files) {
                files[path] = AppAssets.readBytes(asset) ?: return null
            }
            val mulberry = asset.startsWith("symbols/m/")
            return JSONObject()
                .put("id", id)
                .put("path", path)
                .put("content_type", "image/svg+xml")
                .put("ext_gwarek_asset", asset)
                .put(
                    "license", JSONObject()
                        .put("type", "CC BY-SA 4.0")
                        .put("copyright_notice_url", "https://creativecommons.org/licenses/by-sa/4.0/")
                        .put("source_url", if (mulberry) "https://mulberrysymbols.org" else "https://openmoji.org")
                        .put("author_name", if (mulberry) "Mulberry Symbols" else "OpenMoji")
                )
        }
        val path = p.imagePath
        if (path != null && !path.startsWith(ASSET_PREFIX)) {
            val f = File(path)
            if (!f.isFile) return null
            val id = "user_" + f.nameWithoutExtension
            val zipPath = "images/$id.png"
            files[zipPath] = f.readBytes()
            return JSONObject().put("id", id).put("path", zipPath).put("content_type", "image/png")
        }
        return null
    }

    private fun rgb(argb: Int) =
        "rgb(${(argb shr 16) and 0xFF}, ${(argb shr 8) and 0xFF}, ${argb and 0xFF})"

    // ---------------- import ----------------

    suspend fun import(input: InputStream): ImportResult {
        val bytes = input.use { it.readBytes() }
        val files = HashMap<String, ByteArray>()
        val boards = mutableListOf<JSONObject>()
        if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) files[e.name.removePrefix("/")] = zip.readBytes()
                }
            }
            val manifest = files["manifest.json"]?.let { JSONObject(it.toString(Charsets.UTF_8)) }
            val boardFiles = manifest?.optJSONObject("paths")?.optJSONObject("boards")?.let { b ->
                b.keys().asSequence().map { b.getString(it) }.toList()
            } ?: files.keys.filter { it.endsWith(".obf") }.sorted()
            // tablica główna jako pierwsza
            val root = manifest?.optString("root")?.takeIf { it.isNotEmpty() }
            val ordered = if (root != null && root in boardFiles) listOf(root) + (boardFiles - root) else boardFiles
            ordered.forEach { path -> files[path]?.let { boards += JSONObject(it.toString(Charsets.UTF_8)) } }
        } else {
            boards += JSONObject(bytes.toString(Charsets.UTF_8))
        }
        require(boards.isNotEmpty()) { "Nie znaleziono tablic w pliku" }

        var buttonsCount = 0
        var missing = 0
        val imagesDir = File(dataDir, "pictograms").apply { mkdirs() }

        db.transaction {
            var position = dao.nextCategoryPosition()
            for (board in boards) {
                val name = board.optString("name").ifBlank { "Tablica" }
                val emoji = board.optString("ext_gwarek_emoji").ifBlank { "📥" }
                val catId = dao.insertCategory(Category(name = name, emoji = emoji, position = position++))
                val images = board.optJSONArray("images")?.objects()?.associateBy { it.optString("id") } ?: emptyMap()
                val buttons = board.optJSONArray("buttons")?.objects() ?: emptyList()
                val byId = buttons.associateBy { it.optString("id") }

                // kolejność według siatki, potem przyciski spoza siatki
                val ordered = mutableListOf<JSONObject>()
                val used = HashSet<String>()
                board.optJSONObject("grid")?.optJSONArray("order")?.let { rows ->
                    for (r in 0 until rows.length()) {
                        val row = rows.optJSONArray(r) ?: continue
                        for (c in 0 until row.length()) {
                            if (row.isNull(c)) continue
                            val id = row.optString(c)
                            byId[id]?.let { if (used.add(id)) ordered += it }
                        }
                    }
                }
                buttons.forEach { if (used.add(it.optString("id"))) ordered += it }

                val pictograms = ordered.mapIndexedNotNull { i, b ->
                    val label = b.optString("label").ifBlank { b.optString("vocalization") }.trim()
                    if (label.isEmpty()) return@mapIndexedNotNull null
                    val vocal = b.optString("vocalization").trim().takeIf { it.isNotEmpty() && it != label }
                    val image = images[b.optString("image_id")]
                    val imagePath = image?.let { importImage(it, files, imagesDir) }
                    if (image != null && imagePath == null) missing++
                    Pictogram(
                        categoryId = catId,
                        label = label,
                        spokenText = vocal,
                        imagePath = imagePath,
                        wordType = nearestType(b.optString("background_color")).name,
                        position = i,
                    )
                }
                dao.insertPictograms(pictograms)
                buttonsCount += pictograms.size
            }
        }
        return ImportResult(boards.size, buttonsCount, missing)
    }

    /** Zwraca imagePath dla piktogramu albo null. Obrazy z adresów URL pomijamy – aplikacja działa offline. */
    private fun importImage(img: JSONObject, files: Map<String, ByteArray>, dir: File): String? {
        val asset = img.optString("ext_gwarek_asset")
        if (asset.isNotEmpty() && AppAssets.exists(asset)) {
            return ASSET_PREFIX + asset
        }
        val data = img.optString("data")
        val bytes: ByteArray = when {
            data.startsWith("data:") -> runCatching {
                Base64.getMimeDecoder().decode(data.substringAfter("base64,"))
            }.getOrNull()
            img.optString("path").isNotEmpty() -> files[img.optString("path").removePrefix("/")]
            else -> null
        } ?: return null
        val type = img.optString("content_type").ifEmpty { data.substringAfter("data:").substringBefore(';') }
        val isSvg = type.contains("svg") || img.optString("path").endsWith(".svg")
        return saveImageAsPng(bytes, isSvg, dir)
    }

    /** Dopasowuje kolor tła przycisku do najbliższej kategorii klucza Fitzgeralda. */
    private fun nearestType(color: String): WordType {
        val rgb = parseColor(color) ?: return WordType.NOUN
        return WordType.entries.minBy { t ->
            val c = t.background.toArgb()
            val dr = ((c shr 16) and 0xFF) - rgb[0]
            val dg = ((c shr 8) and 0xFF) - rgb[1]
            val db = (c and 0xFF) - rgb[2]
            dr * dr + dg * dg + db * db
        }
    }

    private fun parseColor(s: String): IntArray? {
        val t = s.trim().lowercase()
        if (t.startsWith("#") && t.length >= 7) {
            return runCatching {
                intArrayOf(t.substring(1, 3).toInt(16), t.substring(3, 5).toInt(16), t.substring(5, 7).toInt(16))
            }.getOrNull()
        }
        if (t.startsWith("rgb")) {
            val nums = t.substringAfter('(').substringBefore(')').split(',').map { it.trim() }
            if (nums.size >= 3) {
                return runCatching { intArrayOf(nums[0].toFloat().toInt(), nums[1].toFloat().toInt(), nums[2].toFloat().toInt()) }.getOrNull()
            }
        }
        return null
    }
}
