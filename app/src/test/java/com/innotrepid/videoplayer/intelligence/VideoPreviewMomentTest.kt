package com.innotrepid.videoplayer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPreviewMomentTest {
    @Test
    fun unknownDurationStartsAtZero() {
        assertEquals(0L, VideoPreviewMoment.startPositionMs(0L))
    }

    @Test
    fun shortVideoStartsNearBeginning() {
        assertEquals(2_000L, VideoPreviewMoment.startPositionMs(10_000L))
    }

    @Test
    fun mediumVideoStartsAtTenPercent() {
        assertEquals(5_000L, VideoPreviewMoment.startPositionMs(50_000L))
    }

    @Test
    fun longVideoCapsPreviewOffset() {
        assertEquals(30_000L, VideoPreviewMoment.startPositionMs(600_000L))
    }

    @Test
    fun veryShortVideoNeverSeeksPastEnd() {
        assertEquals(0L, VideoPreviewMoment.startPositionMs(900L))
    }

    @Test
    fun resumePositionBecomesPreviewMoment() {
        assertEquals(
            42_000L,
            VideoPreviewMoment.startPositionMs(300_000L, resumePositionMs = 42_000L)
        )
    }

    @Test
    fun resumeNearCreditsFallsBackToRepresentativeMoment() {
        assertEquals(
            30_000L,
            VideoPreviewMoment.startPositionMs(600_000L, resumePositionMs = 580_000L)
        )
    }
}
