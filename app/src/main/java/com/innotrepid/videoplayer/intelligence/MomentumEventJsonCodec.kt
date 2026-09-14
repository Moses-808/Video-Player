package com.innotrepid.videoplayer.intelligence

import org.json.JSONObject

/**
 * Decodes the persisted Momentum JSON Lines representation back into typed events.
 * Unknown or malformed records are ignored so one bad diagnostic line cannot break
 * the local prediction pipeline.
 */
object MomentumEventJsonCodec {
    fun decode(json: JSONObject): MomentumEvent? = runCatching {
        val type = json.optString("type", "")
        val mediaId = json.optString("mediaId", "")
        if (type.isBlank() || mediaId.isBlank()) return null

        when (type) {
            "video_started" -> MomentumEvent.VideoStarted(
                mediaId = mediaId,
                positionMs = json.optLong("positionMs", 0L),
                timestampMs = json.optLong("timestampMs", 0L)
            )
            "video_resumed" -> MomentumEvent.VideoResumed(
                mediaId = mediaId,
                positionMs = json.optLong("positionMs", 0L),
                timestampMs = json.optLong("timestampMs", 0L)
            )
            "video_paused" -> MomentumEvent.VideoPaused(
                mediaId = mediaId,
                positionMs = json.optLong("positionMs", 0L),
                timestampMs = json.optLong("timestampMs", 0L)
            )
            "video_seeked" -> MomentumEvent.VideoSeeked(
                mediaId = mediaId,
                fromPositionMs = json.optLong("fromPositionMs", 0L),
                toPositionMs = json.optLong("toPositionMs", 0L),
                timestampMs = json.optLong("timestampMs", 0L)
            )
            "video_completed" -> MomentumEvent.VideoCompleted(
                mediaId = mediaId,
                durationMs = json.optLong("durationMs", 0L),
                timestampMs = json.optLong("timestampMs", 0L)
            )
            "video_skipped" -> MomentumEvent.VideoSkipped(
                mediaId = mediaId,
                positionMs = json.optLong("positionMs", 0L),
                durationMs = json.optLong("durationMs", 0L),
                timestampMs = json.optLong("timestampMs", 0L)
            )
            "video_error" -> MomentumEvent.VideoError(
                mediaId = mediaId,
                positionMs = json.optLong("positionMs", 0L),
                message = json.optString("message", "Unknown playback error"),
                timestampMs = json.optLong("timestampMs", 0L)
            )
            else -> null
        }
    }.getOrNull()
}
