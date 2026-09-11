package com.innotrepid.videoplayer.playback

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single command and event boundary around ExoPlayer.
 *
 * UI code can render the player view, but playback commands and playback
 * lifecycle events stay inside this class so there is one source of truth.
 */
class PlaybackController(
    private val player: ExoPlayer,
) {
    sealed interface Event {
        data class Started(val positionMs: Long) : Event
        data class Resumed(val positionMs: Long) : Event
        data class Paused(val positionMs: Long) : Event
        data class Seeked(val fromPositionMs: Long, val toPositionMs: Long) : Event
        data class Completed(val durationMs: Long) : Event
        data class Error(val positionMs: Long, val message: String) : Event
    }

    private val mutableState = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = mutableState.asStateFlow()

    private val eventFlow = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: SharedFlow<Event> = eventFlow.asSharedFlow()

    private var started = false
    private var completed = false
    private var released = false
    private var switchingMedia = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (released || switchingMedia) return
            if (isPlaying) {
                if (started) eventFlow.tryEmit(Event.Resumed(player.currentPosition.coerceAtLeast(0L)))
                else {
                    started = true
                    completed = false
                    eventFlow.tryEmit(Event.Started(player.currentPosition.coerceAtLeast(0L)))
                }
            } else if (started && player.playbackState != Player.STATE_ENDED) {
                eventFlow.tryEmit(Event.Paused(player.currentPosition.coerceAtLeast(0L)))
            }
            publish()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (released || switchingMedia) return
            if (playbackState == Player.STATE_ENDED && !completed) {
                completed = true
                eventFlow.tryEmit(Event.Completed(player.duration.coerceAtLeast(0L)))
            }
            publish()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (released || switchingMedia) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                kotlin.math.abs(newPosition.positionMs - oldPosition.positionMs) >= 1_000L
            ) {
                eventFlow.tryEmit(Event.Seeked(oldPosition.positionMs, newPosition.positionMs))
            }
            publish()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = publish()
        override fun onVolumeChanged(volume: Float) = publish()

        override fun onPlayerError(error: PlaybackException) {
            if (released || switchingMedia) return
            mutableState.value = mutableState.value.copy(
                errorMessage = error.message ?: error.errorCodeName
            )
            eventFlow.tryEmit(
                Event.Error(
                    player.currentPosition.coerceAtLeast(0L),
                    "${error.errorCodeName}: ${error.message.orEmpty()}"
                )
            )
            publish()
        }
    }

    init {
        player.addListener(listener)
        publish()
    }

    fun setMedia(uri: Uri, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        if (released) return
        switchingMedia = true
        started = false
        completed = false
        mutableState.value = mutableState.value.copy(errorMessage = null)
        player.setMediaItem(
            androidx.media3.common.MediaItem.fromUri(uri),
            startPositionMs.coerceAtLeast(0L)
        )
        player.prepare()
        player.playWhenReady = autoPlay
        switchingMedia = false
        publish()
    }

    fun play() {
        if (released) return
        player.play()
        publish()
    }

    fun pause() {
        if (released) return
        player.pause()
        publish()
    }

    fun togglePlayPause() {
        if (released) return
        if (player.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        player.seekTo(positionMs.coerceAtLeast(0L))
        publish()
    }

    fun seekBy(deltaMs: Long) {
        if (released) return
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
        publish()
    }

    fun setSpeed(speed: Float) {
        if (released) return
        player.setPlaybackSpeed(speed.coerceIn(0.25f, 4f))
        publish()
    }

    fun setVolume(volume: Float) {
        if (released) return
        player.volume = volume.coerceIn(0f, 1f)
        publish()
    }

    /** Stop and unload the current media without destroying the controller. */
    fun clearMedia() {
        if (released) return
        switchingMedia = true
        started = false
        completed = false
        player.stop()
        player.clearMediaItems()
        switchingMedia = false
        mutableState.value = PlaybackUiState()
    }

    fun currentPositionMs(): Long = if (released) 0L else player.currentPosition.coerceAtLeast(0L)

    fun durationMs(): Long = if (released) 0L else player.duration.takeIf { it > 0L } ?: 0L

    fun release() {
        if (released) return
        released = true
        player.removeListener(listener)
        player.release()
    }

    fun refresh() {
        if (!released) publish()
    }

    private fun publish() {
        if (released) return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        mutableState.value = mutableState.value.copy(
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            playbackSpeed = player.playbackParameters?.speed ?: 1f,
            volume = player.volume,
            isMuted = player.volume <= 0f,
            errorMessage = mutableState.value.errorMessage,
        )
    }
}
