package org.gadka.aac.data

import android.content.Context

data class Settings(
    val columns: Int = 6,
    val speakOnTap: Boolean = true,
    val speechRate: Float = 1.0f,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        columns = sp.getInt("columns", 6),
        speakOnTap = sp.getBoolean("speakOnTap", true),
        speechRate = sp.getFloat("speechRate", 1.0f),
    )

    fun save(s: Settings) {
        sp.edit()
            .putInt("columns", s.columns)
            .putBoolean("speakOnTap", s.speakOnTap)
            .putFloat("speechRate", s.speechRate)
            .apply()
    }
}
