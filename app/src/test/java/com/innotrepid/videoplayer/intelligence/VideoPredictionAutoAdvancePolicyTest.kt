package com.innotrepid.videoplayer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPredictionAutoAdvancePolicyTest {
    @Test
    fun strongPredictionCanOverrideDeterministicNext() {
        val result = VideoPredictionAutoAdvancePolicy.choose(
            deterministicNext = VideoPredictionAutoAdvancePolicy.Candidate("episode-2", 0.52f),
            predictions = listOf(VideoPredictionAutoAdvancePolicy.Candidate("episode-7", 0.86f))
        )

        assertEquals("episode-7", result)
    }

    @Test
    fun weakPredictionKeepsDeterministicNext() {
        val result = VideoPredictionAutoAdvancePolicy.choose(
            deterministicNext = VideoPredictionAutoAdvancePolicy.Candidate("episode-2", 0.60f),
            predictions = listOf(VideoPredictionAutoAdvancePolicy.Candidate("other", 0.69f))
        )

        assertEquals("episode-2", result)
    }

    @Test
    fun smallConfidenceLeadDoesNotBreakSessionFlow() {
        val result = VideoPredictionAutoAdvancePolicy.choose(
            deterministicNext = VideoPredictionAutoAdvancePolicy.Candidate("episode-2", 0.68f),
            predictions = listOf(VideoPredictionAutoAdvancePolicy.Candidate("other", 0.76f))
        )

        assertEquals("episode-2", result)
    }

    @Test
    fun strongPredictionIsUsedWhenNoDeterministicNextExists() {
        val result = VideoPredictionAutoAdvancePolicy.choose(
            deterministicNext = null,
            predictions = listOf(VideoPredictionAutoAdvancePolicy.Candidate("other", 0.82f))
        )

        assertEquals("other", result)
    }

    @Test
    fun matchingPredictionKeepsDeterministicNext() {
        val result = VideoPredictionAutoAdvancePolicy.choose(
            deterministicNext = VideoPredictionAutoAdvancePolicy.Candidate("episode-2", 0.75f),
            predictions = listOf(VideoPredictionAutoAdvancePolicy.Candidate("episode-2", 0.91f))
        )

        assertEquals("episode-2", result)
    }

    @Test
    fun invalidPredictionIdsAreIgnored() {
        val result = VideoPredictionAutoAdvancePolicy.choose(
            deterministicNext = VideoPredictionAutoAdvancePolicy.Candidate("episode-2", 0.60f),
            predictions = listOf(VideoPredictionAutoAdvancePolicy.Candidate("", 1f))
        )

        assertEquals("episode-2", result)
    }
}
