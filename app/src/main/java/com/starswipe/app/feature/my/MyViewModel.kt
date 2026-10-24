package com.starswipe.app.feature.my

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.starswipe.app.StarSwipeApplication
import com.starswipe.app.core.database.dao.MediaStatusDao
import com.starswipe.app.core.datastore.UserPreferencesRepository
import com.starswipe.app.core.mediastore.MediaRepository
import com.starswipe.app.core.model.CleanupStats
import com.starswipe.app.core.model.SortOrder
import com.starswipe.app.core.model.SwipeDirection
import com.starswipe.app.core.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyUiState(
    val stats: CleanupStats = CleanupStats(),
    val preferences: UserPreferences = UserPreferences(),
    val showResetDialog: Boolean = false,
    val isResetting: Boolean = false
)

class MyViewModel(
    private val repository: MediaRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val statusDao: MediaStatusDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCleanupStats().collect { stats ->
                _uiState.update { it.copy(stats = stats) }
            }
        }
        viewModelScope.launch {
            repository.observeUserPreferences().collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
    }

    fun updateRoundSize(size: Int) {
        viewModelScope.launch {
            preferencesRepository.updateRoundSize(size)
        }
    }

    fun updateSwipeDirection(direction: SwipeDirection) {
        viewModelScope.launch {
            preferencesRepository.updateSwipeDirection(direction)
        }
    }

    fun updateSortOrder(order: SortOrder) {
        viewModelScope.launch {
            preferencesRepository.updateSortOrder(order)
        }
    }

    fun updateAutoPlayVideo(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateAutoPlayVideo(enabled)
        }
    }

    fun updateVideoSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateVideoSoundEnabled(enabled)
        }
    }

    fun showResetDialog(show: Boolean) {
        _uiState.update { it.copy(showResetDialog = show) }
    }

    fun resetSkippedHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isResetting = true) }
            statusDao.clearAll()
            _uiState.update { it.copy(isResetting = false, showResetDialog = false) }
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = (context.applicationContext as? StarSwipeApplication) ?: StarSwipeApplication.instance
                    return MyViewModel(
                        repository = app.mediaRepository,
                        preferencesRepository = app.preferencesRepository,
                        statusDao = app.database.mediaStatusDao()
                    ) as T
                }
            }
    }
}
