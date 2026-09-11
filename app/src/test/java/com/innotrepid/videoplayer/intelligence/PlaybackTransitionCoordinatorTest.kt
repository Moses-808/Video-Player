package com.innotrepid.videoplayer.intelligence

import android.net.Uri
import com.innotrepid.videoplayer.library.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class PlaybackTransitionCoordinatorTest {
    private fun video(id: String, title: String) = VideoItem(
        id = id,
        uri = mock(Uri::class.java),
        title = title,
        relativePath = "Show/"
    )

    @Test
    fun nextAcceptsOnlyTheCurrentSource() {
        val queue = VideoSessionQueue.create(
            listOf(video("1", "Episode 1"), video("2", "Episode 2")),
            "1"
        )!!

        assertEquals("Episode 2", PlaybackTransitionCoordinator.next(queue, "1")!!.current!!.title)
        assertNull(PlaybackTransitionCoordinator.next(queue, "2"))
    }

    @Test
    fun previousAcceptsOnlyTheCurrentSource() {
        val queue = VideoSessionQueue.create(
            listOf(video("1", "Episode 1"), video("2", "Episode 2")),
            "2"
        )!!

        assertEquals("Episode 1", PlaybackTransitionCoordinator.previous(queue, "2")!!.current!!.title)
        assertNull(PlaybackTransitionCoordinator.previous(queue, "1"))
    }
}
