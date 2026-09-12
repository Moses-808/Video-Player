package com.innotrepid.videoplayer.intelligence

/**
 * Chooses a representative preview start point using local viewing behavior when
 * available, with a deterministic duration-based fallback.
 */
object VideoPreviewMoment {
    private const val MIN_VIDEO_MS = 20_000L
    private const val SHORT_START_MS = 2_000L
    private const val LONG_START_FRACTION = 0.10f
    private const val LONG_START_MIN_MS = 5_000L
    private const val LONG_START_MAX_MS = 30_000L
    private const val MIN_LEARNED_POSITION_MS = 5_000L
    private const val EDGE_MARGIN_FRACTION = 0.10f

    fun startPositionMs(
        durationMs: Long,
        events: List<MomentumEvent> = emptyList(),
        mediaId: String? = null,
        resumePositionMs: Long = 0L
    ): Long {
        if (durationMs <= 0L) return 0L

        val resumePosition = resumePositionMs.coerceIn(0L, (durationMs - 1_000L).coerceAtLeast(0L))
        if (resumePosition >= MIN_LEARNED_POSITION_MS && resumePosition <= durationMs * (1f - EDGE_MARGIN_FRACTION)) {
            return resumePosition
        }

        val fallback = fallback(durationMs)
        if (mediaId == null || events.isEmpty()) return fallback

        val learned = events.asSequence()
            .filter { it is MomentumEvent.VideoSeeked && it.mediaId == mediaId }
            .map { it as MomentumEvent.VideoSeeked }
            .map { it.toPositionMs }
            .filter { it >= MIN_LEARNED_POSITION_MS && it <= durationMs * (1f - EDGE_MARGIN_FRACTION) }
            .toList()

        if (learned.isEmpty()) return fallback

        return learned
            .groupingBy { it / 5_000L }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<Long, Int>> { it.value }.thenByDescending { it.key })
            ?.key
            ?.times(5_000L)
            ?.coerceIn(0L, (durationMs - 1_000L).coerceAtLeast(0L))
            ?: fallback
    }

    fun fallback(durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        if (durationMs < MIN_VIDEO_MS) {
            return SHORT_START_MS.coerceAtMost((durationMs - 1_000L).coerceAtLeast(0L))
        }
        return (durationMs * LONG_START_FRACTION)
            .toLong()
            .coerceIn(LONG_START_MIN_MS, LONG_START_MAX_MS)
            .coerceAtMost((durationMs - 1_000L).coerceAtLeast(0L))
    }
}
