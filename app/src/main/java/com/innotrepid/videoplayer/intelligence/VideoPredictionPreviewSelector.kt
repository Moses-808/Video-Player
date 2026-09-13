package com.innotrepid.videoplayer.intelligence

/**
 * Selects a preview moment from the reason the video was predicted.
 *
 * Preview selection is deliberately contextual: a resume prediction should remind
 * the viewer where they left off, while continuation/rewatch predictions should
 * prefer a learned meaningful moment. All paths remain deterministic and local.
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

        if (VideoPrediction.Reason.RESUMEABLE in reasons) {
            return VideoPreviewMoment.startPositionMs(
                durationMs = durationMs,
                resumePositionMs = video.lastPositionMs
            )
        }

        if (learned != null && (
                VideoPrediction.Reason.REWATCHED in reasons ||
                    VideoPrediction.Reason.SAME_FOLDER_CONTINUATION in reasons ||
                    VideoPrediction.Reason.RECENTLY_ENGAGED in reasons
                )
        ) {
            return learned
        }

        return VideoPreviewMoment.startPositionMs(
            durationMs = durationMs,
            events = events,
            mediaId = video.id
        )
    }
}
