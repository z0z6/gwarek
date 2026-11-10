package org.gwarek.aac.data

import android.content.Context

data class Settings(
    val columns: Int = 6,
    val speakOnTap: Boolean = true,
    val speechRate: Float = 1.0f,
    /** Poprawianie zdania lokalnym modelem przed odczytaniem. */
    val smartSpeech: Boolean = true,
    val showPredictions: Boolean = true,
    // Dostępność
    val showLabels: Boolean = true,
    val labelScale: Float = 1.0f,
    val highContrast: Boolean = false,
    /** Ignorowanie kolejnych dotknięć przez tyle ms (drżenie rąk, przypadkowe dotknięcia). */
    val tapGuardMs: Int = 0,
    // Blokada
    /** PIN do trybu edycji; pusty = wystarczy przytrzymać kłódkę. */
    val editPin: String = "",
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        columns = sp.getInt("columns", 6),
        speakOnTap = sp.getBoolean("speakOnTap", true),
        speechRate = sp.getFloat("speechRate", 1.0f),
        smartSpeech = sp.getBoolean("smartSpeech", true),
        showPredictions = sp.getBoolean("showPredictions", true),
        showLabels = sp.getBoolean("showLabels", true),
        labelScale = sp.getFloat("labelScale", 1.0f),
        highContrast = sp.getBoolean("highContrast", false),
        tapGuardMs = sp.getInt("tapGuardMs", 0),
        editPin = sp.getString("editPin", "") ?: "",
    )

    fun save(s: Settings) {
        sp.edit()
            .putInt("columns", s.columns)
            .putBoolean("speakOnTap", s.speakOnTap)
            .putFloat("speechRate", s.speechRate)
            .putBoolean("smartSpeech", s.smartSpeech)
            .putBoolean("showPredictions", s.showPredictions)
            .putBoolean("showLabels", s.showLabels)
            .putFloat("labelScale", s.labelScale)
            .putBoolean("highContrast", s.highContrast)
            .putInt("tapGuardMs", s.tapGuardMs)
            .putString("editPin", s.editPin)
            .apply()
    }
}
