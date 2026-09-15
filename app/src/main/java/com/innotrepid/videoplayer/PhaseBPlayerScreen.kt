package com.innotrepid.videoplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
    open: (VideoItem) -> Unit,
    onRemove: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context.safePhaseBActivity()
    val state by controller.state.collectAsState()
    var chromeVisible by remember { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var volumeMenu by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var subtitleMenu by remember { mutableStateOf(false) }
    var upNextExpanded by remember { mutableStateOf(false) }
    var scrub by remember(video.id) { mutableFloatStateOf(Float.NaN) }
    var playerResizeMode by remember { mutableStateOf(PlayerResizeMode.FIT) }
    val next = queue?.next
    val previous = queue?.previous

    LaunchedEffect(queue?.next?.id, queue?.previous?.id) {
        controller.updateNavigation(
            hasNext = next != null,
            hasPrevious = previous != null,
        )
    }

    DisposableEffect(fullscreen, landscape) {
        activity?.let { host ->
            val bars = WindowCompat.getInsetsController(host.window, host.window.decorView)
            if (fullscreen) bars.hide(WindowInsetsCompat.Type.systemBars())
            else bars.show(WindowInsetsCompat.Type.systemBars())
            host.requestedOrientation =
                when {
                    landscape || fullscreen -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
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
    LaunchedEffect(controller, video.id) {
        while (true) {
            controller.refresh()
            delay(200L)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.resizeMode = playerResizeMode.toMedia3()
                }
            },
            update = {
                it.player = player
                it.resizeMode = playerResizeMode.toMedia3()
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable { chromeVisible = !chromeVisible },
        )

        AnimatedVisibility(
            visible = chromeVisible && state.errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = .78f), Color.Transparent),
                            ),
                        )
                        .align(Alignment.TopCenter),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = .88f)),
                            ),
                        )
                        .align(Alignment.BottomCenter),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = back) {
                        Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            video.title,
                            color = Color.White,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        video.folderName?.let {
                            Text(it, color = Color.White.copy(alpha = .58f), fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = favorite) {
                        Icon(
                            if (video.isFavorite) Icons.Outlined.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            "Save",
                            tint = if (video.isFavorite) MaterialTheme.colorScheme.error
                            else Color.White,
                        )
                    }
                    IconButton(onClick = { fullscreen = !fullscreen }) {
                        Icon(
                            if (fullscreen) Icons.Outlined.FullscreenExit
                            else Icons.Outlined.Fullscreen,
                            "Fullscreen",
                            tint = Color.White,
                        )
                    }
                }
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconButton(
                        onClick = { previous?.let(open) },
                        enabled = state.hasPrevious && previous != null,
                    ) {
                        Icon(
                            Icons.Outlined.SkipPrevious,
                            "Previous",
                            tint = Color.White.copy(alpha = if (state.hasPrevious) 1f else .35f),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { controller.seekBy(-10_000L) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Outlined.Replay10, "Back 10 seconds")
                    }
                    FilledIconButton(
                        onClick = controller::togglePlayPause,
                        modifier = Modifier.size(68.dp),
                    ) {
                        AnimatedContent(
                            targetState = state.isPlaying,
                            label = "playback control",
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = { controller.seekBy(10_000L) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Outlined.Forward10, "Forward 10 seconds")
                    }
                    IconButton(
                        onClick = { next?.let(open) },
                        enabled = state.hasNext && next != null,
                    ) {
                        Icon(
                            Icons.Outlined.SkipNext,
                            "Next",
                            tint = Color.White.copy(alpha = if (state.hasNext) 1f else .35f),
                        )
                    }
                }
                if (state.durationMs > 0L) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                formatPhaseBTime(
                                    if (scrub.isNaN()) state.positionMs else scrub.toLong(),
                                ),
                                color = Color.White.copy(alpha = .82f),
                                fontSize = 10.sp,
                            )
                            Text(
                                formatPhaseBTime(state.durationMs),
                                color = Color.White.copy(alpha = .82f),
                                fontSize = 10.sp,
                            )
                        }
                        Slider(
                            value = if (scrub.isNaN()) {
                                state.positionMs.toFloat()
                            } else {
                                scrub.coerceIn(0f, state.durationMs.toFloat())
                            },
                            onValueChange = {
                                scrub = it
                                chromeVisible = true
                            },
                            onValueChangeFinished = {
                                controller.seekTo(scrub.toLong())
                                scrub = Float.NaN
                            },
                            valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (state.durationMs > 0L) 74.dp else 18.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box {
                        IconButton(onClick = {
                            volumeMenu = !volumeMenu
                            speedMenu = false
                            audioMenu = false
                            subtitleMenu = false
                        }) {
                            Icon(
                                if (state.isMuted) Icons.Outlined.VolumeOff
                                else Icons.Outlined.VolumeUp,
                                "Volume",
                                tint = Color.White,
                            )
                        }
                        DropdownMenu(
                            expanded = volumeMenu,
                            onDismissRequest = { volumeMenu = false },
                        ) {
                            Column(Modifier.width(220.dp).padding(14.dp)) {
                                Text("Volume", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Slider(
                                    value = state.volume,
                                    onValueChange = controller::setVolume,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Mute", style = MaterialTheme.typography.labelSmall)
                                    Switch(
                                        checked = state.isMuted,
                                        onCheckedChange = {
                                            controller.setVolume(if (it) 0f else 1f)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (state.audioTracks.isNotEmpty()) {
                        Box {
                            IconButton(
                                onClick = {
                                    audioMenu = !audioMenu
                                    speedMenu = false
                                    volumeMenu = false
                                    subtitleMenu = false
                                },
                            ) {
                                Icon(Icons.Outlined.Audiotrack, "Audio track", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = audioMenu,
                                onDismissRequest = { audioMenu = false },
                            ) {
                                state.audioTracks.forEach { track ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (track.isSelected) "✓ ${track.label}" else track.label,
                                                color = if (track.isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color.Unspecified
                                                },
                                            )
                                        },
                                        onClick = {
                                            controller.selectAudioTrack(track.id)
                                            audioMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (state.subtitleTracks.isNotEmpty()) {
                        Box {
                            IconButton(
                                onClick = {
                                    subtitleMenu = !subtitleMenu
                                    speedMenu = false
                                    volumeMenu = false
                                    audioMenu = false
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.ClosedCaption,
                                    "Subtitles",
                                    tint = if (state.subtitlesEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White
                                    },
                                )
                            }
                            DropdownMenu(
                                expanded = subtitleMenu,
                                onDismissRequest = { subtitleMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (!state.subtitlesEnabled) "✓ Off" else "Off",
                                            color = if (!state.subtitlesEnabled) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.Unspecified
                                            },
                                        )
                                    },
                                    onClick = {
                                        controller.selectSubtitleTrack(null)
                                        subtitleMenu = false
                                    },
                                )
                                state.subtitleTracks.forEach { track ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (track.isSelected) "✓ ${track.label}" else track.label,
                                                color = if (track.isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color.Unspecified
                                                },
                                            )
                                        },
                                        onClick = {
                                            controller.selectSubtitleTrack(track.id)
                                            subtitleMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = {
                            speedMenu = !speedMenu
                            volumeMenu = false
                            audioMenu = false
                            subtitleMenu = false
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Speed, "Speed", tint = Color.White)
                                Text(
                                    formatPlaybackSpeed(state.playbackSpeed),
                                    color = Color.White.copy(alpha = .85f),
                                    fontSize = 9.sp,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = speedMenu,
                            onDismissRequest = { speedMenu = false },
                        ) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                val selected = kotlin.math.abs(state.playbackSpeed - speed) < 0.01f
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (selected) "✓ ${speed}x" else "${speed}x",
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else Color.Unspecified,
                                        )
                                    },
                                    onClick = {
                                        controller.setSpeed(speed)
                                        speedMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            playerResizeMode = playerResizeMode.next()
                            chromeVisible = true
                        },
                    ) {
                        Icon(
                            playerResizeMode.icon,
                            contentDescription = "Aspect ratio: ${playerResizeMode.label}",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = {
                        landscape = !landscape
                        if (landscape) fullscreen = true
                    }) {
                        Icon(Icons.Outlined.ScreenRotation, "Rotate", tint = Color.White)
                    }
                }
                if (state.hasNext && next != null) {
                    Surface(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = 14.dp,
                                bottom = if (state.durationMs > 0L) 82.dp else 20.dp,
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { upNextExpanded = !upNextExpanded },
                    ) {
                        Column(
                            Modifier
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .widthIn(max = 300.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.QueuePlayNext,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("UP NEXT", style = MaterialTheme.typography.labelSmall)
                            }
                            AnimatedVisibility(
                                visible = upNextExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Column {
                                    Spacer(Modifier.height(7.dp))
                                    Text(
                                        next.title,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Button(onClick = { open(next) }) {
                                        Text("Play next")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.isBuffering && state.errorMessage == null) {
            Surface(
                Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = .58f),
            ) {
                CircularProgressIndicator(
                    Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        state.errorMessage?.let { message ->
            val presentation = remember(message) {
                val pieces = message.split(":", limit = 2)
                presentPlaybackError(
                    errorCodeName = pieces.firstOrNull().orEmpty(),
                    message = pieces.getOrNull(1),
                )
            }
            PlaybackErrorOverlay(
                presentation = presentation,
                onPrimary = {
                    when (presentation.primaryAction) {
                        PlaybackErrorAction.RETRY -> {
                            controller.retry()
                            chromeVisible = true
                        }
                        PlaybackErrorAction.REMOVE_FROM_LIBRARY -> onRemove()
                        PlaybackErrorAction.BACK_TO_LIBRARY -> back()
                    }
                },
                onBack = back,
            )
        }
    }
}

@Composable
private fun PlaybackErrorOverlay(
    presentation: PlaybackErrorPresentation,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        Modifier.fillMaxSize().padding(22.dp),
        color = Color.Transparent,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = .93f),
            ) {
                Column(
                    Modifier.padding(22.dp).widthIn(max = 340.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        presentation.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        presentation.explanation,
                        color = Color.White.copy(alpha = .72f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                        Text(presentation.primaryLabel)
                    }
                    if (presentation.primaryAction != PlaybackErrorAction.BACK_TO_LIBRARY) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Back to library")
                        }
                    }
                }
            }
        }
    }
}

private enum class PlayerResizeMode {
    FIT,
    FILL,
    ZOOM,
    ;

    val label: String
        get() = when (this) {
            FIT -> "Fit"
            FILL -> "Fill"
            ZOOM -> "Zoom"
        }

    val icon
        get() = when (this) {
            FIT -> Icons.Outlined.FitScreen
            FILL -> Icons.Outlined.AspectRatio
            ZOOM -> Icons.Outlined.CropFree
        }

    fun next(): PlayerResizeMode = when (this) {
        FIT -> FILL
        FILL -> ZOOM
        ZOOM -> FIT
    }

    fun toMedia3(): Int = when (this) {
        FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
}

private fun formatPlaybackSpeed(speed: Float): String {
    val normalized = if (kotlin.math.abs(speed - speed.toInt()) < 0.01f) {
        speed.toInt().toString()
    } else {
        "%.2g".format(speed)
    }
    return "${normalized}x"
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
