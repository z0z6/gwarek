package org.gadka.aac.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Category::class, Pictogram::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gadka.db",
                ).build().also { instance = it }
            }
    }
}
