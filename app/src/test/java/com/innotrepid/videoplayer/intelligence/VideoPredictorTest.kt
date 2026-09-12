package com.innotrepid.videoplayer.intelligence

import android.net.Uri
import com.innotrepid.videoplayer.library.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPredictorTest {
    @Test
    fun sameFolderResumeableVideoRanksAboveUnrelatedVideo() {
        val videos = listOf(
            video("episode-2", "Episode 2", "show/season-1", 20_000L, 10_000L, 1_000L),
            video("movie", "Movie", "movies", 20_000L, 0L, 0L)
        )
        val predictions = VideoPredictor.predict(
            videos = videos,
            signals = emptyList(),
            context = VideoPredictor.CandidateContext(
                currentMediaId = "episode-1",
                currentFolderKey = "show/season-1",
                nowMs = 5_000L
            )
        )

        assertEquals("episode-2", predictions.first().mediaId)
        assertTrue(predictions.first().reasons.contains(VideoPrediction.Reason.SAME_FOLDER_CONTINUATION))
        assertTrue(predictions.first().reasons.contains(VideoPrediction.Reason.RESUMEABLE))
    }

    @Test
    fun earlyAbandonmentReducesPredictionScore() {
        val candidate = video("a", "A", "show", 20_000L)
        val base = VideoPredictor.predict(
            listOf(candidate),
            emptyList(),
            VideoPredictor.CandidateContext(currentFolderKey = "show", nowMs = 10_000L)
        ).single()
        val abandoned = VideoPredictor.predict(
            listOf(candidate),
            listOf(VideoBehaviorSignal.WatchAbandonedEarly("a", 0.05f, 9_000L, 0.95f)),
            VideoPredictor.CandidateContext(currentFolderKey = "show", nowMs = 10_000L)
        ).single()

        assertTrue(abandoned.score < base.score)
        assertTrue(abandoned.reasons.contains(VideoPrediction.Reason.RECENT_ABANDONMENT))
    }

    @Test
    fun rewatchedCandidateGetsAdditionalEvidence() {
        val candidate = video("a", "A", "show", 20_000L)
        val prediction = VideoPredictor.predict(
            listOf(candidate),
            listOf(VideoBehaviorSignal.Rewatched("a", 8_000L)),
            VideoPredictor.CandidateContext(nowMs = 9_000L)
        ).single()

        assertTrue(prediction.reasons.contains(VideoPrediction.Reason.REWATCHED))
        assertTrue(prediction.reasons.contains(VideoPrediction.Reason.RECENTLY_ENGAGED))
    }

    @Test
    fun currentVideoIsNeverPredictedAsNext() {
        val videos = listOf(
            video("a", "A", "show", 20_000L),
            video("b", "B", "show", 20_000L)
        )

        val predictions = VideoPredictor.predict(
            videos,
            emptyList(),
            VideoPredictor.CandidateContext(currentMediaId = "a", currentFolderKey = "show")
        )

        assertEquals(listOf("b"), predictions.map { it.mediaId })
    }

    private fun video(
        id: String,
        title: String,
        folder: String?,
        durationMs: Long,
        positionMs: Long = 0L,
        lastPlayedAtMs: Long = 0L
    ) = VideoItem(
        id = id,
        uri = Uri.EMPTY,
        title = title,
        durationMs = durationMs,
        relativePath = folder,
        lastPositionMs = positionMs,
        lastPlayedAtMs = lastPlayedAtMs
    )
}
