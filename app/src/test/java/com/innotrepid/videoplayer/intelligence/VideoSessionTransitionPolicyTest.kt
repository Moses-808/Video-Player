package com.innotrepid.videoplayer.intelligence

import android.net.Uri
import com.innotrepid.videoplayer.library.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class VideoSessionTransitionPolicyTest {
    private fun video(id: String, title: String) = VideoItem(
        id = id,
        uri = mock(Uri::class.java),
        title = title,
        relativePath = "Show/"
    )

    @Test
    fun nextAcceptsOnlyTheCurrentItem() {
        val queue = VideoSessionQueue.create(
            listOf(video("1", "Episode 1"), video("2", "Episode 2"), video("3", "Episode 3")),
            "2"
        )!!

        assertEquals("Episode 3", VideoSessionTransitionPolicy.next(queue, "2")!!.current!!.title)
        assertNull(VideoSessionTransitionPolicy.next(queue, "1"))
        assertNull(VideoSessionTransitionPolicy.next(queue, "3"))
    }

    @Test
    fun previousAcceptsOnlyTheCurrentItem() {
        val queue = VideoSessionQueue.create(
            listOf(video("1", "Episode 1"), video("2", "Episode 2"), video("3", "Episode 3")),
            "2"
        )!!

        assertEquals("Episode 1", VideoSessionTransitionPolicy.previous(queue, "2")!!.current!!.title)
        assertNull(VideoSessionTransitionPolicy.previous(queue, "1"))
        assertNull(VideoSessionTransitionPolicy.previous(queue, "3"))
    }

    @Test
    fun nullQueueProducesNoTransition() {
        assertNull(VideoSessionTransitionPolicy.next(null, "1"))
        assertNull(VideoSessionTransitionPolicy.previous(null, "1"))
    }
}
