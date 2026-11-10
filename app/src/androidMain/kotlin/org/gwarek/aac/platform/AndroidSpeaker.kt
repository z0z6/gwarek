package org.gwarek.aac.platform

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.gwarek.aac.tts.SpeechEngine
import org.gwarek.aac.tts.TtsStatus
import java.util.Locale

/** Cienka nakładka na systemowy TextToSpeech – działa offline, jeśli głos polski jest pobrany. */
class AndroidSpeaker(context: Context) : SpeechEngine {
    private val polish: Locale = Locale.forLanguageTag("pl-PL")
    private val _status = MutableStateFlow(TtsStatus.INITIALIZING)
    override val status: StateFlow<TtsStatus> = _status

    override var rate: Float = 1.0f

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { code -> onInit(code) }

    private fun onInit(code: Int) {
        if (code != TextToSpeech.SUCCESS) {
            _status.value = TtsStatus.ERROR
            return
        }
        applyLanguage()
    }

    private fun applyLanguage() {
        val result = tts.setLanguage(polish)
        _status.value = if (
            result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
        ) TtsStatus.MISSING_LANGUAGE else TtsStatus.READY
    }

    /** Wywołaj po doinstalowaniu głosu. */
    override fun recheck() {
        if (_status.value != TtsStatus.ERROR && _status.value != TtsStatus.INITIALIZING) applyLanguage()
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        if (_status.value == TtsStatus.INITIALIZING || _status.value == TtsStatus.ERROR) return
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gwarek-${SystemClock.uptimeMillis()}")
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
