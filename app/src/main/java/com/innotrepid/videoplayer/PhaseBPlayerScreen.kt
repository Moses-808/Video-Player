package com.innotrepid.videoplayer

import android.app.Activity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.outlined.PictureInPictureAlt
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
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
import kotlin.math.abs

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
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var speedBoostActive by remember { mutableStateOf(false) }
    var speedBeforeBoost by remember { mutableFloatStateOf(1f) }
    var inPictureInPicture by remember { mutableStateOf(false) }
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness
                ?.takeIf { it in 0f..1f }
                ?: 0.5f,
        )
    }
    var playerResizeMode by remember { mutableStateOf(PlayerResizeMode.FIT) }
    val viewConfiguration = LocalViewConfiguration.current
    val pickSubtitle = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull()
        controller.loadExternalSubtitle(uri, name)
        chromeVisible = true
    }
    val next = queue?.next
    val previous = queue?.previous

    LaunchedEffect(queue?.next?.id, queue?.previous?.id) {
        controller.updateNavigation(hasNext = next != null, hasPrevious = previous != null)
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
            if (speedBoostActive) controller.setSpeed(speedBeforeBoost)
            (activity as? MainActivity)?.shouldEnterPipOnLeave = { false }
        }
    }

    DisposableEffect(activity, state.isPlaying, state.errorMessage) {
        val main = activity as? MainActivity
        main?.shouldEnterPipOnLeave = {
            state.isPlaying && state.errorMessage == null && !inPictureInPicture
        }
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPictureInPicture = info.isInPictureInPictureMode
            if (info.isInPictureInPictureMode) chromeVisible = false
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        inPictureInPicture = activity?.isInPictureInPictureMode == true
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
            main?.shouldEnterPipOnLeave = { false }
        }
    }

    LaunchedEffect(chromeVisible, upNextExpanded, state.isPlaying, speedBoostActive) {
        if (chromeVisible && state.errorMessage == null && !upNextExpanded && !speedBoostActive && !inPictureInPicture) {
            delay(if (state.isPlaying) 3500L else 6000L)
            chromeVisible = false
        }
    }
    LaunchedEffect(gestureHint, speedBoostActive) {
        if (gestureHint != null && !speedBoostActive) {
            delay(900L)
            gestureHint = null
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
                    subtitleView?.visibility = android.view.View.GONE
                    this.resizeMode = playerResizeMode.toMedia3()
                }
            },
            update = {
                it.player = player
                it.resizeMode = playerResizeMode.toMedia3()
                it.subtitleView?.visibility = android.view.View.GONE
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPictureInPicture) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (!speedBoostActive) chromeVisible = !chromeVisible
                            },
                            onDoubleTap = { offset ->
                                if (speedBoostActive) return@detectTapGestures
                                val third = size.width / 3f
                                when {
                                    offset.x < third -> {
                                        controller.seekBy(-10_000L)
                                        gestureHint = "−10s"
                                    }
                                    offset.x > third * 2f -> {
                                        controller.seekBy(10_000L)
                                        gestureHint = "+10s"
                                    }
                                    else -> {
                                        controller.togglePlayPause()
                                        gestureHint = "Play / Pause"
                                    }
                                }
                                chromeVisible = true
                            },
                            onPress = {
                                tryAwaitRelease()
                                if (speedBoostActive) {
                                    controller.setSpeed(speedBeforeBoost)
                                    speedBoostActive = false
                                    gestureHint = formatPlaybackSpeed(speedBeforeBoost)
                                }
                            },
                            onLongPress = {
                                if (speedBoostActive) return@detectTapGestures
                                speedBeforeBoost = state.playbackSpeed.coerceIn(0.25f, 4f)
                                speedBoostActive = true
                                controller.setSpeed(2f)
                                gestureHint = "2× hold"
                                chromeVisible = false
                            },
                        )
                    }
                    .pointerInput(state.durationMs) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var lockedHorizontal: Boolean? = null
                            var totalX = 0f
                            var totalY = 0f
                            var startVolume = state.volume
                            var startBrightness = brightness
                            var startPosition = state.positionMs.toFloat()
                            val duration = state.durationMs.toFloat().coerceAtLeast(1f)
                            val seekRangeMs = minOf(duration, 90_000f)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (lockedHorizontal == true && !scrub.isNaN()) {
                                        controller.seekTo(scrub.toLong())
                                        scrub = Float.NaN
                                    }
                                    break
                                }
                                val dx = change.positionChange().x
                                val dy = change.positionChange().y
                                change.consume()
                                totalX += dx
                                totalY += dy
                                if (lockedHorizontal == null) {
                                    if (abs(totalX) > touchSlop || abs(totalY) > touchSlop) {
                                        lockedHorizontal = abs(totalX) >= abs(totalY)
                                        if (lockedHorizontal == true) {
                                            startPosition = state.positionMs.toFloat()
                                        } else {
                                            startVolume = state.volume
                                            startBrightness = brightness
                                            totalX = 0f
                                            totalY = 0f
                                        }
                                    }
                                    continue
                                }
                                if (lockedHorizontal == true) {
                                    if (duration <= 1f) continue
                                    val fraction = (totalX / size.width.toFloat()).coerceIn(-1f, 1f)
                                    val target = (startPosition + fraction * seekRangeMs).coerceIn(0f, duration)
                                    scrub = target
                                    val deltaSec = ((target - startPosition) / 1000f).toInt()
                                    val sign = if (deltaSec >= 0) "+" else ""
                                    gestureHint = "$sign${deltaSec}s → ${formatPhaseBTime(target.toLong())}"
                                } else {
                                    val fraction = (-totalY / size.height.toFloat()).coerceIn(-1f, 1f)
                                    if (down.position.x < size.width / 2f) {
                                        val nextB = (startBrightness + fraction).coerceIn(0.01f, 1f)
                                        brightness = nextB
                                        activity?.window?.let { win ->
                                            val lp = win.attributes
                                            lp.screenBrightness = nextB
                                            win.attributes = lp
                                        }
                                        gestureHint = "Brightness ${(nextB * 100).toInt()}%"
                                    } else {
                                        val nextV = (startVolume + fraction).coerceIn(0f, 1f)
                                        controller.setVolume(nextV)
                                        gestureHint = "Volume ${(nextV * 100).toInt()}%"
                                    }
                                }
                            }
                        }
                    },
            )
        }

        if (state.subtitleText.isNotEmpty() && state.errorMessage == null) {
            Box(
                Modifier.fillMaxSize().padding(bottom = if (chromeVisible && !inPictureInPicture) 110.dp else 24.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val bg = if (state.subtitleBackground) Color.Black.copy(alpha = 0.65f) else Color.Transparent
                Text(
                    text = state.subtitleText,
                    color = Color.White,
                    fontSize = state.subtitleTextSizeSp.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        if (!inPictureInPicture) {
            gestureHint?.let { hint ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.72f)) {
                        Text(
                            hint,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && state.errorMessage == null && !inPictureInPicture,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().height(132.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .78f), Color.Transparent)))
                        .align(Alignment.TopCenter),
                )
                Box(
                    Modifier.fillMaxWidth().height(150.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f))))
                        .align(Alignment.BottomCenter),
                )
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = back) {
                        Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(video.title, color = Color.White, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        video.folderName?.let {
                            Text(it, color = Color.White.copy(alpha = .58f), fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = favorite) {
                        Icon(
                            if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            "Save",
                            tint = if (video.isFavorite) MaterialTheme.colorScheme.error else Color.White,
                        )
                    }
                    IconButton(onClick = { (activity as? MainActivity)?.enterVideoPictureInPicture() }) {
                        Icon(Icons.Outlined.PictureInPictureAlt, "Picture-in-picture", tint = Color.White)
                    }
                    IconButton(onClick = { fullscreen = !fullscreen }) {
                        Icon(
                            if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
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
                    IconButton(onClick = { previous?.let(open) }, enabled = state.hasPrevious && previous != null) {
                        Icon(Icons.Outlined.SkipPrevious, "Previous", tint = Color.White.copy(alpha = if (state.hasPrevious) 1f else .35f))
                    }
                    FilledTonalIconButton(onClick = { controller.seekBy(-10_000L) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Replay10, "Back 10 seconds")
                    }
                    FilledIconButton(onClick = controller::togglePlayPause, modifier = Modifier.size(68.dp)) {
                        AnimatedContent(targetState = state.isPlaying, label = "playback control") { playing ->
                            Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = null)
                        }
                    }
                    FilledTonalIconButton(onClick = { controller.seekBy(10_000L) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Forward10, "Forward 10 seconds")
                    }
                    IconButton(onClick = { next?.let(open) }, enabled = state.hasNext && next != null) {
                        Icon(Icons.Outlined.SkipNext, "Next", tint = Color.White.copy(alpha = if (state.hasNext) 1f else .35f))
                    }
                }
                if (state.durationMs > 0L) {
                    Column(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                formatPhaseBTime(if (scrub.isNaN()) state.positionMs else scrub.toLong()),
                                color = Color.White.copy(alpha = .82f),
                                fontSize = 10.sp,
                            )
                            Text(formatPhaseBTime(state.durationMs), color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
                        }
                        Slider(
                            value = if (scrub.isNaN()) state.positionMs.toFloat() else scrub.coerceIn(0f, state.durationMs.toFloat()),
                            onValueChange = { scrub = it; chromeVisible = true },
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
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .padding(bottom = if (state.durationMs > 0L) 74.dp else 18.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box {
                        IconButton(onClick = {
                            volumeMenu = !volumeMenu; speedMenu = false; audioMenu = false; subtitleMenu = false
                        }) {
                            Icon(if (state.isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, "Volume", tint = Color.White)
                        }
                        DropdownMenu(expanded = volumeMenu, onDismissRequest = { volumeMenu = false }) {
                            Column(Modifier.width(220.dp).padding(14.dp)) {
                                Text("Volume", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Slider(value = state.volume, onValueChange = controller::setVolume, modifier = Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Mute", style = MaterialTheme.typography.labelSmall)
                                    Switch(checked = state.isMuted, onCheckedChange = { controller.setVolume(if (it) 0f else 1f) })
                                }
                            }
                        }
                    }
                    if (state.audioTracks.isNotEmpty()) {
                        Box {
                            IconButton(onClick = {
                                audioMenu = !audioMenu; speedMenu = false; volumeMenu = false; subtitleMenu = false
                            }) {
                                Icon(Icons.Outlined.Audiotrack, "Audio track", tint = Color.White)
                            }
                            DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                                state.audioTracks.forEach { track ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (track.isSelected) "✓ ${track.label}" else track.label,
                                                color = if (track.isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                            )
                                        },
                                        onClick = { controller.selectAudioTrack(track.id); audioMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = {
                            subtitleMenu = !subtitleMenu; speedMenu = false; volumeMenu = false; audioMenu = false
                        }) {
                            Icon(
                                Icons.Outlined.ClosedCaption,
                                "Subtitles",
                                tint = if (state.subtitlesEnabled || state.hasExternalSubtitle) MaterialTheme.colorScheme.primary else Color.White,
                            )
                        }
                        DropdownMenu(expanded = subtitleMenu, onDismissRequest = { subtitleMenu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (!state.subtitlesEnabled) "✓ Off" else "Off",
                                        color = if (!state.subtitlesEnabled) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                    )
                                },
                                onClick = { controller.selectSubtitleTrack(null); subtitleMenu = false },
                            )
                            state.subtitleTracks.forEach { track ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (track.isSelected) "✓ ${track.label}" else track.label,
                                            color = if (track.isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                        )
                                    },
                                    onClick = { controller.selectSubtitleTrack(track.id); subtitleMenu = false },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    val sign = if (state.subtitleDelayMs >= 0) "+" else ""
                                    Text("Delay  $sign${state.subtitleDelayMs} ms")
                                },
                                onClick = { },
                                trailingIcon = {
                                    Row {
                                        Text("−", modifier = Modifier.clickable { controller.adjustSubtitleDelay(-500L) }.padding(horizontal = 10.dp))
                                        Text("+", modifier = Modifier.clickable { controller.adjustSubtitleDelay(500L) }.padding(horizontal = 10.dp))
                                    }
                                },
                            )
                            DropdownMenuItem(text = { Text("Reset delay") }, onClick = { controller.setSubtitleDelay(0L) })
                            DropdownMenuItem(text = { Text("Size  S") }, onClick = { controller.setSubtitleTextSizeSp(14f) })
                            DropdownMenuItem(text = { Text("Size  M") }, onClick = { controller.setSubtitleTextSizeSp(18f) })
                            DropdownMenuItem(text = { Text("Size  L") }, onClick = { controller.setSubtitleTextSizeSp(24f) })
                            DropdownMenuItem(
                                text = { Text(if (state.subtitleBackground) "✓ Background" else "Background") },
                                onClick = { controller.setSubtitleBackground(!state.subtitleBackground) },
                            )
                            DropdownMenuItem(
                                text = { Text("Load external…") },
                                onClick = {
                                    subtitleMenu = false
                                    pickSubtitle.launch(arrayOf("text/*", "application/x-subrip", "application/ttml+xml", "*/*"))
                                },
                            )
                            if (state.hasExternalSubtitle) {
                                DropdownMenuItem(
                                    text = { Text("Remove external" + (state.externalSubtitleLabel?.let { " ($it)" } ?: "")) },
                                    onClick = { controller.clearExternalSubtitle(); subtitleMenu = false },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = {
                            speedMenu = !speedMenu; volumeMenu = false; audioMenu = false; subtitleMenu = false
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Speed, "Speed", tint = Color.White)
                                Text(formatPlaybackSpeed(state.playbackSpeed), color = Color.White.copy(alpha = .85f), fontSize = 9.sp)
                            }
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                val selected = abs(state.playbackSpeed - speed) < 0.01f
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (selected) "✓ ${speed}x" else "${speed}x",
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                        )
                                    },
                                    onClick = { controller.setSpeed(speed); speedMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { playerResizeMode = playerResizeMode.next(); chromeVisible = true }) {
                        Icon(playerResizeMode.icon, contentDescription = "Aspect ratio: ${playerResizeMode.label}", tint = Color.White)
                    }
                    IconButton(onClick = { landscape = !landscape; if (landscape) fullscreen = true }) {
                        Icon(Icons.Outlined.ScreenRotation, "Rotate", tint = Color.White)
                    }
                }
                if (state.hasNext && next != null) {
                    Surface(
                        Modifier.align(Alignment.BottomStart)
                            .padding(start = 14.dp, bottom = if (state.durationMs > 0L) 82.dp else 20.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { upNextExpanded = !upNextExpanded },
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 300.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.QueuePlayNext, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("UP NEXT", style = MaterialTheme.typography.labelSmall)
                            }
                            AnimatedVisibility(visible = upNextExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                                Column {
                                    Spacer(Modifier.height(7.dp))
                                    Text(next.title, color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.height(6.dp))
                                    Button(onClick = { open(next) }) { Text("Play next") }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.isBuffering && state.errorMessage == null && !inPictureInPicture) {
            Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = .58f)) {
                CircularProgressIndicator(Modifier.padding(18.dp), color = MaterialTheme.colorScheme.primary)
            }
        }

        state.errorMessage?.let { message ->
            val presentation = remember(message) {
                val pieces = message.split(":", limit = 2)
                presentPlaybackError(errorCodeName = pieces.firstOrNull().orEmpty(), message = pieces.getOrNull(1))
            }
            PlaybackErrorOverlay(
                presentation = presentation,
                onPrimary = {
                    when (presentation.primaryAction) {
                        PlaybackErrorAction.RETRY -> { controller.retry(); chromeVisible = true }
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
    Surface(Modifier.fillMaxSize().padding(22.dp), color = Color.Transparent) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = .93f)) {
                Column(Modifier.padding(22.dp).widthIn(max = 340.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(presentation.title, color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(presentation.explanation, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(presentation.primaryLabel) }
                    if (presentation.primaryAction != PlaybackErrorAction.BACK_TO_LIBRARY) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to library") }
                    }
                }
            }
        }
    }
}

private enum class PlayerResizeMode {
    FIT, FILL, ZOOM;
    val label: String get() = when (this) { FIT -> "Fit"; FILL -> "Fill"; ZOOM -> "Zoom" }
    val icon get() = when (this) {
        FIT -> Icons.Outlined.FitScreen
        FILL -> Icons.Outlined.AspectRatio
        ZOOM -> Icons.Outlined.CropFree
    }
    fun next(): PlayerResizeMode = when (this) { FIT -> FILL; FILL -> ZOOM; ZOOM -> FIT }
    fun toMedia3(): Int = when (this) {
        FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
}

private fun formatPlaybackSpeed(speed: Float): String {
    val normalized = if (abs(speed - speed.toInt()) < 0.01f) speed.toInt().toString() else "%.2g".format(speed)
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
