package com.starswipe.app.feature.similar

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.starswipe.app.StarSwipeApplication
import com.starswipe.app.core.mediastore.ImageSimilarityEngine
import com.starswipe.app.core.mediastore.MediaRepository
import com.starswipe.app.core.mediastore.SimilarGroup
import com.starswipe.app.core.model.CleanupStats
import com.starswipe.app.core.model.MediaItem
import com.starswipe.app.core.model.MediaType
import com.starswipe.app.core.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SimilarUiState(
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val availableYears: List<String> = listOf("最近", "2026", "2025", "2024"),
    val selectedYear: String = "最近",
    val groups: List<SimilarGroup> = emptyList(),
    val deleteIntentSender: IntentSender? = null,
    val pendingDeleteGroup: SimilarGroup? = null
) {
    val totalReleasableBytes: Long
        get() = groups.sumOf { it.totalReleasableBytes }

    val formattedTotalReleasable: String
        get() = CleanupStats.formatBytes(totalReleasableBytes)
}

private object SimilarGroupCache {
    private fun getCacheFile(context: Context, year: String): File {
        val sanitized = year.replace(Regex("[^a-zA-Z0-9_\\u4e00-\\u9fa5]"), "_")
        return File(context.cacheDir, "similar_groups_${sanitized}.json")
    }

    fun saveGroups(context: Context, year: String, groups: List<SimilarGroup>) {
        try {
            val file = getCacheFile(context, year)
            val jsonArray = JSONArray()
            for (group in groups) {
                val groupObj = JSONObject()
                groupObj.put("id", group.id)
                groupObj.put("bestItem", mediaItemToJson(group.bestItem))

                val itemsArray = JSONArray()
                for (item in group.similarItems) {
                    itemsArray.put(mediaItemToJson(item))
                }
                groupObj.put("similarItems", itemsArray)

                val selectedArray = JSONArray()
                for (sel in group.selectedForDelete) {
                    selectedArray.put(sel)
                }
                groupObj.put("selectedForDelete", selectedArray)

                jsonArray.put(groupObj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadGroups(context: Context, year: String): List<SimilarGroup>? {
        return try {
            val file = getCacheFile(context, year)
            if (!file.exists()) return null
            val content = file.readText()
            if (content.isBlank()) return null
            val jsonArray = JSONArray(content)
            val list = mutableListOf<SimilarGroup>()
            for (i in 0 until jsonArray.length()) {
                val groupObj = jsonArray.getJSONObject(i)
                val id = groupObj.getString("id")
                val bestItem = jsonToMediaItem(groupObj.getJSONObject("bestItem"))

                val similarArray = groupObj.getJSONArray("similarItems")
                val similarItems = mutableListOf<MediaItem>()
                for (j in 0 until similarArray.length()) {
                    similarItems.add(jsonToMediaItem(similarArray.getJSONObject(j)))
                }

                val selectedArray = groupObj.getJSONArray("selectedForDelete")
                val selected = mutableSetOf<Long>()
                for (j in 0 until selectedArray.length()) {
                    selected.add(selectedArray.getLong(j))
                }

                list.add(
                    SimilarGroup(
                        id = id,
                        bestItem = bestItem,
                        similarItems = similarItems,
                        selectedForDelete = selected
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun mediaItemToJson(item: MediaItem): JSONObject {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("uri", item.uri.toString())
        obj.put("displayName", item.displayName)
        obj.put("size", item.size)
        obj.put("mimeType", item.mimeType)
        obj.put("dateAdded", item.dateAdded)
        obj.put("dateTaken", item.dateTaken)
        obj.put("width", item.width)
        obj.put("height", item.height)
        obj.put("duration", item.duration)
        obj.put("orientation", item.orientation)
        obj.put("path", item.path)
        obj.put("bucketId", item.bucketId)
        obj.put("bucketDisplayName", item.bucketDisplayName)
        obj.put("mediaType", item.mediaType.name)
        return obj
    }

    private fun jsonToMediaItem(obj: JSONObject): MediaItem {
        return MediaItem(
            id = obj.getLong("id"),
            uri = Uri.parse(obj.getString("uri")),
            displayName = obj.optString("displayName", ""),
            size = obj.optLong("size", 0L),
            mimeType = obj.optString("mimeType", ""),
            dateAdded = obj.optLong("dateAdded", 0L),
            dateTaken = obj.optLong("dateTaken", 0L),
            width = obj.optInt("width", 0),
            height = obj.optInt("height", 0),
            duration = obj.optLong("duration", 0L),
            orientation = obj.optInt("orientation", 0),
            path = obj.optString("path", ""),
            bucketId = obj.optString("bucketId", ""),
            bucketDisplayName = obj.optString("bucketDisplayName", ""),
            mediaType = try {
                MediaType.valueOf(obj.optString("mediaType", "PHOTO"))
            } catch (e: Exception) {
                MediaType.PHOTO
            }
        )
    }
}

class SimilarViewModel(
    private val repository: MediaRepository,
    private val similarityEngine: ImageSimilarityEngine,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SimilarUiState())
    val uiState: StateFlow<SimilarUiState> = _uiState.asStateFlow()

    private val cachedGroupsByYear = mutableMapOf<String, List<SimilarGroup>>()

    init {
        // 1. Instant cold start: Load from persistent disk cache immediately (0-second wait!)
        val diskCached = SimilarGroupCache.loadGroups(context, _uiState.value.selectedYear)
        if (!diskCached.isNullOrEmpty()) {
            cachedGroupsByYear[_uiState.value.selectedYear] = diskCached
            _uiState.update {
                it.copy(
                    groups = diskCached,
                    isScanning = false
                )
            }
        } else {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0.05f) }
        }

        // 2. Fetch all years and perform background scan
        loadAndScan(silent = !diskCached.isNullOrEmpty())
    }

    fun loadAndScan(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isScanning = true, scanProgress = 0.1f) }
            }

            // Fetch all distinct photo years from the device
            val dbYears = repository.getAvailablePhotoYears()
            val filterYears = listOf("最近") + dbYears
            _uiState.update { it.copy(availableYears = filterYears) }

            // Scan selected year
            scanForYear(_uiState.value.selectedYear, silent = silent)
        }
    }

    fun selectYear(year: String) {
        if (_uiState.value.selectedYear == year && !cachedGroupsByYear[year].isNullOrEmpty()) return

        _uiState.update { it.copy(selectedYear = year) }

        // Memory cache check
        val memCached = cachedGroupsByYear[year]
        if (!memCached.isNullOrEmpty()) {
            _uiState.update {
                it.copy(
                    groups = memCached,
                    isScanning = false
                )
            }
            return
        }

        // Disk cache check
        val diskCached = SimilarGroupCache.loadGroups(context, year)
        if (!diskCached.isNullOrEmpty()) {
            cachedGroupsByYear[year] = diskCached
            _uiState.update {
                it.copy(
                    groups = diskCached,
                    isScanning = false
                )
            }
            // Silent refresh in background
            viewModelScope.launch {
                scanForYear(year, silent = true)
            }
            return
        }

        // No cache: full scan with progress
        viewModelScope.launch {
            scanForYear(year, silent = false)
        }
    }

    private suspend fun scanForYear(year: String, silent: Boolean = false) {
        if (!silent) {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0.1f) }
        }

        // Query targeted photos for this year (or recent)
        val photos = if (year == "最近") {
            repository.loadBatchForOrganize(MediaType.PHOTO, 300, SortOrder.NEWEST_FIRST)
        } else {
            repository.loadPhotosForYear(year, limit = 300)
        }

        val groups = similarityEngine.findSimilarGroups(photos) { progress ->
            if (!silent) {
                _uiState.update { it.copy(scanProgress = progress) }
            }
        }

        // Update memory and disk cache
        cachedGroupsByYear[year] = groups
        withContext(Dispatchers.IO) {
            SimilarGroupCache.saveGroups(context, year, groups)
        }

        _uiState.update {
            it.copy(
                isScanning = false,
                groups = groups
            )
        }
    }

    fun toggleItemSelection(groupId: String, itemId: Long) {
        val updated = _uiState.value.groups.map { group ->
            if (group.id == groupId) {
                val newSelection = if (group.selectedForDelete.contains(itemId)) {
                    group.selectedForDelete - itemId
                } else {
                    group.selectedForDelete + itemId
                }
                group.copy(selectedForDelete = newSelection)
            } else {
                group
            }
        }
        _uiState.update { it.copy(groups = updated) }
    }

    fun deleteGroupSelection(group: SimilarGroup) {
        val itemsToDelete = group.similarItems.filter { group.selectedForDelete.contains(it.id) }
        if (itemsToDelete.isEmpty()) return

        viewModelScope.launch {
            val intentSender = repository.createDeleteIntent(itemsToDelete)
            _uiState.update {
                it.copy(
                    deleteIntentSender = intentSender,
                    pendingDeleteGroup = group
                )
            }
        }
    }

    fun smartCleanAll() {
        val allSelectedItems = _uiState.value.groups.flatMap { group ->
            group.similarItems.filter { group.selectedForDelete.contains(it.id) }
        }
        if (allSelectedItems.isEmpty()) return

        viewModelScope.launch {
            val intentSender = repository.createDeleteIntent(allSelectedItems)
            _uiState.update {
                it.copy(
                    deleteIntentSender = intentSender,
                    pendingDeleteGroup = null
                )
            }
        }
    }

    fun onDeletionResult(granted: Boolean) {
        _uiState.update { it.copy(deleteIntentSender = null) }
        if (granted) {
            val targetGroup = _uiState.value.pendingDeleteGroup
            viewModelScope.launch {
                if (targetGroup != null) {
                    val deletedItems = targetGroup.similarItems.filter { targetGroup.selectedForDelete.contains(it.id) }
                    repository.recordCleanupSuccess(deletedItems, MediaType.PHOTO)
                } else {
                    val allSelectedItems = _uiState.value.groups.flatMap { group ->
                        group.similarItems.filter { group.selectedForDelete.contains(it.id) }
                    }
                    repository.recordCleanupSuccess(allSelectedItems, MediaType.PHOTO)
                }
                loadAndScan(silent = false)
            }
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = (context.applicationContext as? StarSwipeApplication) ?: StarSwipeApplication.instance
                    return SimilarViewModel(
                        repository = app.mediaRepository,
                        similarityEngine = app.similarityEngine,
                        context = app.applicationContext
                    ) as T
                }
            }
    }
}
