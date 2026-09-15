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
    val errorMessage: String? = null,
)

/** One selectable audio track exposed to the player UI. */
data class AudioTrackOption(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
)
