package com.innotrepid.videoplayer

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    /** Returns true when the player is actively playing and PiP should open on Home. */
    var shouldEnterPipOnLeave: () -> Boolean = { false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VideoPlayerRootSafe() }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldEnterPipOnLeave() && !isInPictureInPictureMode) {
            enterVideoPictureInPicture()
        }
    }

    fun enterVideoPictureInPicture(aspectWidth: Int = 16, aspectHeight: Int = 9): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (isInPictureInPictureMode) return true
        val w = aspectWidth.coerceAtLeast(1)
        val h = aspectHeight.coerceAtLeast(1)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(w, h))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(false)
        }
        return try {
            enterPictureInPictureMode(builder.build())
        } catch (_: IllegalStateException) {
            false
        }
    }
}
