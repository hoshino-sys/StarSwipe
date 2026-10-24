package com.starswipe.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cleanup_records")
data class CleanupRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val deletedCount: Int,
    val freedBytes: Long,
    val category: String
)
