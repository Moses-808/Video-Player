package com.innotrepid.videoplayer.intelligence

/**
 * Meaningful behavioral observations derived from raw playback events.
 *
 * These are intentionally deterministic and local. They describe what happened;
 * they do not attempt to predict what the user will do next.
 */
sealed interface VideoBehaviorSignal {
    val mediaId: String
    val timestampMs: Long
    val strength: Float

    data class WatchStarted(
        override val mediaId: String,
        override val timestampMs: Long,
        override val strength: Float = 0.35f
    ) : VideoBehaviorSignal

    data class WatchResumed(
        override val mediaId: String,
        override val timestampMs: Long,
        override val strength: Float = 0.25f
    ) : VideoBehaviorSignal

    data class WatchedMost(
        override val mediaId: String,
        val watchedFraction: Float,
        override val timestampMs: Long,
        override val strength: Float
    ) : VideoBehaviorSignal

    data class WatchCompleted(
        override val mediaId: String,
        override val timestampMs: Long,
        override val strength: Float = 1f
    ) : VideoBehaviorSignal

    data class WatchAbandonedEarly(
        override val mediaId: String,
        val watchedFraction: Float,
        override val timestampMs: Long,
        override val strength: Float
    ) : VideoBehaviorSignal

    data class Rewatched(
        override val mediaId: String,
        override val timestampMs: Long,
        override val strength: Float = 0.8f
    ) : VideoBehaviorSignal

    data class SeekForward(
        override val mediaId: String,
        val fromPositionMs: Long,
        val toPositionMs: Long,
        override val timestampMs: Long,
        override val strength: Float
    ) : VideoBehaviorSignal

    data class SeekBackward(
        override val mediaId: String,
        val fromPositionMs: Long,
        val toPositionMs: Long,
        override val timestampMs: Long,
        override val strength: Float
    ) : VideoBehaviorSignal

    data class Skipped(
        override val mediaId: String,
        val positionMs: Long,
        val durationMs: Long,
        override val timestampMs: Long,
        override val strength: Float = 0.9f
    ) : VideoBehaviorSignal

    data class ContinuedFromPrevious(
        override val mediaId: String,
        val previousMediaId: String,
        val sameFolder: Boolean,
        override val timestampMs: Long,
        override val strength: Float
    ) : VideoBehaviorSignal

    data class SessionContinued(
        override val mediaId: String,
        val previousMediaId: String,
        override val timestampMs: Long,
        override val strength: Float = 0.65f
    ) : VideoBehaviorSignal
}
