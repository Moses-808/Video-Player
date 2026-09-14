package com.innotrepid.videoplayer.intelligence

/**
 * Selects a preview moment from the reason the video was predicted.
 *
 * Preview selection is deliberately contextual: a resume prediction should remind
 * the viewer where they left off, while other predictions should prefer a learned
 * meaningful moment whenever the viewer has supplied enough behavioral evidence.
 * All paths remain deterministic and local.
 */
object VideoPredictionPreviewSelector {
    fun select(
        prediction: VideoPrediction,
        video: com.innotrepid.videoplayer.library.VideoItem,
        events: List<MomentumEvent>
    ): Long {
        val durationMs = video.durationMs
        if (durationMs <= 0L) return 0L

        val reasons = prediction.reasons.toSet()
        val learned = VideoPreviewMomentLearner.learnPositionMs(
            mediaId = video.id,
            durationMs = durationMs,
            events = events
        )

        // Resume intent is the strongest contextual instruction: show the viewer
        // exactly where they left off rather than replacing it with a learned region.
        if (VideoPrediction.Reason.RESUMEABLE in reasons) {
            return VideoPreviewMoment.startPositionMs(
                durationMs = durationMs,
                resumePositionMs = video.lastPositionMs
            )
        }

        // A learned moment is useful even when the prediction itself is only a
        // natural-order/recently-observed prediction. The evidence is about the
        // preview moment, not merely about why the video ranked highly.
        if (learned != null) return learned

        return VideoPreviewMoment.startPositionMs(
            durationMs = durationMs,
            events = events,
            mediaId = video.id
        )
    }
}
