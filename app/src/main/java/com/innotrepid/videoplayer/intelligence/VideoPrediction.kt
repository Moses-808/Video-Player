package com.innotrepid.videoplayer.intelligence

/**
 * A deterministic prediction about what the viewer is most likely to watch next.
 *
 * Predictions are local and explainable. They are not a recommendation-service
 * response and do not imply that a user will definitely choose the item.
 */
data class VideoPrediction(
    val mediaId: String,
    val score: Float,
    val confidence: Float,
    val reasons: List<Reason>
) {
    enum class Reason {
        SAME_FOLDER_CONTINUATION,
        RESUMEABLE,
        RECENTLY_ENGAGED,
        REWATCHED,
        PREVIOUSLY_COMPLETED,
        RECENT_ABANDONMENT,
        NATURAL_ORDER
    }
}
