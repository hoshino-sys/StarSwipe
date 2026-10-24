package com.starswipe.app.feature.album

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.starswipe.app.StarSwipeApplication
import com.starswipe.app.core.mediastore.MediaRepository
import com.starswipe.app.core.model.AlbumItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AlbumUiState(
    val isLoading: Boolean = true,
    val isCleaning: Boolean = false,
    val albums: List<AlbumItem> = emptyList(),
    val emptyAlbums: List<AlbumItem> = emptyList(),
    val emptyAlbumCount: Int = 0,
    val showCleanConfirmDialog: Boolean = false,
    val selectedAlbum: AlbumItem? = null,
    val showActionMenu: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val userMessage: String? = null
)

class AlbumViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    init {
        loadAlbums()
    }

    fun loadAlbums() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val albums = repository.getAlbums()
            val emptyAlbums = albums.filter { it.isEmpty }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    albums = albums,
                    emptyAlbums = emptyAlbums,
                    emptyAlbumCount = emptyAlbums.size
                )
            }
        }
    }

    fun requestCleanEmptyAlbums() {
        if (_uiState.value.emptyAlbumCount == 0) return
        _uiState.update { it.copy(showCleanConfirmDialog = true) }
    }

    fun dismissCleanConfirmDialog() {
        _uiState.update { it.copy(showCleanConfirmDialog = false) }
    }

    fun confirmCleanEmptyAlbums(context: Context) {
        _uiState.update { it.copy(showCleanConfirmDialog = false) }
        cleanEmptyAlbums(context)
    }

    fun cleanEmptyAlbums(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            _uiState.update { it.copy(showPermissionDialog = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCleaning = true) }
            val count = repository.deleteEmptyAlbums()
            loadAlbums()
            _uiState.update {
                it.copy(
                    isCleaning = false,
                    userMessage = if (count > 0) "已成功清理 $count 个空相簿" else "未发现可清理的空相簿"
                )
            }
        }
    }

    fun deleteSelectedAlbum(context: Context) {
        val album = _uiState.value.selectedAlbum ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            _uiState.update { it.copy(showPermissionDialog = true, showActionMenu = false) }
            return
        }
        viewModelScope.launch {
            val success = repository.deleteAlbum(album)
            _uiState.update {
                it.copy(
                    showActionMenu = false,
                    selectedAlbum = null,
                    userMessage = if (success) "已删除相簿「${album.bucketName}」" else "删除相簿失败"
                )
            }
            loadAlbums()
        }
    }

    fun dismissPermissionDialog() {
        _uiState.update { it.copy(showPermissionDialog = false) }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun showCreateDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateDialog = show) }
    }

    fun createNewAlbum(albumName: String) {
        if (albumName.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val newDir = File(picturesDir, albumName.trim())
                if (!newDir.exists()) {
                    newDir.mkdirs()
                }
            }
            _uiState.update { it.copy(showCreateDialog = false) }
            loadAlbums()
        }
    }

    fun selectAlbumForAction(album: AlbumItem?) {
        _uiState.update {
            it.copy(
                selectedAlbum = album,
                showActionMenu = album != null
            )
        }
    }

    fun dismissActionMenu() {
        _uiState.update { it.copy(showActionMenu = false, selectedAlbum = null) }
    }

    fun showRenameDialog(show: Boolean) {
        _uiState.update { it.copy(showRenameDialog = show, showActionMenu = false) }
    }

    fun renameAlbum(newName: String) {
        val album = _uiState.value.selectedAlbum ?: return
        if (newName.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val oldDir = File(picturesDir, album.bucketName)
                val newDir = File(picturesDir, newName.trim())
                if (oldDir.exists() && !newDir.exists()) {
                    oldDir.renameTo(newDir)
                }
            }
            _uiState.update { it.copy(showRenameDialog = false, selectedAlbum = null) }
            loadAlbums()
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = (context.applicationContext as? StarSwipeApplication) ?: StarSwipeApplication.instance
                    return AlbumViewModel(repository = app.mediaRepository) as T
                }
            }
    }
}
