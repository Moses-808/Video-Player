package com.innotrepid.videoplayer.intelligence

/**
 * Transient bridge between the prediction pass and playback completion.
 * It intentionally stores only the latest prediction snapshot in memory;
 * prediction history remains in the existing local diagnostics/feedback stores.
 */
object VideoPredictionAutoAdvanceState {
    @Volatile
    private var latest: List<VideoPredictionAutoAdvancePolicy.Candidate> = emptyList()

    fun publish(predictions: List<VideoPredictionAutoAdvancePolicy.Candidate>) {
        latest = predictions
            .filter { it.mediaId.isNotBlank() }
            .map { it.copy(confidence = it.confidence.coerceIn(0f, 1f)) }
    }

    fun current(): List<VideoPredictionAutoAdvancePolicy.Candidate> = latest

    fun clear() {
        latest = emptyList()
    }
}
