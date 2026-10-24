package com.starswipe.app.feature.organize

import android.content.IntentSender
import com.starswipe.app.core.model.ExifInfo
import com.starswipe.app.core.model.MediaItem
import com.starswipe.app.core.model.MediaType
import com.starswipe.app.core.model.UserPreferences

data class OrganizeUiState(
    val isLoading: Boolean = true,
    val selectedCategory: MediaType = MediaType.ALL_IMAGES,
    val currentBatch: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val pendingDeleteItems: List<MediaItem> = emptyList(),
    val pendingKeepItems: List<MediaItem> = emptyList(),
    val categoryCounts: Map<MediaType, Int> = emptyMap(),
    val userPreferences: UserPreferences = UserPreferences(),
    val showExifBottomSheet: Boolean = false,
    val selectedExifInfo: ExifInfo? = null,
    val showSettlement: Boolean = false,
    val isSettlementDeleting: Boolean = false,
    val deleteIntentSender: IntentSender? = null,
    val canUndo: Boolean = false
) {
    val currentItem: MediaItem?
        get() = currentBatch.getOrNull(currentIndex)

    val currentRoundTotal: Int
        get() = currentBatch.size

    val currentRoundProgress: Float
        get() = if (currentRoundTotal > 0) currentIndex.toFloat() / currentRoundTotal.toFloat() else 0f

    val progressEmoji: String
        get() = when {
            currentRoundProgress < 0.25f -> "🐌" // 蜗牛
            currentRoundProgress < 0.50f -> "🐦" // 飞鸟
            currentRoundProgress < 0.75f -> "🚀" // 火箭
            else -> "🏆" // 奖杯
        }

    val progressTitle: String
        get() = when {
            currentRoundProgress < 0.25f -> "刚刚启程"
            currentRoundProgress < 0.50f -> "渐入佳境"
            currentRoundProgress < 0.75f -> "全力冲刺"
            else -> "即将登顶"
        }
}
