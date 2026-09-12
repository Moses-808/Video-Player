package com.innotrepid.videoplayer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPredictionFeedbackTest {
    @Test
    fun confidenceStaysNeutralBeforeEnoughExposure() {
        assertEquals(0.7f, VideoPredictionFeedback.adjustedConfidence(0.7f, 2, 2), 0.0001f)
    }

    @Test
    fun repeatedAcceptanceRaisesConfidence() {
        assertEquals(0.85f, VideoPredictionFeedback.adjustedConfidence(0.7f, 10, 10), 0.0001f)
    }

    @Test
    fun repeatedIgnoreLowersConfidence() {
        assertEquals(0.55f, VideoPredictionFeedback.adjustedConfidence(0.7f, 10, 0), 0.0001f)
    }

    @Test
    fun mixedFeedbackProducesSmallAdjustment() {
        assertEquals(0.73f, VideoPredictionFeedback.adjustedConfidence(0.7f, 10, 6), 0.0001f)
    }

    @Test
    fun confidenceRemainsBounded() {
        assertEquals(1f, VideoPredictionFeedback.adjustedConfidence(0.99f, 10, 10), 0.0001f)
        assertEquals(0f, VideoPredictionFeedback.adjustedConfidence(0.01f, 10, 0), 0.0001f)
    }

    @Test
    fun feedbackCanChangePredictionOrder() {
        val accepted = VideoPredictionFeedback.adjustedConfidence(0.70f, 10, 10)
        val ignored = VideoPredictionFeedback.adjustedConfidence(0.74f, 10, 0)

        assertTrue(accepted > ignored)
    }
}
