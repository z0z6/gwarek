package org.gwarek.aac.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    @Query("SELECT * FROM categories ORDER BY position, id")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM pictograms WHERE categoryId = :categoryId ORDER BY position, id")
    fun pictograms(categoryId: Long): Flow<List<Pictogram>>

    @Query("SELECT * FROM pictograms WHERE categoryId = :categoryId ORDER BY position, id")
    suspend fun pictogramsOnce(categoryId: Long): List<Pictogram>

    @Query("INSERT OR IGNORE INTO transitions(fromId, toId, count) VALUES(:fromId, :toId, 0)")
    suspend fun ensureTransition(fromId: Long, toId: Long)

    @Query("UPDATE transitions SET count = count + 1 WHERE fromId = :fromId AND toId = :toId")
    suspend fun bumpTransition(fromId: Long, toId: Long)

    @Query(
        "SELECT p.* FROM transitions t JOIN pictograms p ON p.id = t.toId " +
            "WHERE t.fromId = :fromId ORDER BY t.count DESC, p.position LIMIT :limit"
    )
    fun predictions(fromId: Long, limit: Int): Flow<List<Pictogram>>

    // --- Kopie zapasowe ---

    @Query("SELECT * FROM categories ORDER BY position, id")
    suspend fun categoriesOnce(): List<Category>

    @Query("SELECT * FROM pictograms ORDER BY categoryId, position, id")
    suspend fun allPictogramsOnce(): List<Pictogram>

    @Query("SELECT * FROM transitions")
    suspend fun allTransitions(): List<Transition>

    @Query("DELETE FROM transitions")
    suspend fun deleteAllTransitions()

    @Query("DELETE FROM pictograms")
    suspend fun deleteAllPictograms()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Insert
    suspend fun insertCategories(categories: List<Category>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransitions(transitions: List<Transition>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM categories")
    suspend fun nextCategoryPosition(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM pictograms WHERE categoryId = :categoryId")
    suspend fun nextPictogramPosition(categoryId: Long): Int

    @Insert
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Insert
    suspend fun insertPictogram(pictogram: Pictogram): Long

    @Insert
    suspend fun insertPictograms(pictograms: List<Pictogram>)

    @Update
    suspend fun updatePictogram(pictogram: Pictogram)

    @Delete
    suspend fun deletePictogram(pictogram: Pictogram)
}
