package com.innotrepid.videoplayer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBehaviorAnalyzerTest {
    @Test
    fun emptyTelemetryProducesNoDerivedSignals() {
        assertTrue(VideoBehaviorAnalyzer.analyze(emptyList()).isEmpty())
    }

    @Test
    fun analyzerPreservesInterpreterSemantics() {
        val events = listOf(
            MomentumEvent.VideoStarted("video-a", 0L, 1_000L),
            MomentumEvent.VideoCompleted("video-a", 100_000L, 50_000L),
            MomentumEvent.VideoStarted("video-b", 0L, 51_000L)
        )
        val contexts = mapOf(
            "video-a" to VideoBehaviorSignalInterpreter.VideoContext("video-a", "folder", 100_000L),
            "video-b" to VideoBehaviorSignalInterpreter.VideoContext("video-b", "folder", 100_000L)
        )

        val signals = VideoBehaviorAnalyzer.analyze(events, contexts, nowMs = 51_000L)

        assertEquals(2, signals.count { it is VideoBehaviorSignal.WatchStarted })
        val continuation = signals.filterIsInstance<VideoBehaviorSignal.ContinuedFromPrevious>()
        assertEquals(1, continuation.size)
        assertEquals("video-a", continuation.single().previousMediaId)
        assertTrue(continuation.single().sameFolder)
    }
}
