package com.starswipe.app.feature.organize

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.starswipe.app.StarSwipeApplication
import com.starswipe.app.core.mediastore.ExifMetadataReader
import com.starswipe.app.core.mediastore.LocationGeocoder
import com.starswipe.app.core.mediastore.MediaRepository
import com.starswipe.app.core.model.MediaItem
import com.starswipe.app.core.model.MediaType
import com.starswipe.app.core.model.SwipeDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class ActionHistory(
    val item: MediaItem,
    val wasDelete: Boolean
)

class OrganizeViewModel(
    private val repository: MediaRepository,
    private val exifReader: ExifMetadataReader,
    private val geocoder: LocationGeocoder
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizeUiState())
    val uiState: StateFlow<OrganizeUiState> = _uiState.asStateFlow()

    private val actionHistory = mutableListOf<ActionHistory>()

    init {
        viewModelScope.launch {
            var isInitial = true
            repository.observeUserPreferences().collect { prefs ->
                val prevPrefs = _uiState.value.userPreferences
                _uiState.update { it.copy(userPreferences = prefs) }

                if (isInitial) {
                    isInitial = false
                    loadBatchForCategory(_uiState.value.selectedCategory, prefs)
                    refreshCategoryCounts()
                } else if (prevPrefs.sortOrder != prefs.sortOrder || prevPrefs.roundSize != prefs.roundSize) {
                    loadBatchForCategory(_uiState.value.selectedCategory, prefs)
                }
            }
        }
    }

    fun loadInitialData() {
        loadBatchForCategory(_uiState.value.selectedCategory, _uiState.value.userPreferences)
        refreshCategoryCounts()
    }

    fun refreshCategoryCounts() {
        viewModelScope.launch {
            val counts = repository.getAllCategoryCounts()
            _uiState.update { it.copy(categoryCounts = counts) }
        }
    }

    fun selectCategory(category: MediaType) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update {
            it.copy(
                selectedCategory = category,
                currentIndex = 0,
                pendingDeleteItems = emptyList(),
                pendingKeepItems = emptyList()
            )
        }
        actionHistory.clear()
        loadBatchForCategory(category, _uiState.value.userPreferences)
    }

    private fun loadBatchForCategory(
        category: MediaType,
        prefs: com.starswipe.app.core.model.UserPreferences = _uiState.value.userPreferences
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val limit = prefs.roundSize
            val sortOrder = prefs.sortOrder
            val batch = repository.loadBatchForOrganize(category, limit, sortOrder)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentBatch = batch,
                    currentIndex = 0,
                    canUndo = false
                )
            }
        }
    }

    fun openSettlement() {
        if (_uiState.value.pendingDeleteItems.isNotEmpty()) {
            _uiState.update { it.copy(showSettlement = true) }
        }
    }

    fun onSwipeLeft(item: MediaItem) {
        val deleteOnLeft = _uiState.value.userPreferences.swipeDirection == SwipeDirection.LEFT_DELETE_RIGHT_KEEP
        if (deleteOnLeft) {
            handleMarkDelete(item)
        } else {
            handleMarkKeep(item)
        }
    }

    fun onSwipeRight(item: MediaItem) {
        val deleteOnLeft = _uiState.value.userPreferences.swipeDirection == SwipeDirection.LEFT_DELETE_RIGHT_KEEP
        if (deleteOnLeft) {
            handleMarkKeep(item)
        } else {
            handleMarkDelete(item)
        }
    }

    private fun handleMarkDelete(item: MediaItem) {
        actionHistory.add(ActionHistory(item, wasDelete = true))
        val newPending = _uiState.value.pendingDeleteItems + item
        val nextIndex = _uiState.value.currentIndex + 1
        _uiState.update {
            it.copy(
                pendingDeleteItems = newPending,
                currentIndex = nextIndex,
                canUndo = true
            )
        }
        checkRoundFinished(nextIndex)
    }

    private fun handleMarkKeep(item: MediaItem) {
        actionHistory.add(ActionHistory(item, wasDelete = false))
        val newKeep = _uiState.value.pendingKeepItems + item
        val nextIndex = _uiState.value.currentIndex + 1

        viewModelScope.launch {
            repository.markAsSkipped(item)
        }

        _uiState.update {
            it.copy(
                pendingKeepItems = newKeep,
                currentIndex = nextIndex,
                canUndo = true
            )
        }
        checkRoundFinished(nextIndex)
    }

    private fun checkRoundFinished(nextIndex: Int) {
        val total = _uiState.value.currentBatch.size
        if (nextIndex >= total && total > 0) {
            // Round finished! If there are items to delete, show settlement; otherwise reload next round
            if (_uiState.value.pendingDeleteItems.isNotEmpty()) {
                _uiState.update { it.copy(showSettlement = true) }
            } else {
                refreshCategoryCountsAndNextBatch()
            }
        }
    }

    fun undo() {
        if (actionHistory.isEmpty()) return
        val lastAction = actionHistory.removeAt(actionHistory.lastIndex)
        val prevIndex = (_uiState.value.currentIndex - 1).coerceAtLeast(0)

        if (lastAction.wasDelete) {
            _uiState.update { state ->
                state.copy(
                    pendingDeleteItems = state.pendingDeleteItems.filterNot { it.id == lastAction.item.id },
                    currentIndex = prevIndex,
                    canUndo = actionHistory.isNotEmpty()
                )
            }
        } else {
            viewModelScope.launch {
                repository.undoLastAction(lastAction.item)
            }
            _uiState.update { state ->
                state.copy(
                    pendingKeepItems = state.pendingKeepItems.filterNot { it.id == lastAction.item.id },
                    currentIndex = prevIndex,
                    canUndo = actionHistory.isNotEmpty()
                )
            }
        }
    }

    fun showExifDetail(item: MediaItem) {
        viewModelScope.launch {
            val exif = exifReader.readExif(item, geocoder)
            _uiState.update {
                it.copy(
                    showExifBottomSheet = true,
                    selectedExifInfo = exif
                )
            }
        }
    }

    fun dismissExifDetail() {
        _uiState.update { it.copy(showExifBottomSheet = false, selectedExifInfo = null) }
    }

    fun openSettlementManually() {
        if (_uiState.value.pendingDeleteItems.isNotEmpty()) {
            _uiState.update { it.copy(showSettlement = true) }
        }
    }

    fun dismissSettlement() {
        _uiState.update { it.copy(showSettlement = false) }
    }

    fun toggleSettlementItem(item: MediaItem) {
        val currentPending = _uiState.value.pendingDeleteItems
        val updated = if (currentPending.any { it.id == item.id }) {
            currentPending.filterNot { it.id == item.id }
        } else {
            currentPending + item
        }
        _uiState.update { it.copy(pendingDeleteItems = updated) }
    }

    fun startSettlementDeletion() {
        val itemsToDelete = _uiState.value.pendingDeleteItems
        if (itemsToDelete.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSettlementDeleting = true) }
            val intentSender = repository.createDeleteIntent(itemsToDelete)
            if (intentSender != null) {
                _uiState.update {
                    it.copy(
                        deleteIntentSender = intentSender,
                        isSettlementDeleting = false
                    )
                }
            } else {
                // Legacy or direct fallback
                finishCleanupSuccess(itemsToDelete)
            }
        }
    }

    fun onDeletionResult(granted: Boolean) {
        _uiState.update { it.copy(deleteIntentSender = null) }
        if (granted) {
            val itemsToDelete = _uiState.value.pendingDeleteItems
            finishCleanupSuccess(itemsToDelete)
        }
    }

    private fun finishCleanupSuccess(itemsToDelete: List<MediaItem>) {
        viewModelScope.launch {
            repository.recordCleanupSuccess(itemsToDelete, _uiState.value.selectedCategory)
            _uiState.update {
                it.copy(
                    showSettlement = false,
                    isSettlementDeleting = false,
                    pendingDeleteItems = emptyList(),
                    pendingKeepItems = emptyList()
                )
            }
            actionHistory.clear()
            refreshCategoryCountsAndNextBatch()
        }
    }

    private fun refreshCategoryCountsAndNextBatch() {
        viewModelScope.launch {
            val counts = repository.getAllCategoryCounts()
            _uiState.update { it.copy(categoryCounts = counts) }
            loadBatchForCategory(_uiState.value.selectedCategory)
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = (context.applicationContext as? StarSwipeApplication) ?: StarSwipeApplication.instance
                    return OrganizeViewModel(
                        repository = app.mediaRepository,
                        exifReader = app.exifReader,
                        geocoder = app.geocoder
                    ) as T
                }
            }
    }
}
