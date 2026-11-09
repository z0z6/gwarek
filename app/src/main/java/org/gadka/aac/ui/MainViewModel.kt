package org.gadka.aac.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withTimeoutOrNull
import android.net.Uri
import org.gadka.aac.ai.BoardAgent
import org.gadka.aac.ai.GrammarAgent
import org.gadka.aac.ai.LlmStatus
import org.gadka.aac.ai.LocalLlm
import org.gadka.aac.model.WordType
import org.gadka.aac.symbols.ASSET_PREFIX
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import org.gadka.aac.print.PdfBuilder
import org.gadka.aac.print.PrintOptions
import org.gadka.aac.print.PrintSource
import org.gadka.aac.data.AppDatabase
import org.gadka.aac.data.Category
import org.gadka.aac.data.Pictogram
import org.gadka.aac.data.Prefs
import org.gadka.aac.data.SeedData
import org.gadka.aac.data.Settings
import org.gadka.aac.data.spokenOrLabel
import org.gadka.aac.symbols.SymbolLibrary
import org.gadka.aac.tts.Speaker
import org.gadka.aac.tts.TtsStatus

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.boardDao()
    private val prefs = Prefs(app)
    private val speaker = Speaker(app)

    val ttsStatus: StateFlow<TtsStatus> = speaker.status

    val categories: StateFlow<List<Category>> =
        dao.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId

    @OptIn(ExperimentalCoroutinesApi::class)
    val pictograms: StateFlow<List<Pictogram>> = _selectedCategoryId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.pictograms(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _sentence = MutableStateFlow<List<Pictogram>>(emptyList())
    val sentence: StateFlow<List<Pictogram>> = _sentence

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode

    private val _settings = MutableStateFlow(prefs.load())
    val settings: StateFlow<Settings> = _settings

    // --- Asystent AI ---

    val llmStatus: StateFlow<LlmStatus> = LocalLlm.status

    /** Podpowiedzi kolejnego piktogramu na podstawie dotychczasowych wyborów (bez AI, natychmiastowe). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val predictions: StateFlow<List<Pictogram>> = _sentence
        .map { it.lastOrNull()?.id ?: 0L }
        .distinctUntilChanged()
        .flatMapLatest { dao.predictions(it, 8) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Poprawione zdanie: klucz to identyfikatory piktogramów, dla których je policzono. */
    private val _corrected = MutableStateFlow<Pair<List<Long>, String>?>(null)
    val correctedText: StateFlow<String?> = combine(_sentence, _corrected) { s, c ->
        c?.takeIf { it.first == s.map { p -> p.id } }?.second
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _assistantBusy = MutableStateFlow(false)
    val assistantBusy: StateFlow<Boolean> = _assistantBusy

    private val _modelImportProgress = MutableStateFlow<Float?>(null)
    val modelImportProgress: StateFlow<Float?> = _modelImportProgress

    private val _aiMessage = MutableStateFlow<String?>(null)
    val aiMessage: StateFlow<String?> = _aiMessage

    init {
        speaker.rate = _settings.value.speechRate
        viewModelScope.launch { SeedData.ensureSeeded(db) }
        viewModelScope.launch { SymbolLibrary.load(app) }
        viewModelScope.launch { if (LocalLlm.hasModel(app)) LocalLlm.ensureLoaded(app) }
        // Zdanie poprawiamy w tle, gdy użytkownik przestanie dodawać piktogramy –
        // zwykle jest gotowe, zanim dotknie „Mów”.
        viewModelScope.launch {
            _sentence.debounce(400).collectLatest { s ->
                if (!_settings.value.smartSpeech || s.size < 2 || !LocalLlm.isReady) return@collectLatest
                val key = s.map { it.id }
                if (_corrected.value?.first == key) return@collectLatest
                _assistantBusy.value = true
                try {
                    GrammarAgent.correct(app, s.map { it.spokenOrLabel() })?.let { _corrected.value = key to it }
                } finally {
                    _assistantBusy.value = false
                }
            }
        }
        // Zawsze miej zaznaczoną jakąś istniejącą kategorię.
        viewModelScope.launch {
            categories.collect { list ->
                val current = _selectedCategoryId.value
                if (current == null || list.none { it.id == current }) {
                    _selectedCategoryId.value = list.firstOrNull()?.id
                }
            }
        }
    }

    // --- Komunikacja ---

    fun selectCategory(id: Long) {
        _selectedCategoryId.value = id
    }

    fun onPictogramTap(p: Pictogram) {
        val fromId = _sentence.value.lastOrNull()?.id ?: 0L
        viewModelScope.launch {
            dao.ensureTransition(fromId, p.id)
            dao.bumpTransition(fromId, p.id)
        }
        _sentence.update { it + p }
        if (_settings.value.speakOnTap) speaker.speak(p.spokenOrLabel())
    }

    fun speakSentence() {
        val s = _sentence.value
        if (s.isEmpty()) return
        val raw = s.joinToString(" ") { it.spokenOrLabel() }
        if (!_settings.value.smartSpeech || s.size < 2 || !LocalLlm.isReady) {
            speaker.speak(raw)
            return
        }
        val key = s.map { it.id }
        viewModelScope.launch {
            val text = _corrected.value?.takeIf { it.first == key }?.second
                ?: withTimeoutOrNull(6000) { _corrected.first { it?.first == key } }?.second
            speaker.speak(text ?: raw)
        }
    }

    fun speakText(text: String) = speaker.speak(text)

    fun importModel(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _aiMessage.value = null
            _modelImportProgress.value = 0f
            val error = LocalLlm.importModel(app, uri) { _modelImportProgress.value = it }
            _modelImportProgress.value = null
            if (error != null) {
                _aiMessage.value = error
                return@launch
            }
            _aiMessage.value = if (LocalLlm.ensureLoaded(app)) "Model gotowy do pracy."
            else "Nie udało się uruchomić modelu. Sprawdź, czy to model GGUF do czatu."
        }
    }

    fun removeModel() {
        viewModelScope.launch {
            LocalLlm.removeModel(getApplication())
            _corrected.value = null
            _aiMessage.value = "Model usunięty."
        }
    }

    fun modelSizeBytes(): Long = LocalLlm.modelFile(getApplication()).length()

    /** Tworzy kategorię z piktogramami zaproponowanymi przez model. Zwraca liczbę piktogramów. */
    suspend fun generateBoard(topic: String, emoji: String): Int? {
        val app = getApplication<Application>()
        val words = BoardAgent.words(app, topic) ?: return null
        SymbolLibrary.load(app)
        val all = SymbolLibrary.symbols.value
        val catId = dao.insertCategory(
            Category(name = topic.trim(), emoji = emoji, position = dao.nextCategoryPosition())
        )
        val pictograms = words.mapIndexed { i, w ->
            val s = SymbolLibrary.bestMatch(all, w)
            Pictogram(
                categoryId = catId,
                label = w,
                imagePath = s?.let { ASSET_PREFIX + it.assetPath },
                wordType = (s?.type ?: WordType.NOUN).name,
                position = i,
            )
        }
        dao.insertPictograms(pictograms)
        _selectedCategoryId.value = catId
        return pictograms.size
    }

    fun backspace() = _sentence.update { it.dropLast(1) }

    fun clearSentence() {
        _sentence.value = emptyList()
    }

    fun recheckVoice() = speaker.recheck()

    // --- Edycja ---

    fun setEditMode(on: Boolean) {
        _editMode.value = on
    }

    fun updateSettings(s: Settings) {
        _settings.value = s
        prefs.save(s)
        speaker.rate = s.speechRate
    }

    fun savePictogram(p: Pictogram) {
        viewModelScope.launch {
            if (p.id == 0L) {
                dao.insertPictogram(p.copy(position = dao.nextPictogramPosition(p.categoryId)))
            } else {
                dao.updatePictogram(p)
            }
        }
    }

    fun deletePictogram(p: Pictogram) {
        viewModelScope.launch { dao.deletePictogram(p) }
        _sentence.update { list -> list.filterNot { it.id == p.id } }
    }

    fun saveCategory(c: Category) {
        viewModelScope.launch {
            if (c.id == 0L) {
                val id = dao.insertCategory(c.copy(position = dao.nextCategoryPosition()))
                _selectedCategoryId.value = id
            } else {
                dao.updateCategory(c)
            }
        }
    }

    fun deleteCategory(c: Category) {
        viewModelScope.launch { dao.deleteCategory(c) }
    }

    // --- Druk ---

    /** Składa PDF z kartami; null, gdy nie ma czego drukować. */
    suspend fun buildPrintPdf(opt: PrintOptions): File? = withContext(Dispatchers.IO) {
        val sections: List<Pair<String?, List<Pictogram>>> = when (opt.source) {
            PrintSource.CATEGORY -> {
                val c = categories.value.firstOrNull { it.id == _selectedCategoryId.value }
                    ?: return@withContext null
                listOf(c.name to dao.pictogramsOnce(c.id))
            }
            PrintSource.ALL -> categories.value.map { it.name to dao.pictogramsOnce(it.id) }
            PrintSource.SENTENCE -> listOf(null to _sentence.value)
        }
        if (sections.all { it.second.isEmpty() }) return@withContext null
        val app = getApplication<Application>()
        SymbolLibrary.load(app)
        val dir = File(app.cacheDir, "print").apply { mkdirs() }
        val out = File(dir, "gadka.pdf")
        PdfBuilder(app).build(sections, opt, out)
        out
    }

    override fun onCleared() {
        speaker.shutdown()
    }
}
