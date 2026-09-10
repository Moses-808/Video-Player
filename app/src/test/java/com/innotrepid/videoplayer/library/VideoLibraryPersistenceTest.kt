package com.innotrepid.videoplayer.library

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.File

class VideoLibraryPersistenceTest {
    @Test
    fun metadataSurvivesReloadAndPreservesFavoriteAndProgress() {
        val directory = createTempDir(prefix = "video-library-")
        try {
            val context = Mockito.mock(Context::class.java)
            Mockito.`when`(context.filesDir).thenReturn(directory)
            val uri = Mockito.mock(Uri::class.java)
            Mockito.`when`(uri.toString()).thenReturn("content://videos/1")

            val first = VideoLibrary(context)
            first.upsert(
                VideoItem(
                    id = "1",
                    uri = uri,
                    title = "Episode 1",
                    durationMs = 100_000L,
                    relativePath = "Show/Season 1/",
                    isFavorite = false
                )
            )
            first.toggleFavorite("1")
            first.updateProgress("1", 42_000L, 100_000L)

            val reloaded = VideoLibrary(context)
            val restored = reloaded.find("1")!!

            assertEquals("Episode 1", restored.title)
            assertEquals("Show/Season 1/", restored.relativePath)
            assertTrue(restored.isFavorite)
            assertEquals(42_000L, restored.lastPositionMs)
            assertEquals(100_000L, restored.durationMs)
            assertEquals("content://videos/1", restored.uri.toString())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingAndUnknownIdsAreNoOps() {
        val directory = createTempDir(prefix = "video-library-")
        try {
            val context = Mockito.mock(Context::class.java)
            Mockito.`when`(context.filesDir).thenReturn(directory)
            val library = VideoLibrary(context)

            library.toggleFavorite("missing")
            library.updateProgress("missing", 10_000L, 20_000L)
            library.markCompleted("missing")
            library.remove("missing")

            assertTrue(library.all().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun completedVideoClearsResumePosition() {
        val directory = createTempDir(prefix = "video-library-")
        try {
            val context = Mockito.mock(Context::class.java)
            Mockito.`when`(context.filesDir).thenReturn(directory)
            val uri = Mockito.mock(Uri::class.java)
            Mockito.`when`(uri.toString()).thenReturn("content://videos/2")
            val library = VideoLibrary(context)

            library.upsert(VideoItem("2", uri, "Movie", durationMs = 120_000L, lastPositionMs = 60_000L))
            assertTrue(library.find("2")!!.isResumeable)

            library.markCompleted("2")

            assertEquals(0L, library.find("2")!!.lastPositionMs)
            assertFalse(library.find("2")!!.isResumeable)
        } finally {
            directory.deleteRecursively()
        }
    }
}
