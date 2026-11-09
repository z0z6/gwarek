package org.gadka.aac.model

import androidx.compose.ui.graphics.Color

/**
 * Zmodyfikowany klucz Fitzgeralda – kolor kafelka zależy od części mowy.
 * Przechowywane w bazie jako nazwa (String), żeby nie potrzebować konwerterów.
 */
enum class WordType(val label: String, val background: Color, val border: Color) {
    PERSON("Osoby", Color(0xFFFFF4B3), Color(0xFFE0B800)),
    VERB("Czynności", Color(0xFFD4F5C9), Color(0xFF43A047)),
    DESCRIPTOR("Opisy", Color(0xFFCFE6FF), Color(0xFF2E7BD8)),
    NOUN("Rzeczy", Color(0xFFFFE0C2), Color(0xFFF08A24)),
    SOCIAL("Zwroty", Color(0xFFFFD6EA), Color(0xFFE0569B)),
    QUESTION("Pytania", Color(0xFFE6D9FF), Color(0xFF8456D8)),
    NEGATION("Przeczenia", Color(0xFFFFD0D0), Color(0xFFD83A3A)),
    OTHER("Inne", Color(0xFFF2F2F2), Color(0xFF9E9E9E));

    companion object {
        fun from(name: String?): WordType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
