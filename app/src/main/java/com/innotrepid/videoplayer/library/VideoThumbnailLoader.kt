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

object VideoThumbnailLoader {
    suspend fun load(context: Context, uri: Uri, width: Int, height: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(width, height), null)
                } else {
                    loadLegacy(context, uri)
                }
            }.getOrNull()
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
