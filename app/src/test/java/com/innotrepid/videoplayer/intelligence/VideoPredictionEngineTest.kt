package com.innotrepid.videoplayer.intelligence

import android.net.Uri
import com.innotrepid.videoplayer.library.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class VideoPredictionEngineTest {
    @Test
    fun completedVideoFollowedBySameFolderStartRanksContinuationFirst() {
        val videos = listOf(
            video("episode-1", "Episode 1", "show/season-1"),
            video("episode-2", "Episode 2", "show/season-1"),
            video("movie", "Movie", "movies")
        )

        val predictions = VideoPredictionEngine.predict(
            videos = videos,
            events = listOf(
                MomentumEvent.VideoCompleted("episode-1", 60_000L, 1_000L),
                MomentumEvent.VideoStarted("episode-2", 0L, 2_000L)
            ),
            nowMs = 2_000L,
            currentMediaId = "episode-1"
        )

        assertEquals("episode-2", predictions.first().mediaId)
        assertTrue(predictions.first().reasons.contains(VideoPrediction.Reason.SAME_FOLDER_CONTINUATION))
    }

    @Test
    fun learnedSeekRegionIsAttachedToPrediction() {
        val videos = listOf(video("v", "Video", "show"))
        val events = listOf(
            MomentumEvent.VideoSeeked("v", 0L, 120_000L, 1_000L),
            MomentumEvent.VideoSeeked("v", 10_000L, 130_000L, 2_000L)
        )

        val prediction = VideoPredictionEngine.predict(videos, events).single()

        assertEquals(125_000L, prediction.previewPositionMs)
    }

    @Test
    fun emptyEventHistoryStillProvidesNaturalFallback() {
        val videos = listOf(
            video("a", "A", "show"),
            video("b", "B", "show")
        )

        val predictions = VideoPredictionEngine.predict(
            videos = videos,
            events = emptyList(),
            nowMs = 5_000L
        )

        assertEquals(2, predictions.size)
        assertTrue(predictions.all { it.reasons.contains(VideoPrediction.Reason.NATURAL_ORDER) || it.reasons.contains(VideoPrediction.Reason.SAME_FOLDER_CONTINUATION) })
    }

    private fun video(id: String, title: String, folder: String) = VideoItem(
        id = id,
        uri = mock(Uri::class.java),
        title = title,
        durationMs = 300_000L,
        relativePath = folder
    )
}
