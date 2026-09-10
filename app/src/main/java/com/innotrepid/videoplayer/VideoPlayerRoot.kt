package com.innotrepid.videoplayer

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.innotrepid.videoplayer.intelligence.MomentumEvent
import com.innotrepid.videoplayer.intelligence.MomentumEventRecorder
import com.innotrepid.videoplayer.intelligence.VideoSessionQueue
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoLibraryViewModel
import com.innotrepid.videoplayer.library.VideoThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class RootScreen { PULSE, LIBRARY, SAVED, SETTINGS }

private val nightBackground = Color(0xFF07070C)
private val nightSurface = Color(0xFF101018)
private val nightSurface2 = Color(0xFF171724)
private val violet = Color(0xFF8B5CF6)
private val cyan = Color(0xFF22D3EE)
private val pink = Color(0xFFEC4899)

@Composable
fun VideoPlayerRoot() {
    val context = LocalContext.current
    val vm: VideoLibraryViewModel = viewModel()
    val videos by vm.videos.collectAsState()
    val recorder = remember { MomentumEventRecorder(context.applicationContext) }
    val prefs = remember { context.getSharedPreferences("video_player_preferences", 0) }
    val scope = rememberCoroutineScope()
    var lightMode by remember { mutableStateOf(prefs.getBoolean("light_mode", false)) }
    var screen by remember { mutableStateOf(RootScreen.PULSE) }
    var previous by remember { mutableStateOf(RootScreen.PULSE) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf(hasVideoPermission(context)) }
    var sessionQueue by remember { mutableStateOf<VideoSessionQueue?>(null) }
    val selected = videos.firstOrNull { it.id == selectedId }
    val player = remember { ExoPlayer.Builder(context).build() }
    val latestSelected by rememberUpdatedState(selected)
    val latestVideos by rememberUpdatedState(videos)
    val latestQueue by rememberUpdatedState(sessionQueue)

    val startVideo: (VideoItem) -> Unit = remember(videos) {
        { video ->
            sessionQueue = VideoSessionQueue.create(videos, video.id)
            selectedId = video.id
        }
    }
    val openSessionVideo: (VideoItem) -> Unit = { video ->
        val moved = sessionQueue?.moveTo(video.id)
        sessionQueue = moved ?: VideoSessionQueue.create(latestVideos, video.id)
        selectedId = video.id
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permission = grants.values.any { it }
        if (permission) vm.scanDevice()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.add(uri)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/jsonl")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(recorder.exportText().toByteArray()) } }
        }
    }

    LaunchedEffect(Unit) {
        permission = hasVideoPermission(context)
        if (permission) vm.scanDevice()
    }

    DisposableEffect(player) {
        onDispose { player.release(); recorder.shutdown() }
    }

    LaunchedEffect(selectedId) {
        val video = selected ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(video.uri), video.lastPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(selectedId) {
        while (isActive && selectedId != null) {
            delay(8_000L)
            val video = latestSelected ?: continue
            if (player.duration > 0L) vm.updateProgress(video.id, player.currentPosition, player.duration)
        }
    }

    DisposableEffect(selectedId) {
        var started = false
        var completed = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val video = latestSelected ?: return
                val now = System.currentTimeMillis()
                if (isPlaying) {
                    recorder.emit(if (started) MomentumEvent.VideoResumed(video.id, player.currentPosition, now) else MomentumEvent.VideoStarted(video.id, player.currentPosition, now))
                    started = true
                } else if (started && player.playbackState != Player.STATE_ENDED) {
                    recorder.emit(MomentumEvent.VideoPaused(video.id, player.currentPosition, now))
                    if (player.duration > 0L) vm.updateProgress(video.id, player.currentPosition, player.duration)
                }
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    val video = latestSelected ?: return
                    val from = oldPosition.positionMs
                    val to = newPosition.positionMs
                    if (abs(to - from) >= 1000L) recorder.emit(MomentumEvent.VideoSeeked(video.id, from, to, System.currentTimeMillis()))
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                val video = latestSelected ?: return
                if (state == Player.STATE_READY && player.duration > 0L) vm.updateProgress(video.id, player.currentPosition, player.duration)
                if (state == Player.STATE_ENDED && !completed) {
                    completed = true
                    recorder.emit(MomentumEvent.VideoCompleted(video.id, player.duration.coerceAtLeast(0L), System.currentTimeMillis()))
                    vm.markCompleted(video.id)
                    val nextQueue = latestQueue?.advance()
                    if (nextQueue != null) {
                        sessionQueue = nextQueue
                        selectedId = nextQueue.current?.id
                    } else selectedId = null
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                latestSelected?.let { video ->
                    recorder.emit(MomentumEvent.VideoError(video.id, player.currentPosition.coerceAtLeast(0L), "${error.errorCodeName}: ${error.message.orEmpty()} [cause=${error.cause?.javaClass?.name}]", System.currentTimeMillis()))
                }
            }
        }
        player.addListener(listener)
        onDispose {
            latestSelected?.let { video ->
                val duration = player.duration
                if (duration > 0L) {
                    val position = player.currentPosition
                    vm.updateProgress(video.id, position, duration)
                    if (!completed && started && position > 5_000L && position < duration * 0.95f) recorder.emit(MomentumEvent.VideoSkipped(video.id, position, duration, System.currentTimeMillis()))
                }
            }
            player.removeListener(listener)
        }
    }

    val colors = if (lightMode) {
        lightColorScheme(primary = Color(0xFF6D28D9), secondary = Color(0xFF0891B2), tertiary = Color(0xFFDB2777))
    } else {
        darkColorScheme(primary = violet, secondary = cyan, tertiary = pink, background = nightBackground, surface = nightSurface, surfaceVariant = nightSurface2)
    }

    MaterialTheme(colorScheme = colors) {
        if (selected != null) {
            PlayerExperience(selected, player, sessionQueue, { vm.toggleFavorite(selected.id) }, {
                vm.updateProgress(selected.id, player.currentPosition, player.duration)
                selectedId = null
                player.stop()
            }, openSessionVideo)
            return@MaterialTheme
        }

        val order = RootScreen.entries
        val direction = if (order.indexOf(screen) >= order.indexOf(previous)) 1 else -1
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().pointerInput(screen) {
                detectHorizontalDragGestures { _, amount ->
                    if (abs(amount) > 100f) {
                        val index = order.indexOf(screen)
                        val next = (index + if (amount < 0) 1 else -1).coerceIn(0, order.lastIndex)
                        if (next != index) { previous = screen; screen = order[next] }
                    }
                }
            }) {
                AnimatedContent(targetState = screen, transitionSpec = {
                    val forward = direction > 0
                    (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith (slideOutHorizontally { if (forward) -it else it } + fadeOut())
                }, label = "screen") { target ->
                    when (target) {
                        RootScreen.PULSE -> PulseRoot(videos, permission, { permissionLauncher.launch(videoPermissions()) }, vm::toggleFavorite, startVideo, { previous = screen; screen = RootScreen.LIBRARY })
                        RootScreen.LIBRARY -> GroupedLibraryRoot(videos, search, { search = it }, startVideo, vm::toggleFavorite, { picker.launch(arrayOf("video/*")) })
                        RootScreen.SAVED -> SavedRoot(videos.filter { it.isFavorite }, startVideo, vm::toggleFavorite)
                        RootScreen.SETTINGS -> SettingsRoot(lightMode, { lightMode = it; prefs.edit().putBoolean("light_mode", it).apply() }, permission, { permissionLauncher.launch(videoPermissions()) }, { picker.launch(arrayOf("video/*")) }, { exportLauncher.launch("video-player-diagnostics.jsonl") }, { scope.launch(Dispatchers.IO) { recorder.clear() } })
                    }
                }
                NavigationBar(Modifier.align(Alignment.BottomCenter), tonalElevation = 8.dp) {
                    AppNavItem(RootScreen.PULSE, screen, Icons.Outlined.AutoAwesome, "Pulse") { previous = screen; screen = RootScreen.PULSE }
                    AppNavItem(RootScreen.LIBRARY, screen, Icons.Outlined.VideoLibrary, "Library") { previous = screen; screen = RootScreen.LIBRARY }
                    AppNavItem(RootScreen.SAVED, screen, Icons.Outlined.BookmarkBorder, "Saved") { previous = screen; screen = RootScreen.SAVED }
                    AppNavItem(RootScreen.SETTINGS, screen, Icons.Outlined.Settings, "Settings") { previous = screen; screen = RootScreen.SETTINGS }
                }
            }
        }
    }
}

@Composable private fun AppNavItem(item: RootScreen, selected: RootScreen, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, click: () -> Unit) {
    NavigationBarItem(selected = item == selected, onClick = click, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 11.sp) })
}

@Composable private fun PlayerExperience(video: VideoItem, player: ExoPlayer, queue: VideoSessionQueue?, favorite: () -> Unit, back: () -> Unit, open: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val next = queue?.next
    val previous = queue?.previous
    var fullscreen by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var gestureOffset by remember { mutableLongStateOf(0L) }

    DisposableEffect(fullscreen, landscape) {
        activity?.let {
            val controller = WindowCompat.getInsetsController(it.window, it.window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars())
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                video.folderName?.let { Text(it, color = Color.White.copy(alpha = .55f), fontSize = 11.sp) }
            }
            IconButton(onClick = favorite) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = if (video.isFavorite) pink else Color.White) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).pointerInput(video.id) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, amount -> gestureOffset += (amount * -35L).toLong() },
                onDragEnd = {
                    if (abs(gestureOffset) >= 1000L) player.seekTo((player.currentPosition + gestureOffset).coerceIn(0L, player.duration.coerceAtLeast(0L)))
                    gestureOffset = 0L
                }
            )
        }) {
            AndroidView({ PlayerView(context).apply {
                this.player = player
                useController = true
                controllerShowTimeoutMs = 2500
                controllerAutoShow = true
                setShowPreviousButton(false)
                setShowNextButton(false)
            } }, Modifier.fillMaxSize())
            AnimatedVisibility(gestureOffset != 0L, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
                Surface(RoundedCornerShape(18.dp), color = Color.Black.copy(alpha = .78f)) {
                    Text(if (gestureOffset > 0) "+${formatDuration(abs(gestureOffset))}" else "−${formatDuration(abs(gestureOffset))}", color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), color = Color(0xFF09090E)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PlayerIconButton(Icons.Outlined.SkipPrevious, "Previous", previous != null) { previous?.let(open) }
                    PlayerIconButton(Icons.Outlined.Replay10, "-10 seconds", true) { player.seekBack() }
                    FilledIconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(58.dp)) { Icon(if (player.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "Play") }
                    PlayerIconButton(Icons.Outlined.Forward10, "+10 seconds", true) { player.seekForward() }
                    PlayerIconButton(Icons.Outlined.SkipNext, "Next", next != null) { next?.let(open) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    PlayerIconButton(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, "Fullscreen", true) { fullscreen = !fullscreen }
                    PlayerIconButton(Icons.Outlined.ScreenRotation, "Orientation", true) { landscape = !landscape }
                    Box {
                        PlayerIconButton(Icons.Outlined.Speed, "Speed", true) { showSpeedMenu = true }
                        DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
                                DropdownMenuItem(text = { Text("${option}x") }, onClick = { speed = option; player.setPlaybackSpeed(option); showSpeedMenu = false })
                            }
                        }
                    }
                }
                AnimatedVisibility(next != null, enter = fadeIn(), exit = fadeOut()) {
                    next?.let {
                        Spacer(Modifier.height(10.dp))
                        FutureTile(modifier = Modifier.fillMaxWidth().animateContentSize(), accent = cyan, onClick = { open(it) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.SkipNext, null, tint = cyan)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("UP NEXT", fontSize = 10.sp, color = cyan)
                                    Text(it.title, color = Color.White, maxLines = 1)
                                    it.folderName?.let { folder -> Text(folder, color = Color.White.copy(alpha = .5f), fontSize = 11.sp) }
                                }
                                Icon(Icons.Outlined.ChevronRight, null, tint = Color.White.copy(alpha = .6f))
                            }
                        }
                    }
                }
                Text("Swipe on the picture to seek", color = Color.White.copy(alpha = .42f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable private fun PlayerIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, description) }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable private fun PulseRoot(videos: List<VideoItem>, permission: Boolean, request: () -> Unit, favorite: (String) -> Unit, open: (VideoItem) -> Unit, library: () -> Unit) {
    val resume = videos.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    val recent = videos.filter { it.lastPlayedAtMs > 0 }.sortedByDescending { it.lastPlayedAtMs }.take(10)
    val folders = videos.mapNotNull { it.folderName }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    LazyColumn(contentPadding = PaddingValues(top = 18.dp, bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PulseHero(videos.size, library) }
        if (!permission) item { FuturisticSettingTile(Icons.Outlined.Storage, "Connect your library", "Let Pulse index local videos without copying them.", "ALLOW", request, violet) }
        if (resume.isNotEmpty()) item { VideoRail("Continue", resume, open, favorite, violet) }
        if (folders.isNotEmpty()) item { FolderRail(folders, library) }
        if (recent.isNotEmpty()) item { VideoRail("Recently watched", recent, open, favorite, cyan) }
        item { SectionHeader("Your space", "Everything you have watched, ready to move again.") }
        itemsIndexed(videos.take(16), key = { _, v -> "feed-${v.id}" }) { i, video -> FeedTile(i + 1, video, open, favorite) }
        if (videos.isEmpty()) item { FuturisticSettingTile(Icons.Outlined.VideoLibrary, "Your library is quiet", "Open Library to bring in videos and build your space.", "OPEN LIBRARY", library, cyan) }
    }
}

@Composable private fun PulseHero(count: Int, library: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(violet.copy(alpha = .9f), Color(0xFF1D1740), cyan.copy(alpha = .65f))))) {
        Column(Modifier.padding(24.dp).align(Alignment.BottomStart)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("PULSE", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp) }
            Spacer(Modifier.height(8.dp))
            Text("Your watchspace,\nin motion.", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text("$count videos in your orbit", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
        }
        IconButton(onClick = library, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) { Icon(Icons.Outlined.VideoLibrary, "Library", tint = Color.White) }
    }
}

@Composable private fun FutureTile(modifier: Modifier = Modifier, accent: Color, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(modifier.clickable { onClick() }, RoundedCornerShape(20.dp), color = accent.copy(alpha = .08f), border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .28f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable private fun FuturisticSettingTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String, onClick: () -> Unit, accent: Color) {
    FutureTile(Modifier.fillMaxWidth().padding(horizontal = 20.dp), accent, onClick) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        if (action.isNotBlank()) Text(action, color = accent, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable private fun SectionHeader(title: String, subtitle: String) { Column(Modifier.padding(horizontal = 20.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) } }

@Composable private fun VideoRail(title: String, videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit, accent: Color) {
    Column { SectionHeader(title, "Keep moving through what matters." ); LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(videos, key = { it.id }) { MiniTile(it, open, favorite, accent) } } }
}

@Composable private fun MiniTile(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit, accent: Color) {
    Column(Modifier.width(190.dp).clickable { open(video) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            ThumbnailRoot(video, Modifier.fillMaxSize())
            if (video.isResumeable) LinearProgressIndicator(progress = { video.progress }, modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
            IconButton(onClick = { favorite(video.id) }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = if (video.isFavorite) pink else Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
        video.folderName?.let { Text(it, color = accent, fontSize = 10.sp, maxLines = 1) }
    }
}

@Composable private fun FolderRail(folders: List<Pair<String, Int>>, open: () -> Unit) {
    Column { SectionHeader("Collections", "Folders from your device, kept intact."); LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(folders) { (name, count) -> FutureTile(Modifier.width(190.dp), violet, open) { Icon(Icons.Outlined.Folder, null, tint = violet, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, maxLines = 2); Text("$count videos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }; Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
}

@Composable private fun FeedTile(index: Int, video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { open(video) }, shape = RoundedCornerShape(24.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { ThumbnailRoot(video, Modifier.fillMaxSize()); Surface(Modifier.align(Alignment.TopStart).padding(10.dp), RoundedCornerShape(10.dp), Color.Black.copy(alpha = .62f)) { Text("%02d".format(index), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) } }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2); if (video.isResumeable) LinearProgressIndicator(progress = { video.progress }, Modifier.fillMaxWidth().padding(top = 7.dp)) }; IconButton(onClick = { favorite(video.id) }) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = if (video.isFavorite) pink else MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

@Composable private fun SavedRoot(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("Saved", "Your private shelf of videos worth returning to.") }
        items(videos, key = { it.id }) { SavedTile(it, open, favorite) }
        if (videos.isEmpty()) item { FuturisticSettingTile(Icons.Outlined.BookmarkBorder, "Nothing saved yet", "Use the bookmark on any video to build your shelf.", "EXPLORE LIBRARY", {}, cyan) }
    }
}

@Composable private fun SavedTile(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { open(video) }, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { ThumbnailRoot(video, Modifier.size(130.dp, 82.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2); video.folderName?.let { Text(it, color = cyan, fontSize = 11.sp) }; if (video.isResumeable) LinearProgressIndicator(progress = { video.progress }, Modifier.fillMaxWidth().padding(top = 6.dp)) }; IconButton(onClick = { favorite(video.id) }) { Icon(Icons.Outlined.Favorite, "Remove from saved", tint = pink) } }
    }
}

@Composable private fun ThumbnailRoot(video: VideoItem, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(video.id, video.uri) { bitmap = VideoThumbnailLoader.load(context, video.uri, 480, 270) }
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Outlined.Movie, "Video", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
    }
}

@Composable private fun SettingsRoot(light: Boolean, setLight: (Boolean) -> Unit, permission: Boolean, request: () -> Unit, add: () -> Unit, export: () -> Unit, clear: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Column(Modifier.padding(horizontal = 20.dp)) { Text("Settings", style = MaterialTheme.typography.headlineMedium); Text("Tune the player, library and intelligence layer.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) } }
        item { SettingsSection("Appearance", Icons.Outlined.Palette) { SettingRow(Icons.Outlined.DarkMode, "Theme", if (light) "Light" else "Dark", null, setLight) } }
        item { SettingsSection("Library", Icons.Outlined.VideoLibrary) { SettingRow(Icons.Outlined.Storage, "Device access", if (permission) "Connected" else "Needs permission", if (permission) "SCAN" else "ALLOW", if (permission) add else request); SettingRow(Icons.Outlined.AddCircleOutline, "Import", "Choose a local video", "IMPORT", add) } }
        item { SettingsSection("Playback", Icons.Outlined.PlayCircleOutline) { SettingRow(Icons.Outlined.Fullscreen, "Immersive playback", "Fullscreen and orientation controls", null, {}); SettingRow(Icons.Outlined.Speed, "Playback speed", "0.75x — 2x", null, {}) } }
        item { SettingsSection("Diagnostics", Icons.Outlined.BugReport) { SettingRow(Icons.Outlined.FileDownload, "Export diagnostics", "JSONL playback and intelligence events", "EXPORT", export); SettingRow(Icons.Outlined.DeleteSweep, "Clear diagnostics", "Remove local event history", "CLEAR", clear) } }
        item { FutureTile(Modifier.fillMaxWidth().padding(horizontal = 20.dp), pink, {}) { Icon(Icons.Outlined.AutoAwesome, null, tint = pink); Spacer(Modifier.width(12.dp)); Column { Text("Momentum ready", style = MaterialTheme.typography.titleMedium); Text("The player is collecting the signals needed for anticipatory recommendations.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) } } }
    }
}

@Composable private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(title.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary) }
        Surface(RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), modifier = Modifier.fillMaxWidth()) { Column(content = content) }
    }
}

@Composable private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = action != null) { onClick() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
        if (title == "Theme") Switch(checked = body == "Light", onCheckedChange = onClick)
        else if (!action.isNullOrBlank()) Text(action, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

private fun hasVideoPermission(context: android.content.Context): Boolean = if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED else ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
private fun videoPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 34) arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if (Build.VERSION.SDK_INT >= 33) arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
