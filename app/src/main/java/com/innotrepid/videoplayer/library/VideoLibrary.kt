package com.innotrepid.videoplayer.library

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.File

/** Small JSON-backed local metadata store. Original video bytes never live here. */
class VideoLibrary(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "video_library.json"))

    private val items = LinkedHashMap<String, VideoItem>()

    init { load() }

    @Synchronized
    fun all(): List<VideoItem> = items.values.sortedWith(
        compareByDescending<VideoItem> { it.lastPlayedAtMs }
            .thenByDescending { it.addedAtMs }
    )

    @Synchronized fun find(id: String): VideoItem? = items[id]

    @Synchronized
    fun upsert(item: VideoItem) { items[item.id] = item; save() }

    @Synchronized
    fun remove(id: String) { items.remove(id); save() }

    @Synchronized
    fun toggleFavorite(id: String) {
        val current = items[id] ?: return
        items[id] = current.copy(isFavorite = !current.isFavorite)
        save()
    }

    @Synchronized
    fun updateProgress(id: String, positionMs: Long, durationMs: Long) {
        val current = items[id] ?: return
        items[id] = current.copy(
            durationMs = durationMs.coerceAtLeast(current.durationMs),
            lastPositionMs = positionMs.coerceAtLeast(0L),
            lastPlayedAtMs = System.currentTimeMillis()
        )
        save()
    }

    @Synchronized
    fun markCompleted(id: String) {
        val current = items[id] ?: return
        items[id] = current.copy(lastPositionMs = 0L, lastPlayedAtMs = System.currentTimeMillis())
        save()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val array = JSONArray(file.readText())
            for (index in 0 until array.length()) {
                val value = array.getJSONObject(index)
                val item = VideoItem(
                    id = value.getString("id"),
                    uri = Uri.parse(value.getString("uri")),
                    title = value.optString("title", "Untitled video"),
                    durationMs = value.optLong("durationMs", 0L),
                    sizeBytes = value.optLong("sizeBytes", 0L),
                    dateModifiedMs = value.optLong("dateModifiedMs", 0L),
                    relativePath = if (value.isNull("relativePath")) null else value.optString("relativePath"),
                    mimeType = if (value.isNull("mimeType")) null else value.optString("mimeType"),
                    lastPositionMs = value.optLong("lastPositionMs", 0L),
                    lastPlayedAtMs = value.optLong("lastPlayedAtMs", 0L),
                    addedAtMs = value.optLong("addedAtMs", System.currentTimeMillis()),
                    isFavorite = value.optBoolean("isFavorite", false)
                )
                items[item.id] = item
            }
        }
    }

    private fun save() {
        file.parentFile?.mkdirs()
        file.writeText(buildJson())
    }

    private fun buildJson(): String = buildString {
        append('[')
        items.values.forEachIndexed { index, item ->
            if (index > 0) append(',')
            append('{')
            stringField("id", item.id)
            stringField("uri", item.uri.toString())
            stringField("title", item.title)
            numberField("durationMs", item.durationMs)
            numberField("sizeBytes", item.sizeBytes)
            numberField("dateModifiedMs", item.dateModifiedMs)
            nullableField("relativePath", item.relativePath)
            nullableField("mimeType", item.mimeType)
            numberField("lastPositionMs", item.lastPositionMs)
            numberField("lastPlayedAtMs", item.lastPlayedAtMs)
            numberField("addedAtMs", item.addedAtMs)
            booleanField("isFavorite", item.isFavorite)
            append('}')
        }
        append(']')
    }

    private fun StringBuilder.separator() {
        if (length > 1) append(',')
    }

    private fun StringBuilder.stringField(name: String, value: String) {
        separator()
        append('"').append(name).append("\":\"").append(escapeJson(value)).append('"')
    }

    private fun StringBuilder.nullableField(name: String, value: String?) {
        separator()
        append('"').append(name).append("\":")
        if (value == null) append("null") else append('"').append(escapeJson(value)).append('"')
    }

    private fun StringBuilder.numberField(name: String, value: Long) {
        separator()
        append('"').append(name).append("\":").append(value)
    }

    private fun StringBuilder.booleanField(name: String, value: Boolean) {
        separator()
        append('"').append(name).append("\":").append(value)
    }

    private fun escapeJson(value: String): String = buildString(value.length + 8) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else append(char)
            }
        }
    }
}
