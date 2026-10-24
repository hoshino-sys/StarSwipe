package com.starswipe.app.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class ExifInfo(
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val resolution: String = "",
    val fileFormat: String = "",
    val fileSizeFormatted: String = "",
    val dateTakenFormatted: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAddress: String? = null
)
