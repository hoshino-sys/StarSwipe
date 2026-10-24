package com.starswipe.app.core.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val mimeType: String,
    val dateAdded: Long,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val duration: Long = 0L,
    val orientation: Int = 0,
    val path: String = "",
    val bucketId: String = "",
    val bucketDisplayName: String = "",
    val isFavorite: Boolean = false,
    val mediaType: MediaType = MediaType.PHOTO
) {
    val formattedSize: String
        get() {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                else -> String.format("%.0f KB", kb)
            }
        }

    val formattedDuration: String
        get() {
            if (duration <= 0) return ""
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val formattedDate: String
        get() {
            val timestamp = if (dateTaken > 0) dateTaken else dateAdded * 1000L
            return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        }
}
