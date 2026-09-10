package com.innotrepid.videoplayer.intelligence

import android.net.Uri
import com.innotrepid.videoplayer.library.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class VideoSessionQueueTest {
    private fun video(id: String, title: String, folder: String) = VideoItem(
        id = id,
        uri = mock(Uri::class.java),
        title = title,
        relativePath = "$folder/"
    )

    @Test
    fun ordersEpisodesNaturallyAndStaysWithinSelectedFolder() {
        val queue = VideoSessionQueue.create(
            listOf(
                video("3", "Episode 10", "Show"),
                video("1", "Episode 2", "Show"),
                video("2", "Episode 1", "Show"),
                video("4", "Bonus", "Other")
            ),
            "1"
        )!!

        assertEquals("Episode 2", queue.current!!.title)
        assertEquals("Episode 10", queue.next!!.title)
        assertEquals("Episode 1", queue.previous!!.title)
        assertEquals(listOf("Episode 10"), queue.remaining.map { it.title })
    }

    @Test
    fun moveToAndAdvanceTrackSessionPosition() {
        val videos = listOf(video("1", "A", "Show"), video("2", "B", "Show"), video("3", "C", "Show"))
        val queue = VideoSessionQueue.create(videos, "1")!!

        val moved = queue.moveTo("2")!!
        assertEquals("B", moved.current!!.title)
        assertEquals("C", moved.next!!.title)
        assertEquals("C", moved.advance()!!.current!!.title)
        assertNull(moved.advance()!!.next)
    }
}
