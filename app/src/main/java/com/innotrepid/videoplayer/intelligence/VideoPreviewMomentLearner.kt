package com.innotrepid.videoplayer.intelligence

/**
 * Extracts a representative preview point from local viewing behavior.
 *
 * Repeatedly seeking to roughly the same part of a video is treated as evidence
 * that the region is meaningful to this viewer. The learner is deliberately
 * conservative: it needs repeated evidence and avoids the opening/ending edges.
 */
object VideoPreviewMomentLearner {
    private const val MIN_REPEAT_SEEKS = 2
    private const val CLUSTER_RADIUS_MS = 15_000L
    private const val MIN_MOMENT_MS = 5_000L
    private const val EDGE_MARGIN_MS = 5_000L

    fun learnPositionMs(
        mediaId: String,
        durationMs: Long,
        events: List<MomentumEvent>
    ): Long? {
        if (durationMs <= MIN_MOMENT_MS * 2) return null

        val targets = events.asSequence()
            .filterIsInstance<MomentumEvent.VideoSeeked>()
            .filter { it.mediaId == mediaId }
            .map { it.toPositionMs.coerceIn(0L, durationMs) }
            .filter { it >= MIN_MOMENT_MS && it <= durationMs - EDGE_MARGIN_MS }
            .toList()

        if (targets.size < MIN_REPEAT_SEEKS) return null

        val best = targets
            .distinct()
            .map { center ->
                val members = targets.filter { kotlin.math.abs(it - center) <= CLUSTER_RADIUS_MS }
                val strength = members.size
                val average = members.average().toLong()
                Triple(strength, average, kotlin.math.abs(average - center))
            }
            .maxWithOrNull(compareBy<Triple<Int, Long, Long>> { it.first }
                .thenBy { -it.third }
                .thenBy { it.second })
            ?: return null

        return best.second.coerceIn(MIN_MOMENT_MS, durationMs - EDGE_MARGIN_MS)
    }
}
