package de.steppicrew.kladde.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Plain Room for now; moves onto SQLCipher with the vault key once core/crypto exists. */
@Database(entities = [TrackerEntity::class, DataPointEntity::class], version = 1)
abstract class KladdeDatabase : RoomDatabase() {
    internal abstract fun trackers(): TrackerDao

    internal abstract fun dataPoints(): DataPointDao

    companion object {
        fun open(context: Context): KladdeDatabase = Room.databaseBuilder(context, KladdeDatabase::class.java, "kladde.db").build()

        /** For tests. */
        fun inMemory(context: Context): KladdeDatabase =
            Room.inMemoryDatabaseBuilder(context, KladdeDatabase::class.java).allowMainThreadQueries().build()
    }
}
