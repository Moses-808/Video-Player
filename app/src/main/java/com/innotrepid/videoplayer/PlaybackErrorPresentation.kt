package com.innotrepid.videoplayer

/**
 * Classifies playback failures into user-facing outcomes and recommended actions.
 */
internal enum class PlaybackErrorKind {
    MISSING_MEDIA,
    PERMISSION,
    UNSUPPORTED_FORMAT,
    DECODER,
    SOURCE,
    NETWORK,
    UNKNOWN,
}

/**
 * Recommended primary action for a classified playback error.
 */
internal enum class PlaybackErrorAction {
    /** Re-prepare the current media item. */
    RETRY,

    /** Remove the broken library entry and leave the player. */
    REMOVE_FROM_LIBRARY,

    /** Leave the player and return to the library. */
    BACK_TO_LIBRARY,
}

internal data class PlaybackErrorPresentation(
    val kind: PlaybackErrorKind,
    val title: String,
    val explanation: String,
    val primaryAction: PlaybackErrorAction,
    val primaryLabel: String,
) {
    val isRetryable: Boolean
        get() = primaryAction == PlaybackErrorAction.RETRY

    val canRemove: Boolean
        get() = primaryAction == PlaybackErrorAction.REMOVE_FROM_LIBRARY
}

internal fun presentPlaybackError(
    errorCodeName: String,
    message: String?,
): PlaybackErrorPresentation {
    val code = errorCodeName.uppercase()
    val detail = message.orEmpty()
    val lower = detail.lowercase()

    return when {
        code.contains("IO_FILE_NOT_FOUND") ||
            code.contains("FILE_NOT_FOUND") ||
            lower.contains("no such file") ||
            lower.contains("file not found") ->
            PlaybackErrorPresentation(
                kind = PlaybackErrorKind.MISSING_MEDIA,
                title = "Video unavailable",
                explanation = "This video can no longer be opened. It may have been deleted or moved.",
                primaryAction = PlaybackErrorAction.REMOVE_FROM_LIBRARY,
                primaryLabel = "Remove from library",
            )

        code.contains("PERMISSION") ||
            code.contains("SECURITY") ||
            lower.contains("permission") ||
            lower.contains("access denied") ->
            PlaybackErrorPresentation(
                kind = PlaybackErrorKind.PERMISSION,
                title = "Permission needed",
                explanation = "Visonate no longer has access to this video. Re-import the file or grant access again.",
                primaryAction = PlaybackErrorAction.BACK_TO_LIBRARY,
                primaryLabel = "Back to library",
            )

        code.contains("DECODER") ||
            code.contains("CODEC") ||
            code.contains("RENDERER") ||
            lower.contains("decoder") ||
            lower.contains("codec") ->
            PlaybackErrorPresentation(
                kind = PlaybackErrorKind.DECODER,
                title = "Decoder failure",
                explanation = "This device could not decode the video. The file may use an unsupported codec or be corrupted.",
                primaryAction = PlaybackErrorAction.BACK_TO_LIBRARY,
                primaryLabel = "Back to library",
            )

        code.contains("UNSUPPORTED") || lower.contains("unsupported") ->
            PlaybackErrorPresentation(
                kind = PlaybackErrorKind.UNSUPPORTED_FORMAT,
                title = "Format not supported",
                explanation = "This video uses a format this device could not play.",
                primaryAction = PlaybackErrorAction.BACK_TO_LIBRARY,
                primaryLabel = "Back to library",
            )

        code.contains("IO_NETWORK") ||
            code.contains("NETWORK") ||
            lower.contains("network") ||
            lower.contains("http") ->
            PlaybackErrorPresentation(
                kind = PlaybackErrorKind.NETWORK,
                title = "Media source unavailable",
                explanation = "The video source could not be reached. Check the source and try again.",
                primaryAction = PlaybackErrorAction.RETRY,
                primaryLabel = "Retry",
            )

        code.contains("SOURCE") || code.contains("IO_") ->
            PlaybackErrorPresentation(
                kind = PlaybackErrorKind.SOURCE,
                title = "Video could not be opened",
                explanation = "The media source could not be read. The file may be incomplete, corrupted, or unavailable.",
                primaryAction = PlaybackErrorAction.RETRY,
                primaryLabel = "Retry",
            )

        else ->
            PlaybackErrorPresentation(
                kind = PlaybackErrorKind.UNKNOWN,
                title = "Playback stopped",
                explanation = "Visonate could not continue playback. Try again, or return to your library.",
                primaryAction = PlaybackErrorAction.RETRY,
                primaryLabel = "Retry",
            )
    }
}
