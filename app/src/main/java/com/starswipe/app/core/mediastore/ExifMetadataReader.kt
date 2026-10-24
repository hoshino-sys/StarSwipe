package com.starswipe.app.core.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.starswipe.app.core.model.ExifInfo
import com.starswipe.app.core.model.MediaItem
import com.starswipe.app.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ExifMetadataReader(private val context: Context) {

    suspend fun readExif(mediaItem: MediaItem, geocoder: LocationGeocoder? = null): ExifInfo = withContext(Dispatchers.IO) {
        var aperture: String? = null
        var shutterSpeed: String? = null
        var iso: String? = null
        var focalLength: String? = null
        var cameraModel: String? = null
        var latitude: Double? = null
        var longitude: Double? = null

        fun parseFromExif(exif: ExifInterface) {
            val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                ?: exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)
            if (!fNumber.isNullOrEmpty()) {
                aperture = "f/$fNumber"
            }

            val expTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            if (!expTime.isNullOrEmpty()) {
                shutterSpeed = try {
                    val exp = expTime.toDouble()
                    if (exp < 1.0 && exp > 0) {
                        "1/${(1.0 / exp).toInt()}s"
                    } else {
                        "${exp}s"
                    }
                } catch (e: Exception) {
                    "${expTime}s"
                }
            }

            val isoValue = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            if (!isoValue.isNullOrEmpty()) {
                iso = "ISO $isoValue"
            }

            val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            if (!focal.isNullOrEmpty()) {
                focalLength = try {
                    val parts = focal.split("/")
                    if (parts.size == 2) {
                        val num = parts[0].toDouble()
                        val den = parts[1].toDouble()
                        "${(num / den).toInt()}mm"
                    } else {
                        "${focal}mm"
                    }
                } catch (e: Exception) {
                    "${focal}mm"
                }
            }

            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
            val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.trim()
            cameraModel = when {
                !model.isNullOrEmpty() && !make.isNullOrEmpty() -> {
                    if (model.startsWith(make, ignoreCase = true)) model else "$make $model"
                }
                !model.isNullOrEmpty() -> model
                !make.isNullOrEmpty() -> make
                !software.isNullOrEmpty() -> software
                else -> null
            }

            val latLong = exif.latLong
            if (latLong != null && latLong.size >= 2 && (latLong[0] != 0.0 || latLong[1] != 0.0)) {
                latitude = latLong[0]
                longitude = latLong[1]
            }
        }

        val hasLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

        var streamReadSuccess = false

        // 1. Try reading with unredacted original URI if ACCESS_MEDIA_LOCATION is granted
        if (hasLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val originalUri = MediaStore.setRequireOriginal(mediaItem.uri)
                context.contentResolver.openInputStream(originalUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    parseFromExif(exif)
                    streamReadSuccess = true
                }
            } catch (ignored: Exception) {}
        }

        // 2. Open standard item.uri (guaranteed to not throw SecurityException)
        if (!streamReadSuccess || cameraModel == null) {
            try {
                context.contentResolver.openInputStream(mediaItem.uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    parseFromExif(exif)
                    streamReadSuccess = true
                }
            } catch (ignored: Exception) {}
        }

        // 3. Fallback to direct file path if accessible
        if (mediaItem.path.isNotEmpty() && (cameraModel == null || latitude == null)) {
            try {
                val file = File(mediaItem.path)
                if (file.exists() && file.canRead()) {
                    val exif = ExifInterface(file)
                    parseFromExif(exif)
                }
            } catch (ignored: Exception) {}
        }

        // 4. For videos, extract metadata via MediaMetadataRetriever
        val isVideo = mediaItem.mediaType.isVideo || mediaItem.mimeType.startsWith("video/")
        if (isVideo && (latitude == null || cameraModel == null)) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, mediaItem.uri)

                if (latitude == null) {
                    val locationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                    if (!locationStr.isNullOrEmpty()) {
                        val parsed = parseIso6709(locationStr)
                        if (parsed != null) {
                            latitude = parsed[0]
                            longitude = parsed[1]
                        }
                    }
                }
                retriever.release()
            } catch (ignored: Exception) {}
        }

        val resolution = if (mediaItem.width > 0 && mediaItem.height > 0) {
            "${mediaItem.width} × ${mediaItem.height}"
        } else {
            "未知分辨率"
        }

        val fileFormat = mediaItem.mimeType.substringAfterLast("/").uppercase()

        val dateTakenFormatted = if (mediaItem.dateTaken > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(mediaItem.dateTaken))
        } else if (mediaItem.dateAdded > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(mediaItem.dateAdded * 1000))
        } else {
            "未知时间"
        }

        var addressName: String? = null
        val finalLat = latitude
        val finalLng = longitude
        if (finalLat != null && finalLng != null) {
            if (geocoder != null) {
                addressName = geocoder.getAddress(finalLat, finalLng)
            }
            if (addressName.isNullOrEmpty()) {
                val latDir = if (finalLat >= 0) "N" else "S"
                val lngDir = if (finalLng >= 0) "E" else "W"
                addressName = String.format(Locale.getDefault(), "%.3f°%s, %.3f°%s", abs(finalLat), latDir, abs(finalLng), lngDir)
            }
        }

        ExifInfo(
            aperture = aperture,
            shutterSpeed = shutterSpeed,
            iso = iso,
            focalLength = focalLength,
            cameraModel = cameraModel,
            resolution = resolution,
            fileFormat = fileFormat,
            fileSizeFormatted = mediaItem.formattedSize,
            dateTakenFormatted = dateTakenFormatted,
            latitude = latitude,
            longitude = longitude,
            locationAddress = addressName
        )
    }

    private fun parseIso6709(locationStr: String): DoubleArray? {
        val regex = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)")
        val match = regex.find(locationStr) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lng = match.groupValues[2].toDoubleOrNull() ?: return null
        return doubleArrayOf(lat, lng)
    }
}
