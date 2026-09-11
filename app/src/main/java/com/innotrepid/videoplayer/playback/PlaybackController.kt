package com.innotrepid.videoplayer.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small, testable façade around ExoPlayer.
 *
 * UI actions are routed through this class, while the state is rebuilt from
 * Player callbacks. This prevents play/pause icons from drifting away from
 * the real playback state.
 */
class PlaybackController(
    private val player: ExoPlayer,
) {
    private val mutableState = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = mutableState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
        override fun onPlaybackStateChanged(playbackState: Int) = publish()
        override fun onEvents(player: Player, events: Player.Events) = publish()
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = publish()
        override fun onVolumeChanged(volume: Float) = publish()
        override fun onPlayerError(error: PlaybackException) {
            mutableState.value = mutableState.value.copy(errorMessage = error.message ?: error.errorCodeName)
            publish()
        }
    }

    init {
        player.addListener(listener)
        publish()
    }

    fun setMedia(uri: android.net.Uri, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        mutableState.value = mutableState.value.copy(errorMessage = null)
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri), startPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = autoPlay
        publish()
    }

    fun play() {
        player.play()
        publish()
    }

    fun pause() {
        player.pause()
        publish()
    }

    fun togglePlayPause() {
        if (player.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        publish()
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed.coerceIn(0.25f, 4f))
        publish()
    }

    fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
        publish()
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }

    fun refresh() = publish()

    private fun publish() {
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        mutableState.value = mutableState.value.copy(
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            playbackSpeed = player.playbackParameters.speed,
            volume = player.volume,
            isMuted = player.volume <= 0f,
            errorMessage = mutableState.value.errorMessage,
        )
    }
}
