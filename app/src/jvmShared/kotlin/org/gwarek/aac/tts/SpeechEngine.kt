package org.gwarek.aac.tts

import kotlinx.coroutines.flow.StateFlow

enum class TtsStatus { INITIALIZING, READY, MISSING_LANGUAGE, ERROR }

/** Synteza mowy offline: Android TextToSpeech albo Windows SAPI (część platformowa). */
interface SpeechEngine {
    val status: StateFlow<TtsStatus>
    var rate: Float
    fun speak(text: String)
    /** Sprawdza ponownie dostępność polskiego głosu (np. po jego doinstalowaniu). */
    fun recheck()
    fun shutdown()
}
