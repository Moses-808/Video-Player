package com.innotrepid.videoplayer.intelligence

import android.net.Uri
import com.innotrepid.videoplayer.library.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class PlaybackTransitionCoordinatorTest {
    private fun video(id: String, title: String, folder: String = "Show/") = VideoItem(
        id = id,
        uri = mock(Uri::class.java),
        title = title,
        relativePath = folder
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

    @Test
    fun nextStopsAtTheEndOfTheSession() {
        val queue = VideoSessionQueue.create(
            listOf(video("1", "Episode 1"), video("2", "Episode 2")),
            "2"
        )!!

        assertNull(PlaybackTransitionCoordinator.next(queue, "2"))
    }

    @Test
    fun previousStopsAtTheStartOfTheSession() {
        val queue = VideoSessionQueue.create(
            listOf(video("1", "Episode 1"), video("2", "Episode 2")),
            "1"
        )!!

        assertNull(PlaybackTransitionCoordinator.previous(queue, "1"))
    }

    @Test
    fun strongPredictionCanSelectAnotherItemInsideSession() {
        val queue = VideoSessionQueue.create(
            listOf(
                video("1", "Episode 1"),
                video("2", "Episode 2"),
                video("3", "Episode 3")
            ),
            "1"
        )!!

        val result = PlaybackTransitionCoordinator.predictiveNext(
            queue = queue,
            sourceId = "1",
            predictions = listOf(
                VideoPredictionAutoAdvancePolicy.Candidate("3", 0.86f)
            )
        )

        assertEquals("3", result?.current?.id)
    }

    @Test
    fun weakPredictionKeepsDeterministicNext() {
        val queue = VideoSessionQueue.create(
            listOf(
                video("1", "Episode 1"),
                video("2", "Episode 2"),
                video("3", "Episode 3")
            ),
            "1"
        )!!

        val result = PlaybackTransitionCoordinator.predictiveNext(
            queue = queue,
            sourceId = "1",
            predictions = listOf(
                VideoPredictionAutoAdvancePolicy.Candidate("3", 0.66f)
            )
        )

        assertEquals("2", result?.current?.id)
    }

    @Test
    fun predictionOutsideSessionCannotBreakFolderContract() {
        val queue = VideoSessionQueue.create(
            listOf(
                video("1", "Episode 1", "Show/"),
                video("2", "Episode 2", "Show/"),
                video("other", "Other", "Other/")
            ),
            "1"
        )!!

        val result = PlaybackTransitionCoordinator.predictiveNext(
            queue = queue,
            sourceId = "1",
            predictions = listOf(
                VideoPredictionAutoAdvancePolicy.Candidate("other", 0.99f)
            )
        )

        assertEquals("2", result?.current?.id)
    }
}
