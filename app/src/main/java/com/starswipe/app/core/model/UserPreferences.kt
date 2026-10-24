package com.starswipe.app.core.model

import androidx.compose.runtime.Immutable

enum class SortOrder(val label: String) {
    RANDOM("随机"),
    BIG_FILE_FIRST("大文件优先"),
    NEWEST_FIRST("最新优先"),
    OLDEST_FIRST("最早优先")
}

enum class SwipeDirection(val label: String) {
    LEFT_DELETE_RIGHT_KEEP("左滑删除 / 右滑跳过"),
    LEFT_KEEP_RIGHT_DELETE("左滑跳过 / 右滑删除")
}

@Immutable
data class UserPreferences(
    val roundSize: Int = 20,
    val swipeDirection: SwipeDirection = SwipeDirection.LEFT_DELETE_RIGHT_KEEP,
    val sortOrder: SortOrder = SortOrder.RANDOM,
    val autoPlayVideo: Boolean = true,
    val videoSoundEnabled: Boolean = false
)
