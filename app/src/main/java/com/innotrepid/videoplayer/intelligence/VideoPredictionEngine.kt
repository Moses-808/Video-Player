package com.innotrepid.videoplayer.intelligence

import com.innotrepid.videoplayer.library.VideoItem

/**
 * Builds the local prediction from the same raw event history used for diagnostics.
 * This keeps interpretation and ranking together without coupling either layer to UI.
 */
object VideoPredictionEngine {
    fun predict(
        videos: List<VideoItem>,
        events: List<MomentumEvent>,
        nowMs: Long = System.currentTimeMillis(),
        currentMediaId: String? = null
    ): List<VideoPrediction> {
        val contexts = videos.associate { video ->
            video.id to VideoBehaviorSignalInterpreter.VideoContext(
                mediaId = video.id,
                folderKey = video.folderKey,
                durationMs = video.durationMs
            )
        }
        val signals = VideoBehaviorSignalInterpreter.interpret(
            events = events,
            contexts = contexts,
            nowMs = nowMs
        )
        val current = videos.firstOrNull { it.id == currentMediaId }
        val predictions = VideoPredictor.predict(
            videos = videos,
            signals = signals,
            context = VideoPredictor.CandidateContext(
                currentMediaId = currentMediaId,
                currentFolderKey = current?.folderKey,
                nowMs = nowMs
            )
        )

        return predictions.map { prediction ->
            prediction.copy(
                previewPositionMs = videos.firstOrNull { it.id == prediction.mediaId }?.let { video ->
                    VideoPreviewMomentLearner.learnPositionMs(video.id, video.durationMs, events)
                        ?: VideoPreviewMoment.startPositionMs(video.durationMs)
                }
            )
        }
    }
}
