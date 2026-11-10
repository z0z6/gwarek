package org.gwarek.aac.ai

import android.content.Context

/**
 * Agent gramatyczny: zamienia ciąg piktogramów w poprawne polskie zdanie
 * („mama iść sklep” → „Mama idzie do sklepu.”).
 */
object GrammarAgent {
    private const val SYSTEM = """Jesteś asystentem komunikacji wspomagającej (AAC). Dostajesz słowa wybrane z piktogramów przez osobę, która nie mówi. Ułóż z nich jedno krótkie, poprawne gramatycznie zdanie po polsku. Odmień wyrazy, dodaj tylko konieczne przyimki i spójniki. Jeśli brak podmiotu, użyj pierwszej osoby. Nie dodawaj nowych treści. Odpowiedz wyłącznie tym zdaniem.
Przykłady:
Słowa: ja chcieć pić woda
Zdanie: Chcę pić wodę.
Słowa: mama iść sklep
Zdanie: Mama idzie do sklepu.
Słowa: nie lubić zupa
Zdanie: Nie lubię zupy.
Słowa: gdzie? tata
Zdanie: Gdzie jest tata?
Słowa: chcę bawię się piłka kolega
Zdanie: Chcę bawić się piłką z kolegą."""

    suspend fun correct(context: Context, words: List<String>): String? {
        val input = words.joinToString(" ")
        val out = LocalLlm.generate(context, SYSTEM, "Słowa: $input\nZdanie:", maxTokens = 60) ?: return null
        var line = out.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        line = line.removePrefix("Zdanie:").trim()
            .trim('"', '„', '”', '*', '\'')
            .trim()
        if (line.isEmpty() || line.length > input.length * 3 + 40) return null
        if (line.last() !in ".?!") line += "."
        return line.replaceFirstChar { it.uppercase() }
    }
}

/** Agent tablic: proponuje słownictwo do tablicy tematycznej („wizyta u dentysty”). */
object BoardAgent {
    private const val SYSTEM =
        "Jesteś logopedą i specjalistą komunikacji wspomagającej (AAC). Tworzysz tablice komunikacyjne z piktogramami."

    private val listMarker = Regex("^[\\s\\-*•–\\d.)]+")

    suspend fun words(context: Context, topic: String): List<String>? {
        val user = "Podaj 16 najważniejszych słów dla osoby korzystającej z komunikacji wspomagającej " +
            "w sytuacji: „$topic”. Uwzględnij osoby, czynności, rzeczy, uczucia i krótkie zwroty. " +
            "Każde słowo w formie podstawowej, po jednym w linii, bez numeracji i bez opisów."
        val out = LocalLlm.generate(context, SYSTEM, user, maxTokens = 200, temperature = 0.3f) ?: return null
        return out.lines()
            .map { it.replace(listMarker, "").trim().trimEnd('.', ',', ';', ':').trim().lowercase() }
            .filter { it.isNotEmpty() && it.length <= 30 && it.split(' ').size <= 3 }
            .distinct()
            .take(20)
            .ifEmpty { null }
    }
}
