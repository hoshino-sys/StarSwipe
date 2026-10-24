package com.starswipe.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.starswipe.app.core.database.dao.CleanupRecordDao
import com.starswipe.app.core.database.dao.MediaStatusDao
import com.starswipe.app.core.database.entity.CleanupRecordEntity
import com.starswipe.app.core.database.entity.MediaStatusEntity

@Database(
    entities = [MediaStatusEntity::class, CleanupRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StarSwipeDatabase : RoomDatabase() {
    abstract fun mediaStatusDao(): MediaStatusDao
    abstract fun cleanupRecordDao(): CleanupRecordDao

    companion object {
        @Volatile
        private var INSTANCE: StarSwipeDatabase? = null

        fun getInstance(context: Context): StarSwipeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StarSwipeDatabase::class.java,
                    "starswipe.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
