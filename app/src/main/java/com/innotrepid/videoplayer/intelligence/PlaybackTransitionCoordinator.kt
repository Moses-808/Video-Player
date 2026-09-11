package com.innotrepid.videoplayer.intelligence

/**
 * Small, side-effect-free coordinator for deciding whether a manual or
 * automatic session transition is still valid.
 *
 * The UI remains responsible for persisting playback progress and changing
 * the selected item; this class only decides the destination queue state.
 */
object PlaybackTransitionCoordinator {
    fun next(queue: VideoSessionQueue?, sourceId: String): VideoSessionQueue? =
        VideoSessionTransitionPolicy.next(queue, sourceId)

    fun previous(queue: VideoSessionQueue?, sourceId: String): VideoSessionQueue? =
        VideoSessionTransitionPolicy.previous(queue, sourceId)
}
