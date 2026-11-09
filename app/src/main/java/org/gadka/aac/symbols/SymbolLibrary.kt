package org.gadka.aac.symbols

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.gadka.aac.data.Pictogram
import org.gadka.aac.model.WordType
import java.text.Normalizer
import java.util.Locale

/** Prefiks w Pictogram.imagePath oznaczający plik z assets (biblioteka symboli). */
const val ASSET_PREFIX = "asset:"

data class Symbol(
    val id: String,
    val source: String,
    val file: String,
    val label: String,
    val type: WordType,
    val domain: String,
    val keywords: String,
    val emoji: String,
) {
    val assetPath: String get() = "symbols/$file"
    internal val normLabel: String = normalize(label)
    internal val normAll: String = normalize("$label $domain $keywords")
}

private val polish: Locale = Locale.forLanguageTag("pl-PL")
private val marks = Regex("\\p{Mn}+")

/** Małe litery, bez polskich znaków – żeby "zolw" znalazło "żółw". */
fun normalize(s: String): String {
    val lower = s.lowercase(polish).replace('ł', 'l')
    return marks.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
}

/**
 * Wolne zestawy symboli (Mulberry + OpenMoji) wczytywane z assets/symbols/index.tsv.
 * Indeks generuje tools/fetch_symbols.py podczas budowania.
 */
object SymbolLibrary {
    private val _symbols = MutableStateFlow<List<Symbol>>(emptyList())
    val symbols: StateFlow<List<Symbol>> = _symbols

    @Volatile
    private var byEmoji: Map<String, Symbol> = emptyMap()

    suspend fun load(context: Context) {
        if (_symbols.value.isNotEmpty()) return
        val list = withContext(Dispatchers.IO) { readIndex(context) }
        val emojiMap = HashMap<String, Symbol>()
        for (s in list) {
            if (s.emoji.isNotEmpty()) {
                emojiMap[s.emoji] = s
                emojiMap[s.emoji.replace("\uFE0F", "")] = s
            }
        }
        byEmoji = emojiMap
        _symbols.value = list
    }

    private fun readIndex(context: Context): List<Symbol> = try {
        context.assets.open("symbols/index.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val c = line.split('\t')
                if (c.size < 8) null
                else Symbol(
                    id = c[0], source = c[1], file = c[2], label = c[3],
                    type = WordType.from(c[4]), domain = c[5], keywords = c[6], emoji = c[7],
                )
            }.toList()
        }
    } catch (e: java.io.IOException) {
        emptyList() // biblioteka nie została dołączona do builda
    }

    /** Obrazek OpenMoji dla emoji – dzięki temu emoji wyglądają tak samo na każdym tablecie. */
    fun forEmoji(emoji: String): Symbol? =
        byEmoji[emoji] ?: byEmoji[emoji.replace("\uFE0F", "")]

    /** Najlepszy symbol dla słowa, o ile nazwa naprawdę pasuje. */
    fun bestMatch(all: List<Symbol>, word: String): Symbol? {
        val w = normalize(word.trim())
        if (w.isEmpty()) return null
        return search(all, word, null).firstOrNull { it.normLabel == w || it.normLabel.startsWith("$w ") }
            ?: search(all, word, null).firstOrNull { it.normLabel.startsWith(w) }
    }

    fun search(all: List<Symbol>, query: String, domain: String?): List<Symbol> {
        val pool = if (domain == null) all else all.filter { it.domain == domain }
        val q = normalize(query.trim())
        if (q.isEmpty()) return pool
        return pool.mapNotNull { s ->
            val score = when {
                s.normLabel == q -> 0
                s.normLabel.startsWith(q) -> 1
                s.normLabel.contains(" $q") -> 2
                s.normLabel.contains(q) -> 3
                s.normAll.contains(q) -> 4
                else -> return@mapNotNull null
            }
            Triple(s, score, if (s.source == "mulberry") 0 else 1)
        }
            .sortedWith(compareBy<Triple<Symbol, Int, Int>>({ it.second }, { it.third }, { it.first.label.length }))
            .map { it.first }
    }
}

/**
 * Plik SVG z biblioteki dla piktogramu: wybrany symbol albo obrazek OpenMoji
 * odpowiadający emoji. Null, gdy piktogram ma własny obrazek z pliku lub nic.
 */
fun Pictogram.libraryAssetPath(): String? {
    val path = imagePath
    val e = emoji
    return when {
        path != null && path.startsWith(ASSET_PREFIX) -> path.removePrefix(ASSET_PREFIX)
        path == null && !e.isNullOrBlank() -> SymbolLibrary.forEmoji(e)?.assetPath
        else -> null
    }
}
