package com.innotrepid.videoplayer.library

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Discovers videos already present on the device without copying their bytes into the app. */
class MediaStoreVideoScanner(private val resolver: ContentResolver) {
    data class Result(val discovered: Int, val removed: Int)

    fun scan(library: VideoLibrary): Result {
        val discoveredIds = mutableSetOf<String>()
        val discoveredItems = mutableListOf<VideoItem>()
        val duplicateIds = mutableListOf<String>()
        var discovered = 0
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Video.Media.RELATIVE_PATH)
        }.toTypedArray()

        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val id = "media:$mediaId"
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
                val title = cursor.getString(nameIndex).orEmpty().ifBlank { "Untitled video" }
                val duration = cursor.getLong(durationIndex).coerceAtLeast(0L)
                val addedAt = cursor.getLong(addedIndex).coerceAtLeast(0L) * 1000L
                val modifiedAt = cursor.getLong(modifiedIndex).coerceAtLeast(0L) * 1000L
                val size = cursor.getLong(sizeIndex).coerceAtLeast(0L)
                val mime = cursor.getString(mimeIndex)
                val relativePath = if (relativePathIndex >= 0) cursor.getString(relativePathIndex) else null
                val existing = library.find(id)
                val duplicateForUri = library.idsForUri(uri).filter { it != id }
                val base = existing ?: duplicateForUri.firstOrNull()?.let(library::find) ?: VideoItem(
                    id = id,
                    uri = uri,
                    title = title,
                    addedAtMs = addedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
                discoveredItems += base.copy(
                    id = id,
                    uri = uri,
                    title = title,
                    durationMs = duration,
                    sizeBytes = size,
                    dateModifiedMs = modifiedAt,
                    relativePath = relativePath,
                    mimeType = mime,
                    addedAtMs = existing?.addedAtMs ?: base.addedAtMs.takeIf { it > 0L } ?: (addedAt.takeIf { it > 0L } ?: System.currentTimeMillis())
                )
                duplicateIds += duplicateForUri
                discoveredIds += id
                discovered++
            }
        }

        // A device scan used to rewrite the JSON file once for every discovered video,
        // then once again for every duplicate/stale item. Batch the reconciliation so
        // large libraries remain responsive and startup does not amplify to O(n²) I/O.
        library.upsertAll(discoveredItems)
        library.removeAll(duplicateIds.distinct())

        val staleIds = library.all()
            .filter { it.id.startsWith("media:") && it.id !in discoveredIds }
            .map { it.id }
        val missingImportedIds = library.all()
            .filter { !it.id.startsWith("media:") && !mediaIsReadable(it.uri) }
            .map { it.id }
        val removals = (staleIds + missingImportedIds).distinct()
        library.removeAll(removals)

        return Result(discovered = discovered, removed = duplicateIds.distinct().size + removals.size)
    }

    private fun mediaIsReadable(uri: Uri): Boolean = runCatching {
        resolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)
}
