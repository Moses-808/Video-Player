package com.innotrepid.videoplayer.intelligence

import android.net.Uri
import com.innotrepid.videoplayer.library.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun sameNamedSeasonFoldersFromDifferentSeriesStayIsolated() {
        val videos = listOf(
            video("got1", "Episode 1", "Game of Thrones/Sn1"),
            video("got2", "Episode 2", "Game of Thrones/Sn1"),
            video("other1", "Episode 1", "Other Series/Sn1"),
            video("other2", "Episode 2", "Other Series/Sn1")
        )

        val queue = VideoSessionQueue.create(videos, "got1")!!

        assertEquals(listOf("Episode 1", "Episode 2"), queue.remaining.map { it.title }.let { listOf(queue.current!!.title) + it })
        assertEquals("Episode 2", queue.next!!.title)
        assertNull(queue.advance()!!.next)
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

    @Test
    fun retreatMovesBackWithoutLeavingSession() {
        val videos = listOf(
            video("1", "Episode 1", "Show"),
            video("2", "Episode 2", "Show"),
            video("3", "Episode 3", "Show"),
            video("4", "Bonus", "Other")
        )
        val queue = VideoSessionQueue.create(videos, "3")!!

        val previous = queue.retreat()!!

        assertEquals("Episode 2", previous.current!!.title)
        assertEquals("Episode 1", previous.previous!!.title)
        assertEquals("Episode 3", previous.next!!.title)
        assertFalse(previous.isFirst)
    }

    @Test
    fun guardedTransitionsRejectStaleCurrentIds() {
        val queue = VideoSessionQueue.create(
            listOf(video("1", "Episode 1", "Show"), video("2", "Episode 2", "Show"), video("3", "Episode 3", "Show")),
            "2"
        )!!

        assertEquals("Episode 3", queue.advanceIfCurrent("2")!!.current!!.title)
        assertNull(queue.advanceIfCurrent("1"))
        assertEquals("Episode 1", queue.retreatIfCurrent("2")!!.current!!.title)
        assertNull(queue.retreatIfCurrent("3"))
    }

    @Test
    fun retreatAtFirstItemCannotMovePastSessionBoundary() {
        val queue = VideoSessionQueue.create(
            listOf(video("1", "Episode 1", "Show"), video("2", "Episode 2", "Show")),
            "1"
        )!!

        assertTrue(queue.isFirst)
        assertNull(queue.previous)
        assertNull(queue.retreat())
    }

    @Test
    fun rejectsEmptyOrUnknownSessions() {
        assertNull(VideoSessionQueue.create(emptyList(), "missing"))
        assertNull(VideoSessionQueue.create(listOf(video("1", "A", "Show")), "missing"))
    }

    @Test
    fun singleItemSessionHasNoNeighborsAndCannotAdvanceOrRetreat() {
        val queue = VideoSessionQueue.create(listOf(video("1", "Only", "Show")), "1")!!

        assertEquals("Only", queue.current!!.title)
        assertTrue(queue.isFirst)
        assertTrue(queue.isLast)
        assertNull(queue.previous)
        assertNull(queue.next)
        assertTrue(queue.remaining.isEmpty())
        assertNull(queue.advance())
        assertNull(queue.retreat())
    }
}
