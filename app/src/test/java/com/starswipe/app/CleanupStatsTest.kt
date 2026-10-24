package com.starswipe.app

import com.starswipe.app.core.model.CleanupStats
import com.starswipe.app.core.model.SortOrder
import com.starswipe.app.core.model.SwipeDirection
import com.starswipe.app.core.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupStatsTest {

    @Test
    fun testFormatBytes() {
        assertEquals("500 KB", CleanupStats.formatBytes(500L * 1024L))
        assertEquals("15.5 MB", CleanupStats.formatBytes(15L * 1024L * 1024L + 512L * 1024L))
        assertEquals("2.50 GB", CleanupStats.formatBytes(2560L * 1024L * 1024L))
        assertEquals("0 KB", CleanupStats.formatBytes(0L))
    }

    @Test
    fun testUsagePercentage() {
        val stats = CleanupStats(
            totalCleanedCount = 10,
            totalFreedBytes = 100_000L,
            usedStorageBytes = 50_000_000L,
            totalStorageBytes = 100_000_000L
        )
        assertEquals(0.5f, stats.usagePercentage, 0.001f)
    }

    @Test
    fun testZeroStorageGracefulHandling() {
        val stats = CleanupStats(
            totalCleanedCount = 0,
            totalFreedBytes = 0L,
            usedStorageBytes = 0L,
            totalStorageBytes = 0L
        )
        assertEquals(0f, stats.usagePercentage, 0.0001f)
        assertEquals("0 KB", stats.formattedFreedBytes)
    }

    @Test
    fun testUserPreferencesDefaults() {
        val prefs = UserPreferences()
        assertEquals(20, prefs.roundSize)
        assertEquals(SwipeDirection.LEFT_DELETE_RIGHT_KEEP, prefs.swipeDirection)
        assertEquals(SortOrder.RANDOM, prefs.sortOrder)
        assertTrue(prefs.autoPlayVideo)
        assertFalse(prefs.videoSoundEnabled)
    }
}
