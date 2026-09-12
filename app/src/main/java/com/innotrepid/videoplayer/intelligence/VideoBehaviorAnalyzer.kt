package com.innotrepid.videoplayer.intelligence

/**
 * Explicit boundary between persisted playback telemetry and derived intelligence.
 *
 * Raw MomentumEvent values are playback telemetry. VideoBehaviorSignal values are
 * derived observations used by prediction. Keeping this conversion behind one
 * pure entry point prevents prediction code from inventing a second telemetry
 * representation or depending on persistence details.
 */
object VideoBehaviorAnalyzer {
    fun analyze(
        events: List<MomentumEvent>,
        contexts: Map<String, VideoBehaviorSignalInterpreter.VideoContext> = emptyMap(),
        nowMs: Long = events.maxOfOrNull { it.timestampMs } ?: 0L
    ): List<VideoBehaviorSignal> = VideoBehaviorSignalInterpreter.interpret(
        events = events,
        contexts = contexts,
        nowMs = nowMs
    )
}
