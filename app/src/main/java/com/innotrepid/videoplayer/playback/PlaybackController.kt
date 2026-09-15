package com.innotrepid.videoplayer.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.logging.Logger

/**
 * Single command and event boundary around ExoPlayer.
 *
 * UI code can render the player view, but playback commands and playback
 * lifecycle events stay inside this class so there is one source of truth.
 *
 * Media switches are identity-gated: every load gets a unique mediaId. Listener
 * callbacks and published state are ignored unless they belong to the active id.
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
    private var hasNext = false
    private var hasPrevious = false
    private var audioTracks: List<AudioTrackOption> = emptyList()
    private var selectedAudioTrackId: String? = null
    private var subtitleTracks: List<SubtitleTrackOption> = emptyList()
    private var selectedSubtitleTrackId: String? = null
    private var subtitlesEnabled = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val transitionId = mediaItem?.mediaId
            if (released || switchingMedia) return
            if (transitionId == null || transitionId != activeMediaId) {
                logger.fine(
                    "Ignoring media transition for id=$transitionId (active=$activeMediaId)",
                )
                return
            }
            callbackMediaId = transitionId
            publish()
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

        override fun onTracksChanged(tracks: Tracks) {
            if (!hasActiveMediaCallback()) return
            refreshAudioTracks(tracks)
            refreshSubtitleTracks(tracks)
            publish()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!hasActiveMediaCallback()) return
            val errorCodeName = error.errorCodeName.orEmpty()
            val errorMessage = error.message.orEmpty()
            lastErrorCodeName = errorCodeName
            canRetry = isRetryablePlaybackError(error)
            retryCount = 0

            mutableState.value = mutableState.value.copy(
                errorMessage = "$errorCodeName: $errorMessage",
            )

            logger.warning(
                "Playback error: mediaId=$activeMediaId code=$errorCodeName " +
                    "retryable=$canRetry message=$errorMessage",
            )

            eventFlow.tryEmit(
                Event.Error(
                    player.currentPosition.coerceAtLeast(0L),
                    "$errorCodeName: $errorMessage",
                ),
            )
            publish()
        }
    }

    init {
        player.addListener(listener)
        publish()
    }

    fun updateNavigation(hasNext: Boolean, hasPrevious: Boolean) {
        if (released) return
        if (this.hasNext == hasNext && this.hasPrevious == hasPrevious) return
        this.hasNext = hasNext
        this.hasPrevious = hasPrevious
        publish()
    }

    fun setMedia(uri: Uri, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        if (released) return

        switchingMedia = true
        try {
            started = false
            completed = false
            canRetry = false
            retryCount = 0
            lastErrorCodeName = ""
            callbackMediaId = null
            audioTracks = emptyList()
            selectedAudioTrackId = null
            subtitleTracks = emptyList()
            selectedSubtitleTrackId = null
            subtitlesEnabled = false

            mediaSequence += 1L
            val mediaId = "playback-$mediaSequence"
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(mediaId)
                .build()

            activeMediaUri = uri
            activeMediaId = mediaId

            mutableState.value = PlaybackUiState(
                hasNext = hasNext,
                hasPrevious = hasPrevious,
            )

            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = autoPlay

            if (player.currentMediaItem?.mediaId == mediaId) {
                callbackMediaId = mediaId
            }
        } finally {
            switchingMedia = false
        }
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

    fun seekTo(positionMs: Long) {
        if (released) return
        canRetry = false
        val wasPlaying = player.isPlaying
        player.seekTo(positionMs.coerceAtLeast(0L))
        if (wasPlaying) player.playWhenReady = true
        publish()
    }

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

    fun selectAudioTrack(trackId: String) {
        if (released || switchingMedia) return
        val parts = trackId.split(':')
        if (parts.size != 2) return
        val groupIndex = parts[0].toIntOrNull() ?: return
        val trackIndex = parts[1].toIntOrNull() ?: return

        val tracks = player.currentTracks
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groupIndex !in audioGroups.indices) return
        val group = audioGroups[groupIndex]
        if (trackIndex !in 0 until group.length) return
        if (!group.isTrackSupported(trackIndex)) return

        val mediaTrackGroup = group.mediaTrackGroup
        val parameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(TrackSelectionOverride(mediaTrackGroup, trackIndex))
            .build()
        player.trackSelectionParameters = parameters
        selectedAudioTrackId = trackId
        audioTracks = audioTracks.map { option ->
            option.copy(isSelected = option.id == trackId)
        }
        publish()
    }

    /**
     * Select an embedded subtitle track, or pass null to disable text tracks.
     * [trackId] format is "{groupIndex}:{trackIndex}".
     */
    fun selectSubtitleTrack(trackId: String?) {
        if (released || switchingMedia) return

        if (trackId == null) {
            val parameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            player.trackSelectionParameters = parameters
            selectedSubtitleTrackId = null
            subtitlesEnabled = false
            subtitleTracks = subtitleTracks.map { it.copy(isSelected = false) }
            publish()
            return
        }

        val parts = trackId.split(':')
        if (parts.size != 2) return
        val groupIndex = parts[0].toIntOrNull() ?: return
        val trackIndex = parts[1].toIntOrNull() ?: return

        val tracks = player.currentTracks
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (groupIndex !in textGroups.indices) return
        val group = textGroups[groupIndex]
        if (trackIndex !in 0 until group.length) return
        if (!group.isTrackSupported(trackIndex)) return

        val mediaTrackGroup = group.mediaTrackGroup
        val parameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .addOverride(TrackSelectionOverride(mediaTrackGroup, trackIndex))
            .build()
        player.trackSelectionParameters = parameters
        selectedSubtitleTrackId = trackId
        subtitlesEnabled = true
        subtitleTracks = subtitleTracks.map { option ->
            option.copy(isSelected = option.id == trackId)
        }
        publish()
    }

    fun retry() {
        if (released || !canRetry || player.currentMediaItem == null) return
        if (activeMediaId == null) return

        retryCount += 1
        logger.info(
            "Retry attempt #$retryCount mediaId=$activeMediaId errorCode=$lastErrorCodeName",
        )

        canRetry = false
        mutableState.value = mutableState.value.copy(errorMessage = null)
        player.prepare()
        player.playWhenReady = true
        publish()
    }

    fun clearMedia() {
        if (released) return
        switchingMedia = true
        try {
            player.stop()
            player.clearMediaItems()
            activeMediaUri = null
            activeMediaId = null
            callbackMediaId = null
            started = false
            completed = false
            canRetry = false
            retryCount = 0
            lastErrorCodeName = ""
            hasNext = false
            hasPrevious = false
            audioTracks = emptyList()
            selectedAudioTrackId = null
            subtitleTracks = emptyList()
            selectedSubtitleTrackId = null
            subtitlesEnabled = false
            mutableState.value = PlaybackUiState()
        } finally {
            switchingMedia = false
        }
    }

    fun currentMediaUri(): Uri? =
        if (released) null else activeMediaUri ?: player.currentMediaItem?.localConfiguration?.uri

    fun currentMediaId(): String? = if (released) null else activeMediaId

    fun currentPositionMs(): Long =
        if (released) 0L else player.currentPosition.coerceAtLeast(0L)

    fun durationMs(): Long =
        if (released) 0L else player.duration.takeIf { it > 0L } ?: 0L

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
        hasNext = false
        hasPrevious = false
        audioTracks = emptyList()
        selectedAudioTrackId = null
        subtitleTracks = emptyList()
        selectedSubtitleTrackId = null
        subtitlesEnabled = false
        player.removeListener(listener)
        player.release()
    }

    fun refresh() {
        if (!released && !switchingMedia) publish()
    }

    private fun hasActiveMediaCallback(): Boolean {
        if (released || switchingMedia || activeMediaId == null) return false
        val currentId = player.currentMediaItem?.mediaId
        return callbackMediaId == activeMediaId || currentId == activeMediaId
    }

    private fun refreshAudioTracks(tracks: Tracks) {
        val options = mutableListOf<AudioTrackOption>()
        var selectedId: String? = null
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        audioGroups.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val id = "$groupIndex:$trackIndex"
                val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
                val label = buildAudioTrackLabel(
                    explicitLabel = format.label,
                    language = language,
                    channelCount = format.channelCount,
                    bitrate = format.bitrate,
                    index = options.size + 1,
                )
                val selected = group.isTrackSelected(trackIndex)
                if (selected) selectedId = id
                options += AudioTrackOption(
                    id = id,
                    label = label,
                    language = language,
                    isSelected = selected,
                )
            }
        }
        audioTracks = options
        selectedAudioTrackId = selectedId
    }

    private fun refreshSubtitleTracks(tracks: Tracks) {
        val options = mutableListOf<SubtitleTrackOption>()
        var selectedId: String? = null
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        textGroups.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val id = "$groupIndex:$trackIndex"
                val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
                val label = buildSubtitleTrackLabel(
                    explicitLabel = format.label,
                    language = language,
                    index = options.size + 1,
                )
                val selected = group.isTrackSelected(trackIndex)
                if (selected) selectedId = id
                options += SubtitleTrackOption(
                    id = id,
                    label = label,
                    language = language,
                    isSelected = selected,
                )
            }
        }
        subtitleTracks = options
        selectedSubtitleTrackId = selectedId
        subtitlesEnabled = selectedId != null &&
            !player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    }

    private fun buildAudioTrackLabel(
        explicitLabel: String?,
        language: String?,
        channelCount: Int,
        bitrate: Int,
        index: Int,
    ): String {
        val parts = mutableListOf<String>()
        val cleanedLabel = explicitLabel?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanedLabel != null) {
            parts += cleanedLabel
        } else if (language != null) {
            parts += languageDisplayName(language)
        } else {
            parts += "Track $index"
        }
        when {
            channelCount >= 6 -> parts += "5.1"
            channelCount == 2 -> parts += "Stereo"
            channelCount == 1 -> parts += "Mono"
        }
        if (bitrate > 0) {
            parts += "${bitrate / 1000} kbps"
        }
        return parts.joinToString(" · ")
    }

    private fun buildSubtitleTrackLabel(
        explicitLabel: String?,
        language: String?,
        index: Int,
    ): String {
        val cleanedLabel = explicitLabel?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            cleanedLabel != null && language != null ->
                "$cleanedLabel (${languageDisplayName(language)})"
            cleanedLabel != null -> cleanedLabel
            language != null -> languageDisplayName(language)
            else -> "Subtitle $index"
        }
    }

    private fun languageDisplayName(code: String): String =
        runCatching {
            val locale = Locale.forLanguageTag(code)
            locale.getDisplayLanguage(Locale.getDefault()).ifBlank { code.uppercase(Locale.US) }
        }.getOrDefault(code.uppercase(Locale.US))

    private fun publish() {
        if (released || switchingMedia) return
        val currentId = player.currentMediaItem?.mediaId
        if (activeMediaId != null && currentId != null && currentId != activeMediaId) {
            return
        }
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
            hasNext = hasNext,
            hasPrevious = hasPrevious,
            audioTracks = audioTracks,
            selectedAudioTrackId = selectedAudioTrackId,
            subtitleTracks = subtitleTracks,
            selectedSubtitleTrackId = selectedSubtitleTrackId,
            subtitlesEnabled = subtitlesEnabled,
            errorMessage = mutableState.value.errorMessage,
        )
    }

    private fun isRetryablePlaybackError(error: PlaybackException): Boolean {
        val code = error.errorCodeName.orEmpty().uppercase()
        val detail = error.message.orEmpty().lowercase()

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

        if (code.contains("NETWORK") ||
            code.contains("SOURCE") ||
            code.contains("IO_") ||
            detail.contains("network") ||
            detail.contains("http")
        ) {
            return true
        }

        return true
    }

    companion object {
        private val logger = Logger.getLogger(PlaybackController::class.java.name)
    }
}
