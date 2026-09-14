package com.innotrepid.videoplayer.library

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files

class VideoLibraryPersistenceTest {
    private fun newFile(): File = Files.createTempDirectory("video-library-").toFile().resolve("video_library.json")

    @Test
    fun metadataIsPersistedWithFavoriteAndProgress() {
        val file = newFile()
        try {
            val uri = Mockito.mock(Uri::class.java)
            Mockito.`when`(uri.toString()).thenReturn("content://videos/1")

            val library = VideoLibrary(file)
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

            val persisted = file.readText()

            assertTrue(persisted.contains("\"id\":\"1\""))
            assertTrue(persisted.contains("\"title\":\"Episode 1\""))
            assertTrue(persisted.contains("\"relativePath\":\"Show/Season 1/\""))
            assertTrue(persisted.contains("\"isFavorite\":true"))
            assertTrue(persisted.contains("\"lastPositionMs\":42000"))
            assertTrue(persisted.contains("\"durationMs\":100000"))
            assertTrue(persisted.contains("\"uri\":\"content://videos/1\""))
        } finally {
            file.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun missingAndUnknownIdsAreNoOps() {
        val file = newFile()
        try {
            val library = VideoLibrary(file)

            library.toggleFavorite("missing")
            library.updateProgress("missing", 10_000L, 20_000L)
            library.markCompleted("missing")
            library.remove("missing")

            assertTrue(library.all().isEmpty())
        } finally {
            file.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun completedVideoClearsResumePosition() {
        val file = newFile()
        try {
            val uri = Mockito.mock(Uri::class.java)
            Mockito.`when`(uri.toString()).thenReturn("content://videos/2")
            val library = VideoLibrary(file)

            library.upsert(VideoItem("2", uri, "Movie", durationMs = 120_000L, lastPositionMs = 60_000L))
            assertTrue(library.find("2")!!.isResumeable)

            library.markCompleted("2")

            assertEquals(0L, library.find("2")!!.lastPositionMs)
            assertFalse(library.find("2")!!.isResumeable)
        } finally {
            file.parentFile?.deleteRecursively()
        }
    }
}
