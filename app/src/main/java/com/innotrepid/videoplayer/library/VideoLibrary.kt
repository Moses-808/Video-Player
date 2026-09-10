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

    init {
        load()
    }

    @Synchronized
    fun all(): List<VideoItem> = items.values.sortedWith(
        compareByDescending<VideoItem> { it.lastPlayedAtMs }
            .thenByDescending { it.addedAtMs }
    )

    @Synchronized
    fun find(id: String): VideoItem? = items[id]

    @Synchronized
    fun upsert(item: VideoItem) {
        items[item.id] = item
        save()
    }

    @Synchronized
    fun remove(id: String) {
        items.remove(id)
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
        items[id] = current.copy(
            lastPositionMs = 0L,
            lastPlayedAtMs = System.currentTimeMillis()
        )
        save()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val array = JSONArray(file.readText())
            for (index in 0 until array.length()) {
                val objectValue = array.getJSONObject(index)
                val item = VideoItem(
                    id = objectValue.getString("id"),
                    uri = Uri.parse(objectValue.getString("uri")),
                    title = objectValue.optString("title", "Untitled video"),
                    durationMs = objectValue.optLong("durationMs", 0L),
                    sizeBytes = objectValue.optLong("sizeBytes", 0L),
                    dateModifiedMs = objectValue.optLong("dateModifiedMs", 0L),
                    relativePath = objectValue.optString("relativePath", null),
                    mimeType = objectValue.optString("mimeType", null),
                    lastPositionMs = objectValue.optLong("lastPositionMs", 0L),
                    lastPlayedAtMs = objectValue.optLong("lastPlayedAtMs", 0L),
                    addedAtMs = objectValue.optLong("addedAtMs", System.currentTimeMillis())
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
                })
            }
            file.writeText(array.toString())
        }
    }
}
