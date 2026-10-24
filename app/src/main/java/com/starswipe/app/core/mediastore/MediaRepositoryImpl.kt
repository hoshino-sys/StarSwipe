package com.starswipe.app.core.mediastore

import android.content.IntentSender
import android.os.Environment
import com.starswipe.app.core.database.dao.CleanupRecordDao
import com.starswipe.app.core.database.dao.MediaStatusDao
import com.starswipe.app.core.database.entity.CleanupRecordEntity
import com.starswipe.app.core.database.entity.MediaStatusEntity
import com.starswipe.app.core.datastore.UserPreferencesRepository
import com.starswipe.app.core.model.AlbumItem
import com.starswipe.app.core.model.CleanupStats
import com.starswipe.app.core.model.MediaItem
import com.starswipe.app.core.model.MediaType
import com.starswipe.app.core.model.SortOrder
import com.starswipe.app.core.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepositoryImpl(
    private val dataSource: MediaStoreDataSource,
    private val statusDao: MediaStatusDao,
    private val cleanupDao: CleanupRecordDao,
    private val preferencesRepository: UserPreferencesRepository
) : MediaRepository {

    override fun observeUserPreferences(): Flow<UserPreferences> =
        preferencesRepository.userPreferencesFlow

    override suspend fun updateUserPreferences(update: suspend (UserPreferences) -> Unit) {
        // Handled via specific methods in UserPreferencesRepository
    }

    override suspend fun loadBatchForOrganize(
        category: MediaType,
        limit: Int,
        sortOrder: SortOrder
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val processedIds = statusDao.getAllProcessedMediaIds().toSet()
        dataSource.queryMedia(
            category = category,
            sortOrder = sortOrder,
            excludedIds = processedIds,
            limit = limit
        )
    }

    override suspend fun getRemainingCount(category: MediaType): Int = withContext(Dispatchers.IO) {
        val processedIds = statusDao.getAllProcessedMediaIds().toSet()
        dataSource.getRemainingCountByCategory(category, processedIds)
    }

    override suspend fun getAllCategoryCounts(): Map<MediaType, Int> = withContext(Dispatchers.IO) {
        val processedIds = statusDao.getAllProcessedMediaIds().toSet()
        dataSource.getAllCategoryCounts(processedIds)
    }

    override suspend fun markAsSkipped(item: MediaItem) = withContext(Dispatchers.IO) {
        statusDao.insertStatus(
            MediaStatusEntity(
                mediaId = item.id,
                uri = item.uri.toString(),
                status = "SKIPPED",
                mediaType = item.mediaType.name
            )
        )
    }

    override suspend fun undoLastAction(item: MediaItem) = withContext(Dispatchers.IO) {
        statusDao.removeStatus(item.id)
    }

    override suspend fun createDeleteIntent(items: List<MediaItem>): IntentSender? = withContext(Dispatchers.IO) {
        val uris = items.map { it.uri }
        dataSource.createDeleteRequest(uris)
    }

    override suspend fun recordCleanupSuccess(items: List<MediaItem>, category: MediaType) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext

        val totalFreed = items.sumOf { it.size }
        val count = items.size

        // Record into cleanup session
        cleanupDao.insertRecord(
            CleanupRecordEntity(
                deletedCount = count,
                freedBytes = totalFreed,
                category = category.name
            )
        )

        // Mark in database as deleted
        val entities = items.map { item ->
            MediaStatusEntity(
                mediaId = item.id,
                uri = item.uri.toString(),
                status = "DELETED",
                mediaType = category.name
            )
        }
        statusDao.insertStatuses(entities)
    }

    override fun observeCleanupStats(): Flow<CleanupStats> {
        return combine(
            cleanupDao.observeTotalDeletedCount(),
            cleanupDao.observeTotalFreedBytes()
        ) { deletedCount, freedBytes ->
            val storageStats = dataSource.getStorageStats()
            storageStats.copy(
                totalCleanedCount = deletedCount,
                totalFreedBytes = freedBytes
            )
        }
    }

    override suspend fun getAlbums(): List<AlbumItem> = withContext(Dispatchers.IO) {
        dataSource.queryAlbums()
    }

    override suspend fun deleteEmptyAlbums(): Int = withContext(Dispatchers.IO) {
        val albums = dataSource.queryAlbums().filter { it.isEmpty && it.path.isNotEmpty() }
        var deletedCount = 0

        fun deleteRecursively(file: File): Boolean {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursively(it) }
            }
            return file.delete()
        }

        for (album in albums) {
            try {
                val dir = File(album.path)
                if (dir.exists() && dir.isDirectory) {
                    if (deleteRecursively(dir)) {
                        deletedCount++
                        dataSource.notifyDirectoryDeleted(album.path)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        deletedCount
    }

    override suspend fun deleteAlbum(album: AlbumItem): Boolean = withContext(Dispatchers.IO) {
        if (album.path.isEmpty()) return@withContext false
        try {
            val dir = File(album.path)
            if (dir.exists() && dir.isDirectory) {
                fun deleteRecursively(file: File): Boolean {
                    if (file.isDirectory) {
                        file.listFiles()?.forEach { deleteRecursively(it) }
                    }
                    return file.delete()
                }
                val success = deleteRecursively(dir)
                if (success) {
                    dataSource.notifyDirectoryDeleted(album.path)
                }
                success
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun getAvailablePhotoYears(): List<String> = withContext(Dispatchers.IO) {
        dataSource.queryAvailablePhotoYears()
    }

    override suspend fun loadPhotosForYear(year: String, limit: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        dataSource.queryPhotosForYear(year, limit)
    }

    override suspend fun loadAllPhotosForFootprint(limit: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        dataSource.queryMedia(
            category = MediaType.ALL_IMAGES,
            sortOrder = SortOrder.NEWEST_FIRST,
            excludedIds = emptySet(),
            limit = limit
        )
    }
}
