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

    /** Returns all stored records pointing at the same underlying media URI. */
    @Synchronized
    fun idsForUri(uri: Uri): List<String> = items.values
        .filter { it.uri == uri }
        .map { it.id }

    @Synchronized
    fun upsert(item: VideoItem) { items[item.id] = item; save() }

    /** Apply a set of metadata changes with one disk write instead of one write per item. */
    @Synchronized
    fun upsertAll(values: Iterable<VideoItem>) {
        values.forEach { items[it.id] = it }
        save()
    }

    @Synchronized
    fun remove(id: String) { if (items.remove(id) != null) save() }

    @Synchronized
    fun removeAll(ids: Iterable<String>) {
        var changed = false
        ids.forEach { changed = items.remove(it) != null || changed }
        if (changed) save()
    }

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
                
                // Validate required fields (id and uri) before constructing VideoItem
                val id = value.optString("id", "").takeIf { it.isNotBlank() }
                val uriString = value.optString("uri", "").takeIf { it.isNotBlank() }
                
                if (id == null || uriString == null) {
                    // Skip malformed entries instead of crashing
                    continue
                }
                
                val item = VideoItem(
                    id = id,
                    uri = Uri.parse(uriString),
                    title = value.optString("title", "Untitled video").takeIf { it.isNotBlank() } ?: "Untitled video",
                    durationMs = value.optLong("durationMs", 0L).coerceAtLeast(0L),
                    sizeBytes = value.optLong("sizeBytes", 0L).coerceAtLeast(0L),
                    dateModifiedMs = value.optLong("dateModifiedMs", 0L).coerceAtLeast(0L),
                    relativePath = if (value.isNull("relativePath")) null else value.optString("relativePath").takeIf { it.isNotBlank() },
                    mimeType = if (value.isNull("mimeType")) null else value.optString("mimeType").takeIf { it.isNotBlank() },
                    lastPositionMs = value.optLong("lastPositionMs", 0L).coerceAtLeast(0L),
                    lastPlayedAtMs = value.optLong("lastPlayedAtMs", 0L).coerceAtLeast(0L),
                    addedAtMs = value.optLong("addedAtMs", System.currentTimeMillis()).coerceAtLeast(0L),
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

    private fun StringBuilder.separator() { if (length > 1) append(',') }
    private fun StringBuilder.stringField(name: String, value: String) { separator(); append('"').append(name).append("\":\"").append(escapeJson(value)).append('"') }
    private fun StringBuilder.nullableField(name: String, value: String?) { separator(); append('"').append(name).append("\":"); if (value == null) append("null") else append('"').append(escapeJson(value)).append('"') }
    private fun StringBuilder.numberField(name: String, value: Long) { separator(); append('"').append(name).append("\":").append(value) }
    private fun StringBuilder.booleanField(name: String, value: Boolean) { separator(); append('"').append(name).append("\":").append(value) }
    private fun escapeJson(value: String): String = buildString(value.length + 8) { value.forEach { char -> when (char) { '\\' -> append("\\\\"); '"' -> append("\\\""); '\b' -> append("\\b"); '\u000c' -> append("\\f"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> append(char) } } }
}
