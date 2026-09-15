package com.innotrepid.videoplayer.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.MediaMetadataRetriever

/** Loads small representative frames without keeping video bytes in the app. */
object VideoThumbnailLoader {
    private const val MAX_CACHE_ENTRIES = 64
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > MAX_CACHE_ENTRIES
    }

    suspend fun load(context: Context, uri: Uri, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri|$width|$height"
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(width, height), null)
            } else {
                loadLegacy(context, uri)
            }
        }.getOrNull()
        if (bitmap != null) synchronized(cache) { cache[key] = bitmap }
        bitmap
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    @WorkerThread
    private fun loadLegacy(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            retriever.release()
        }
    }
}
