package com.innotrepid.videoplayer.intelligence

/**
 * Converts raw Momentum playback events into higher-value behavioral observations.
 *
 * The interpreter is pure: it has no Android, Compose, or ExoPlayer dependency and
 * can therefore be tested independently. Invalid or incomplete duration data is
 * ignored rather than turned into misleading percentage-based signals.
 */
object VideoBehaviorSignalInterpreter {
    data class VideoContext(
        val mediaId: String,
        val folderKey: String? = null,
        val durationMs: Long = 0L
    )

    fun interpret(
        events: List<MomentumEvent>,
        contexts: Map<String, VideoContext> = emptyMap(),
        nowMs: Long = events.maxOfOrNull { it.timestampMs } ?: 0L
    ): List<VideoBehaviorSignal> {
        if (events.isEmpty()) return emptyList()

        val ordered = events.sortedBy { it.timestampMs }
        val signals = mutableListOf<VideoBehaviorSignal>()
        val startedCounts = mutableMapOf<String, Int>()
        var previousCompleted: MomentumEvent.VideoCompleted? = null

        for (event in ordered) {
            when (event) {
                is MomentumEvent.VideoStarted -> {
                    val starts = (startedCounts[event.mediaId] ?: 0) + 1
                    startedCounts[event.mediaId] = starts
                    signals += VideoBehaviorSignal.WatchStarted(event.mediaId, event.timestampMs)
                    if (starts > 1) {
                        signals += VideoBehaviorSignal.Rewatched(event.mediaId, event.timestampMs)
                    }

                    previousCompleted?.let { completed ->
                        if (event.timestampMs >= completed.timestampMs &&
                            event.timestampMs - completed.timestampMs <= CONTINUATION_WINDOW_MS &&
                            completed.mediaId != event.mediaId
                        ) {
                            val sameFolder = contexts[event.mediaId]?.folderKey != null &&
                                contexts[event.mediaId]?.folderKey == contexts[completed.mediaId]?.folderKey
                            val strength = if (sameFolder) 0.9f else 0.55f
                            signals += VideoBehaviorSignal.ContinuedFromPrevious(
                                mediaId = event.mediaId,
                                previousMediaId = completed.mediaId,
                                sameFolder = sameFolder,
                                timestampMs = event.timestampMs,
                                strength = strength
                            )
                            signals += VideoBehaviorSignal.SessionContinued(
                                mediaId = event.mediaId,
                                previousMediaId = completed.mediaId,
                                timestampMs = event.timestampMs
                            )
                        }
                    }
                }

                is MomentumEvent.VideoResumed -> {
                    signals += VideoBehaviorSignal.WatchResumed(event.mediaId, event.timestampMs)
                }

                is MomentumEvent.VideoPaused -> {
                    emitProgressSignals(
                        signals = signals,
                        mediaId = event.mediaId,
                        positionMs = event.positionMs,
                        durationMs = contexts[event.mediaId]?.durationMs ?: 0L,
                        timestampMs = event.timestampMs,
                        nowMs = nowMs
                    )
                }

                is MomentumEvent.VideoSeeked -> {
                    val delta = event.toPositionMs - event.fromPositionMs
                    if (kotlin.math.abs(delta) >= MIN_SEEK_SIGNAL_MS) {
                        val strength = (kotlin.math.abs(delta).toFloat() / SEEK_STRENGTH_REFERENCE_MS)
                            .coerceIn(0.2f, 1f)
                        if (delta > 0) {
                            signals += VideoBehaviorSignal.SeekForward(
                                event.mediaId, event.fromPositionMs, event.toPositionMs,
                                event.timestampMs, strength
                            )
                        } else {
                            signals += VideoBehaviorSignal.SeekBackward(
                                event.mediaId, event.fromPositionMs, event.toPositionMs,
                                event.timestampMs, strength
                            )
                        }
                    }
                }

                is MomentumEvent.VideoCompleted -> {
                    if (event.durationMs > 0L) {
                        signals += VideoBehaviorSignal.WatchedMost(
                            mediaId = event.mediaId,
                            watchedFraction = 1f,
                            timestampMs = event.timestampMs,
                            strength = 1f
                        )
                    }
                    signals += VideoBehaviorSignal.WatchCompleted(event.mediaId, event.timestampMs)
                    previousCompleted = event
                }

                is MomentumEvent.VideoSkipped -> {
                    signals += VideoBehaviorSignal.Skipped(
                        event.mediaId, event.positionMs, event.durationMs,
                        event.timestampMs
                    )
                    emitProgressSignals(
                        signals = signals,
                        mediaId = event.mediaId,
                        positionMs = event.positionMs,
                        durationMs = event.durationMs,
                        timestampMs = event.timestampMs,
                        nowMs = nowMs
                    )
                }

                is MomentumEvent.VideoError -> Unit
            }
        }

        return signals
    }

    private fun emitProgressSignals(
        signals: MutableList<VideoBehaviorSignal>,
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        timestampMs: Long,
        nowMs: Long
    ) {
        if (durationMs <= 0L || positionMs < 0L) return
        val fraction = (positionMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)

        when {
            fraction >= MOST_WATCHED_THRESHOLD -> {
                signals += VideoBehaviorSignal.WatchedMost(
                    mediaId = mediaId,
                    watchedFraction = fraction,
                    timestampMs = timestampMs,
                    strength = fraction.coerceIn(0.8f, 1f)
                )
            }
            fraction <= EARLY_ABANDONMENT_THRESHOLD && nowMs - timestampMs >= MIN_ABANDONMENT_AGE_MS -> {
                signals += VideoBehaviorSignal.WatchAbandonedEarly(
                    mediaId = mediaId,
                    watchedFraction = fraction,
                    timestampMs = timestampMs,
                    strength = (1f - fraction).coerceIn(0.5f, 1f)
                )
            }
        }
    }

    private const val EARLY_ABANDONMENT_THRESHOLD = 0.10f
    private const val MOST_WATCHED_THRESHOLD = 0.80f
    private const val MIN_ABANDONMENT_AGE_MS = 0L
    private const val MIN_SEEK_SIGNAL_MS = 5_000L
    private const val SEEK_STRENGTH_REFERENCE_MS = 60_000f
    private const val CONTINUATION_WINDOW_MS = 2 * 60 * 1000L
}
