package com.innotrepid.videoplayer.intelligence

/**
 * Small, explainable adjustment to prediction confidence based on what the viewer
 * actually did with the prediction surface.
 */
object VideoPredictionFeedback {
    private const val MIN_EXPOSURES = 3
    private const val MAX_ADJUSTMENT = 0.15f

    fun adjustedConfidence(
        baseConfidence: Float,
        shownCount: Int,
        acceptedCount: Int
    ): Float {
        if (shownCount < MIN_EXPOSURES || shownCount <= 0) return baseConfidence.coerceIn(0f, 1f)

        val acceptanceRate = (acceptedCount.toFloat() / shownCount).coerceIn(0f, 1f)
        val adjustment = ((acceptanceRate - 0.5f) * 0.30f)
            .coerceIn(-MAX_ADJUSTMENT, MAX_ADJUSTMENT)

        return (baseConfidence + adjustment).coerceIn(0f, 1f)
    }
}
