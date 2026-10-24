package com.starswipe.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.starswipe.app.core.model.SortOrder
import com.starswipe.app.core.model.SwipeDirection
import com.starswipe.app.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val ROUND_SIZE = intPreferencesKey("round_size")
        val SWIPE_DIRECTION = stringPreferencesKey("swipe_direction")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val AUTO_PLAY_VIDEO = booleanPreferencesKey("auto_play_video")
        val VIDEO_SOUND_ENABLED = booleanPreferencesKey("video_sound_enabled")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
        val roundSize = preferences[PreferencesKeys.ROUND_SIZE] ?: 20
        val swipeDirString = preferences[PreferencesKeys.SWIPE_DIRECTION] ?: SwipeDirection.LEFT_DELETE_RIGHT_KEEP.name
        val swipeDirection = try {
            SwipeDirection.valueOf(swipeDirString)
        } catch (e: Exception) {
            SwipeDirection.LEFT_DELETE_RIGHT_KEEP
        }
        val sortOrderString = preferences[PreferencesKeys.SORT_ORDER] ?: SortOrder.RANDOM.name
        val sortOrder = try {
            SortOrder.valueOf(sortOrderString)
        } catch (e: Exception) {
            SortOrder.RANDOM
        }
        val autoPlayVideo = preferences[PreferencesKeys.AUTO_PLAY_VIDEO] ?: true
        val videoSoundEnabled = preferences[PreferencesKeys.VIDEO_SOUND_ENABLED] ?: false

        UserPreferences(
            roundSize = roundSize,
            swipeDirection = swipeDirection,
            sortOrder = sortOrder,
            autoPlayVideo = autoPlayVideo,
            videoSoundEnabled = videoSoundEnabled
        )
    }

    suspend fun updateRoundSize(roundSize: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ROUND_SIZE] = roundSize.coerceIn(10, 300)
        }
    }

    suspend fun updateSwipeDirection(direction: SwipeDirection) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SWIPE_DIRECTION] = direction.name
        }
    }

    suspend fun updateSortOrder(sortOrder: SortOrder) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_ORDER] = sortOrder.name
        }
    }

    suspend fun updateAutoPlayVideo(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_PLAY_VIDEO] = enabled
        }
    }

    suspend fun updateVideoSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIDEO_SOUND_ENABLED] = enabled
        }
    }
}
