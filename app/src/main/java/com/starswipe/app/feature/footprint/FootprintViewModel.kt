package com.starswipe.app.feature.footprint

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.starswipe.app.StarSwipeApplication
import com.starswipe.app.core.mediastore.FootprintEngine
import com.starswipe.app.core.mediastore.MediaRepository
import com.starswipe.app.core.mediastore.OnThisDayMemories
import com.starswipe.app.core.mediastore.ProvinceFootprint
import com.starswipe.app.core.model.MediaType
import com.starswipe.app.core.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FootprintSubMode(val title: String) {
    CITY_FOOTPRINT("城市足迹"),
    ON_THIS_DAY("往年今日")
}

enum class FootprintViewMode {
    CARD_MODE,
    LIST_MODE
}

data class FootprintUiState(
    val isLoading: Boolean = true,
    val subMode: FootprintSubMode = FootprintSubMode.CITY_FOOTPRINT,
    val viewMode: FootprintViewMode = FootprintViewMode.CARD_MODE,
    val provinces: List<ProvinceFootprint> = emptyList(),
    val onThisDayMemories: List<OnThisDayMemories> = emptyList(),
    val selectedProvince: ProvinceFootprint? = null
) {
    val totalProvincesCount: Int
        get() = provinces.size

    val totalMemoriesCount: Int
        get() = provinces.sumOf { it.totalCount }
}

class FootprintViewModel(
    private val repository: MediaRepository,
    private val footprintEngine: FootprintEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(FootprintUiState())
    val uiState: StateFlow<FootprintUiState> = _uiState.asStateFlow()

    init {
        loadData(force = false)
    }

    fun switchSubMode(mode: FootprintSubMode) {
        _uiState.update { it.copy(subMode = mode) }
    }

    fun toggleViewMode() {
        val next = if (_uiState.value.viewMode == FootprintViewMode.CARD_MODE) {
            FootprintViewMode.LIST_MODE
        } else {
            FootprintViewMode.CARD_MODE
        }
        _uiState.update { it.copy(viewMode = next) }
    }

    fun selectProvince(province: ProvinceFootprint?) {
        _uiState.update { it.copy(selectedProvince = province) }
    }

    fun loadData(force: Boolean = false) {
        viewModelScope.launch {
            if (!force && footprintEngine.hasCachedData()) {
                val cachedP = footprintEngine.getCachedProvinces() ?: emptyList()
                val cachedM = footprintEngine.getCachedMemories() ?: emptyList()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        provinces = cachedP,
                        onThisDayMemories = cachedM
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }
            val photos = repository.loadAllPhotosForFootprint(2000)
            val footprints = footprintEngine.buildCityFootprints(photos, forceRefresh = force)
            val onThisDay = footprintEngine.buildOnThisDayMemories(photos, forceRefresh = force)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    provinces = footprints,
                    onThisDayMemories = onThisDay
                )
            }
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = (context.applicationContext as? StarSwipeApplication) ?: StarSwipeApplication.instance
                    return FootprintViewModel(
                        repository = app.mediaRepository,
                        footprintEngine = app.footprintEngine
                    ) as T
                }
            }
    }
}
