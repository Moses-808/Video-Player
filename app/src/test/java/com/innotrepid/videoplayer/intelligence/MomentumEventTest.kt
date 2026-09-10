package com.innotrepid.videoplayer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentumEventTest {
    @Test
    fun videoStarted_preservesPlaybackContext() {
        val event = MomentumEvent.VideoStarted(
            mediaId = "media:42",
            positionMs = 12_500L,
            timestampMs = 99L
        )

        assertEquals("media:42", event.mediaId)
        assertEquals(12_500L, event.positionMs)
        assertEquals(99L, event.timestampMs)
    }

    @Test
    fun playbackEvents_areDistinctSignals() {
        val events = listOf<MomentumEvent>(
            MomentumEvent.VideoStarted("v", 0L, 1L),
            MomentumEvent.VideoPaused("v", 5_000L, 2L),
            MomentumEvent.VideoSeeked("v", 5_000L, 20_000L, 3L),
            MomentumEvent.VideoCompleted("v", 60_000L, 4L),
            MomentumEvent.VideoSkipped("v", 25_000L, 60_000L, 5L)
        )

        assertEquals(5, events.size)
        assertTrue(events[2] is MomentumEvent.VideoSeeked)
        assertEquals(20_000L, (events[2] as MomentumEvent.VideoSeeked).toPositionMs)
    }
}
