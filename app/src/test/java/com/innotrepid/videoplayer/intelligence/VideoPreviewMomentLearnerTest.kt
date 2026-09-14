package com.innotrepid.videoplayer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoPreviewMomentLearnerTest {
    @Test
    fun repeatedSeeksProduceRepresentativeMoment() {
        val events = listOf(
            MomentumEvent.VideoSeeked("v", 10_000L, 120_000L, 1L),
            MomentumEvent.VideoSeeked("v", 20_000L, 130_000L, 2L),
            MomentumEvent.VideoSeeked("v", 30_000L, 125_000L, 3L),
            MomentumEvent.VideoSeeked("other", 0L, 120_000L, 4L)
        )
        assertEquals(125_000L, VideoPreviewMomentLearner.learnPositionMs("v", 300_000L, events))
    }

    @Test
    fun oneSeekIsNotEnoughEvidence() {
        val events = listOf(MomentumEvent.VideoSeeked("v", 0L, 120_000L, 1L))
        assertNull(VideoPreviewMomentLearner.learnPositionMs("v", 300_000L, events))
    }

    @Test
    fun learnedMomentAvoidsVideoEdges() {
        val events = listOf(
            MomentumEvent.VideoSeeked("v", 0L, 1_000L, 1L),
            MomentumEvent.VideoSeeked("v", 5_000L, 1_500L, 2L)
        )
        assertNull(VideoPreviewMomentLearner.learnPositionMs("v", 100_000L, events))
    }
}
