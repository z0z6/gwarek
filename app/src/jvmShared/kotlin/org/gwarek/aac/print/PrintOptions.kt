package org.gwarek.aac.print

enum class PrintSource { CATEGORY, ALL, SENTENCE }

enum class CardSize(val label: String, val cm: Float) {
    SMALL("4 cm", 4f),
    MEDIUM("6 cm", 6f),
    LARGE("8 cm", 8f),
}

enum class LabelPosition(val label: String) {
    BELOW("Pod obrazkiem"),
    ABOVE("Nad obrazkiem"),
    NONE("Bez podpisu"),
}

data class PrintOptions(
    val source: PrintSource = PrintSource.CATEGORY,
    val size: CardSize = CardSize.MEDIUM,
    val label: LabelPosition = LabelPosition.BELOW,
    val colored: Boolean = true,
    val cutLines: Boolean = true,
)
