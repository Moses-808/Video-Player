package com.innotrepid.videoplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.logging.Logger

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
        data class Completed(val positionMs: Long, val durationMs: Long) : Event
        data class Error(val positionMs: Long, val message: String) : Event
        data class Released(val mediaUri: Uri, val positionMs: Long, val durationMs: Long) : Event
    }

    private val mutableState = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = mutableState.asStateFlow()

    private val eventFlow = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: SharedFlow<Event> = eventFlow.asSharedFlow()

    private var started = false
    private var completed = false
    private var released = false
    private var switchingMedia = false
    private var canRetry = false
    private var retryCount = 0
    private var lastErrorCodeName = ""
    private var activeMediaUri: Uri? = null
    private var mediaSequence = 0L
    private var activeMediaId: String? = null
    private var callbackMediaId: String? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            callbackMediaId = mediaItem?.mediaId
            if (!released &&
                !switchingMedia &&
                activeMediaId != null &&
                callbackMediaId == activeMediaId
            ) {
                publish()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!hasActiveMediaCallback()) return
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
            if (!hasActiveMediaCallback()) return
            if (playbackState == Player.STATE_ENDED && !completed) {
                completed = true
                canRetry = false
                val duration = player.duration.coerceAtLeast(0L)
                val position = player.currentPosition.coerceAtLeast(0L).coerceAtMost(duration)
                eventFlow.tryEmit(Event.Completed(position, duration))
            }
            publish()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (!hasActiveMediaCallback()) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                kotlin.math.abs(newPosition.positionMs - oldPosition.positionMs) >= 1_000L
            ) {
                eventFlow.tryEmit(Event.Seeked(oldPosition.positionMs, newPosition.positionMs))
            }
            publish()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            if (!hasActiveMediaCallback()) return
            publish()
        }

        override fun onVolumeChanged(volume: Float) {
            if (!hasActiveMediaCallback()) return
            publish()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!hasActiveMediaCallback()) return
            val errorCodeName = error.errorCodeName.orEmpty()
            val errorMessage = error.message.orEmpty()
            lastErrorCodeName = errorCodeName
            canRetry = isRetryablePlaybackError(error)
            retryCount = 0  // Reset retry counter on new error

            mutableState.value = mutableState.value.copy(
                errorMessage = "$errorCodeName: $errorMessage"
            )
            
            // Log error details for diagnostics
            logger.warning(
                "Playback error: code=$errorCodeName, retryable=$canRetry, message=$errorMessage"
            )
            
            eventFlow.tryEmit(
                Event.Error(
                    player.currentPosition.coerceAtLeast(0L),
                    "$errorCodeName: $errorMessage"
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
        canRetry = false
        retryCount = 0
        callbackMediaId = null
        mutableState.value = PlaybackUiState()
        mediaSequence += 1L
        val mediaId = "playback-$mediaSequence"
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(mediaId)
            .build()
        activeMediaUri = uri
        activeMediaId = mediaId
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = autoPlay
        switchingMedia = false
        publish()
    }

    fun play() {
        if (released) return
        canRetry = false
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

    /** Seek without changing the user's current play/pause intent. */
    fun seekTo(positionMs: Long) {
        if (released) return
        canRetry = false
        val wasPlaying = player.isPlaying
        player.seekTo(positionMs.coerceAtLeast(0L))
        if (wasPlaying) player.playWhenReady = true
        publish()
    }

    /** Relative seek without changing the user's current play/pause intent. */
    fun seekBy(deltaMs: Long) {
        if (released) return
        canRetry = false
        val wasPlaying = player.isPlaying
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
        if (wasPlaying) player.playWhenReady = true
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

    /** Retry the current failed media from its current position. */
    fun retry() {
        if (released || !canRetry || player.currentMediaItem == null) return
        
        retryCount += 1
        logger.info("Retry attempt #$retryCount for media with error code: $lastErrorCodeName")
        
        canRetry = false
        mutableState.value = mutableState.value.copy(errorMessage = null)
        player.prepare()
        player.playWhenReady = true
        publish()
    }

    /** Stop and unload the current media without destroying the controller. */
    fun clearMedia() {
        if (released) return
        player.stop()
        player.clearMediaItems()
        activeMediaUri = null
        activeMediaId = null
        callbackMediaId = null
        switchingMedia = false
        retryCount = 0
        mutableState.value = PlaybackUiState()
    }

    fun currentMediaUri(): Uri? = if (released) null else player.currentMediaItem?.localConfiguration?.uri

    fun currentPositionMs(): Long = if (released) 0L else player.currentPosition.coerceAtLeast(0L)

    fun durationMs(): Long = if (released) 0L else player.duration.takeIf { it > 0L } ?: 0L

    fun release() {
        if (released) return
        val mediaUri = activeMediaUri ?: player.currentMediaItem?.localConfiguration?.uri
        if (mediaUri != null) {
            val duration = player.duration.coerceAtLeast(0L)
            val position = player.currentPosition.coerceAtLeast(0L).coerceAtMost(duration)
            eventFlow.tryEmit(Event.Released(mediaUri, position, duration))
        }
        released = true
        activeMediaUri = null
        activeMediaId = null
        callbackMediaId = null
        retryCount = 0
        player.removeListener(listener)
        player.release()
    }

    fun refresh() {
        if (!released) publish()
    }

    private fun hasActiveMediaCallback(): Boolean =
        !released &&
            !switchingMedia &&
            activeMediaId != null &&
            callbackMediaId == activeMediaId

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

    private fun isRetryablePlaybackError(error: PlaybackException): Boolean {
        val code = error.errorCodeName.orEmpty().uppercase()
        val detail = error.message.orEmpty().lowercase()

        // These failures require a different user action or a different media
        // file. Re-preparing the same item cannot repair them.
        if (code.contains("FILE_NOT_FOUND") ||
            code.contains("PERMISSION") ||
            code.contains("SECURITY") ||
            code.contains("DECODER") ||
            code.contains("CODEC") ||
            code.contains("RENDERER") ||
            code.contains("UNSUPPORTED") ||
            detail.contains("no such file") ||
            detail.contains("file not found") ||
            detail.contains("access denied") ||
            detail.contains("permission") ||
            detail.contains("decoder") ||
            detail.contains("unsupported")
        ) {
            return false
        }

        // Network/source failures are normally transient and Media3 supports
        // recovery by preparing the failed player again.
        if (code.contains("NETWORK") ||
            code.contains("SOURCE") ||
            code.contains("IO_") ||
            detail.contains("network") ||
            detail.contains("http")
        ) {
            return true
        }

        // Unknown failures remain manually retryable so diagnostics and the
        // recovery path are still available without auto-looping retries.
        return true
    }

    companion object {
        private val logger = Logger.getLogger(PlaybackController::class.java.name)
    }
}
