package com.innotrepid.videoplayer.intelligence

object VideoPredictionFeedback {
    private const val MIN_EXPOSURES = 3
    private const val MAX_ADJUSTMENT = 0.15f

    fun adjustedConfidence(
        baseConfidence: Float,
        shownCount: Int,
        acceptedCount: Int
    ): Float {
        val safeShownCount = shownCount.coerceAtLeast(0)
        if (safeShownCount < MIN_EXPOSURES) return baseConfidence.coerceIn(0f, 1f)

        // Acceptance is a subset of exposure. Clamp persisted/corrupt state here so
        // bad aggregate data can never create an artificial confidence boost.
        val safeAcceptedCount = acceptedCount.coerceIn(0, safeShownCount)
        val acceptanceRate = (safeAcceptedCount.toFloat() / safeShownCount).coerceIn(0f, 1f)
        val adjustment = ((acceptanceRate - 0.5f) * 0.30f)
            .coerceIn(-MAX_ADJUSTMENT, MAX_ADJUSTMENT)

        return (baseConfidence + adjustment).coerceIn(0f, 1f)
    }
}
