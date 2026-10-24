package com.starswipe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.starswipe.app.core.database.entity.CleanupRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CleanupRecordDao {
    @Insert
    suspend fun insertRecord(record: CleanupRecordEntity)

    @Query("SELECT COALESCE(SUM(freedBytes), 0) FROM cleanup_records")
    fun observeTotalFreedBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(deletedCount), 0) FROM cleanup_records")
    fun observeTotalDeletedCount(): Flow<Int>

    @Query("SELECT * FROM cleanup_records ORDER BY timestamp DESC")
    fun observeRecords(): Flow<List<CleanupRecordEntity>>

    @Query("SELECT * FROM cleanup_records ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecentRecords(): List<CleanupRecordEntity>
}
