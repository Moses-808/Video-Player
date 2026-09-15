package com.innotrepid.videoplayer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.playback.PlaybackController
import com.innotrepid.videoplayer.playback.PlaybackUiState

/**
 * Keeps Picture-in-Picture RemoteActions (play/pause, next, previous) wired to the
 * active [PlaybackController] and queue, and refreshes the PiP chrome when state changes.
 */
@Composable
internal fun PipTransportBinding(
    activity: ComponentActivity?,
    controller: PlaybackController,
    state: PlaybackUiState,
    next: VideoItem?,
    previous: VideoItem?,
    open: (VideoItem) -> Unit,
    inPictureInPicture: Boolean,
    setInPictureInPicture: (Boolean) -> Unit,
    onEnterPipChromeHidden: () -> Unit,
) {
    val main = activity as? MainActivity
    val hasNext = state.hasNext && next != null
    val hasPrevious = state.hasPrevious && previous != null

    DisposableEffect(activity, next?.id, previous?.id) {
        main?.onPipPlayPause = { controller.togglePlayPause() }
        main?.onPipNext = { next?.let(open) }
        main?.onPipPrevious = { previous?.let(open) }

        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            setInPictureInPicture(info.isInPictureInPictureMode)
            if (info.isInPictureInPictureMode) {
                onEnterPipChromeHidden()
                main?.updatePipActions(
                    isPlaying = state.isPlaying,
                    hasNext = hasNext,
                    hasPrevious = hasPrevious,
                )
            }
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        setInPictureInPicture(activity?.isInPictureInPictureMode == true)

        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
            main?.onPipPlayPause = null
            main?.onPipNext = null
            main?.onPipPrevious = null
            main?.shouldEnterPipOnLeave = { false }
        }
    }

    // Keep shouldEnterPip and action icons in sync with playback / queue state.
    LaunchedEffect(state.isPlaying, state.errorMessage, inPictureInPicture, hasNext, hasPrevious) {
        main?.shouldEnterPipOnLeave = {
            state.isPlaying && state.errorMessage == null && !inPictureInPicture
        }
        main?.updatePipActions(
            isPlaying = state.isPlaying,
            hasNext = hasNext,
            hasPrevious = hasPrevious,
        )
    }
}

/** Enter PiP with the current transport state so the first frame of the window has correct buttons. */
internal fun MainActivity?.enterPipWithState(
    state: PlaybackUiState,
    next: VideoItem?,
    previous: VideoItem?,
) {
    this?.enterVideoPictureInPicture(
        isPlaying = state.isPlaying,
        hasNext = state.hasNext && next != null,
        hasPrevious = state.hasPrevious && previous != null,
    )
}
