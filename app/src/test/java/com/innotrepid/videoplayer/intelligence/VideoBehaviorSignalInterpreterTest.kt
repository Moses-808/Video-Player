package com.innotrepid.videoplayer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBehaviorSignalInterpreterTest {
    @Test
    fun pauseNearBeginningProducesEarlyAbandonment() {
        val signals = VideoBehaviorSignalInterpreter.interpret(
            events = listOf(
                MomentumEvent.VideoStarted("a", 0L, 1_000L),
                MomentumEvent.VideoPaused("a", 80L, 2_000L)
            ),
            contexts = mapOf("a" to VideoBehaviorSignalInterpreter.VideoContext("a", durationMs = 1_000L)),
            nowMs = 2_000L
        )

        val abandonment = signals.filterIsInstance<VideoBehaviorSignal.WatchAbandonedEarly>()
        assertEquals(1, abandonment.size)
        assertEquals(0.08f, abandonment.single().watchedFraction, 0.001f)
    }

    @Test
    fun pauseAfterMostOfVideoProducesStrongEngagement() {
        val signals = VideoBehaviorSignalInterpreter.interpret(
            events = listOf(
                MomentumEvent.VideoStarted("a", 0L, 1_000L),
                MomentumEvent.VideoPaused("a", 850L, 2_000L)
            ),
            contexts = mapOf("a" to VideoBehaviorSignalInterpreter.VideoContext("a", durationMs = 1_000L))
        )

        val watchedMost = signals.filterIsInstance<VideoBehaviorSignal.WatchedMost>()
        assertEquals(1, watchedMost.size)
        assertTrue(watchedMost.single().strength >= 0.8f)
    }

    @Test
    fun completionProducesCompletionAndWatchedMost() {
        val signals = VideoBehaviorSignalInterpreter.interpret(
            listOf(MomentumEvent.VideoCompleted("a", 1_000L, 3_000L))
        )

        assertTrue(signals.any { it is VideoBehaviorSignal.WatchCompleted })
        assertTrue(signals.any { it is VideoBehaviorSignal.WatchedMost })
    }

    @Test
    fun seeksAreDirectionalAndSmallMovesAreIgnored() {
        val signals = VideoBehaviorSignalInterpreter.interpret(
            listOf(
                MomentumEvent.VideoSeeked("a", 10_000L, 40_000L, 1_000L),
                MomentumEvent.VideoSeeked("a", 40_000L, 20_000L, 2_000L),
                MomentumEvent.VideoSeeked("a", 20_000L, 24_000L, 3_000L)
            )
        )

        assertEquals(1, signals.filterIsInstance<VideoBehaviorSignal.SeekForward>().size)
        assertEquals(1, signals.filterIsInstance<VideoBehaviorSignal.SeekBackward>().size)
    }

    @Test
    fun secondStartOfSameVideoProducesRewatchSignal() {
        val signals = VideoBehaviorSignalInterpreter.interpret(
            listOf(
                MomentumEvent.VideoStarted("a", 1_000L, 1_000L),
                MomentumEvent.VideoStarted("a", 2_000L, 2_000L)
            )
        )

        assertEquals(1, signals.filterIsInstance<VideoBehaviorSignal.Rewatched>().size)
    }

    @Test
    fun completionFollowedByAnotherVideoProducesContinuation() {
        val signals = VideoBehaviorSignalInterpreter.interpret(
            listOf(
                MomentumEvent.VideoCompleted("a", 10_000L, 9_000L),
                MomentumEvent.VideoStarted("b", 11_000L, 11_000L)
            ),
            contexts = mapOf(
                "a" to VideoBehaviorSignalInterpreter.VideoContext("a", folderKey = "season/1"),
                "b" to VideoBehaviorSignalInterpreter.VideoContext("b", folderKey = "season/1")
            )
        )

        val continuation = signals.filterIsInstance<VideoBehaviorSignal.ContinuedFromPrevious>()
        assertEquals(1, continuation.size)
        assertTrue(continuation.single().sameFolder)
        assertEquals("a", continuation.single().previousMediaId)
    }

    @Test
    fun outOfOrderEventsAreProcessedChronologically() {
        val signals = VideoBehaviorSignalInterpreter.interpret(
            listOf(
                MomentumEvent.VideoStarted("a", 5_000L, 5_000L),
                MomentumEvent.VideoCompleted("a", 1_000L, 4_000L)
            )
        )

        assertEquals(listOf(1_000L, 5_000L), signals.map { it.timestampMs }.sorted())
    }

    @Test
    fun invalidDurationDoesNotProducePercentageSignals() {
        val signals = VideoBehaviorSignalInterpreter.interpret(
            listOf(MomentumEvent.VideoPaused("a", 500L, 1_000L)),
            contexts = mapOf("a" to VideoBehaviorSignalInterpreter.VideoContext("a", durationMs = 0L))
        )

        assertTrue(signals.none { it is VideoBehaviorSignal.WatchedMost || it is VideoBehaviorSignal.WatchAbandonedEarly })
    }
}
