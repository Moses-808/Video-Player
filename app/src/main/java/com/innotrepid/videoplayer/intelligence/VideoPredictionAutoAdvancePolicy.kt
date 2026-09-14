package com.innotrepid.videoplayer.intelligence

/**
 * Decides whether Momentum has enough evidence to override the deterministic
 * session queue when a video completes.
 *
 * The policy is deliberately conservative: prediction must be strong enough,
 * and it must beat the deterministic next item by a meaningful margin. This
 * prevents a weak prediction from unexpectedly breaking a user's series flow.
 */
object VideoPredictionAutoAdvancePolicy {
    data class Candidate(
        val mediaId: String,
        val confidence: Float
    )

    fun choose(
        deterministicNext: Candidate?,
        predictions: List<Candidate>,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
        minimumMargin: Float = DEFAULT_MINIMUM_MARGIN
    ): String? {
        val best = predictions
            .filter { it.mediaId.isNotBlank() }
            .maxByOrNull { it.confidence.coerceIn(0f, 1f) }
            ?: return deterministicNext?.mediaId

        val bestConfidence = best.confidence.coerceIn(0f, 1f)
        if (bestConfidence < minConfidence.coerceIn(0f, 1f)) {
            return deterministicNext?.mediaId
        }

        if (deterministicNext == null) return best.mediaId
        if (best.mediaId == deterministicNext.mediaId) return deterministicNext.mediaId

        val deterministicConfidence = deterministicNext.confidence.coerceIn(0f, 1f)
        return if (bestConfidence - deterministicConfidence >= minimumMargin.coerceAtLeast(0f)) {
            best.mediaId
        } else {
            deterministicNext.mediaId
        }
    }

    private const val DEFAULT_MIN_CONFIDENCE = 0.70f
    private const val DEFAULT_MINIMUM_MARGIN = 0.12f
}
