package com.innotrepid.videoplayer.intelligence

/**
 * Pure transition policy for a live playback session.
 *
 * The caller must supply the item that generated the transition request. A
 * delayed callback from an older item is therefore rejected instead of
 * advancing a newer session position.
 */
object VideoSessionTransitionPolicy {
    fun next(queue: VideoSessionQueue?, currentId: String): VideoSessionQueue? =
        queue?.advanceIfCurrent(currentId)

    fun previous(queue: VideoSessionQueue?, currentId: String): VideoSessionQueue? =
        queue?.retreatIfCurrent(currentId)
}
