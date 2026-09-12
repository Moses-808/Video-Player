package com.innotrepid.videoplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.innotrepid.videoplayer.intelligence.VideoPreviewMoment
import com.innotrepid.videoplayer.library.VideoItem

@Composable
internal fun VideoPredictionPreview(
    video: VideoItem,
    modifier: Modifier = Modifier,
    previewPositionMs: Long? = null
) {
    val context = LocalContext.current
    val previewPlayer = remember(video.id, video.uri, previewPositionMs) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            val position = previewPositionMs ?: VideoPreviewMoment.startPositionMs(
                durationMs = video.durationMs,
                resumePositionMs = video.lastPositionMs
            )
            setMediaItem(MediaItem.fromUri(video.uri), position)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(previewPlayer) {
        onDispose {
            previewPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = previewPlayer
                useController = false
            }
        },
        update = { playerView ->
            playerView.player = previewPlayer
        },
        modifier = modifier
    )
}
