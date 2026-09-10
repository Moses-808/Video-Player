package com.innotrepid.videoplayer.library

import android.net.Uri

data class VideoItem(
    val id: String,
    val uri: Uri,
    val title: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val dateModifiedMs: Long = 0L,
    val relativePath: String? = null,
    val mimeType: String? = null,
    val lastPositionMs: Long = 0L,
    val lastPlayedAtMs: Long = 0L,
    val addedAtMs: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (durationMs > 0L) (lastPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val isResumeable: Boolean
        get() = lastPositionMs > 5_000L && durationMs > 0L && lastPositionMs < durationMs * 0.95f
}
