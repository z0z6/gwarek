package org.gwarek.aac.data

import java.io.File
import java.util.Properties

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

/** Ustawienia w prostym pliku .properties – tak samo na Androidzie i na komputerze. */
class SettingsStore(private val file: File) {
    fun load(): Settings {
        val p = Properties()
        if (file.isFile) runCatching { file.inputStream().use { p.load(it) } }
        val d = Settings()
        fun int(k: String, def: Int) = p.getProperty(k)?.toIntOrNull() ?: def
        fun float(k: String, def: Float) = p.getProperty(k)?.toFloatOrNull() ?: def
        fun bool(k: String, def: Boolean) = p.getProperty(k)?.toBooleanStrictOrNull() ?: def
        return Settings(
            columns = int("columns", d.columns),
            speakOnTap = bool("speakOnTap", d.speakOnTap),
            speechRate = float("speechRate", d.speechRate),
            smartSpeech = bool("smartSpeech", d.smartSpeech),
            showPredictions = bool("showPredictions", d.showPredictions),
            showLabels = bool("showLabels", d.showLabels),
            labelScale = float("labelScale", d.labelScale),
            highContrast = bool("highContrast", d.highContrast),
            tapGuardMs = int("tapGuardMs", d.tapGuardMs),
            editPin = p.getProperty("editPin") ?: d.editPin,
        )
    }

    fun save(s: Settings) {
        val p = Properties()
        p.setProperty("columns", s.columns.toString())
        p.setProperty("speakOnTap", s.speakOnTap.toString())
        p.setProperty("speechRate", s.speechRate.toString())
        p.setProperty("smartSpeech", s.smartSpeech.toString())
        p.setProperty("showPredictions", s.showPredictions.toString())
        p.setProperty("showLabels", s.showLabels.toString())
        p.setProperty("labelScale", s.labelScale.toString())
        p.setProperty("highContrast", s.highContrast.toString())
        p.setProperty("tapGuardMs", s.tapGuardMs.toString())
        p.setProperty("editPin", s.editPin)
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { p.store(it, "Gwarek") }
        }
    }
}
