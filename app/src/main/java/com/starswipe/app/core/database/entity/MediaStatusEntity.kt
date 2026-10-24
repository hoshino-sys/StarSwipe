package com.starswipe.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_status")
data class MediaStatusEntity(
    @PrimaryKey
    val mediaId: Long,
    val uri: String,
    val status: String, // "SKIPPED", "PENDING_DELETE", "DELETED"
    val mediaType: String,
    val updatedAt: Long = System.currentTimeMillis()
)
