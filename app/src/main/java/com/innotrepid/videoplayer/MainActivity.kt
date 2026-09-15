package com.innotrepid.videoplayer

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi

class MainActivity : ComponentActivity() {

    /** Returns true when the player is actively playing and PiP should open on Home. */
    var shouldEnterPipOnLeave: () -> Boolean = { false }

    /** Invoked by PiP transport buttons. Cleared when the player screen leaves. */
    var onPipPlayPause: (() -> Unit)? = null
    var onPipNext: (() -> Unit)? = null
    var onPipPrevious: (() -> Unit)? = null

    private var lastIsPlaying: Boolean = false
    private var lastHasNext: Boolean = false
    private var lastHasPrevious: Boolean = false

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY_PAUSE -> onPipPlayPause?.invoke()
                ACTION_PIP_NEXT -> onPipNext?.invoke()
                ACTION_PIP_PREVIOUS -> onPipPrevious?.invoke()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = IntentFilter().apply {
                addAction(ACTION_PIP_PLAY_PAUSE)
                addAction(ACTION_PIP_NEXT)
                addAction(ACTION_PIP_PREVIOUS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(pipActionReceiver, filter)
            }
        }
        setContent { VideoPlayerRootSafe() }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { unregisterReceiver(pipActionReceiver) }
        }
        onPipPlayPause = null
        onPipNext = null
        onPipPrevious = null
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldEnterPipOnLeave() && !isInPictureInPictureMode) {
            enterVideoPictureInPicture()
        }
    }

    /**
     * Enter PiP (or refresh actions if already in PiP) with the current transport state.
     */
    fun enterVideoPictureInPicture(
        aspectWidth: Int = 16,
        aspectHeight: Int = 9,
        isPlaying: Boolean = lastIsPlaying,
        hasNext: Boolean = lastHasNext,
        hasPrevious: Boolean = lastHasPrevious,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        lastIsPlaying = isPlaying
        lastHasNext = hasNext
        lastHasPrevious = hasPrevious
        val params = buildPipParams(aspectWidth, aspectHeight, isPlaying, hasNext, hasPrevious)
        return if (isInPictureInPictureMode) {
            setPictureInPictureParams(params)
            true
        } else {
            try {
                enterPictureInPictureMode(params)
            } catch (_: IllegalStateException) {
                false
            }
        }
    }

    /**
     * Keep PiP buttons in sync while the window is already open (play/pause toggle,
     * next/previous availability).
     */
    fun updatePipActions(
        isPlaying: Boolean,
        hasNext: Boolean,
        hasPrevious: Boolean,
        aspectWidth: Int = 16,
        aspectHeight: Int = 9,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!isInPictureInPictureMode) {
            lastIsPlaying = isPlaying
            lastHasNext = hasNext
            lastHasPrevious = hasPrevious
            return
        }
        if (isPlaying == lastIsPlaying && hasNext == lastHasNext && hasPrevious == lastHasPrevious) {
            return
        }
        lastIsPlaying = isPlaying
        lastHasNext = hasNext
        lastHasPrevious = hasPrevious
        setPictureInPictureParams(
            buildPipParams(aspectWidth, aspectHeight, isPlaying, hasNext, hasPrevious),
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(
        aspectWidth: Int,
        aspectHeight: Int,
        isPlaying: Boolean,
        hasNext: Boolean,
        hasPrevious: Boolean,
    ): PictureInPictureParams {
        val w = aspectWidth.coerceAtLeast(1)
        val h = aspectHeight.coerceAtLeast(1)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(w, h))
            .setActions(buildRemoteActions(isPlaying, hasNext, hasPrevious))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(false)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildRemoteActions(
        isPlaying: Boolean,
        hasNext: Boolean,
        hasPrevious: Boolean,
    ): List<RemoteAction> {
        val actions = ArrayList<RemoteAction>(3)

        actions += remoteAction(
            requestCode = REQUEST_PREVIOUS,
            action = ACTION_PIP_PREVIOUS,
            iconRes = android.R.drawable.ic_media_previous,
            title = "Previous",
            enabled = hasPrevious,
        )

        actions += remoteAction(
            requestCode = REQUEST_PLAY_PAUSE,
            action = ACTION_PIP_PLAY_PAUSE,
            iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            title = if (isPlaying) "Pause" else "Play",
            enabled = true,
        )

        actions += remoteAction(
            requestCode = REQUEST_NEXT,
            action = ACTION_PIP_NEXT,
            iconRes = android.R.drawable.ic_media_next,
            title = "Next",
            enabled = hasNext,
        )

        return actions
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun remoteAction(
        requestCode: Int,
        action: String,
        iconRes: Int,
        title: String,
        enabled: Boolean,
    ): RemoteAction {
        val intent = Intent(action).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getBroadcast(this, requestCode, intent, flags)
        return RemoteAction(
            Icon.createWithResource(this, iconRes),
            title,
            title,
            pending,
        ).also { it.isEnabled = enabled }
    }

    companion object {
        const val ACTION_PIP_PLAY_PAUSE = "com.innotrepid.videoplayer.PIP_PLAY_PAUSE"
        const val ACTION_PIP_NEXT = "com.innotrepid.videoplayer.PIP_NEXT"
        const val ACTION_PIP_PREVIOUS = "com.innotrepid.videoplayer.PIP_PREVIOUS"

        private const val REQUEST_PLAY_PAUSE = 1001
        private const val REQUEST_NEXT = 1002
        private const val REQUEST_PREVIOUS = 1003
    }
}
