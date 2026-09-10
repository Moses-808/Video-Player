package com.innotrepid.videoplayer.library

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class VideoLibraryPersistenceTest {
    @Test
    fun metadataIsPersistedWithFavoriteAndProgress() {
        val directory = createTempDir(prefix = "video-library-")
        try {
            val context = Mockito.mock(Context::class.java)
            Mockito.`when`(context.filesDir).thenReturn(directory)
            val uri = Mockito.mock(Uri::class.java)
            Mockito.`when`(uri.toString()).thenReturn("content://videos/1")

            val library = VideoLibrary(context)
            library.upsert(
                VideoItem(
                    id = "1",
                    uri = uri,
                    title = "Episode 1",
                    durationMs = 100_000L,
                    relativePath = "Show/Season 1/",
                    isFavorite = false
                )
            )
            library.toggleFavorite("1")
            library.updateProgress("1", 42_000L, 100_000L)

            val persisted = JSONArray(directory.resolve("video_library.json").readText())
                .getJSONObject(0)

            assertEquals("1", persisted.getString("id"))
            assertEquals("Episode 1", persisted.getString("title"))
            assertEquals("Show/Season 1/", persisted.getString("relativePath"))
            assertTrue(persisted.getBoolean("isFavorite"))
            assertEquals(42_000L, persisted.getLong("lastPositionMs"))
            assertEquals(100_000L, persisted.getLong("durationMs"))
            assertEquals("content://videos/1", persisted.getString("uri"))
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
