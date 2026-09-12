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
        if (videos.isEmpty() || events.isEmpty()) {
            return VideoPredictor.predict(
                videos = videos,
                signals = emptyList(),
                context = VideoPredictor.CandidateContext(
                    currentMediaId = currentMediaId,
                    currentFolderKey = videos.firstOrNull { it.id == currentMediaId }?.folderKey,
                    nowMs = nowMs
                )
            )
        }

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

        return VideoPredictor.predict(
            videos = videos,
            signals = signals,
            context = VideoPredictor.CandidateContext(
                currentMediaId = currentMediaId,
                currentFolderKey = current?.folderKey,
                nowMs = nowMs
            )
        )
    }
}
