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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.innotrepid.videoplayer.intelligence.MomentumEvent
import com.innotrepid.videoplayer.intelligence.MomentumEventRecorder
import com.innotrepid.videoplayer.intelligence.VideoSessionQueue
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoLibraryViewModel
import com.innotrepid.videoplayer.library.VideoThumbnailLoader
import com.innotrepid.videoplayer.playback.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class SafeScreen { PULSE, LIBRARY, SAVED, SETTINGS }
private val SafeViolet = Color(0xFF8B5CF6)
private val SafeCyan = Color(0xFF22D3EE)
private val SafePink = Color(0xFFEC4899)

@Composable
fun VideoPlayerRootSafe() {
    val context = LocalContext.current
    val vm: VideoLibraryViewModel = viewModel()
    val videos by vm.videos.collectAsState()
    val recorder = remember { MomentumEventRecorder(context.applicationContext) }
    val prefs = remember { context.getSharedPreferences("video_player_preferences", 0) }
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(context).build() }
    val playbackController = remember(player) { PlaybackController(player) }
    var screen by remember { mutableStateOf(SafeScreen.PULSE) }
    var previousScreen by remember { mutableStateOf(SafeScreen.PULSE) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var queue by remember { mutableStateOf<VideoSessionQueue?>(null) }
    var search by remember { mutableStateOf("") }
    var lightMode by remember { mutableStateOf(prefs.getBoolean("light_mode", false)) }
    var permission by remember { mutableStateOf(hasSafeVideoPermission(context)) }
    val selected = videos.firstOrNull { it.id == selectedId }
    val latestSelected by rememberUpdatedState(selected)
    val latestQueue by rememberUpdatedState(queue)
    val latestVideos by rememberUpdatedState(videos)

    val openVideo: (VideoItem) -> Unit = remember(videos) { { video -> queue = VideoSessionQueue.create(videos, video.id); selectedId = video.id } }
    val openSession: (VideoItem) -> Unit = { video -> queue = queue?.moveTo(video.id) ?: VideoSessionQueue.create(latestVideos, video.id); selectedId = video.id }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> permission = result.values.any { it }; if (permission) vm.scanDevice() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; vm.add(uri) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/jsonl")) { uri -> if (uri != null) scope.launch(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(recorder.exportText().toByteArray()) } } } }

    LaunchedEffect(Unit) { if (permission) vm.scanDevice() }
    DisposableEffect(player) { onDispose { playbackController.release(); recorder.shutdown() } }
    LaunchedEffect(selectedId) {
        val video = selected ?: return@LaunchedEffect
        playbackController.setMedia(video.uri, video.lastPositionMs.coerceAtLeast(0L), true)
    }
    LaunchedEffect(selectedId) {
        while (isActive && selectedId != null) {
            delay(8_000L)
            latestSelected?.let { video ->
                val duration = playbackController.durationMs()
                if (duration > 0L) vm.updateProgress(video.id, playbackController.currentPositionMs(), duration)
            }
        }
    }
    LaunchedEffect(playbackController) {
        playbackController.events.collect { event ->
            val video = latestSelected ?: return@collect
            val now = System.currentTimeMillis()
            when (event) {
                is PlaybackController.Event.Started -> recorder.emit(MomentumEvent.VideoStarted(video.id, event.positionMs, now))
                is PlaybackController.Event.Resumed -> recorder.emit(MomentumEvent.VideoResumed(video.id, event.positionMs, now))
                is PlaybackController.Event.Paused -> recorder.emit(MomentumEvent.VideoPaused(video.id, event.positionMs, now))
                is PlaybackController.Event.Seeked -> recorder.emit(MomentumEvent.VideoSeeked(video.id, event.fromPositionMs, event.toPositionMs, now))
                is PlaybackController.Event.Completed -> {
                    recorder.emit(MomentumEvent.VideoCompleted(video.id, event.durationMs, now))
                    vm.markCompleted(video.id)
                    val next = latestQueue?.advance()
                    if (next != null) {
                        queue = next
                        selectedId = next.current?.id
                    } else {
                        selectedId = null
                    }
                }
                is PlaybackController.Event.Error -> recorder.emit(MomentumEvent.VideoError(video.id, event.positionMs, event.message, now))
            }
        }
    }

    val scheme = if (lightMode) lightColorScheme(primary = Color(0xFF6D28D9), secondary = Color(0xFF0891B2), tertiary = Color(0xFFDB2777)) else darkColorScheme(primary = SafeViolet, secondary = SafeCyan, tertiary = SafePink, background = Color(0xFF07070C), surface = Color(0xFF101018), surfaceVariant = Color(0xFF171724))
    MaterialTheme(colorScheme = scheme) {
        if (selected != null) {
            SafePlayer(selected, player, playbackController, queue, { vm.toggleFavorite(selected.id) }, {
                val duration = playbackController.durationMs()
                if (duration > 0L) vm.updateProgress(selected.id, playbackController.currentPositionMs(), duration)
                selectedId = null
                playbackController.pause()
            }, openSession)
        } else {
            val order = SafeScreen.entries
            val direction = if (order.indexOf(screen) >= order.indexOf(previousScreen)) 1 else -1
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(screen) { detectHorizontalDragGestures { _, amount -> if (abs(amount) > 100f) { val i = order.indexOf(screen); val next = (i + if (amount < 0) 1 else -1).coerceIn(0, order.lastIndex); if (next != i) { previousScreen = screen; screen = order[next] } } } }) {
                AnimatedContent(targetState = screen, transitionSpec = { val forward = direction > 0; (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith (slideOutHorizontally { if (forward) -it else it } + fadeOut()) }, label = "safe-screen") { target ->
                    when (target) {
                        SafeScreen.PULSE -> SafePulse(videos, permission, { permissionLauncher.launch(safeVideoPermissions()) }, vm::toggleFavorite, openVideo) { previousScreen = screen; screen = SafeScreen.LIBRARY }
                        SafeScreen.LIBRARY -> GroupedLibraryRoot(videos, search, { search = it }, openVideo, vm::toggleFavorite) { picker.launch(arrayOf("video/*")) }
                        SafeScreen.SAVED -> SafeSaved(videos.filter { it.isFavorite }, openVideo, vm::toggleFavorite)
                        SafeScreen.SETTINGS -> SafeSettings(lightMode, { value -> lightMode = value; prefs.edit().putBoolean("light_mode", value).apply() }, permission, { permissionLauncher.launch(safeVideoPermissions()) }, { picker.launch(arrayOf("video/*")) }, { exportLauncher.launch("video-player-diagnostics.jsonl") }, { scope.launch(Dispatchers.IO) { recorder.clear() } })
                    }
                }
                SafeBottomBar(screen) { previousScreen = screen; screen = it }
            }
        }
    }
}

@Composable private fun SafeBottomBar(selected: SafeScreen, onSelect: (SafeScreen) -> Unit) {
    NavigationBar(modifier = Modifier.fillMaxWidth(), tonalElevation = 10.dp) {
        SafeNav(SafeScreen.PULSE, selected, Icons.Outlined.AutoAwesome, "Pulse", onSelect)
        SafeNav(SafeScreen.LIBRARY, selected, Icons.Outlined.VideoLibrary, "Library", onSelect)
        SafeNav(SafeScreen.SAVED, selected, Icons.Outlined.BookmarkBorder, "Saved", onSelect)
        SafeNav(SafeScreen.SETTINGS, selected, Icons.Outlined.Settings, "Settings", onSelect)
    }
}

@Composable private fun RowScope.SafeNav(item: SafeScreen, selected: SafeScreen, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onSelect: (SafeScreen) -> Unit) {
    val active = item == selected
    Box(Modifier.weight(1f).clickable { onSelect(item) }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); Text(label, fontSize = 10.sp, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable private fun SafePulse(videos: List<VideoItem>, permission: Boolean, request: () -> Unit, favorite: (String) -> Unit, open: (VideoItem) -> Unit, library: () -> Unit) {
    val resume = videos.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    val recent = videos.filter { it.lastPlayedAtMs > 0 }.sortedByDescending { it.lastPlayedAtMs }.take(10)
    val folders = videos.mapNotNull { it.folderName }.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }
    LazyColumn(contentPadding = PaddingValues(top = 18.dp, bottom = 105.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(210.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(SafeViolet, Color(0xFF1D1740), SafeCyan)))) { Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) { Text("PULSE", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp); Spacer(Modifier.height(8.dp)); Text("Your watchspace,\nin motion.", color = Color.White, style = MaterialTheme.typography.headlineMedium); Text("${videos.size} videos in your orbit", color = Color.White.copy(alpha = .72f), fontSize = 12.sp) }; IconButton(onClick = library, Modifier.align(Alignment.TopEnd).padding(10.dp)) { Icon(Icons.Outlined.VideoLibrary, "Library", tint = Color.White) } } }
        if (!permission) item { SafeAction(Icons.Outlined.Storage, "Connect your library", "Index local videos without copying them.", "ALLOW", request, SafeViolet) }
        if (resume.isNotEmpty()) item { SafeRail("Continue", resume, open, favorite, SafeViolet) }
        if (folders.isNotEmpty()) item { SafeFolderRail(folders, library) }
        if (recent.isNotEmpty()) item { SafeRail("Recently watched", recent, open, favorite, SafeCyan) }
        item { SafeSection("Your space", "Everything in your orbit, ready to move again.") }
        items(videos.take(16), key = { it.id }) { video -> SafeFeed(video, open, favorite) }
        if (videos.isEmpty()) item { SafeAction(Icons.Outlined.VideoLibrary, "Your library is quiet", "Open Library to import videos and build your space.", "OPEN LIBRARY", library, SafeCyan) }
    }
}

@Composable private fun SafeRail(title: String, videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit, accent: Color) { Column { SafeSection(title, "Keep moving through what matters."); LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(videos, key = { it.id }) { video -> Column(Modifier.width(190.dp).clickable { open(video) }) { Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp))) { SafeThumb(video); IconButton(onClick = { favorite(video.id) }, Modifier.align(Alignment.TopEnd)) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = if (video.isFavorite) SafePink else Color.White) } }; Spacer(Modifier.height(7.dp)); Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall); video.folderName?.let { Text(it, color = accent, fontSize = 10.sp) } } } } } }
@Composable private fun SafeFolderRail(folders: List<Pair<String, Int>>, open: () -> Unit) { Column { SafeSection("Collections", "Folders from your device, kept intact."); LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(folders) { (name, count) -> SafeAction(Modifier.width(210.dp), Icons.Outlined.Folder, name, "$count videos", "OPEN", open, SafeViolet) } } } }
@Composable private fun SafeFeed(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { open(video) }, RoundedCornerShape(24.dp)) { Column { Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { SafeThumb(video) }; Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2); video.folderName?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp) } }; IconButton(onClick = { favorite(video.id) }) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save") } } } } }
@Composable private fun SafeSaved(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 105.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { SafeSection("Saved", "Videos you chose to keep close.") }; if (videos.isEmpty()) item { SafeAction(Icons.Outlined.BookmarkBorder, "Nothing saved yet", "Save a video from any screen and it will appear here.", "OPEN LIBRARY", {}, SafeViolet) }; items(videos, key = { it.id }) { SafeFeed(it, open, favorite) } } }
@Composable private fun SafeSettings(lightMode: Boolean, setLightMode: (Boolean) -> Unit, permission: Boolean, requestPermission: () -> Unit, addVideo: () -> Unit, exportDiagnostics: () -> Unit, clearDiagnostics: () -> Unit) { LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 105.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { SafeSection("Settings", "Shape the player around the way you watch.") }; item { SafeSettingCard("Appearance") { SafeSettingRow(Icons.Outlined.DarkMode, "Light appearance", "Use a brighter interface") { Switch(checked = lightMode, onCheckedChange = setLightMode) } }; }; item { SafeSettingCard("Library") { SafeSettingRow(Icons.Outlined.Storage, "Device library", if (permission) "Permission granted" else "Permission required") { if (!permission) TextButton(onClick = requestPermission) { Text("ALLOW") } }; SafeSettingRow(Icons.Outlined.VideoLibrary, "Import video", "Add a video without copying it") { TextButton(onClick = addVideo) { Text("IMPORT") } } } }; item { SafeSettingCard("Playback") { SafeSettingRow(Icons.Outlined.PlayCircleOutline, "Resume position", "Playback position is saved automatically"); SafeSettingRow(Icons.Outlined.Speed, "Playback speed", "Available from player controls"); SafeSettingRow(Icons.Outlined.ScreenRotation, "Orientation", "Switch portrait or landscape while watching") } }; item { SafeSettingCard("Diagnostics") { SafeSettingRow(Icons.Outlined.BugReport, "Export diagnostics", "Share playback events for debugging") { TextButton(onClick = exportDiagnostics) { Text("EXPORT") } }; SafeSettingRow(Icons.Outlined.DeleteSweep, "Clear diagnostics", "Remove local event history") { TextButton(onClick = clearDiagnostics) { Text("CLEAR") } } } }; item { SafeSettingCard("Momentum") { SafeSettingRow(Icons.Outlined.AutoAwesome, "Learning layer", "Playback behavior is recorded as metadata only; it can later help anticipate what you want next.") } } } }

@Composable private fun SafeSettingCard(title: String, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(24.dp)) { Column(Modifier.padding(vertical = 8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, color = SafeViolet, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)); content() } } }
@Composable private fun SafeSettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: (@Composable () -> Unit)? = null) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }; action?.invoke() } }
@Composable private fun SafeSection(title: String, subtitle: String) { Column(Modifier.padding(horizontal = 20.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) } }
@Composable private fun SafeAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: String, onClick: () -> Unit, accent: Color) = SafeAction(Modifier.fillMaxWidth().padding(horizontal = 20.dp), icon, title, subtitle, action, onClick, accent)
@Composable private fun SafeAction(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: String, onClick: () -> Unit, accent: Color) { Card(modifier.clickable { onClick() }, RoundedCornerShape(24.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }; Text(action, color = accent, fontSize = 10.sp) } } }
@Composable private fun SafeThumb(video: VideoItem) { val context = LocalContext.current; var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }; LaunchedEffect(video.id, video.uri) { bitmap = runCatching { VideoThumbnailLoader.load(context, video.uri, 480, 270) }.getOrNull() }; if (bitmap != null) androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), video.title, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF171724), Color(0xFF292044))))) { Icon(Icons.Outlined.PlayCircleOutline, null, tint = Color.White.copy(alpha = .55f), modifier = Modifier.align(Alignment.Center).size(42.dp)) } }

@Composable private fun SafePlayer(video: VideoItem, player: ExoPlayer, playbackController: PlaybackController, queue: VideoSessionQueue?, favorite: () -> Unit, back: () -> Unit, open: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val activity = context.safeActivity()
    val playbackState by playbackController.state.collectAsState()
    val next = queue?.next
    val previous = queue?.previous
    var fullscreen by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var volumeExpanded by remember { mutableStateOf(false) }
    DisposableEffect(fullscreen, landscape) { activity?.let { val controller = WindowCompat.getInsetsController(it.window, it.window.decorView); if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars()); it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }; onDispose { } }
    LaunchedEffect(Unit) { while (isActive) { delay(250L); playbackController.refresh() } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White) }; Column(Modifier.weight(1f)) { Text(video.title, color = Color.White, maxLines = 1); video.folderName?.let { Text(it, color = Color.White.copy(alpha = .55f), fontSize = 11.sp) } }; IconButton(onClick = favorite) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = if (video.isFavorite) SafePink else Color.White) } }
        AndroidView(factory = { PlayerView(context).apply { this.player = player; useController = true; controllerShowTimeoutMs = 2500; controllerAutoShow = true; setShowPreviousButton(false); setShowNextButton(false) } }, modifier = Modifier.fillMaxWidth().weight(1f))
        Surface(Modifier.fillMaxWidth(), color = Color(0xFF09090E)) {
            Column(Modifier.padding(12.dp)) {
                if (playbackState.durationMs > 0L) {
                    Slider(value = playbackState.positionMs.coerceIn(0L, playbackState.durationMs).toFloat(), onValueChange = { playbackController.seekTo(it.toLong()) }, valueRange = 0f..playbackState.durationMs.toFloat(), modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatPlaybackTime(playbackState.positionMs), color = Color.White.copy(alpha = .72f), fontSize = 10.sp); Text(formatPlaybackTime(playbackState.durationMs), color = Color.White.copy(alpha = .72f), fontSize = 10.sp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SafeControl(Icons.Outlined.SkipPrevious, previous != null) { previous?.let(open) }
                    SafeControl(Icons.Outlined.Replay10, true) { playbackController.seekBy(-10_000L) }
                    FilledIconButton(onClick = playbackController::togglePlayPause) { Icon(if (playbackState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playbackState.isPlaying) "Pause" else "Play") }
                    SafeControl(Icons.Outlined.Forward10, true) { playbackController.seekBy(10_000L) }
                    SafeControl(Icons.Outlined.SkipNext, next != null) { next?.let(open) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SafeControl(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, true) { fullscreen = !fullscreen }
                    SafeControl(Icons.Outlined.ScreenRotation, true) { landscape = !landscape }
                    Box {
                        SafeControl(Icons.Outlined.Speed, true) { speedMenu = true }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) { listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { value -> DropdownMenuItem(text = { Text("${value}x") }, onClick = { playbackController.setSpeed(value); speedMenu = false }) } }
                    }
                    Box {
                        SafeControl(if (playbackState.isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, true) { volumeExpanded = !volumeExpanded }
                        DropdownMenu(expanded = volumeExpanded, onDismissRequest = { volumeExpanded = false }) {
                            Column(Modifier.width(220.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Volume", color = Color.White)
                                Slider(value = playbackState.volume, onValueChange = playbackController::setVolume, valueRange = 0f..1f)
                            }
                        }
                    }
                }
                if (playbackState.errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SafePink.copy(alpha = .12f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = SafePink)
                        Spacer(Modifier.width(8.dp))
                        Text(playbackState.errorMessage ?: "Playback error", color = Color.White, modifier = Modifier.weight(1f), maxLines = 2)
                        TextButton(onClick = { playbackController.play() }) { Text("RETRY", color = SafePink) }
                    }
                }
                if (next != null) { Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { open(next) }.background(SafeCyan.copy(alpha = .08f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.SkipNext, null, tint = SafeCyan); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("UP NEXT", color = SafeCyan, fontSize = 10.sp); Text(next.title, color = Color.White, maxLines = 1) }; Icon(Icons.Outlined.ChevronRight, null, tint = Color.White.copy(alpha = .6f)) } }
            }
        }
    }
}

@Composable private fun SafeControl(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) { IconButton(onClick = onClick, enabled = enabled) { Icon(icon, null, tint = if (enabled) Color.White else Color.White.copy(alpha = .25f)) } }

private fun formatPlaybackTime(milliseconds: Long): String { val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L); val hours = totalSeconds / 3600L; val minutes = (totalSeconds % 3600L) / 60L; val seconds = totalSeconds % 60L; return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds) }
private fun Context.safeActivity(): Activity? { var current = this; while (current is ContextWrapper) { if (current is Activity) return current; current = current.baseContext }; return null }
private fun hasSafeVideoPermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 33) androidx.core.content.ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VIDEO") == PackageManager.PERMISSION_GRANTED else androidx.core.content.ContextCompat.checkSelfPermission(context, "android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED
private fun safeVideoPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) arrayOf("android.permission.READ_MEDIA_VIDEO") else arrayOf("android.permission.READ_EXTERNAL_STORAGE")