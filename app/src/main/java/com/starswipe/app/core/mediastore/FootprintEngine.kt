package com.starswipe.app.core.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.starswipe.app.core.model.MediaItem
import com.starswipe.app.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale
import androidx.compose.runtime.Immutable

@Immutable
data class CityFootprint(
    val cityName: String,
    val mediaCount: Int,
    val items: List<MediaItem>
)

@Immutable
data class ProvinceFootprint(
    val provinceName: String,
    val totalCount: Int,
    val coverUri: android.net.Uri?,
    val cities: List<CityFootprint>,
    val allItems: List<MediaItem>
)

@Immutable
data class OnThisDayMemories(
    val year: Int,
    val formattedDate: String,
    val items: List<MediaItem>
)

class FootprintEngine(
    private val context: Context,
    private val geocoder: LocationGeocoder
) {
    // In-memory cache to prevent re-processing on bottom bar tab switches
    private var cachedProvinces: List<ProvinceFootprint>? = null
    private var cachedMemories: List<OnThisDayMemories>? = null
    private var hasCompletedInitialScan = false

    fun hasCachedData(): Boolean = hasCompletedInitialScan
    suspend fun getCachedProvinces(): List<ProvinceFootprint>? = cachedProvinces
    suspend fun getCachedMemories(): List<OnThisDayMemories>? = cachedMemories

    private fun extractLatLong(item: MediaItem, hasLocationPermission: Boolean): DoubleArray? {
        // 1. Try setRequireOriginal if ACCESS_MEDIA_LOCATION is granted
        if (hasLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val originalUri = MediaStore.setRequireOriginal(item.uri)
                context.contentResolver.openInputStream(originalUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val result = exif.latLong
                    if (result != null && result.size >= 2 && (result[0] != 0.0 || result[1] != 0.0)) {
                        return result
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 2. Try regular input stream (for devices/versions where GPS is available or unredacted)
        try {
            context.contentResolver.openInputStream(item.uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val result = exif.latLong
                if (result != null && result.size >= 2 && (result[0] != 0.0 || result[1] != 0.0)) {
                    return result
                }
            }
        } catch (ignored: Exception) {}

        // 3. Try direct file path if accessible
        if (item.path.isNotEmpty()) {
            try {
                val file = File(item.path)
                if (file.exists() && file.canRead()) {
                    val exif = ExifInterface(file)
                    val result = exif.latLong
                    if (result != null && result.size >= 2 && (result[0] != 0.0 || result[1] != 0.0)) {
                        return result
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 4. For videos, extract GPS from container metadata
        val isVideo = item.mediaType.isVideo || item.mimeType.startsWith("video/")
        if (isVideo) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, item.uri)
                val locationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                retriever.release()
                if (!locationStr.isNullOrEmpty()) {
                    val parsed = parseIso6709(locationStr)
                    if (parsed != null && (parsed[0] != 0.0 || parsed[1] != 0.0)) {
                        return parsed
                    }
                }
            } catch (ignored: Exception) {}
        }

        return null
    }

    private fun parseIso6709(locationStr: String): DoubleArray? {
        val regex = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)")
        val match = regex.find(locationStr) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lng = match.groupValues[2].toDoubleOrNull() ?: return null
        return doubleArrayOf(lat, lng)
    }

    suspend fun buildCityFootprints(items: List<MediaItem>, forceRefresh: Boolean = false): List<ProvinceFootprint> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedProvinces != null && hasCompletedInitialScan) {
            return@withContext cachedProvinces!!
        }

        val hasLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

        data class ItemCoord(val item: MediaItem, val lat: Double, val lng: Double, val clusterKey: String)

        // Parallel extraction of GPS coordinates across IO thread pool
        val itemsWithGps = items.chunked(32).flatMap { chunk ->
            chunk.map { item ->
                async(Dispatchers.IO) {
                    val latLong = extractLatLong(item, hasLocationPermission)
                    if (latLong != null && latLong.size >= 2) {
                        val lat = latLong[0]
                        val lng = latLong[1]
                        val clusterKey = String.format(Locale.US, "%.2f,%.2f", lat, lng)
                        ItemCoord(item, lat, lng, clusterKey)
                    } else null
                }
            }.awaitAll().filterNotNull()
        }

        // 2. Resolve addresses once per coordinate cluster (~1.1km)
        val uniqueClusters = itemsWithGps.distinctBy { it.clusterKey }
        val clusterToAddress = mutableMapOf<String, String>()
        for (cluster in uniqueClusters) {
            val addr = geocoder.getAddress(cluster.lat, cluster.lng)
            if (!addr.isNullOrEmpty()) {
                clusterToAddress[cluster.clusterKey] = addr
            }
        }

        // 3. Aggregate into Province -> City -> MediaItems
        val provinceMap = mutableMapOf<String, MutableMap<String, MutableList<MediaItem>>>()
        for (itemCoord in itemsWithGps) {
            val address = clusterToAddress[itemCoord.clusterKey] ?: continue
            val parts = address.split("·").map { it.trim() }
            val province = parts.firstOrNull() ?: "其他"
            val city = parts.getOrNull(1) ?: province

            val cityMap = provinceMap.getOrPut(province) { mutableMapOf() }
            cityMap.getOrPut(city) { mutableListOf() }.add(itemCoord.item)
        }

        // 4. Convert to ProvinceFootprint list
        val result = mutableListOf<ProvinceFootprint>()
        for ((province, cityMap) in provinceMap) {
            val cities = cityMap.map { (cityName, cityItems) ->
                CityFootprint(
                    cityName = cityName,
                    mediaCount = cityItems.size,
                    items = cityItems
                )
            }.sortedByDescending { it.mediaCount }

            val allItems = cities.flatMap { it.items }
            result.add(
                ProvinceFootprint(
                    provinceName = province,
                    totalCount = allItems.size,
                    coverUri = allItems.firstOrNull()?.uri,
                    cities = cities,
                    allItems = allItems
                )
            )
        }

        val sorted = result.sortedByDescending { it.totalCount }
        cachedProvinces = sorted
        hasCompletedInitialScan = true
        sorted
    }

    suspend fun buildOnThisDayMemories(items: List<MediaItem>, forceRefresh: Boolean = false): List<OnThisDayMemories> = withContext(Dispatchers.Default) {
        if (!forceRefresh && cachedMemories != null && hasCompletedInitialScan) {
            return@withContext cachedMemories!!
        }

        val today = Calendar.getInstance()
        val currentMonth = today.get(Calendar.MONTH)
        val currentDay = today.get(Calendar.DAY_OF_MONTH)
        val currentYear = today.get(Calendar.YEAR)

        val itemCal = Calendar.getInstance()
        val yearlyMemories = mutableMapOf<Int, MutableList<MediaItem>>()

        for (item in items) {
            val time = if (item.dateTaken > 0) item.dateTaken else item.dateAdded * 1000
            itemCal.timeInMillis = time

            val year = itemCal.get(Calendar.YEAR)
            val month = itemCal.get(Calendar.MONTH)
            val day = itemCal.get(Calendar.DAY_OF_MONTH)

            if (month == currentMonth && day == currentDay) {
                yearlyMemories.getOrPut(year) { mutableListOf() }.add(item)
            }
        }

        val result = yearlyMemories.map { (year, yearItems) ->
            val formatted = if (year == currentYear) "今天" else "$year 年的今天"
            OnThisDayMemories(
                year = year,
                formattedDate = formatted,
                items = yearItems.sortedByDescending { it.dateTaken }
            )
        }.sortedByDescending { it.year }

        cachedMemories = result
        result
    }
}
