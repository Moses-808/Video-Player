package com.innotrepid.videoplayer

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import com.innotrepid.videoplayer.intelligence.MomentumEvent
import com.innotrepid.videoplayer.intelligence.MomentumEventRecorder
import com.innotrepid.videoplayer.intelligence.PlaybackTransitionCoordinator
import com.innotrepid.videoplayer.intelligence.VideoSessionQueue
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoLibraryViewModel
import com.innotrepid.videoplayer.playback.PlaybackController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Owns the ExoPlayer instance, PlaybackController, current selection, session queue,
 * progress persistence and Momentum event recording.
 *
 * Keeps playback side-effects out of the root composable so the UI tree stays readable.
 *
 * Progress is persisted on pause, seek, completion, error, video switch, background,
 * composition dispose, and player release — not only on the periodic checkpoint.
 */
class PlaybackSession(
    val player: ExoPlayer,
    val controller: PlaybackController,
    val recorder: MomentumEventRecorder,
    val vm: VideoLibraryViewModel,
) {
    var selectedId by mutableStateOf<String?>(null)
        internal set

    var queue by mutableStateOf<VideoSessionQueue?>(null)
        internal set

    fun openVideo(videos: List<VideoItem>, video: VideoItem) {
        val previousId = selectedId
        if (previousId != null && previousId != video.id) {
            videos.firstOrNull { it.id == previousId }?.let { persist(it) }
        }
        queue = VideoSessionQueue.create(videos, video.id)
        selectedId = video.id
    }

    fun openSession(videos: List<VideoItem>, video: VideoItem, current: VideoItem?) {
        if (video.id == current?.id) return
        current?.let { persist(it) }
        queue = queue?.moveTo(video.id) ?: VideoSessionQueue.create(videos, video.id)
        selectedId = video.id
    }

    fun closePlayer(current: VideoItem?) {
        current?.let { persist(it) }
        controller.clearMedia()
        selectedId = null
    }

    /**
     * Checkpoint the active controller position for [video].
     *
     * Falls back to the library's known duration when the player has not reported
     * one yet, so early pause / background still saves a useful position.
     */
    fun persist(video: VideoItem) {
        val duration = controller.durationMs().takeIf { it > 0L } ?: video.durationMs
        if (duration <= 0L) return
        val position = controller.currentPositionMs().coerceIn(0L, duration)
        vm.updateProgress(video.id, position, duration)
    }

    fun release() {
        controller.release()
        recorder.shutdown()
    }
}

@Composable
fun rememberPlaybackSession(
    context: Context,
    vm: VideoLibraryViewModel,
): PlaybackSession {
    val player = remember { ExoPlayer.Builder(context).build() }
    val controller = remember(player) { PlaybackController(player) }
    val recorder = remember { MomentumEventRecorder(context.applicationContext) }

    return remember(player, controller, recorder, vm) {
        PlaybackSession(player, controller, recorder, vm)
    }
}

/**
 * Wires lifecycle, media loading, periodic checkpoints and Momentum event collection
 * for the active [PlaybackSession].
 */
@Composable
fun PlaybackSessionEffects(
    session: PlaybackSession,
    videos: List<VideoItem>,
    autoAdvance: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val selected = videos.firstOrNull { it.id == session.selectedId }
    val latestSelected by rememberUpdatedState(selected)
    val latestVideos by rememberUpdatedState(videos)
    val latestQueue by rememberUpdatedState(session.queue)
    val currentPersist by rememberUpdatedState { latestSelected?.let { session.persist(it) } }

    // Persist on background / multi-window pause
    DisposableEffect(lifecycleOwner, session.controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                currentPersist()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Persist then release when the composition leaves
    DisposableEffect(session) {
        onDispose {
            currentPersist()
            session.release()
        }
    }

    // Load media when selection changes
    LaunchedEffect(session.selectedId) {
        val video = selected ?: return@LaunchedEffect
        session.controller.setMedia(
            uri = video.uri,
            startPositionMs = video.lastPositionMs.coerceAtLeast(0L),
            autoPlay = true,
        )
    }

    // Periodic progress checkpoint while a video is selected (backup, not primary)
    LaunchedEffect(session.selectedId) {
        while (isActive && session.selectedId != null) {
            delay(8_000L)
            latestSelected?.let { session.persist(it) }
        }
    }

    // Collect playback events → Momentum + progress + auto-advance
    LaunchedEffect(session.controller) {
        session.controller.events.collect { event ->
            val now = System.currentTimeMillis()

            when (event) {
                is PlaybackController.Event.Released -> {
                    // Selection may already be cleared; resolve by URI so progress is not lost.
                    val video = latestSelected?.takeIf { it.uri == event.mediaUri }
                        ?: latestVideos.firstOrNull { it.uri == event.mediaUri }
                    if (video != null && event.durationMs > 0L) {
                        session.vm.updateProgress(
                            video.id,
                            event.positionMs.coerceIn(0L, event.durationMs),
                            event.durationMs,
                        )
                    }
                    return@collect
                }
                else -> Unit
            }

            val video = latestSelected ?: return@collect
            if (session.controller.currentMediaUri() != null &&
                session.controller.currentMediaUri() != video.uri
            ) {
                return@collect
            }

            when (event) {
                is PlaybackController.Event.Started -> {
                    session.recorder.emit(
                        MomentumEvent.VideoStarted(video.id, event.positionMs, now),
                    )
                }
                is PlaybackController.Event.Resumed -> {
                    session.recorder.emit(
                        MomentumEvent.VideoResumed(video.id, event.positionMs, now),
                    )
                }
                is PlaybackController.Event.Paused -> {
                    session.recorder.emit(
                        MomentumEvent.VideoPaused(video.id, event.positionMs, now),
                    )
                    session.persist(video)
                }
                is PlaybackController.Event.Seeked -> {
                    session.recorder.emit(
                        MomentumEvent.VideoSeeked(
                            video.id,
                            event.fromPositionMs,
                            event.toPositionMs,
                            now,
                        ),
                    )
                    session.persist(video)
                }
                is PlaybackController.Event.Completed -> {
                    session.recorder.emit(
                        MomentumEvent.VideoCompleted(video.id, event.durationMs, now),
                    )
                    session.persist(video)
                    session.vm.markCompleted(video.id)

                    if (autoAdvance) {
                        val next = PlaybackTransitionCoordinator.next(latestQueue, video.id)
                        if (next != null) {
                            session.queue = next
                            session.selectedId = next.current?.id
                        } else {
                            session.selectedId = null
                        }
                    } else {
                        session.selectedId = null
                    }
                }
                is PlaybackController.Event.Error -> {
                    session.recorder.emit(
                        MomentumEvent.VideoError(
                            video.id,
                            event.positionMs,
                            event.message,
                            now,
                        ),
                    )
                    session.persist(video)
                }
                is PlaybackController.Event.Released -> {
                    // Handled above.
                }
            }
        }
    }
}
