package com.innotrepid.videoplayer.playback

/**
 * Immutable state exposed by the playback layer to keep controls synchronized
 * with the actual Media3 player rather than with click intent.
 */
data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val audioTracks: List<AudioTrackOption> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val subtitleTracks: List<SubtitleTrackOption> = emptyList(),
    val selectedSubtitleTrackId: String? = null,
    val subtitlesEnabled: Boolean = false,
    val hasExternalSubtitle: Boolean = false,
    val externalSubtitleLabel: String? = null,
    /** Active subtitle lines after delay is applied (for Compose overlay). */
    val subtitleText: String = "",
    /** Signed offset in milliseconds; positive delays appearance. */
    val subtitleDelayMs: Long = 0L,
    val subtitleTextSizeSp: Float = 18f,
    val subtitleBackground: Boolean = true,
    val errorMessage: String? = null,
)

/** One selectable audio track exposed to the player UI. */
data class AudioTrackOption(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
)

/** One selectable subtitle/text track exposed to the player UI. */
data class SubtitleTrackOption(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
)
