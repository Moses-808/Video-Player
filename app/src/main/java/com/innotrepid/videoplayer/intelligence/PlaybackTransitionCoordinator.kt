package com.innotrepid.videoplayer.intelligence

/**
 * Small, side-effect-free coordinator for deciding whether a manual or
 * automatic session transition is still valid.
 *
 * The UI remains responsible for persisting playback progress and changing
 * the selected item; this class only decides the destination queue state.
 */
object PlaybackTransitionCoordinator {
    /**
     * Advances using Momentum when the latest prediction snapshot is strong
     * enough; otherwise this is exactly the deterministic queue transition.
     */
    fun next(queue: VideoSessionQueue?, sourceId: String): VideoSessionQueue? =
        predictiveNext(
            queue = queue,
            sourceId = sourceId,
            predictions = VideoPredictionAutoAdvanceState.current()
        )

    fun previous(queue: VideoSessionQueue?, sourceId: String): VideoSessionQueue? =
        VideoSessionTransitionPolicy.previous(queue, sourceId)

    /**
     * Returns the queue state that should follow a completed item when
     * Momentum has enough evidence to override the deterministic next item.
     * The actual UI transition remains outside this coordinator.
     */
    fun predictiveNext(
        queue: VideoSessionQueue?,
        sourceId: String,
        predictions: List<VideoPredictionAutoAdvancePolicy.Candidate>
    ): VideoSessionQueue? {
        val deterministic = VideoSessionTransitionPolicy.next(queue, sourceId)
        val deterministicCandidate = deterministic?.current?.let { current ->
            VideoPredictionAutoAdvancePolicy.Candidate(
                mediaId = current.id,
                confidence = deterministicConfidence(deterministic, sourceId)
            )
        }
        val chosenId = VideoPredictionAutoAdvancePolicy.choose(
            deterministicNext = deterministicCandidate,
            predictions = predictions
        ) ?: return null

        if (deterministic?.current?.id == chosenId) return deterministic

        // A prediction outside the current session must never break the
        // deterministic folder/series contract.
        return queue?.moveTo(chosenId) ?: deterministic
    }

    /**
     * Deterministic continuation is intentionally treated as a modest prior.
     * Momentum must beat it by the policy margin before changing the flow.
     */
    private fun deterministicConfidence(queue: VideoSessionQueue, sourceId: String): Float =
        if (queue.current?.id == sourceId) 0.58f else 0.50f
}
