package org.gwarek.aac.data

import androidx.room.withTransaction
import org.gwarek.aac.model.WordType
import org.gwarek.aac.model.WordType.DESCRIPTOR
import org.gwarek.aac.model.WordType.NEGATION
import org.gwarek.aac.model.WordType.NOUN
import org.gwarek.aac.model.WordType.PERSON
import org.gwarek.aac.model.WordType.QUESTION
import org.gwarek.aac.model.WordType.SOCIAL
import org.gwarek.aac.model.WordType.VERB

/**
 * Startowe słownictwo. Grafiki to na razie emoji (działają offline, bez kwestii licencyjnych).
 * Czasowniki są w 1. osobie ("chcę", "jem"), żeby zdania brzmiały naturalnie
 * zanim w etapie 4 dojdzie agent odmieniający wyrazy.
 */
object SeedData {
    private data class Item(val label: String, val emoji: String, val type: WordType)
    private data class Cat(val name: String, val emoji: String, val items: List<Item>)

    private val categories = listOf(
        Cat("Rozmowa", "💬", listOf(
            Item("tak", "👍", SOCIAL),
            Item("nie", "👎", NEGATION),
            Item("cześć", "👋", SOCIAL),
            Item("proszę", "🙏", SOCIAL),
            Item("dziękuję", "😊", SOCIAL),
            Item("przepraszam", "🙇", SOCIAL),
            Item("pomocy", "🆘", SOCIAL),
            Item("jeszcze", "➕", SOCIAL),
            Item("koniec", "🛑", SOCIAL),
            Item("poczekaj", "✋", SOCIAL),
            Item("nie wiem", "🤷", NEGATION),
        )),
        Cat("Ludzie", "👪", listOf(
            Item("ja", "🙋", PERSON),
            Item("ty", "👉", PERSON),
            Item("mama", "👩", PERSON),
            Item("tata", "👨", PERSON),
            Item("babcia", "👵", PERSON),
            Item("dziadek", "👴", PERSON),
            Item("brat", "👦", PERSON),
            Item("siostra", "👧", PERSON),
            Item("pani", "👩‍🏫", PERSON),
            Item("lekarz", "🧑‍⚕️", PERSON),
            Item("kolega", "🧒", PERSON),
        )),
        Cat("Czynności", "🏃", listOf(
            Item("chcę", "🤲", VERB),
            Item("nie chcę", "🙅", NEGATION),
            Item("lubię", "❤️", VERB),
            Item("idę", "🚶", VERB),
            Item("jem", "🍽️", VERB),
            Item("piję", "🥤", VERB),
            Item("bawię się", "🧸", VERB),
            Item("śpię", "😴", VERB),
            Item("oglądam", "📺", VERB),
            Item("słucham", "🎧", VERB),
            Item("rysuję", "🖍️", VERB),
            Item("czytam", "📖", VERB),
            Item("myję się", "🧼", VERB),
            Item("boli mnie", "🤕", VERB),
            Item("daj", "🤝", VERB),
        )),
        Cat("Jedzenie", "🍎", listOf(
            Item("chleb", "🍞", NOUN),
            Item("kanapka", "🥪", NOUN),
            Item("jabłko", "🍎", NOUN),
            Item("banan", "🍌", NOUN),
            Item("zupa", "🍲", NOUN),
            Item("makaron", "🍝", NOUN),
            Item("pizza", "🍕", NOUN),
            Item("jajko", "🥚", NOUN),
            Item("ser", "🧀", NOUN),
            Item("ciastko", "🍪", NOUN),
            Item("lody", "🍦", NOUN),
            Item("czekolada", "🍫", NOUN),
        )),
        Cat("Picie", "🥛", listOf(
            Item("woda", "💧", NOUN),
            Item("sok", "🧃", NOUN),
            Item("mleko", "🥛", NOUN),
            Item("herbata", "🍵", NOUN),
            Item("kakao", "☕", NOUN),
        )),
        Cat("Uczucia", "😀", listOf(
            Item("wesoły", "😀", DESCRIPTOR),
            Item("smutny", "😢", DESCRIPTOR),
            Item("zły", "😠", DESCRIPTOR),
            Item("zmęczony", "😫", DESCRIPTOR),
            Item("boję się", "😨", DESCRIPTOR),
            Item("chory", "🤒", DESCRIPTOR),
            Item("głodny", "😋", DESCRIPTOR),
            Item("spragniony", "👅", DESCRIPTOR),
            Item("zimno mi", "🥶", DESCRIPTOR),
            Item("gorąco mi", "🥵", DESCRIPTOR),
        )),
        Cat("Miejsca", "🏠", listOf(
            Item("dom", "🏠", NOUN),
            Item("szkoła", "🏫", NOUN),
            Item("przedszkole", "🎒", NOUN),
            Item("sklep", "🛒", NOUN),
            Item("park", "🌳", NOUN),
            Item("plac zabaw", "🎠", NOUN),
            Item("łazienka", "🛁", NOUN),
            Item("toaleta", "🚽", NOUN),
            Item("przychodnia", "🏥", NOUN),
            Item("samochód", "🚗", NOUN),
            Item("autobus", "🚌", NOUN),
        )),
        Cat("Rzeczy", "🧸", listOf(
            Item("zabawka", "🧸", NOUN),
            Item("piłka", "⚽", NOUN),
            Item("książka", "📚", NOUN),
            Item("tablet", "📱", NOUN),
            Item("telewizor", "📺", NOUN),
            Item("kredki", "🖍️", NOUN),
            Item("koszulka", "👕", NOUN),
            Item("buty", "👟", NOUN),
            Item("kurtka", "🧥", NOUN),
            Item("łóżko", "🛏️", NOUN),
        )),
        Cat("Pytania", "❓", listOf(
            Item("co?", "❓", QUESTION),
            Item("gdzie?", "📍", QUESTION),
            Item("kiedy?", "🕒", QUESTION),
            Item("kto?", "👤", QUESTION),
            Item("dlaczego?", "🤔", QUESTION),
            Item("ile?", "🔢", QUESTION),
            Item("jak?", "💭", QUESTION),
        )),
        Cat("Opisy", "📏", listOf(
            Item("duży", "🐘", DESCRIPTOR),
            Item("mały", "🐭", DESCRIPTOR),
            Item("dobry", "🌟", DESCRIPTOR),
            Item("niedobry", "🤢", DESCRIPTOR),
            Item("teraz", "⏰", DESCRIPTOR),
            Item("potem", "⏭️", DESCRIPTOR),
            Item("szybko", "⚡", DESCRIPTOR),
            Item("wolno", "🐢", DESCRIPTOR),
            Item("ciepły", "🔥", DESCRIPTOR),
            Item("zimny", "🧊", DESCRIPTOR),
        )),
    )

    suspend fun ensureSeeded(db: AppDatabase) {
        val dao = db.boardDao()
        if (dao.categoryCount() > 0) return
        db.withTransaction {
            categories.forEachIndexed { ci, cat ->
                val id = dao.insertCategory(Category(name = cat.name, emoji = cat.emoji, position = ci))
                dao.insertPictograms(cat.items.mapIndexed { pi, item ->
                    Pictogram(
                        categoryId = id,
                        label = item.label,
                        emoji = item.emoji,
                        wordType = item.type.name,
                        position = pi,
                    )
                })
            }
        }
    }
}
