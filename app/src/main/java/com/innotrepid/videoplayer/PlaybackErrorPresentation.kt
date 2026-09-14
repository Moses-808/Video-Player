package com.innotrepid.videoplayer

import androidx.media3.common.PlaybackException

internal enum class PlaybackErrorKind {
    MISSING_MEDIA,
    PERMISSION,
    UNSUPPORTED_FORMAT,
    DECODER,
    SOURCE,
    NETWORK,
    UNKNOWN
}

internal data class PlaybackErrorPresentation(
    val kind: PlaybackErrorKind,
    val title: String,
    val explanation: String,
    val actionLabel: String
)

internal fun presentPlaybackError(errorCodeName: String, message: String?): PlaybackErrorPresentation {
    val code = errorCodeName.uppercase()
    val detail = message.orEmpty()
    val lower = detail.lowercase()
    return when {
        code.contains("IO_FILE_NOT_FOUND") || code.contains("FILE_NOT_FOUND") || lower.contains("no such file") || lower.contains("file not found") ->
            PlaybackErrorPresentation(PlaybackErrorKind.MISSING_MEDIA, "Video unavailable", "This video can no longer be opened. It may have been deleted or moved.", "Remove from library")
        code.contains("PERMISSION") || code.contains("SECURITY") || lower.contains("permission") || lower.contains("access denied") ->
            PlaybackErrorPresentation(PlaybackErrorKind.PERMISSION, "Permission needed", "Visonate no longer has access to this video. Re-import the file or grant access again.", "Back to library")
        code.contains("DECODER") || code.contains("CODEC") || code.contains("RENDERER") || lower.contains("decoder") || lower.contains("codec") || lower.contains("unsupported") ->
            PlaybackErrorPresentation(PlaybackErrorKind.UNSUPPORTED_FORMAT, "Format not supported", "This video uses a format or codec this device could not decode.", "Back to library")
        code.contains("IO_NETWORK") || code.contains("NETWORK") || lower.contains("network") || lower.contains("http") ->
            PlaybackErrorPresentation(PlaybackErrorKind.NETWORK, "Media source unavailable", "The video source could not be reached. Check the source and try again.", "Retry")
        code.contains("SOURCE") || code.contains("IO_") ->
            PlaybackErrorPresentation(PlaybackErrorKind.SOURCE, "Video could not be opened", "The media source could not be read. The file may be incomplete, corrupted, or unavailable.", "Retry")
        else ->
            PlaybackErrorPresentation(PlaybackErrorKind.UNKNOWN, "Playback stopped", "Visonate could not continue playback. Try again, or return to your library.", "Retry")
    }
}
