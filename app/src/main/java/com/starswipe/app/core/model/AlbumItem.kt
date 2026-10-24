package com.starswipe.app.core.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class AlbumItem(
    val bucketId: String,
    val bucketName: String,
    val relativePath: String,
    val mediaCount: Int,
    val coverUri: Uri?,
    val isEmpty: Boolean = false,
    val path: String = ""
)
