package com.innotrepid.videoplayer.library

import android.net.Uri
import org.junit.Assert.assertEquals
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
    fun favoriteStateIsPartOfTheVideoModel() {
        assertTrue(video("1", "Saved", favorite = true).isFavorite)
    }
}
