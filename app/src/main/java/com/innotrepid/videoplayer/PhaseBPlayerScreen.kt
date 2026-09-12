package com.innotrepid.videoplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.innotrepid.videoplayer.intelligence.VideoSessionQueue
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.playback.PlaybackController
import kotlinx.coroutines.delay

@Composable
internal fun PhaseBPlayerScreen(
    video: VideoItem,
    player: ExoPlayer,
    controller: PlaybackController,
    queue: VideoSessionQueue?,
    favorite: () -> Unit,
    back: () -> Unit,
    open: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val activity = context.safePhaseBActivity()
    val state by controller.state.collectAsState()
    var chromeVisible by remember { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var volumeMenu by remember { mutableStateOf(false) }
    var upNextExpanded by remember { mutableStateOf(false) }
    var scrub by remember(video.id) { mutableFloatStateOf(Float.NaN) }
    val next = queue?.next
    val previous = queue?.previous

    DisposableEffect(fullscreen, landscape) {
        activity?.let { host ->
            val bars = WindowCompat.getInsetsController(host.window, host.window.decorView)
            if (fullscreen) bars.hide(WindowInsetsCompat.Type.systemBars()) else bars.show(WindowInsetsCompat.Type.systemBars())
            host.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { host ->
                val bars = WindowCompat.getInsetsController(host.window, host.window.decorView)
                bars.show(WindowInsetsCompat.Type.systemBars())
                host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    LaunchedEffect(chromeVisible, upNextExpanded, state.isPlaying) {
        if (chromeVisible && state.errorMessage == null && !upNextExpanded) {
            delay(if (state.isPlaying) 3500L else 6000L)
            chromeVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(context).apply {
                this.player = player
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize().clickable { chromeVisible = !chromeVisible }
        )

        AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().height(132.dp).background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = .78f), Color.Transparent))
                    ).align(Alignment.TopCenter)
                )
                Box(
                    Modifier.fillMaxWidth().height(150.dp).background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f)))
                    ).align(Alignment.BottomCenter)
                )

                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(video.title, color = Color.White, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        video.folderName?.let { Text(it, color = Color.White.copy(alpha = .58f), fontSize = 10.sp) }
                    }
                    IconButton(onClick = favorite) {
                        Icon(
                            if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            "Save",
                            tint = if (video.isFavorite) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(onClick = { fullscreen = !fullscreen }) {
                        Icon(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, "Fullscreen", tint = Color.White)
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IconButton(onClick = { previous?.let(open) }, enabled = previous != null) {
                        Icon(Icons.Outlined.SkipPrevious, "Previous", tint = Color.White.copy(alpha = if (previous != null) 1f else .35f))
                    }
                    FilledTonalIconButton(onClick = { controller.seekBy(-10_000L) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Replay10, "Back 10 seconds")
                    }
                    FilledIconButton(onClick = controller::togglePlayPause, modifier = Modifier.size(68.dp)) {
                        Icon(if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (state.isPlaying) "Pause" else "Play", modifier = Modifier.size(34.dp))
                    }
                    FilledTonalIconButton(onClick = { controller.seekBy(10_000L) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Forward10, "Forward 10 seconds")
                    }
                    IconButton(onClick = { next?.let(open) }, enabled = next != null) {
                        Icon(Icons.Outlined.SkipNext, "Next", tint = Color.White.copy(alpha = if (next != null) 1f else .35f))
                    }
                }

                if (state.durationMs > 0L) {
                    Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatPhaseBTime(if (scrub.isNaN()) state.positionMs else scrub.toLong()), color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
                            Text(formatPhaseBTime(state.durationMs), color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
                        }
                        Slider(
                            value = if (scrub.isNaN()) state.positionMs.toFloat() else scrub.coerceIn(0f, state.durationMs.toFloat()),
                            onValueChange = { scrub = it; chromeVisible = true },
                            onValueChangeFinished = { val target = scrub; scrub = Float.NaN; if (!target.isNaN()) controller.seekTo(target.toLong()) },
                            valueRange = 0f..state.durationMs.toFloat()
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = if (state.durationMs > 0L) 74.dp else 18.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box {
                        IconButton(onClick = { volumeMenu = !volumeMenu; speedMenu = false }) {
                            Icon(if (state.isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, "Volume", tint = Color.White)
                        }
                        DropdownMenu(expanded = volumeMenu, onDismissRequest = { volumeMenu = false }) {
                            Column(Modifier.width(220.dp).padding(14.dp)) {
                                Text("Volume", style = MaterialTheme.typography.labelLarge)
                                Slider(state.volume, controller::setVolume, valueRange = 0f..1f)
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { speedMenu = !speedMenu; volumeMenu = false }) {
                            Icon(Icons.Outlined.Speed, "Speed", tint = Color.White)
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                DropdownMenuItem(text = { Text("${speed}x") }, onClick = { controller.setSpeed(speed); speedMenu = false })
                            }
                        }
                    }
                    IconButton(onClick = { landscape = !landscape }) {
                        Icon(Icons.Outlined.ScreenRotation, "Rotate", tint = Color.White)
                    }
                }

                if (next != null) {
                    Surface(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 14.dp, bottom = if (state.durationMs > 0L) 82.dp else 20.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { upNextExpanded = !upNextExpanded },
                        color = Color.Black.copy(alpha = .74f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 300.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.QueuePlayNext, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("UP NEXT", color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, letterSpacing = 1.4.sp)
                                Spacer(Modifier.weight(1f))
                                Icon(if (upNextExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(17.dp))
                            }
                            AnimatedVisibility(upNextExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                                Column {
                                    Spacer(Modifier.height(7.dp))
                                    Text(next.title, color = Color.White, maxLines = 2, style = MaterialTheme.typography.labelLarge)
                                    next.folderName?.let { Text(it, color = Color.White.copy(alpha = .52f), fontSize = 10.sp, maxLines = 1) }
                                    Spacer(Modifier.height(9.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { open(next); upNextExpanded = false }) { Text("Play next") }
                                        TextButton(onClick = { upNextExpanded = false }) { Text("Later") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.isBuffering && state.errorMessage == null) {
            Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = .58f)) {
                CircularProgressIndicator(Modifier.padding(14.dp).size(28.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }

        state.errorMessage?.let { message ->
            Surface(Modifier.align(Alignment.Center).padding(28.dp), shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = .90f)) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Playback stopped", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(message, color = Color.White.copy(alpha = .72f), fontSize = 11.sp, maxLines = 3)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = back) { Text("Back") }
                        Button(onClick = { controller.retry(); chromeVisible = true }) { Text("Retry") }
                    }
                }
            }
        }
    }
}

private fun Context.safePhaseBActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun formatPhaseBTime(milliseconds: Long): String {
    val total = milliseconds.coerceAtLeast(0L) / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
