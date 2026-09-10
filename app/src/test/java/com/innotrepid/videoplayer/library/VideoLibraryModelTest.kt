package com.innotrepid.videoplayer.library

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class VideoLibraryModelTest {
    private fun video(id: String, title: String, folder: String? = null, favorite: Boolean = false) = VideoItem(
        id = id,
        uri = mock(Uri::class.java),
        title = title,
        relativePath = folder?.let { "$it/" },
        isFavorite = favorite
    )

    @Test
    fun folderNameComesFromLastPathSegment() {
        assertEquals("Season 1", video("1", "Episode 1", "Show/Season 1").folderName)
        assertEquals(null, video("2", "Loose clip").folderName)
    }

    @Test
    fun progressIsClampedAndResumeRequiresMeaningfulProgress() {
        val video = VideoItem("1", mock(Uri::class.java), "Movie", durationMs = 100_000L, lastPositionMs = 120_000L)
        assertEquals(1f, video.progress)
        assertTrue(!video.isResumeable)

        val resumable = video.copy(lastPositionMs = 50_000L)
        assertEquals(.5f, resumable.progress)
        assertTrue(resumable.isResumeable)
    }

    @Test
    fun resumeabilityHandlesBoundaryAndMissingDurationCases() {
        val uri = mock(Uri::class.java)

        assertFalse(VideoItem("1", uri, "Unknown", durationMs = 0L, lastPositionMs = 10_000L).isResumeable)
        assertFalse(VideoItem("2", uri, "Start", durationMs = 100_000L, lastPositionMs = 5_000L).isResumeable)
        assertTrue(VideoItem("3", uri, "Almost done", durationMs = 100_000L, lastPositionMs = 94_999L).isResumeable)
        assertFalse(VideoItem("4", uri, "Finished", durationMs = 100_000L, lastPositionMs = 95_000L).isResumeable)
    }

    @Test
    fun progressIsZeroWhenDurationIsUnknown() {
        val video = VideoItem("1", mock(Uri::class.java), "Unknown", durationMs = 0L, lastPositionMs = 10_000L)
        assertEquals(0f, video.progress)
    }

    @Test
    fun favoriteStateIsPartOfTheVideoModel() {
        assertTrue(video("1", "Saved", favorite = true).isFavorite)
    }
}
