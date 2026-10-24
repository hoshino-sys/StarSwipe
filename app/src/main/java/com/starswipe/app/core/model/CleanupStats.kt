package com.starswipe.app.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class CleanupStats(
    val totalCleanedCount: Int = 0,
    val totalFreedBytes: Long = 0L,
    val usedStorageBytes: Long = 0L,
    val totalStorageBytes: Long = 0L
) {
    val formattedFreedBytes: String
        get() = formatBytes(totalFreedBytes)

    val formattedUsedStorage: String
        get() = formatBytes(usedStorageBytes)

    val formattedTotalStorage: String
        get() = formatBytes(totalStorageBytes)

    val usagePercentage: Float
        get() = if (totalStorageBytes > 0) (usedStorageBytes.toFloat() / totalStorageBytes.toFloat()).coerceIn(0f, 1f) else 0f

    companion object {
        fun formatBytes(bytes: Long): String {
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                else -> String.format("%.0f KB", kb)
            }
        }
    }
}
