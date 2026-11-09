package org.gadka.aac.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.gadka.aac.model.WordType

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val position: Int = 0,
)

@Entity(
    tableName = "pictograms",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("categoryId")],
)
data class Pictogram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    /** Podpis na kafelku. */
    val label: String,
    /** Co ma zostać wypowiedziane (gdy inne niż podpis). */
    val spokenText: String? = null,
    /** Tymczasowa grafika: emoji. */
    val emoji: String? = null,
    /** Ścieżka do pliku graficznego (baza piktogramów, zdjęcie, kreator) – etapy 2–3. */
    val imagePath: String? = null,
    val wordType: String = WordType.NOUN.name,
    val position: Int = 0,
)

/** Ile razy po piktogramie fromId wybrano toId (0 = początek zdania). Podstawa podpowiedzi. */
@Entity(tableName = "transitions", primaryKeys = ["fromId", "toId"])
data class Transition(
    val fromId: Long,
    val toId: Long,
    val count: Int,
)

fun Pictogram.spokenOrLabel(): String = spokenText?.takeIf { it.isNotBlank() } ?: label
