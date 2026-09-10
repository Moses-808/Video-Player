package com.innotrepid.videoplayer.intelligence

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local append-only behavioral log for Momentum signals.
 *
 * The log stores playback metadata only; it never copies or modifies video bytes.
 */
class MomentumEventRecorder(context: Context) : MomentumEventSink {
    private val file = File(context.filesDir, "momentum_events.json")

    override fun emit(event: MomentumEvent) {
        runCatching {
            val array = load()
            array.put(event.toJson())
            file.writeText(array.toString())
        }
    }

    fun recent(limit: Int = 200): List<JSONObject> = runCatching {
        val array = load()
        val start = (array.length() - limit.coerceAtLeast(0)).coerceAtLeast(0)
        buildList {
            for (index in start until array.length()) add(array.getJSONObject(index))
        }
    }.getOrDefault(emptyList())

    fun clear() {
        runCatching { file.delete() }
    }

    fun exportText(): String = runCatching { file.takeIf { it.exists() }?.readText() ?: "[]" }
        .getOrDefault("[]")

    private fun load(): JSONArray = runCatching {
        if (file.exists()) JSONArray(file.readText()) else JSONArray()
    }.getOrElse { JSONArray() }

    private fun MomentumEvent.toJson(): JSONObject = JSONObject().apply {
        when (this@toJson) {
            is MomentumEvent.VideoStarted -> {
                put("type", "video_started")
                put("mediaId", mediaId)
                put("positionMs", positionMs)
            }
            is MomentumEvent.VideoResumed -> {
                put("type", "video_resumed")
                put("mediaId", mediaId)
                put("positionMs", positionMs)
            }
            is MomentumEvent.VideoPaused -> {
                put("type", "video_paused")
                put("mediaId", mediaId)
                put("positionMs", positionMs)
            }
            is MomentumEvent.VideoSeeked -> {
                put("type", "video_seeked")
                put("mediaId", mediaId)
                put("fromPositionMs", fromPositionMs)
                put("toPositionMs", toPositionMs)
            }
            is MomentumEvent.VideoCompleted -> {
                put("type", "video_completed")
                put("mediaId", mediaId)
                put("durationMs", durationMs)
            }
            is MomentumEvent.VideoSkipped -> {
                put("type", "video_skipped")
                put("mediaId", mediaId)
                put("positionMs", positionMs)
                put("durationMs", durationMs)
            }
            is MomentumEvent.VideoError -> {
                put("type", "video_error")
                put("mediaId", mediaId)
                put("positionMs", positionMs)
                put("message", message)
            }
        }
        put("timestampMs", timestampMs)
    }
}
