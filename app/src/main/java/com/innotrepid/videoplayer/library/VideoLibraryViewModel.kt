package com.innotrepid.videoplayer.library

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val library = VideoLibrary(application)
    private val scanner = MediaStoreVideoScanner(application.contentResolver)
    private val _videos = MutableStateFlow(library.all())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    fun scanDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { scanner.scan(library) }
            _videos.value = library.all()
        }
    }

    fun add(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val title = queryDisplayName(resolver, uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "Untitled video"
            val id = uri.toString().hashCode().toString(16)
            val existing = library.find(id)
            library.upsert((existing ?: VideoItem(id = id, uri = uri, title = title)).copy(uri = uri, title = title))
            _videos.value = library.all()
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            library.toggleFavorite(id)
            _videos.value = library.all()
        }
    }

    /**
     * Playback progress is a durability checkpoint. It is intentionally persisted
     * synchronously so an Activity/process shutdown cannot cancel the write before
     * the latest known position reaches disk.
     */
    fun updateProgress(id: String, positionMs: Long, durationMs: Long) {
        library.updateProgress(id, positionMs, durationMs)
        _videos.value = library.all()
    }

    fun markCompleted(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            library.markCompleted(id)
            _videos.value = library.all()
        }
    }

    fun remove(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            library.remove(id)
            _videos.value = library.all()
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
