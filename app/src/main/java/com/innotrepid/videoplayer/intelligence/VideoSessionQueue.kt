package com.innotrepid.videoplayer.intelligence

import com.innotrepid.videoplayer.library.VideoItem

/**
 * A deterministic playback session scoped to the selected video's folder.
 * Keeping the session separate from the live library means rescans cannot
 * silently reorder what the user is currently watching.
 */
class VideoSessionQueue private constructor(
    private val items: List<VideoItem>,
    private val currentIndex: Int
) {
    val current: VideoItem? get() = items.getOrNull(currentIndex)
    val next: VideoItem? get() = items.getOrNull(currentIndex + 1)
    val previous: VideoItem? get() = items.getOrNull(currentIndex - 1)
    val remaining: List<VideoItem> get() = items.drop(currentIndex + 1)
    val isFirst: Boolean get() = currentIndex == 0
    val isLast: Boolean get() = currentIndex >= items.lastIndex

    fun moveTo(id: String): VideoSessionQueue? {
        val index = items.indexOfFirst { it.id == id }
        return if (index >= 0) copy(currentIndex = index) else null
    }

    fun advance(): VideoSessionQueue? =
        if (next != null) copy(currentIndex = currentIndex + 1) else null

    fun retreat(): VideoSessionQueue? =
        if (previous != null) copy(currentIndex = currentIndex - 1) else null

    private fun copy(currentIndex: Int) = VideoSessionQueue(items, currentIndex)

    companion object {
        fun create(videos: List<VideoItem>, selectedId: String): VideoSessionQueue? {
            if (videos.isEmpty()) return null

            val selected = videos.firstOrNull { it.id == selectedId } ?: return null
            val selectedFolder = selected.folderName
            val sessionItems = videos.filter { it.folderName == selectedFolder }
            val ordered = sessionItems.sortedWith(videoQueueComparator())
            val index = ordered.indexOfFirst { it.id == selectedId }

            return if (index >= 0) VideoSessionQueue(ordered, index) else null
        }

        private fun videoQueueComparator(): Comparator<VideoItem> =
            compareBy<VideoItem>({ naturalKey(it.title) }, { it.title.lowercase() }, { it.id })

        private fun naturalKey(title: String): String = buildString {
            var cursor = 0
            Regex("\\d+").findAll(title.lowercase()).forEach { match ->
                append(title.substring(cursor, match.range.first).lowercase())
                append(match.value.toLongOrNull()?.toString()?.padStart(12, '0') ?: match.value)
                cursor = match.range.last + 1
            }
            append(title.substring(cursor).lowercase())
        }
    }
}
