package org.gwarek.aac.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.gwarek.aac.data.AppDatabase
import org.gwarek.aac.data.Category
import org.gwarek.aac.data.Pictogram
import org.gwarek.aac.data.Settings
import org.gwarek.aac.data.Transition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pełna kopia zapasowa: tablice, piktogramy, własne obrazki, historia podpowiedzi i ustawienia
 * w jednym pliku .zip. Model AI nie jest kopiowany (jest duży i łatwo go wczytać ponownie).
 */
class BackupManager(private val context: Context, private val db: AppDatabase) {
    private val dao = db.boardDao()
    private val userImagesDir: File get() = File(context.filesDir, "pictograms")

    suspend fun export(uri: Uri, settings: Settings) {
        val categories = dao.categoriesOnce()
        val pictograms = dao.allPictogramsOnce()
        val transitions = dao.allTransitions()
        val images = mutableListOf<File>()

        val json = JSONObject()
        json.put("format", "gwarek-backup")
        json.put("version", 1)
        json.put("settings", settingsToJson(settings))
        json.put("categories", JSONArray().apply {
            categories.forEach { c ->
                put(JSONObject().put("id", c.id).put("name", c.name).put("emoji", c.emoji).put("position", c.position))
            }
        })
        json.put("pictograms", JSONArray().apply {
            pictograms.forEach { p ->
                val o = JSONObject()
                    .put("id", p.id).put("categoryId", p.categoryId).put("label", p.label)
                    .put("wordType", p.wordType).put("position", p.position)
                p.spokenText?.let { o.put("spokenText", it) }
                p.emoji?.let { o.put("emoji", it) }
                val path = p.imagePath
                if (path != null) {
                    val f = File(path)
                    if (f.isAbsolute) {
                        if (f.isFile) {
                            images += f
                            o.put("imagePath", "file:" + f.name)
                        }
                    } else {
                        o.put("imagePath", path) // np. asset:symbols/m/123.svg
                    }
                }
                put(o)
            }
        })
        json.put("transitions", JSONArray().apply {
            transitions.forEach { t -> put(JSONObject().put("from", t.fromId).put("to", t.toId).put("count", t.count)) }
        })

        val out = context.contentResolver.openOutputStream(uri) ?: error("Nie można zapisać pliku")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            images.distinctBy { it.name }.forEach { f ->
                zip.putNextEntry(ZipEntry("images/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Zastępuje wszystkie dane zawartością kopii. Zwraca ustawienia z kopii. */
    suspend fun import(uri: Uri, current: Settings): Settings {
        var json: JSONObject? = null
        val imageMap = HashMap<String, String>() // nazwa w kopii -> nowa ścieżka
        userImagesDir.mkdirs()
        val input = context.contentResolver.openInputStream(uri) ?: error("Nie można otworzyć pliku")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "backup.json" ->
                        json = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    entry.name.startsWith("images/") && !entry.isDirectory -> {
                        val name = entry.name.substringAfterLast('/')
                        val target = File(userImagesDir, "${UUID.randomUUID()}.png")
                        target.outputStream().use { zip.copyTo(it) }
                        imageMap[name] = target.absolutePath
                    }
                }
            }
        }
        val root = json ?: error("To nie jest kopia zapasowa Gwarka")
        require(root.optString("format") == "gwarek-backup") { "To nie jest kopia zapasowa Gwarka" }

        val categories = root.getJSONArray("categories").objects().map { o ->
            Category(o.getLong("id"), o.getString("name"), o.getString("emoji"), o.optInt("position"))
        }
        val pictograms = root.getJSONArray("pictograms").objects().map { o ->
            val raw = o.optString("imagePath").ifEmpty { null }
            val path = when {
                raw == null -> null
                raw.startsWith("file:") -> imageMap[raw.removePrefix("file:")]
                else -> raw
            }
            Pictogram(
                id = o.getLong("id"),
                categoryId = o.getLong("categoryId"),
                label = o.getString("label"),
                spokenText = o.optString("spokenText").ifEmpty { null },
                emoji = o.optString("emoji").ifEmpty { null },
                imagePath = path,
                wordType = o.optString("wordType", "NOUN"),
                position = o.optInt("position"),
            )
        }
        val transitions = root.optJSONArray("transitions")?.objects()?.map { o ->
            Transition(o.getLong("from"), o.getLong("to"), o.getInt("count"))
        } ?: emptyList()

        db.withTransaction {
            dao.deleteAllTransitions()
            dao.deleteAllPictograms()
            dao.deleteAllCategories()
            dao.insertCategories(categories)
            dao.insertPictograms(pictograms)
            dao.insertTransitions(transitions)
        }
        removeUnusedImages(pictograms.mapNotNull { it.imagePath }.toSet())
        return root.optJSONObject("settings")?.let { settingsFromJson(it, current) } ?: current
    }

    private fun removeUnusedImages(used: Set<String>) {
        userImagesDir.listFiles()?.forEach { f -> if (f.absolutePath !in used) f.delete() }
    }

    private fun settingsToJson(s: Settings) = JSONObject()
        .put("columns", s.columns)
        .put("speakOnTap", s.speakOnTap)
        .put("speechRate", s.speechRate.toDouble())
        .put("smartSpeech", s.smartSpeech)
        .put("showPredictions", s.showPredictions)
        .put("showLabels", s.showLabels)
        .put("labelScale", s.labelScale.toDouble())
        .put("highContrast", s.highContrast)
        .put("tapGuardMs", s.tapGuardMs)

    /** PIN nie jest przenoszony – żeby kopia nie zablokowała edycji na innym urządzeniu. */
    private fun settingsFromJson(o: JSONObject, current: Settings) = current.copy(
        columns = o.optInt("columns", current.columns),
        speakOnTap = o.optBoolean("speakOnTap", current.speakOnTap),
        speechRate = o.optDouble("speechRate", current.speechRate.toDouble()).toFloat(),
        smartSpeech = o.optBoolean("smartSpeech", current.smartSpeech),
        showPredictions = o.optBoolean("showPredictions", current.showPredictions),
        showLabels = o.optBoolean("showLabels", current.showLabels),
        labelScale = o.optDouble("labelScale", current.labelScale.toDouble()).toFloat(),
        highContrast = o.optBoolean("highContrast", current.highContrast),
        tapGuardMs = o.optInt("tapGuardMs", current.tapGuardMs),
    )
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
