package com.innotrepid.videoplayer.intelligence

import com.innotrepid.videoplayer.library.VideoItem

/**
 * Scores local videos using observable behavior and library context.
 *
 * The predictor deliberately avoids opaque heuristics: every score contribution
 * has a named reason, and natural folder order remains the final fallback.
 */
object VideoPredictor {
    data class CandidateContext(
        val currentMediaId: String? = null,
        val currentFolderKey: String? = null,
        val nowMs: Long = System.currentTimeMillis()
    )

    fun predict(
        videos: List<VideoItem>,
        signals: List<VideoBehaviorSignal>,
        context: CandidateContext = CandidateContext()
    ): List<VideoPrediction> {
        if (videos.isEmpty()) return emptyList()

        val signalByMedia = signals.groupBy { it.mediaId }
        val ordered = videos.sortedWith(
            compareBy<VideoItem> { it.folderKey.orEmpty().lowercase() }
                .thenBy { naturalKey(it.title) }
                .thenBy { it.id }
        )

        return ordered
            .asSequence()
            .filter { it.id != context.currentMediaId }
            .map { video ->
                val mediaSignals = signalByMedia[video.id].orEmpty()
                val reasons = linkedSetOf<VideoPrediction.Reason>()
                var score = 0f

                if (context.currentFolderKey != null && video.folderKey == context.currentFolderKey) {
                    score += 0.45f
                    reasons += VideoPrediction.Reason.SAME_FOLDER_CONTINUATION
                }

                if (video.isResumeable) {
                    score += 0.30f
                    reasons += VideoPrediction.Reason.RESUMEABLE
                }

                val recentSignal = mediaSignals
                    .filter { context.nowMs - it.timestampMs in 0L..RECENT_SIGNAL_WINDOW_MS }
                    .maxByOrNull { it.timestampMs }
                if (recentSignal != null) {
                    score += 0.10f * recentSignal.strength.coerceIn(0f, 1f)
                    reasons += VideoPrediction.Reason.RECENTLY_ENGAGED
                }

                if (mediaSignals.any { it is VideoBehaviorSignal.Rewatched }) {
                    score += 0.12f
                    reasons += VideoPrediction.Reason.REWATCHED
                }

                if (mediaSignals.any { it is VideoBehaviorSignal.WatchCompleted }) {
                    score += 0.05f
                    reasons += VideoPrediction.Reason.PREVIOUSLY_COMPLETED
                }

                if (mediaSignals.any { it is VideoBehaviorSignal.WatchAbandonedEarly }) {
                    score -= 0.20f
                    reasons += VideoPrediction.Reason.RECENT_ABANDONMENT
                }

                if (reasons.isEmpty()) {
                    score += 0.05f
                    reasons += VideoPrediction.Reason.NATURAL_ORDER
                }

                val confidence = confidenceFor(score, reasons.size)
                VideoPrediction(
                    mediaId = video.id,
                    score = score.coerceIn(0f, 1f),
                    confidence = confidence,
                    reasons = reasons.toList()
                )
            }
            .sortedWith(compareByDescending<VideoPrediction> { it.score }.thenBy { it.mediaId })
            .toList()
    }

    private fun confidenceFor(score: Float, reasonCount: Int): Float {
        val evidence = (reasonCount / 3f).coerceIn(0f, 1f)
        return ((score.coerceIn(0f, 1f) * 0.7f) + (evidence * 0.3f)).coerceIn(0f, 1f)
    }

    private fun naturalKey(title: String): String = title.lowercase()

    private const val RECENT_SIGNAL_WINDOW_MS = 24 * 60 * 60 * 1000L
}
