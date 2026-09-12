package com.innotrepid.videoplayer.intelligence

import android.content.Context
import java.security.MessageDigest

/**
 * Persists lightweight prediction exposure/acceptance counts locally.
 * This intentionally stores aggregate counts, not a second event stream.
 */
class VideoPredictionFeedbackStore(context: Context) {
    private val prefs = context.getSharedPreferences("video_prediction_feedback", Context.MODE_PRIVATE)

    data class Stats(
        val shownCount: Int,
        val acceptedCount: Int
    )

    fun recordShown(mediaId: String) {
        val stats = stats(mediaId)
        prefs.edit().putInt(shownKey(mediaId), stats.shownCount + 1).apply()
    }

    /**
     * Acceptance is only valid after an exposure. Persisting that invariant here
     * keeps every caller from accidentally manufacturing positive feedback.
     */
    fun recordAccepted(mediaId: String) {
        val stats = stats(mediaId)
        if (stats.acceptedCount >= stats.shownCount) return
        prefs.edit().putInt(acceptedKey(mediaId), stats.acceptedCount + 1).apply()
    }

    fun stats(mediaId: String): Stats = Stats(
        shownCount = prefs.getInt(shownKey(mediaId), 0).coerceAtLeast(0),
        acceptedCount = prefs.getInt(acceptedKey(mediaId), 0).coerceAtLeast(0)
    )

    fun adjustedConfidence(mediaId: String, baseConfidence: Float): Float {
        val stats = stats(mediaId)
        return VideoPredictionFeedback.adjustedConfidence(
            baseConfidence = baseConfidence,
            shownCount = stats.shownCount,
            acceptedCount = stats.acceptedCount
        )
    }

    private fun shownKey(mediaId: String): String = "shown_${digest(mediaId)}"

    private fun acceptedKey(mediaId: String): String = "accepted_${digest(mediaId)}"

    private fun digest(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
