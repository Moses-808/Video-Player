package com.innotrepid.videoplayer.intelligence

/**
 * Deterministic local policy for choosing a representative preview start point.
 *
 * This is intentionally content-agnostic for now. It gives short videos a near-start
 * preview and moves longer videos past the likely opening section. A future Momentum
 * model can replace this policy with content-aware or learned moments without changing
 * the preview player API.
 */
object VideoPreviewMoment {
    private const val MIN_VIDEO_MS = 20_000L
    private const val SHORT_START_MS = 2_000L
    private const val LONG_START_FRACTION = 0.10f
    private const val LONG_START_MIN_MS = 5_000L
    private const val LONG_START_MAX_MS = 30_000L

    fun startPositionMs(durationMs: Long): Long {
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
