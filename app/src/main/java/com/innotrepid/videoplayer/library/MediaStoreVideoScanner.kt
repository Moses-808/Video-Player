package com.innotrepid.videoplayer.library

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore

/**
 * Discovers videos already present on the device without copying their bytes into the app.
 * MediaStore is the Android-safe equivalent of a lightweight VLC-style device index.
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
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val id = "media:$mediaId"
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
                val title = cursor.getString(nameIndex).orEmpty().ifBlank { "Untitled video" }
                val duration = cursor.getLong(durationIndex).coerceAtLeast(0L)
                val addedAt = cursor.getLong(addedIndex).coerceAtLeast(0L) * 1000L
                val existing = library.find(id)
                library.upsert(
                    (existing ?: VideoItem(id = id, uri = uri, title = title, addedAtMs = addedAt))
                        .copy(uri = uri, title = title, durationMs = duration, addedAtMs = addedAt.takeIf { it > 0L } ?: (existing?.addedAtMs ?: System.currentTimeMillis()))
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
