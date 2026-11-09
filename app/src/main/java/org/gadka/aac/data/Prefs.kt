package org.gadka.aac.data

import android.content.Context

data class Settings(
    val columns: Int = 6,
    val speakOnTap: Boolean = true,
    val speechRate: Float = 1.0f,
    /** Poprawianie zdania lokalnym modelem przed odczytaniem. */
    val smartSpeech: Boolean = true,
    val showPredictions: Boolean = true,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        columns = sp.getInt("columns", 6),
        speakOnTap = sp.getBoolean("speakOnTap", true),
        speechRate = sp.getFloat("speechRate", 1.0f),
        smartSpeech = sp.getBoolean("smartSpeech", true),
        showPredictions = sp.getBoolean("showPredictions", true),
    )

    fun save(s: Settings) {
        sp.edit()
            .putInt("columns", s.columns)
            .putBoolean("speakOnTap", s.speakOnTap)
            .putFloat("speechRate", s.speechRate)
            .putBoolean("smartSpeech", s.smartSpeech)
            .putBoolean("showPredictions", s.showPredictions)
            .apply()
    }
}
