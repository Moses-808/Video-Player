package com.innotrepid.videoplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
private val violet = Color(0xFF8B5CF6)
private val cyan = Color(0xFF22D3EE)
private val pink = Color(0xFFEC4899)
private val night = Color(0xFF07070C)

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
    var previousScreen by remember { mutableStateOf(RootScreen.PULSE) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf(hasVideoPermission(context)) }
    var queue by remember { mutableStateOf<VideoSessionQueue?>(null) }
    val selected = videos.firstOrNull { it.id == selectedId }
    val latestSelected by rememberUpdatedState(selected)
    val latestVideos by rememberUpdatedState(videos)
    val latestQueue by rememberUpdatedState(queue)
    val player = remember { ExoPlayer.Builder(context).build() }

    val openVideo: (VideoItem) -> Unit = remember(videos) {
        { video ->
            queue = VideoSessionQueue.create(videos, video.id)
            selectedId = video.id
        }
    }
    val openSessionVideo: (VideoItem) -> Unit = { video ->
        queue = queue?.moveTo(video.id) ?: VideoSessionQueue.create(latestVideos, video.id)
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
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(recorder.exportText().toByteArray()) } }
            }
        }
    }

    LaunchedEffect(Unit) {
        permission = hasVideoPermission(context)
        if (permission) vm.scanDevice()
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
            recorder.shutdown()
        }
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
            latestSelected?.let {
                if (player.duration > 0L) vm.updateProgress(it.id, player.currentPosition, player.duration)
            }
        }
    }
    DisposableEffect(selectedId) {
        var started = false
        var completed = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val video = latestSelected ?: return
                if (isPlaying) {
                    recorder.emit(
                        if (started) MomentumEvent.VideoResumed(video.id, player.currentPosition, System.currentTimeMillis())
                        else MomentumEvent.VideoStarted(video.id, player.currentPosition, System.currentTimeMillis())
                    )
                    started = true
                } else if (started && player.playbackState != Player.STATE_ENDED) {
                    recorder.emit(MomentumEvent.VideoPaused(video.id, player.currentPosition, System.currentTimeMillis()))
                    if (player.duration > 0L) vm.updateProgress(video.id, player.currentPosition, player.duration)
                }
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    val video = latestSelected ?: return
                    if (abs(newPosition.positionMs - oldPosition.positionMs) >= 1000L) {
                        recorder.emit(MomentumEvent.VideoSeeked(video.id, oldPosition.positionMs, newPosition.positionMs, System.currentTimeMillis()))
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val video = latestSelected ?: return
                if (state == Player.STATE_READY && player.duration > 0L) {
                    vm.updateProgress(video.id, player.currentPosition, player.duration)
                }
                if (state == Player.STATE_ENDED && !completed) {
                    completed = true
                    recorder.emit(MomentumEvent.VideoCompleted(video.id, player.duration.coerceAtLeast(0L), System.currentTimeMillis()))
                    vm.markCompleted(video.id)
                    val next = latestQueue?.advance()
                    if (next != null) {
                        queue = next
                        selectedId = next.current?.id
                    } else {
                        selectedId = null
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                latestSelected?.let {
                    recorder.emit(MomentumEvent.VideoError(it.id, player.currentPosition.coerceAtLeast(0L), "${error.errorCodeName}: ${error.message.orEmpty()}", System.currentTimeMillis()))
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
                    if (!completed && started && position > 5_000L && position < duration * .95f) {
                        recorder.emit(MomentumEvent.VideoSkipped(video.id, position, duration, System.currentTimeMillis()))
                    }
                }
            }
            player.removeListener(listener)
        }
    }

    val colors = if (lightMode) {
        lightColorScheme(primary = Color(0xFF6D28D9), secondary = Color(0xFF0891B2), tertiary = Color(0xFFDB2777))
    } else {
        darkColorScheme(primary = violet, secondary = cyan, tertiary = pink, background = night, surface = Color(0xFF101018), surfaceVariant = Color(0xFF171724))
    }

    MaterialTheme(colorScheme = colors) {
        if (selected != null) {
            PlayerExperience(
                video = selected,
                player = player,
                queue = queue,
                favorite = { vm.toggleFavorite(selected.id) },
                back = {
                    vm.updateProgress(selected.id, player.currentPosition, player.duration)
                    selectedId = null
                    player.stop()
                },
                open = openSessionVideo
            )
            return@MaterialTheme
        }

        val order = RootScreen.entries
        val direction = if (order.indexOf(screen) >= order.indexOf(previousScreen)) 1 else -1
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(screen) {
                    detectHorizontalDragGestures { _, amount ->
                        if (abs(amount) > 100f) {
                            val i = order.indexOf(screen)
                            val n = (i + if (amount < 0) 1 else -1).coerceIn(0, order.lastIndex)
                            if (n != i) {
                                previousScreen = screen
                                screen = order[n]
                            }
                        }
                    }
                }
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val forward = direction > 0
                    (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it else it } + fadeOut())
                },
                label = "screen"
            ) { target ->
                when (target) {
                    RootScreen.PULSE -> PulseRoot(
                        videos = videos,
                        permission = permission,
                        request = { permissionLauncher.launch(videoPermissions()) },
                        favorite = vm::toggleFavorite,
                        open = openVideo,
                        library = { previousScreen = screen; screen = RootScreen.LIBRARY }
                    )
                    RootScreen.LIBRARY -> GroupedLibraryRoot(
                        videos,
                        search,
                        { search = it },
                        openVideo,
                        vm::toggleFavorite
                    ) { picker.launch(arrayOf("video/*")) }
                    RootScreen.SAVED -> SavedRoot(videos.filter { it.isFavorite }, openVideo, vm::toggleFavorite)
                    RootScreen.SETTINGS -> SettingsRoot(
                        lightMode,
                        { value -> lightMode = value; prefs.edit().putBoolean("light_mode", value).apply() },
                        permission,
                        { permissionLauncher.launch(videoPermissions()) },
                        { picker.launch(arrayOf("video/*")) },
                        { exportLauncher.launch("video-player-diagnostics.jsonl") },
                        { scope.launch(Dispatchers.IO) { recorder.clear() } }
                    )
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BottomNavigation(screen) { previousScreen = screen; screen = it }
            }
        }
    }
}

@Composable
private fun BottomNavigation(selected: RootScreen, onSelect: (RootScreen) -> Unit) {
    NavigationBar(tonalElevation = 10.dp) {
        NavItem(RootScreen.PULSE, selected, Icons.Outlined.AutoAwesome, "Pulse", onSelect)
        NavItem(RootScreen.LIBRARY, selected, Icons.Outlined.VideoLibrary, "Library", onSelect)
        NavItem(RootScreen.SAVED, selected, Icons.Outlined.BookmarkBorder, "Saved", onSelect)
        NavItem(RootScreen.SETTINGS, selected, Icons.Outlined.Settings, "Settings", onSelect)
    }
}

@Composable
private fun RowScope.NavItem(item: RootScreen, selected: RootScreen, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onSelect: (RootScreen) -> Unit) {
    val active = item == selected
    Box(
        Modifier.weight(1f).clickable { onSelect(item) }.padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, fontSize = 10.sp, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PulseRoot(videos: List<VideoItem>, permission: Boolean, request: () -> Unit, favorite: (String) -> Unit, open: (VideoItem) -> Unit, library: () -> Unit) {
    val resume = videos.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    val recent = videos.filter { it.lastPlayedAtMs > 0 }.sortedByDescending { it.lastPlayedAtMs }.take(10)
    val folders = videos.mapNotNull { it.folderName }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    LazyColumn(
        contentPadding = PaddingValues(top = 18.dp, bottom = 105.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Hero(videos.size, library) }
        if (!permission) item { ActionTile(Icons.Outlined.Storage, "Connect your library", "Index local videos without copying them.", "ALLOW", request, violet) }
        if (resume.isNotEmpty()) item { Rail("Continue", resume, open, favorite, violet) }
        if (folders.isNotEmpty()) item { FolderRail(folders, library) }
        if (recent.isNotEmpty()) item { Rail("Recently watched", recent, open, favorite, cyan) }
        item { Section("Your space", "Everything in your orbit, ready to move again.") }
        itemsIndexed(videos.take(16), key = { _, v -> v.id }) { index, video -> FeedTile(index + 1, video, open, favorite) }
        if (videos.isEmpty()) item { ActionTile(Icons.Outlined.VideoLibrary, "Your library is quiet", "Open Library to import videos and build your space.", "OPEN LIBRARY", library, cyan) }
    }
}

@Composable
private fun Hero(count: Int, library: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp).clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(violet, Color(0xFF1D1740), cyan)))
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("PULSE", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("Your watchspace,\nin motion.", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text("$count videos in your orbit", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
        }
        IconButton(onClick = library, Modifier.align(Alignment.TopEnd).padding(10.dp)) {
            Icon(Icons.Outlined.VideoLibrary, "Library", tint = Color.White)
        }
    }
}

@Composable private fun Section(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable private fun Rail(title: String, videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit, accent: Color) {
    Column {
        Section(title, "Keep moving through what matters.")
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(videos, key = { it.id }) { video -> MiniTile(video, open, favorite, accent) }
        }
    }
}

@Composable private fun MiniTile(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit, accent: Color) {
    Column(Modifier.width(190.dp).clickable { open(video) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp))) {
            Thumbnail(video)
            if (video.isResumeable) LinearProgressIndicator(progress = { video.progress }, Modifier.fillMaxWidth().align(Alignment.BottomCenter))
            IconButton(onClick = { favorite(video.id) }, Modifier.align(Alignment.TopEnd)) {
                Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = if (video.isFavorite) pink else Color.White)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
        video.folderName?.let { Text(it, color = accent, fontSize = 10.sp) }
    }
}

@Composable private fun FolderRail(folders: List<Pair<String, Int>>, open: () -> Unit) {
    Column {
        Section("Collections", "Folders from your device, kept intact.")
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(folders) { (name, count) -> ActionTile(Modifier.width(210.dp), Icons.Outlined.Folder, name, "$count videos", "OPEN", open, violet) }
        }
    }
}

@Composable private fun FeedTile(index: Int, video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { open(video) }, RoundedCornerShape(24.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Thumbnail(video)
                Surface(Modifier.align(Alignment.TopStart).padding(10.dp), shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = .62f)) {
                    Text("%02d".format(index), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
            }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(video.title, maxLines = 2)
                    if (video.isResumeable) Text("Continue at ${formatDuration(video.lastPositionMs)}", color = violet, fontSize = 11.sp)
                }
                IconButton(onClick = { favorite(video.id) }) {
                    Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save")
                }
            }
        }
    }
}

@Composable
private fun SavedRoot(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 105.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Section("Saved", "Your personal watchlist, ready when you are.") }
        if (videos.isEmpty()) item { ActionTile(Icons.Outlined.BookmarkBorder, "Nothing saved yet", "Tap the bookmark on any video to keep it here.", "OPEN LIBRARY", {}, cyan) }
        items(videos, key = { it.id }) { video -> FeedTile(0, video, open, favorite) }
    }
}

@Composable
private fun SettingsRoot(
    lightMode: Boolean,
    setLightMode: (Boolean) -> Unit,
    permission: Boolean,
    requestPermission: () -> Unit,
    addVideo: () -> Unit,
    exportDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 105.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Section("Settings", "Shape the player around the way you watch.") }
        item { SettingsSection("Appearance") {
            SettingRow(Icons.Outlined.DarkMode, "Light appearance", "Use a brighter interface", Switch(checked = lightMode, onCheckedChange = setLightMode))
        } }
        item { SettingsSection("Library") {
            SettingRow(Icons.Outlined.Storage, "Device library", if (permission) "Permission granted" else "Permission required", if (permission) null else TextButton(onClick = requestPermission) { Text("ALLOW") })
            SettingRow(Icons.Outlined.VideoLibrary, "Import video", "Add a video without copying it", TextButton(onClick = addVideo) { Text("IMPORT") })
        } }
        item { SettingsSection("Playback") {
            SettingRow(Icons.Outlined.PlayCircleOutline, "Resume position", "Playback position is saved automatically", null)
            SettingRow(Icons.Outlined.Speed, "Playback speed", "Available from the player controls", null)
            SettingRow(Icons.Outlined.ScreenRotation, "Orientation", "Switch portrait or landscape while watching", null)
        } }
        item { SettingsSection("Diagnostics") {
            SettingRow(Icons.Outlined.BugReport, "Export diagnostics", "Share Momentum playback events for debugging", TextButton(onClick = exportDiagnostics) { Text("EXPORT") })
            SettingRow(Icons.Outlined.DeleteSweep, "Clear diagnostics", "Remove the local event history", TextButton(onClick = clearDiagnostics) { Text("CLEAR") })
        } }
        item { SettingsSection("Momentum") {
            SettingRow(Icons.Outlined.AutoAwesome, "Learning layer", "Playback behavior is recorded as metadata only; future versions can use it to anticipate what you want next.", null)
        } }
    }
}

@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = violet, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            content()
        }
    }
}

@Composable private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: (@Composable () -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        action?.invoke()
    }
}

@Composable private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: String, onClick: () -> Unit, accent: Color) {
    ActionTile(Modifier.fillMaxWidth().padding(horizontal = 20.dp), icon, title, subtitle, action, onClick, accent)
}

@Composable private fun ActionTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: String, onClick: () -> Unit, accent: Color) {
    Card(modifier.clickable { onClick() }, RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = .12f)) {
                Icon(icon, null, tint = accent, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Text(action, color = accent, fontSize = 10.sp)
        }
    }
}

@Composable private fun Thumbnail(video: VideoItem) {
    val context = LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(video.id, video.uri) {
        bitmap = withContextOrNull { VideoThumbnailLoader.load(context, video.uri, 480, 270) }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF171724), Color(0xFF292044))))) {
            Icon(Icons.Outlined.PlayCircleOutline, null, tint = Color.White.copy(alpha = .55f), modifier = Modifier.align(Alignment.Center).size(42.dp))
        }
    }
}

private suspend fun <T> withContextOrNull(block: suspend () -> T): T? = runCatching { block() }.getOrNull()

@Composable
private fun PlayerExperience(video: VideoItem, player: ExoPlayer, queue: VideoSessionQueue?, favorite: () -> Unit, back: () -> Unit, open: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val next = queue?.next
    val previous = queue?.previous
    var fullscreen by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, maxLines = 1)
                video.folderName?.let { Text(it, color = Color.White.copy(alpha = .55f), fontSize = 11.sp) }
            }
            IconButton(onClick = favorite) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = if (video.isFavorite) pink else Color.White) }
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).pointerInput(video.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> gestureOffset += (amount * -35L).toLong() },
                    onDragEnd = {
                        if (abs(gestureOffset) >= 1000L) player.seekTo((player.currentPosition + gestureOffset).coerceIn(0L, player.duration.coerceAtLeast(0L)))
                        gestureOffset = 0L
                    }
                )
            }
        ) {
            AndroidView(
                factory = { PlayerView(context).apply { player = this@PlayerExperience.player; useController = true; controllerShowTimeoutMs = 2500; controllerAutoShow = true; setShowPreviousButton(false); setShowNextButton(false) } },
                modifier = Modifier.fillMaxSize()
            )
            if (gestureOffset != 0L) {
                Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(18.dp), color = Color.Black.copy(alpha = .78f)) {
                    Text(if (gestureOffset > 0) "+${formatDuration(abs(gestureOffset))}" else "−${formatDuration(abs(gestureOffset))}", color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), color = Color(0xFF09090E)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Control(Icons.Outlined.SkipPrevious, "Previous", previous != null) { previous?.let(open) }
                    Control(Icons.Outlined.Replay10, "Back 10 seconds", true) { player.seekBack() }
                    FilledIconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, Modifier.size(58.dp)) { Icon(if (player.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "Play") }
                    Control(Icons.Outlined.Forward10, "Forward 10 seconds", true) { player.seekForward() }
                    Control(Icons.Outlined.SkipNext, "Next", next != null) { next?.let(open) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Control(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, "Fullscreen", true) { fullscreen = !fullscreen }
                    Control(Icons.Outlined.ScreenRotation, "Orientation", true) { landscape = !landscape }
                    Box {
                        Control(Icons.Outlined.Speed, "Speed", true) { showSpeed = true }
                        DropdownMenu(expanded = showSpeed, onDismissRequest = { showSpeed = false }) {
                            listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                                DropdownMenuItem(text = { Text("${value}x") }, onClick = { player.setPlaybackSpeed(value); showSpeed = false })
                            }
                        }
                    }
                }
                if (next != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        Modifier.fillMaxWidth().clickable { open(next) },
                        shape = RoundedCornerShape(18.dp),
                        color = cyan.copy(alpha = .08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, cyan.copy(alpha = .25f))
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SkipNext, null, tint = cyan)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("UP NEXT", color = cyan, fontSize = 10.sp)
                                Text(next.title, color = Color.White, maxLines = 1)
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = Color.White.copy(alpha = .6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Control(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, description) }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun hasVideoPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_VIDEO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun videoPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
    arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
} else {
    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val seconds = total % 60
    val minutes = (total / 60) % 60
    val hours = total / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
