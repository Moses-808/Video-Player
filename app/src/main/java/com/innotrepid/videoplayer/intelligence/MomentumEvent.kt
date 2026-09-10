package com.innotrepid.videoplayer.intelligence

/**
 * Stable behavioral signals emitted by the player.
 *
 * These events stay local for now. A future Momentum bridge can consume the same
 * contract without coupling intelligence to the playback implementation.
 */
sealed interface MomentumEvent {
    val timestampMs: Long

    data class VideoStarted(
        val mediaId: String,
        val positionMs: Long,
        override val timestampMs: Long
    ) : MomentumEvent

    data class VideoResumed(
        val mediaId: String,
        val positionMs: Long,
        override val timestampMs: Long
    ) : MomentumEvent

    data class VideoPaused(
        val mediaId: String,
        val positionMs: Long,
        override val timestampMs: Long
    ) : MomentumEvent

    data class VideoSeeked(
        val mediaId: String,
        val fromPositionMs: Long,
        val toPositionMs: Long,
        override val timestampMs: Long
    ) : MomentumEvent

    data class VideoCompleted(
        val mediaId: String,
        val durationMs: Long,
        override val timestampMs: Long
    ) : MomentumEvent

    data class VideoSkipped(
        val mediaId: String,
        val positionMs: Long,
        val durationMs: Long,
        override val timestampMs: Long
    ) : MomentumEvent

    data class VideoError(
        val mediaId: String,
        val positionMs: Long,
        val message: String,
        override val timestampMs: Long
    ) : MomentumEvent
}

interface MomentumEventSink {
    fun emit(event: MomentumEvent)
}

object NoOpMomentumEventSink : MomentumEventSink {
    override fun emit(event: MomentumEvent) = Unit
}
