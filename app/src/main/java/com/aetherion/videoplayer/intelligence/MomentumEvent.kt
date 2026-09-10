package com.aetherion.videoplayer.intelligence

/**
 * Stable behavioral signals emitted by the player.
 *
 * The first implementation keeps these events local. A future Momentum bridge can
 * consume the same contract without changing playback code.
 */
sealed interface MomentumEvent {
    val timestampMs: Long

    data class VideoStarted(
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
}

interface MomentumEventSink {
    fun emit(event: MomentumEvent)
}

object NoOpMomentumEventSink : MomentumEventSink {
    override fun emit(event: MomentumEvent) = Unit
}
