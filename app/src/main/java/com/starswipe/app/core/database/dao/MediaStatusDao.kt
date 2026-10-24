package com.starswipe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.starswipe.app.core.database.entity.MediaStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(entity: MediaStatusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatuses(entities: List<MediaStatusEntity>)

    @Query("SELECT mediaId FROM media_status WHERE status = 'SKIPPED'")
    suspend fun getSkippedMediaIds(): List<Long>

    @Query("SELECT mediaId FROM media_status WHERE status = 'DELETED'")
    suspend fun getDeletedMediaIds(): List<Long>

    @Query("SELECT mediaId FROM media_status")
    suspend fun getAllProcessedMediaIds(): List<Long>

    @Query("SELECT mediaId FROM media_status WHERE mediaType = :mediaType")
    suspend fun getProcessedMediaIdsByType(mediaType: String): List<Long>

    @Query("DELETE FROM media_status WHERE mediaId = :mediaId")
    suspend fun removeStatus(mediaId: Long)

    @Query("DELETE FROM media_status")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM media_status WHERE status = 'SKIPPED'")
    fun observeSkippedCount(): Flow<Int>
}
