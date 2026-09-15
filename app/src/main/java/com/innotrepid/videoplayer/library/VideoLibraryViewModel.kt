package com.innotrepid.videoplayer.library

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
            
            // Re-query within the IO thread to check for concurrent additions.
            // This reduces (but doesn't eliminate) the race window.
            val existingIds = library.idsForUri(uri)
            var existing = existingIds.firstNotNullOfOrNull(library::find)
            
            val id = if (existing != null) {
                existing.id
            } else {
                // Generate a new ID if no existing record was found
                uri.toString().hashCode().toString(16)
            }
            
            val importedFolder = existing?.relativePath ?: queryDocumentFolder(resolver, uri)
            val item = (existing ?: VideoItem(id = id, uri = uri, title = title)).copy(
                uri = uri,
                title = title,
                relativePath = importedFolder
            )
            
            // Upsert the item and clean up any stale duplicates for this URI
            library.upsert(item)
            existingIds.filter { it != id }.forEach(library::remove)
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
     * Async progress checkpoint (safe for periodic / non-critical saves).
     * Prefer [updateProgressBlocking] for lifecycle events that can precede process death.
     */
    fun updateProgress(id: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            library.updateProgress(id, positionMs, durationMs)
            _videos.value = library.all()
        }
    }

    /**
     * Blocking progress write used on critical paths (ON_PAUSE / ON_STOP, player close,
     * composition dispose). Ensures the JSON is flushed to disk before the process can
     * be killed, so last watch position survives a full app close.
     */
    fun updateProgressBlocking(id: String, positionMs: Long, durationMs: Long) {
        runBlocking(Dispatchers.IO) {
            library.updateProgress(id, positionMs, durationMs)
        }
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
            if (cursor.moveToFirst()) {
                // Safely retrieve the display name column, guarding against null or missing columns.
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0 && !cursor.isNull(columnIndex)) {
                    cursor.getString(columnIndex)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }.getOrNull()

    private fun queryDocumentFolder(resolver: ContentResolver, uri: Uri): String? = runCatching {
        if (!DocumentsContract.isDocumentUri(getApplication(), uri)) return@runCatching null
        val documentId = DocumentsContract.getDocumentId(uri)
        val path = documentId.substringAfter(':', documentId)
            .replace('\\', '/')
            .trim('/')
        path.substringBeforeLast('/', missingDelimiterValue = "")
            .trim('/')
            .takeIf { it.isNotBlank() }
    }.getOrNull()
}
