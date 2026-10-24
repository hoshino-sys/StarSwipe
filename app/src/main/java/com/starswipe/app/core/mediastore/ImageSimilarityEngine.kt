package com.starswipe.app.core.mediastore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.starswipe.app.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

@Immutable
data class SimilarGroup(
    val id: String,
    val bestItem: MediaItem,
    val similarItems: List<MediaItem>,
    val selectedForDelete: Set<Long> // media IDs chosen for deletion
) {
    val totalReleasableBytes: Long
        get() = similarItems.filter { selectedForDelete.contains(it.id) }.sumOf { it.size }
}

class ImageSimilarityEngine(private val context: Context) {

    // Persistent disk hash cache: Media ID -> (DateModified, 64-bit dHash)
    private val hashCache = ConcurrentHashMap<Long, Pair<Long, Long>>()
    private val cacheFile: File by lazy {
        File(context.cacheDir, "similar_dhash_v2.bin")
    }

    init {
        loadHashCache()
    }

    private fun loadHashCache() {
        try {
            if (cacheFile.exists()) {
                DataInputStream(FileInputStream(cacheFile)).use { dis ->
                    val size = dis.readInt()
                    for (i in 0 until size) {
                        val id = dis.readLong()
                        val modified = dis.readLong()
                        val hash = dis.readLong()
                        hashCache[id] = Pair(modified, hash)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveHashCache() {
        try {
            val tempFile = File(context.cacheDir, "similar_dhash_v2.tmp")
            DataOutputStream(FileOutputStream(tempFile)).use { dos ->
                dos.writeInt(hashCache.size)
                for ((id, pair) in hashCache) {
                    dos.writeLong(id)
                    dos.writeLong(pair.first)
                    dos.writeLong(pair.second)
                }
            }
            if (tempFile.exists()) {
                if (cacheFile.exists()) cacheFile.delete()
                tempFile.renameTo(cacheFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun findSimilarGroups(
        items: List<MediaItem>,
        onProgress: (Float) -> Unit
    ): List<SimilarGroup> = withContext(Dispatchers.Default) {
        if (items.size < 2) return@withContext emptyList()

        // 1. Sort items chronologically
        val sorted = items.sortedWith(
            compareBy<MediaItem> { if (it.dateTaken > 0) it.dateTaken else it.dateAdded * 1000L }
                .thenBy { it.id }
        )

        val totalCount = sorted.size
        var cacheUpdated = false

        // 2. Pre-calculate or retrieve hashes for all items with progress reporting
        val itemHashes = mutableMapOf<Long, Long>()
        sorted.forEachIndexed { index, item ->
            val cached = hashCache[item.id]
            val hash = if (cached != null && cached.first == item.dateAdded) {
                cached.second
            } else {
                val computed = calculateDHash(item.uri)
                if (computed != null) {
                    hashCache[item.id] = Pair(item.dateAdded, computed)
                    cacheUpdated = true
                }
                computed
            }

            if (hash != null) {
                itemHashes[item.id] = hash
            }

            if (index % 10 == 0 || index == totalCount - 1) {
                onProgress(0.1f + (index.toFloat() / totalCount.toFloat()) * 0.6f)
            }
        }

        if (cacheUpdated) {
            saveHashCache()
        }

        // 3. Time-window Sliding Clustering (Disjoint Set Union)
        // Group photos taken within 180 seconds that match visual or burst criteria
        val parent = mutableMapOf<Long, Long>()
        fun find(id: Long): Long {
            var curr = id
            while (curr != (parent[curr] ?: curr)) {
                parent[curr] = parent[parent[curr]!!] ?: parent[curr]!!
                curr = parent[curr]!!
            }
            return curr
        }

        fun union(id1: Long, id2: Long) {
            val root1 = find(id1)
            val root2 = find(id2)
            if (root1 != root2) {
                parent[root1] = root2
            }
        }

        for (item in sorted) {
            parent[item.id] = item.id
        }

        val TIME_WINDOW_MS = 180_000L // 3-minute sliding window

        for (i in sorted.indices) {
            val itemA = sorted[i]
            val timeA = if (itemA.dateTaken > 0) itemA.dateTaken else itemA.dateAdded * 1000L
            val hashA = itemHashes[itemA.id]
            val aspectA = if (itemA.height > 0) itemA.width.toFloat() / itemA.height.toFloat() else 1f

            for (j in (i + 1) until sorted.size) {
                val itemB = sorted[j]
                val timeB = if (itemB.dateTaken > 0) itemB.dateTaken else itemB.dateAdded * 1000L
                val timeDiff = abs(timeB - timeA)

                // If outside the sliding time window, subsequent photos will be even further away
                if (timeDiff > TIME_WINDOW_MS) break

                val aspectB = if (itemB.height > 0) itemB.width.toFloat() / itemB.height.toFloat() else 1f
                val aspectDiff = abs(aspectA - aspectB)

                // Allow slight framing or orientation variance (within 20% aspect difference)
                val aspectCompatible = aspectDiff <= 0.20f

                val hashB = itemHashes[itemB.id]

                var isSimilar = false

                // Criterion A: High-speed burst shot detection (< 5s apart, exact/near dimensions)
                if (timeDiff <= 5_000L) {
                    if (itemA.width == itemB.width && itemA.height == itemB.height) {
                        isSimilar = true
                    } else if (hashA != null && hashB != null && hammingDistance(hashA, hashB) <= 20) {
                        isSimilar = true
                    }
                }

                // Criterion B: Short-window similar pose/photo (5s ~ 30s)
                if (!isSimilar && timeDiff <= 30_000L && aspectCompatible) {
                    if (hashA != null && hashB != null && hammingDistance(hashA, hashB) <= 18) {
                        isSimilar = true
                    }
                }

                // Criterion C: Medium-window similar scene (30s ~ 180s)
                if (!isSimilar && timeDiff <= TIME_WINDOW_MS && aspectCompatible) {
                    if (hashA != null && hashB != null && hammingDistance(hashA, hashB) <= 14) {
                        isSimilar = true
                    }
                }

                if (isSimilar) {
                    union(itemA.id, itemB.id)
                }
            }
        }

        onProgress(0.85f)

        // 4. Collect connected components into groups
        val clusters = mutableMapOf<Long, MutableList<MediaItem>>()
        for (item in sorted) {
            val root = find(item.id)
            clusters.getOrPut(root) { mutableListOf() }.add(item)
        }

        // Only retain groups with at least 2 photos
        val validClusters = clusters.values.filter { it.size >= 2 }

        val resultGroups = validClusters.map { groupItems ->
            // Determine best item:
            // 1. Highest resolution (width * height)
            // 2. Highest file size
            // 3. Newest
            val best = groupItems.maxWithOrNull(
                compareBy<MediaItem> { it.width * it.height }
                    .thenBy { it.size }
                    .thenBy { it.dateTaken }
            ) ?: groupItems.first()

            val others = groupItems.filterNot { it.id == best.id }
            SimilarGroup(
                id = "group_${best.id}",
                bestItem = best,
                similarItems = others,
                selectedForDelete = others.map { it.id }.toSet() // default select others for deletion
            )
        }

        onProgress(1.0f)
        resultGroups
    }

    private fun calculateDHash(uri: Uri): Long? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 16 // Downscale significantly for fast 9x8 dHash
                    inPreferredConfig = Bitmap.Config.RGB_565 // 50% less RAM, faster decode
                }
                val bitmap = BitmapFactory.decodeStream(stream, null, options) ?: return null
                val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
                if (scaled !== bitmap) {
                    bitmap.recycle()
                }

                var hash = 0L
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        val leftPixel = scaled.getPixel(x, y)
                        val rightPixel = scaled.getPixel(x + 1, y)

                        val leftGray = (leftPixel shr 16 and 0xFF) * 0.3 + (leftPixel shr 8 and 0xFF) * 0.59 + (leftPixel and 0xFF) * 0.11
                        val rightGray = (rightPixel shr 16 and 0xFF) * 0.3 + (rightPixel shr 8 and 0xFF) * 0.59 + (rightPixel and 0xFF) * 0.11

                        if (leftGray > rightGray) {
                            hash = hash or (1L shl (y * 8 + x))
                        }
                    }
                }
                scaled.recycle()
                hash
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }
}
