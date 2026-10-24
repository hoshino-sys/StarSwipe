package com.starswipe.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.starswipe.app.core.database.StarSwipeDatabase
import com.starswipe.app.core.datastore.UserPreferencesRepository
import com.starswipe.app.core.mediastore.ExifMetadataReader
import com.starswipe.app.core.mediastore.FootprintEngine
import com.starswipe.app.core.mediastore.ImageSimilarityEngine
import com.starswipe.app.core.mediastore.LocationGeocoder
import com.starswipe.app.core.mediastore.MediaRepository
import com.starswipe.app.core.mediastore.MediaRepositoryImpl
import com.starswipe.app.core.mediastore.MediaStoreDataSource

class StarSwipeApplication : Application(), ImageLoaderFactory {

    lateinit var database: StarSwipeDatabase
        private set
    lateinit var dataSource: MediaStoreDataSource
        private set
    lateinit var preferencesRepository: UserPreferencesRepository
        private set
    lateinit var mediaRepository: MediaRepository
        private set
    lateinit var exifReader: ExifMetadataReader
        private set
    lateinit var geocoder: LocationGeocoder
        private set
    lateinit var similarityEngine: ImageSimilarityEngine
        private set
    lateinit var footprintEngine: FootprintEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Install Crash Handler immediately to capture any startup or runtime crashes
        com.starswipe.app.crash.CrashHandler.init(this)

        database = StarSwipeDatabase.getInstance(this)
        dataSource = MediaStoreDataSource(this)
        preferencesRepository = UserPreferencesRepository(this)
        mediaRepository = MediaRepositoryImpl(
            dataSource = dataSource,
            statusDao = database.mediaStatusDao(),
            cleanupDao = database.cleanupRecordDao(),
            preferencesRepository = preferencesRepository
        )
        geocoder = LocationGeocoder(this)
        exifReader = ExifMetadataReader(this)
        similarityEngine = ImageSimilarityEngine(this)
        footprintEngine = FootprintEngine(this, geocoder)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB
                    .build()
            }
            .allowHardware(true)
            .bitmapConfig(android.graphics.Bitmap.Config.HARDWARE)
            .crossfade(false)
            .build()
    }

    companion object {
        lateinit var instance: StarSwipeApplication
            private set
    }
}
