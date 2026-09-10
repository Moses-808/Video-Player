package com.innotrepid.videoplayer.intelligence

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Local append-only behavioral log for Momentum signals.
 *
 * Writes are serialized off the main thread and stored as bounded JSON Lines so
 * the recorder never rewrites the entire history for every event. The log stores
 * playback metadata only; it never copies or modifies video bytes.
 */
class MomentumEventRecorder(context: Context) : MomentumEventSink {
    private val filesDir = context.filesDir
    private val file = File(filesDir, "momentum_events.jsonl")
    private val legacyFile = File(filesDir, "momentum_events.json")
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val MAX_BYTES = 2L * 1024L * 1024L
        private const val TRIM_TO_LINES = 1_000
    }

    init {
        migrateLegacyLog()
    }

    override fun emit(event: MomentumEvent) {
        executor.execute {
            runCatching {
                ensureParent()
                file.appendText(event.toJson().toString() + "\n")
                trimIfNeeded()
            }
        }
    }

    fun recent(limit: Int = 200): List<JSONObject> = runCatching {
        flush()
        readLines().takeLast(limit.coerceAtLeast(0)).mapNotNull { line ->
            runCatching { JSONObject(line) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun clear() {
        runCatching {
            executor.submit {
                file.delete()
                legacyFile.delete()
            }.get()
        }
    }

    /** Writes a self-contained diagnostics file into app cache and returns its path. */
    fun exportToCache(): File? = runCatching {
        flush()
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val exportFile = File(filesDir.parentFile?.resolve("cache"), "video-player-diagnostics-$timestamp.jsonl")
        exportFile.parentFile?.mkdirs()
        exportFile.writeText(exportText())
        exportFile
    }.getOrNull()

    fun exportText(): String = runCatching {
        flush()
        val text = if (file.exists()) file.readText() else ""
        text.ifBlank { "# Video Player diagnostics\n" }
    }.getOrDefault("# Video Player diagnostics\n")

    fun shutdown() {
        executor.shutdown()
    }

    private fun flush() {
        executor.submit { }.get()
    }

    private fun ensureParent() {
        file.parentFile?.mkdirs()
    }

    private fun readLines(): List<String> = if (file.exists()) {
        file.readLines().filter { it.isNotBlank() }
    } else {
        emptyList()
    }

    private fun trimIfNeeded() {
        if (file.length() <= MAX_BYTES) return
        val lines = readLines().takeLast(TRIM_TO_LINES)
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun migrateLegacyLog() {
        if (file.exists() || !legacyFile.exists()) return
        runCatching {
            val array = JSONArray(legacyFile.readText())
            ensureParent()
            file.printWriter().use { writer ->
                for (index in 0 until array.length()) {
                    writer.println(
                        array.getJSONObject(index).put("schemaVersion", SCHEMA_VERSION).toString()
                    )
                }
            }
            legacyFile.delete()
            trimIfNeeded()
        }
    }

    private fun MomentumEvent.toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
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
