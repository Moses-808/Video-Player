package com.innotrepid.videoplayer.library

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small JSON-backed local metadata store. Original video bytes never live here. */
class VideoLibrary(context: Context) {
    private val file = File(context.filesDir, "video_library.json")
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
                    relativePath = value.optString("relativePath", null),
                    mimeType = value.optString("mimeType", null),
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
        runCatching {
            val array = JSONArray()
            items.values.forEach { item ->
                array.put(JSONObject().apply {
                    put("id", item.id)
                    put("uri", item.uri.toString())
                    put("title", item.title)
                    put("durationMs", item.durationMs)
                    put("sizeBytes", item.sizeBytes)
                    put("dateModifiedMs", item.dateModifiedMs)
                    put("relativePath", item.relativePath)
                    put("mimeType", item.mimeType)
                    put("lastPositionMs", item.lastPositionMs)
                    put("lastPlayedAtMs", item.lastPlayedAtMs)
                    put("addedAtMs", item.addedAtMs)
                    put("isFavorite", item.isFavorite)
                })
            }
            file.writeText(array.toString())
        }
    }
}
