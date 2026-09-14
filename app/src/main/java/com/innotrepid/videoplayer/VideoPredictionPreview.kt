package com.innotrepid.videoplayer

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.innotrepid.videoplayer.intelligence.VideoPreviewMoment
import com.innotrepid.videoplayer.library.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun VideoPredictionPreview(
    video: VideoItem,
    modifier: Modifier = Modifier,
    previewPositionMs: Long? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewStartMs = remember(video.id, video.uri, previewPositionMs, video.durationMs, video.lastPositionMs) {
        previewPositionMs ?: VideoPreviewMoment.startPositionMs(
            durationMs = video.durationMs,
            resumePositionMs = video.lastPositionMs
        )
    }
    val previewPlayer = remember(video.id, video.uri, previewPositionMs) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(video.uri), previewStartMs)
            prepare()
            playWhenReady = true
        }
    }
    var previewFailed by remember(previewPlayer) { mutableStateOf(false) }
    var fallbackBitmap by remember(previewPlayer) { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                previewPlayer.pause()
                previewFailed = true
            }
        }
        previewPlayer.addListener(listener)
        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, previewPlayer, previewFailed) {
        if (previewFailed) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (previewPlayer.playbackState == Player.STATE_ENDED) {
                        previewPlayer.seekTo(previewStartMs)
                    }
                    previewPlayer.playWhenReady = true
                    previewPlayer.play()
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> previewPlayer.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(previewPlayer, previewFailed, previewStartMs, video.durationMs) {
        if (previewFailed) return@LaunchedEffect
        previewPlayer.seekTo(previewStartMs)
        previewPlayer.playWhenReady = true
        previewPlayer.play()
        val previewEndMs = if (video.durationMs > previewStartMs) {
            (previewStartMs + 8_000L).coerceAtMost(video.durationMs)
        } else {
            null
        }
        if (previewEndMs == null || previewEndMs <= previewStartMs) return@LaunchedEffect
        while (true) {
            delay(250L)
            if (!previewPlayer.isPlaying) continue
            if (previewPlayer.currentPosition >= previewEndMs) {
                previewPlayer.seekTo(previewStartMs)
            }
        }
    }

    LaunchedEffect(previewFailed, video.uri, previewPositionMs, video.lastPositionMs, video.durationMs) {
        if (!previewFailed) return@LaunchedEffect
        fallbackBitmap = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, video.uri)
                    retriever.getFrameAtTime(
                        previewStartMs.coerceAtLeast(0L) * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }

    if (previewFailed) {
        Box(modifier) {
            fallbackBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: Box(
                Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
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
