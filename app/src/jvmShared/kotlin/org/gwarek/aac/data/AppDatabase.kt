package org.gwarek.aac.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Definicja bazy wspólna dla Androida i komputera. Tworzenie bazy (ścieżka, sterownik,
 * migracje) jest w części platformowej: createDatabase() w org.gwarek.aac.platform.
 */
@Database(entities = [Category::class, Pictogram::class, Transition::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
}
