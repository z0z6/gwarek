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
import kotlinx.coroutines.launch
import org.gadka.aac.data.AppDatabase
import org.gadka.aac.data.Category
import org.gadka.aac.data.Pictogram
import org.gadka.aac.data.Prefs
import org.gadka.aac.data.SeedData
import org.gadka.aac.data.Settings
import org.gadka.aac.data.spokenOrLabel
import org.gadka.aac.tts.Speaker
import org.gadka.aac.tts.TtsStatus

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

    init {
        speaker.rate = _settings.value.speechRate
        viewModelScope.launch { SeedData.ensureSeeded(db) }
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
        _sentence.update { it + p }
        if (_settings.value.speakOnTap) speaker.speak(p.spokenOrLabel())
    }

    fun speakSentence() {
        speaker.speak(_sentence.value.joinToString(" ") { it.spokenOrLabel() })
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

    override fun onCleared() {
        speaker.shutdown()
    }
}
