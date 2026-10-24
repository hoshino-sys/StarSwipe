package com.starswipe.app.core.mediastore

import android.content.IntentSender
import com.starswipe.app.core.model.AlbumItem
import com.starswipe.app.core.model.CleanupStats
import com.starswipe.app.core.model.MediaItem
import com.starswipe.app.core.model.MediaType
import com.starswipe.app.core.model.SortOrder
import com.starswipe.app.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeUserPreferences(): Flow<UserPreferences>
    suspend fun updateUserPreferences(update: suspend (UserPreferences) -> Unit)

    suspend fun loadBatchForOrganize(category: MediaType, limit: Int, sortOrder: SortOrder): List<MediaItem>
    suspend fun getRemainingCount(category: MediaType): Int
    suspend fun getAllCategoryCounts(): Map<MediaType, Int>

    suspend fun markAsSkipped(item: MediaItem)
    suspend fun undoLastAction(item: MediaItem)

    suspend fun createDeleteIntent(items: List<MediaItem>): IntentSender?
    suspend fun recordCleanupSuccess(items: List<MediaItem>, category: MediaType)

    fun observeCleanupStats(): Flow<CleanupStats>
    suspend fun getAlbums(): List<AlbumItem>
    suspend fun deleteEmptyAlbums(): Int
    suspend fun deleteAlbum(album: AlbumItem): Boolean

    suspend fun getAvailablePhotoYears(): List<String>
    suspend fun loadPhotosForYear(year: String, limit: Int = 300): List<MediaItem>
    suspend fun loadAllPhotosForFootprint(limit: Int = 2000): List<MediaItem>
}
