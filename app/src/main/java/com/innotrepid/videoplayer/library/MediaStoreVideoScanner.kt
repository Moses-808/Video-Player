package com.innotrepid.videoplayer.library

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore

/**
 * Discovers videos already present on the device without copying their bytes into the app.
 */
class MediaStoreVideoScanner(private val resolver: ContentResolver) {
    data class Result(
        val discovered: Int,
        val removed: Int
    )

    fun scan(library: VideoLibrary): Result {
        val discoveredIds = mutableSetOf<String>()
        var discovered = 0
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
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
            val relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            } else -1

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

                library.upsert(
                    (existing ?: VideoItem(
                        id = id,
                        uri = uri,
                        title = title,
                        addedAtMs = addedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )).copy(
                        uri = uri,
                        title = title,
                        durationMs = duration,
                        sizeBytes = size,
                        dateModifiedMs = modifiedAt,
                        relativePath = relativePath,
                        mimeType = mime,
                        addedAtMs = existing?.addedAtMs ?: (addedAt.takeIf { it > 0L } ?: System.currentTimeMillis())
                    )
                )
                discoveredIds += id
                discovered++
            }
        }

        val staleIds = library.all()
            .map { it.id }
            .filter { it.startsWith("media:") && it !in discoveredIds }
        staleIds.forEach(library::remove)

        return Result(discovered = discovered, removed = staleIds.size)
    }
}
