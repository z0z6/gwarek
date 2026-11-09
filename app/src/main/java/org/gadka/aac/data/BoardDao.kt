package org.gadka.aac.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    @Query("SELECT * FROM categories ORDER BY position, id")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM pictograms WHERE categoryId = :categoryId ORDER BY position, id")
    fun pictograms(categoryId: Long): Flow<List<Pictogram>>

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
