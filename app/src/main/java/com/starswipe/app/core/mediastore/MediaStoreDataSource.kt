package com.starswipe.app.core.mediastore

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.starswipe.app.core.model.AlbumItem
import com.starswipe.app.core.model.CleanupStats
import com.starswipe.app.core.model.MediaItem
import com.starswipe.app.core.model.MediaType
import com.starswipe.app.core.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreDataSource(private val context: Context) {

    private val contentResolver get() = context.contentResolver

    private object LivePhotoDetector {
        private val cache = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()
        private val LIVE_PHOTO_REGEX = Regex("""(?i)(?:MotionPhoto|MicroVideo|LivePhoto)\s*=\s*["']?1["']?""")

        fun isLivePhoto(
            id: Long,
            path: String,
            displayName: String,
            uri: Uri,
            contentResolver: android.content.ContentResolver
        ): Boolean {
            cache[id]?.let { return it }

            val lowerName = displayName.lowercase()
            val lowerPath = path.lowercase()

            // 1. Fast match on known filenames and paths (MVIMG, LIVE_, .live., etc.)
            val isNameMatched = lowerName.startsWith("mvimg_") ||
                    lowerName.contains(".live.") ||
                    lowerName.contains("live_") ||
                    lowerName.contains("_live") ||
                    lowerName.contains("motionphoto") ||
                    lowerPath.contains("mvimg") ||
                    lowerPath.contains("/.live/") ||
                    lowerPath.contains("motionphoto")

            if (isNameMatched) {
                cache[id] = true
                return true
            }

            // 2. Only candidate JPEG/HEIC camera photos need deep XMP scan
            val isCandidate = (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".heic")) &&
                    (lowerPath.contains("/dcim/") || lowerPath.contains("/camera/") || lowerName.startsWith("img_"))

            if (!isCandidate) {
                cache[id] = false
                return false
            }

            // 3. Deep scan: Inspect first 64KB for XMP / APP1 motion photo markers
            val isLive = inspectHeader(uri, path, contentResolver)
            cache[id] = isLive
            return isLive
        }

        private fun inspectHeader(
            uri: Uri,
            path: String,
            contentResolver: android.content.ContentResolver
        ): Boolean {
            return try {
                val buffer = ByteArray(65536)
                var bytesRead = 0

                val file = if (path.isNotEmpty()) File(path) else null
                if (file != null && file.exists() && file.canRead()) {
                    file.inputStream().use { input ->
                        bytesRead = input.read(buffer)
                    }
                } else {
                    contentResolver.openInputStream(uri)?.use { input ->
                        bytesRead = input.read(buffer)
                    }
                }

                if (bytesRead <= 0) return false

                // Convert directly using ISO-8859-1 for 1:1 single-byte character mapping
                val header = String(buffer, 0, bytesRead, Charsets.ISO_8859_1)

                header.contains("MotionPhoto_Data", ignoreCase = true) ||
                        header.contains("vivo:LivePhoto", ignoreCase = true) ||
                        header.contains("MiLivePhoto", ignoreCase = true) ||
                        LIVE_PHOTO_REGEX.containsMatchIn(header)
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun getBaseSelection(): String {
        return buildString {
            append("(${MediaStore.MediaColumns.DATA} NOT LIKE '%/Android/%' OR ${MediaStore.MediaColumns.DATA} IS NULL)")
            append(" AND (${MediaStore.MediaColumns.SIZE} > 0)")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" AND (${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE 'Android/%' OR ${MediaStore.MediaColumns.RELATIVE_PATH} IS NULL)")
            }
        }
    }

    suspend fun queryMedia(
        category: MediaType,
        sortOrder: SortOrder = SortOrder.RANDOM,
        excludedIds: Set<Long> = emptySet(),
        limit: Int = 100
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val isVideo = category.isVideo
        val uri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        queryMediaInternal(uri, isVideo, category, sortOrder, excludedIds, limit)
    }

    private fun queryMediaInternal(
        uri: Uri,
        isVideo: Boolean,
        category: MediaType,
        sortOrder: SortOrder,
        excludedIds: Set<Long>,
        limit: Int
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.DATE_TAKEN)
            projection.add(MediaStore.MediaColumns.BUCKET_ID)
            projection.add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
            projection.add(MediaStore.MediaColumns.ORIENTATION)
            if (isVideo) {
                // MediaStore.MediaColumns.DURATION is only valid on video/audio URIs
                projection.add(MediaStore.MediaColumns.DURATION)
            }
        }

        val orderClause = when (sortOrder) {
            SortOrder.RANDOM -> null // Shuffled after load
            SortOrder.BIG_FILE_FIRST -> "${MediaStore.MediaColumns.SIZE} DESC"
            SortOrder.NEWEST_FIRST -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC"
            } else {
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            }
            SortOrder.OLDEST_FIRST -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.MediaColumns.DATE_TAKEN} ASC, ${MediaStore.MediaColumns.DATE_ADDED} ASC"
            } else {
                "${MediaStore.MediaColumns.DATE_MODIFIED} ASC"
            }
        }

        try {
            contentResolver.query(
                uri,
                projection.toTypedArray(),
                getBaseSelection(),
                null,
                orderClause
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                val dateTakenCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                } else -1

                val bucketIdCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                } else -1

                val bucketNameCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                } else -1

                val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                val orientationCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
                } else -1

                val durationCol = if (isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                } else -1

                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    if (excludedIds.contains(id)) continue

                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    if (size <= 0) continue

                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                    val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L
                    val rawDateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val dateTaken = if (rawDateTaken > 0L) {
                        rawDateTaken
                    } else if (dateAdded > 0L) {
                        dateAdded * 1000L
                    } else {
                        dateModified * 1000L
                    }
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""

                    val bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) ?: "" else ""
                    val bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) ?: "" else ""
                    val relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol) ?: "" else ""
                    val orientation = if (orientationCol >= 0) cursor.getInt(orientationCol) else 0
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L

                    // Extra guarantee to strictly reject /Android/ cache/stickers
                    if (path.contains("/Android/", ignoreCase = true) || relativePath.startsWith("Android/", ignoreCase = true)) {
                        continue
                    }

                    val itemUri = ContentUris.withAppendedId(uri, id)

                    // Category Classification Filter
                    val isScreenshot = relativePath.contains("screenshot", ignoreCase = true) ||
                            bucketName.contains("screenshot", ignoreCase = true) ||
                            name.contains("screenshot", ignoreCase = true) ||
                            path.contains("screenshot", ignoreCase = true)

                    val isScreenRecording = relativePath.contains("screenrecord", ignoreCase = true) ||
                            bucketName.contains("screenrecord", ignoreCase = true) ||
                            name.contains("screenrecord", ignoreCase = true) ||
                            path.contains("screenrecord", ignoreCase = true)

                    val isSelfie = relativePath.contains("selfie", ignoreCase = true) ||
                            bucketName.contains("selfie", ignoreCase = true) ||
                            name.contains("selfie", ignoreCase = true)

                    val isLivePhoto = if (!isVideo && !isScreenshot) {
                        LivePhotoDetector.isLivePhoto(id, path, name, itemUri, contentResolver)
                    } else false

                    val matched = when (category) {
                        MediaType.ALL_IMAGES -> true
                        MediaType.ALL_VIDEOS -> true
                        MediaType.PHOTO -> !isScreenshot && !isLivePhoto
                        MediaType.VIDEO -> !isScreenRecording
                        MediaType.SCREENSHOT -> isScreenshot
                        MediaType.SCREEN_RECORDING -> isScreenRecording
                        MediaType.SELFIE -> isSelfie
                        MediaType.LIVE_PHOTO -> isLivePhoto
                    }

                    if (matched) {
                        items.add(
                            MediaItem(
                                id = id,
                                uri = itemUri,
                                displayName = name,
                                size = size,
                                mimeType = mimeType,
                                dateAdded = dateAdded,
                                dateTaken = dateTaken,
                                width = width,
                                height = height,
                                duration = duration,
                                orientation = orientation,
                                path = path,
                                bucketId = bucketId,
                                bucketDisplayName = bucketName,
                                mediaType = if (isVideo) {
                                    if (isScreenRecording) MediaType.SCREEN_RECORDING else MediaType.VIDEO
                                } else {
                                    if (isScreenshot) MediaType.SCREENSHOT
                                    else if (isLivePhoto) MediaType.LIVE_PHOTO
                                    else if (isSelfie) MediaType.SELFIE
                                    else MediaType.PHOTO
                                }
                            )
                        )
                    }

                    if (sortOrder != SortOrder.RANDOM && items.size >= limit) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return when (sortOrder) {
            SortOrder.RANDOM -> items.shuffled().take(limit)
            SortOrder.BIG_FILE_FIRST -> items.sortedByDescending { it.size }.take(limit)
            SortOrder.NEWEST_FIRST -> items.sortedByDescending { it.dateTaken }.take(limit)
            SortOrder.OLDEST_FIRST -> items.sortedBy { it.dateTaken }.take(limit)
        }
    }

    suspend fun getAllCategoryCounts(excludedIds: Set<Long>): Map<MediaType, Int> = withContext(Dispatchers.IO) {
        val counts = MediaType.entries.associateWith { 0 }.toMutableMap()

        // 1. Scan Images
        val imageProjection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imageProjection.add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            imageProjection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }

        var totalImageCount = 0

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection.toTypedArray(),
                getBaseSelection(),
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val bucketNameCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                } else -1
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                var allImagesCount = 0
                var photoCount = 0
                var screenshotCount = 0
                var selfieCount = 0
                var livePhotoCount = 0

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                    if (idCol >= 0 && excludedIds.contains(id)) continue

                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) ?: "" else ""
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else ""

                    if (path.contains("/Android/", ignoreCase = true) || relPath.startsWith("Android/", ignoreCase = true)) {
                        continue
                    }

                    allImagesCount++

                    val isScreenshot = relPath.contains("screenshot", ignoreCase = true) ||
                            bucketName.contains("screenshot", ignoreCase = true) ||
                            name.contains("screenshot", ignoreCase = true) ||
                            path.contains("screenshot", ignoreCase = true)

                    val isSelfie = relPath.contains("selfie", ignoreCase = true) ||
                            bucketName.contains("selfie", ignoreCase = true) ||
                            name.contains("selfie", ignoreCase = true)

                    val itemUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val isLivePhoto = if (!isScreenshot) {
                        LivePhotoDetector.isLivePhoto(id, path, name, itemUri, contentResolver)
                    } else false

                    when {
                        isScreenshot -> screenshotCount++
                        isLivePhoto -> livePhotoCount++
                        isSelfie -> {
                            selfieCount++
                            photoCount++
                        }
                        else -> photoCount++
                    }
                }

                counts[MediaType.ALL_IMAGES] = allImagesCount
                counts[MediaType.PHOTO] = photoCount
                counts[MediaType.SCREENSHOT] = screenshotCount
                counts[MediaType.SELFIE] = selfieCount
                counts[MediaType.LIVE_PHOTO] = livePhotoCount
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Scan Videos
        val videoProjection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            videoProjection.add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            videoProjection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }

        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection.toTypedArray(),
                getBaseSelection(),
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val bucketNameCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                } else -1
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                var allVideosCount = 0
                var videoCount = 0
                var screenRecordCount = 0

                while (cursor.moveToNext()) {
                    if (idCol >= 0) {
                        val id = cursor.getLong(idCol)
                        if (excludedIds.contains(id)) continue
                    }
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) ?: "" else ""
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else ""

                    if (path.contains("/Android/", ignoreCase = true) || relPath.startsWith("Android/", ignoreCase = true)) {
                        continue
                    }

                    allVideosCount++

                    val isScreenRecording = relPath.contains("screenrecord", ignoreCase = true) ||
                            bucketName.contains("screenrecord", ignoreCase = true) ||
                            name.contains("screenrecord", ignoreCase = true) ||
                            path.contains("screenrecord", ignoreCase = true)

                    if (isScreenRecording) {
                        screenRecordCount++
                    } else {
                        videoCount++
                    }
                }

                counts[MediaType.ALL_VIDEOS] = allVideosCount
                counts[MediaType.VIDEO] = videoCount
                counts[MediaType.SCREEN_RECORDING] = screenRecordCount
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        counts
    }

    suspend fun getRemainingCountByCategory(
        category: MediaType,
        excludedIds: Set<Long>
    ): Int = withContext(Dispatchers.IO) {
        val isVideo = category.isVideo
        val uri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        countByUri(uri, isVideo, category, excludedIds)
    }

    private fun countByUri(
        uri: Uri,
        isVideo: Boolean,
        category: MediaType,
        excludedIds: Set<Long>
    ): Int {
        var count = 0
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }

        try {
            contentResolver.query(uri, projection.toTypedArray(), getBaseSelection(), null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val bucketNameCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                } else -1
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    if (excludedIds.contains(id)) continue

                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) ?: "" else ""
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else ""

                    if (path.contains("/Android/", ignoreCase = true) || relPath.startsWith("Android/", ignoreCase = true)) {
                        continue
                    }

                    val isScreenshot = relPath.contains("screenshot", ignoreCase = true) ||
                            bucketName.contains("screenshot", ignoreCase = true) ||
                            name.contains("screenshot", ignoreCase = true) ||
                            path.contains("screenshot", ignoreCase = true)

                    val isScreenRecording = relPath.contains("screenrecord", ignoreCase = true) ||
                            bucketName.contains("screenrecord", ignoreCase = true) ||
                            name.contains("screenrecord", ignoreCase = true) ||
                            path.contains("screenrecord", ignoreCase = true)

                    val isSelfie = relPath.contains("selfie", ignoreCase = true) ||
                            bucketName.contains("selfie", ignoreCase = true) ||
                            name.contains("selfie", ignoreCase = true)

                    val itemUri = ContentUris.withAppendedId(uri, id)
                    val isLivePhoto = if (!isVideo && !isScreenshot) {
                        LivePhotoDetector.isLivePhoto(id, path, name, itemUri, contentResolver)
                    } else false

                    val matched = when (category) {
                        MediaType.ALL_IMAGES -> true
                        MediaType.ALL_VIDEOS -> true
                        MediaType.PHOTO -> !isScreenshot && !isLivePhoto
                        MediaType.VIDEO -> !isScreenRecording
                        MediaType.SCREENSHOT -> isScreenshot
                        MediaType.SCREEN_RECORDING -> isScreenRecording
                        MediaType.SELFIE -> isSelfie
                        MediaType.LIVE_PHOTO -> isLivePhoto
                    }

                    if (matched) {
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    suspend fun queryAvailablePhotoYears(): List<String> = withContext(Dispatchers.IO) {
        val years = mutableSetOf<String>()
        val cal = java.util.Calendar.getInstance()
        val currentYear = cal.get(java.util.Calendar.YEAR).toString()
        years.add(currentYear)

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.DATE_TAKEN)
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                getBaseSelection(),
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val dateTakenCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                } else -1
                val dateModCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else ""
                    if (path.contains("/Android/", ignoreCase = true) || relPath.startsWith("Android/", ignoreCase = true)) {
                        continue
                    }

                    val dateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val dateMod = if (dateModCol >= 0) cursor.getLong(dateModCol) else 0L
                    val time = if (dateTaken > 0) dateTaken else dateMod * 1000L
                    if (time > 0) {
                        cal.timeInMillis = time
                        val y = cal.get(java.util.Calendar.YEAR)
                        if (y in 2000..2100) {
                            years.add(y.toString())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        years.sortedDescending()
    }

    suspend fun queryPhotosForYear(
        year: String,
        limit: Int = 300
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.DATE_TAKEN)
            projection.add(MediaStore.MediaColumns.BUCKET_ID)
            projection.add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
            projection.add(MediaStore.MediaColumns.ORIENTATION)
        }

        val yearSelection = if (year != "最近") {
            try {
                val y = year.toInt()
                val cal = java.util.Calendar.getInstance()
                cal.set(y, java.util.Calendar.JANUARY, 1, 0, 0, 0)
                val startTs = cal.timeInMillis
                cal.set(y, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
                val endTs = cal.timeInMillis
                val startSec = startTs / 1000L
                val endSec = endTs / 1000L

                val dateClause = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "((${MediaStore.MediaColumns.DATE_TAKEN} BETWEEN $startTs AND $endTs) OR (${MediaStore.MediaColumns.DATE_MODIFIED} BETWEEN $startSec AND $endSec))"
                } else {
                    "(${MediaStore.MediaColumns.DATE_MODIFIED} BETWEEN $startSec AND $endSec)"
                }
                "${getBaseSelection()} AND $dateClause"
            } catch (e: Exception) {
                getBaseSelection()
            }
        } else {
            getBaseSelection()
        }

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                yearSelection,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                val dateTakenCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                } else -1
                val bucketIdCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                } else -1
                val bucketNameCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                } else -1
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1
                val orientationCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
                } else -1

                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else ""

                    if (path.contains("/Android/", ignoreCase = true) || relPath.startsWith("Android/", ignoreCase = true)) {
                        continue
                    }

                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    if (size <= 0) continue

                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                    val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L
                    val dateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else dateModified * 1000L
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) ?: "" else ""
                    val bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) ?: "" else ""
                    val orientation = if (orientationCol >= 0) cursor.getInt(orientationCol) else 0

                    items.add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                            displayName = name,
                            size = size,
                            mimeType = mimeType,
                            dateAdded = dateAdded,
                            dateTaken = dateTaken,
                            width = width,
                            height = height,
                            duration = 0L,
                            orientation = orientation,
                            path = path,
                            bucketId = bucketId,
                            bucketDisplayName = bucketName,
                            mediaType = MediaType.PHOTO
                        )
                    )

                    if (items.size >= limit) break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        items
    }

    private class AlbumAccumulator(
        val bucketName: String,
        val relativePath: String,
        var path: String = "",
        var count: Int = 0,
        var coverUri: Uri? = null
    )

    private data class FolderContentInfo(
        val hasMedia: Boolean = false,
        val hasUserFiles: Boolean = false,
        val isPrivateOrHidden: Boolean = false
    )

    companion object {
        private val FORBIDDEN_ALBUM_NAMES = setOf(
            // Cache & Temp
            "cache", "caches", "temp", "tmp", "temporary", "image_cache", "disk_cache",
            "fresco_cache", "glide_cache", "coil_cache", "picasso_cache",
            "thumb", "thumbs", "thumbnail", "thumbnails",
            // Logs & Crash
            "log", "logs", "crash", "crashes", "dump", "dumps", "trace", "traces",
            // App Data & System
            "data", "database", "databases", "db", "shared_prefs", "files", "storage",
            "internal", "lib", "libs", "bin", "build", "obj", "target", "sys", "system",
            // Assets & Resources
            "res", "assets", "resources", "fonts", "font", "sticker", "stickers",
            "emoji", "emojis", "filter", "filters", "effect", "effects",
            "model", "models", "ai_model", "ai_models",
            // Config & Preferences
            "config", "configs", "configuration", "setting", "settings", "pref", "prefs",
            "preferences", "metadata", "meta",
            // Video drafts & Subtitles
            "draft", "drafts", "project", "projects", "workspace", "subtitles", "subtitle",
            // Trash & Backups
            "trash", "trashed", "recycle", "recycler", "recycled", "bin", "globaltrash",
            "backup", "backups", "lost+found",
            // Ads & Plugins
            "ad", "ads", "advert", "advertisement", "plugin", "plugins", "patch", "patches",
            "sdk", "sdks", "update", "updates", "component", "components",
            // Common App internal subdirs & Non-albums
            "avatar", "avatars", "icon", "icons", "badge", "badges", "splash",
            "webview", "browser", "xwalk", "tencent", "micromsg"
        )

        private val HASH_PATTERN = Regex("^[0-9a-fA-F]{16,}$")
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private val TIMESTAMP_PATTERN = Regex("^[0-9]{10,14}$")

        fun isForbiddenAlbumName(name: String): Boolean {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return true
            if (trimmed.startsWith(".")) return true
            val lower = trimmed.lowercase()
            if (FORBIDDEN_ALBUM_NAMES.contains(lower)) return true
            if (lower.startsWith("cache_") || lower.endsWith("_cache") ||
                lower.startsWith("temp_") || lower.endsWith("_temp") ||
                lower.startsWith("tmp_") || lower.endsWith("_tmp") ||
                lower.startsWith("thumb_") || lower.endsWith("_thumb") ||
                lower.startsWith("thumbs_") || lower.endsWith("_thumbs")
            ) return true

            if (HASH_PATTERN.matches(trimmed) || UUID_PATTERN.matches(trimmed) || TIMESTAMP_PATTERN.matches(trimmed)) {
                return true
            }
            return false
        }
    }

    suspend fun queryAlbums(): List<AlbumItem> = withContext(Dispatchers.IO) {
        val albumsMap = mutableMapOf<String, AlbumAccumulator>()

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.BUCKET_ID)
            projection.add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
            projection.add(MediaStore.MediaColumns.WIDTH)
            projection.add(MediaStore.MediaColumns.HEIGHT)
        }

        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true
        )

        for ((contentUri, isVideo) in uris) {
            try {
                contentResolver.query(
                    contentUri,
                    projection.toTypedArray(),
                    getBaseSelection(),
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val bucketIdCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                    } else -1
                    val bucketNameCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    } else -1
                    val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else -1
                    val widthCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                    } else -1
                    val heightCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                    } else -1

                    while (cursor.moveToNext()) {
                        if (idCol < 0) continue
                        val id = cursor.getLong(idCol)
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                        val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                        val relPath = when {
                            relPathCol >= 0 -> cursor.getString(relPathCol) ?: ""
                            path.isNotEmpty() -> File(path).parent ?: ""
                            else -> ""
                        }

                        // Filter out Android system directories and system trash
                        if (path.contains("/Android/", ignoreCase = true) ||
                            relPath.startsWith("Android/", ignoreCase = true) ||
                            path.contains("/.trash", ignoreCase = true) ||
                            path.contains("/.globalTrash", ignoreCase = true)
                        ) {
                            continue
                        }

                        // Filter out tiny placeholder images, app icons, stickers, emojis
                        if (!isVideo) {
                            val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                            val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                            if (size < 15360L) continue // < 15KB
                            if (width > 0 && height > 0 && (width < 180 || height < 180)) continue
                        } else {
                            if (size < 51200L) continue // < 50KB
                        }

                        val bucketId = when {
                            bucketIdCol >= 0 -> cursor.getString(bucketIdCol) ?: ""
                            path.isNotEmpty() -> File(path).parentFile?.name.hashCode().toString()
                            else -> "default"
                        }
                        if (bucketId.isEmpty()) continue

                        val bucketName = when {
                            bucketNameCol >= 0 -> cursor.getString(bucketNameCol) ?: "相册"
                            path.isNotEmpty() -> File(path).parentFile?.name ?: "相册"
                            else -> "相册"
                        }

                        // Filter out forbidden album names (cache, temp, ad, etc.)
                        if (isForbiddenAlbumName(bucketName)) continue

                        val parentDirPath = if (path.isNotEmpty()) {
                            try { File(path).parent ?: "" } catch (e: Exception) { "" }
                        } else ""

                        val album = albumsMap.getOrPut(bucketId) {
                            AlbumAccumulator(bucketName, relPath, parentDirPath)
                        }
                        album.count++
                        if (album.path.isEmpty() && parentDirPath.isNotEmpty()) {
                            album.path = parentDirPath
                        }
                        if (album.coverUri == null) {
                            album.coverUri = ContentUris.withAppendedId(contentUri, id)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Empty albums scan in external storage directories (STRICTLY Pictures and DCIM)
        val emptyAlbums = scanEmptyDirectories()

        val result = mutableListOf<AlbumItem>()
        // Add existing media albums
        for ((bucketId, acc) in albumsMap) {
            result.add(
                AlbumItem(
                    bucketId = bucketId,
                    bucketName = acc.bucketName,
                    relativePath = acc.relativePath,
                    mediaCount = acc.count,
                    coverUri = acc.coverUri,
                    isEmpty = false,
                    path = acc.path
                )
            )
        }
        // Add empty albums
        result.addAll(emptyAlbums)

        // Real media albums sorted first by photo count descending; empty albums at the end
        result.sortedWith(
            compareBy<AlbumItem> { it.isEmpty }
                .thenByDescending { it.mediaCount }
                .thenBy { it.bucketName.lowercase() }
        )
    }

    private fun scanEmptyDirectories(): List<AlbumItem> {
        val emptyAlbums = mutableListOf<AlbumItem>()
        // STRICTLY scan standard photo album roots: Pictures and DCIM
        val rootPaths = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        )

        val mediaExtensions = setOf(
            "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp",
            "mp4", "mov", "mkv", "3gp", "avi", "flv", "webm", "m4v"
        )

        val allowedSystemArtifacts = setOf(
            "thumbs.db", ".ds_store", "desktop.ini", "albumthumbs.db"
        )

        fun checkFolderContents(dir: File): FolderContentInfo {
            val children = dir.listFiles() ?: return FolderContentInfo()
            var hasMedia = false
            var hasUserFiles = false

            for (file in children) {
                if (file.name.equals(".nomedia", ignoreCase = true)) {
                    // Having .nomedia means it's an app private hidden folder, NOT an album
                    return FolderContentInfo(isPrivateOrHidden = true)
                }
                if (file.isDirectory) {
                    if (file.name.startsWith(".") || isForbiddenAlbumName(file.name)) {
                        continue
                    }
                    val subInfo = checkFolderContents(file)
                    if (subInfo.isPrivateOrHidden) return FolderContentInfo(isPrivateOrHidden = true)
                    if (subInfo.hasMedia) hasMedia = true
                    if (subInfo.hasUserFiles) hasUserFiles = true
                } else {
                    val ext = file.extension.lowercase()
                    val lowerName = file.name.lowercase()
                    if (allowedSystemArtifacts.contains(lowerName)) {
                        continue
                    }
                    if (mediaExtensions.contains(ext)) {
                        hasMedia = true
                    } else if (file.length() > 0) {
                        hasUserFiles = true
                    }
                }
            }
            return FolderContentInfo(
                hasMedia = hasMedia,
                hasUserFiles = hasUserFiles,
                isPrivateOrHidden = false
            )
        }

        fun scanFolderRecursively(folder: File, depth: Int) {
            // Maximum depth of 2 (Pictures/Album or Pictures/Category/Album)
            if (depth > 2) return
            val subFiles = folder.listFiles() ?: return

            for (file in subFiles) {
                if (file.isDirectory) {
                    val name = file.name
                    // Reject hidden folders, Android system folders, or forbidden names
                    if (name.startsWith(".") || name.equals("Android", ignoreCase = true) || isForbiddenAlbumName(name)) {
                        continue
                    }

                    // Protect standard camera capture directories (e.g. DCIM/Camera)
                    val isProtectedCameraDir = (file.parentFile?.name.equals("DCIM", ignoreCase = true) && name.equals("Camera", ignoreCase = true)) ||
                            name.equals("Camera", ignoreCase = true)

                    if (isProtectedCameraDir) {
                        continue
                    }

                    val contentInfo = checkFolderContents(file)
                    if (contentInfo.isPrivateOrHidden) {
                        // Contains .nomedia -> not an album!
                        continue
                    }

                    if (!contentInfo.hasMedia && !contentInfo.hasUserFiles) {
                        // Truly a genuine empty album!
                        emptyAlbums.add(
                            AlbumItem(
                                bucketId = file.absolutePath.hashCode().toString(),
                                bucketName = file.name,
                                relativePath = (file.parentFile?.name ?: "") + "/" + file.name,
                                mediaCount = 0,
                                coverUri = null,
                                isEmpty = true,
                                path = file.absolutePath
                            )
                        )
                    } else if (contentInfo.hasMedia) {
                        // Contains media, inspect deeper subdirectories if depth allows
                        scanFolderRecursively(file, depth + 1)
                    }
                }
            }
        }

        for (dir in rootPaths) {
            if (dir.exists() && dir.isDirectory) {
                scanFolderRecursively(dir, 1)
            }
        }
        return emptyAlbums
    }

    fun notifyDirectoryDeleted(path: String) {
        try {
            val where = "${MediaStore.MediaColumns.DATA} LIKE ?"
            val args = arrayOf("$path%")
            contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, where, args)
            contentResolver.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, where, args)

            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                null,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createDeleteRequest(uris: List<Uri>): IntentSender? = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                pendingIntent.intentSender
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }

    suspend fun deleteLegacy(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (uri in uris) {
            try {
                val rows = contentResolver.delete(uri, null, null)
                if (rows > 0) count++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        count
    }

    fun getStorageStats(): CleanupStats {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes

            CleanupStats(
                totalCleanedCount = 0,
                totalFreedBytes = 0L,
                usedStorageBytes = usedBytes,
                totalStorageBytes = totalBytes
            )
        } catch (e: Exception) {
            CleanupStats()
        }
    }
}
