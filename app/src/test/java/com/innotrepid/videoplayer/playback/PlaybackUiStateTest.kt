package com.innotrepid.videoplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackUiStateTest {
    @Test
    fun defaultsRepresentStoppedPlayer() {
        val state = PlaybackUiState()
        assertFalse(state.isPlaying)
        assertEquals(0L, state.positionMs)
        assertEquals(1f, state.playbackSpeed)
        assertEquals(1f, state.volume)
    }

    @Test
    fun stateCanRepresentSynchronizedControlValues() {
        val state = PlaybackUiState(isPlaying = true, positionMs = 12_000L, durationMs = 60_000L)
        assertEquals(true, state.isPlaying)
        assertEquals(12_000L, state.positionMs)
        assertEquals(60_000L, state.durationMs)
    }
}
