package com.innotrepid.videoplayer

import android.app.Activity
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoLibraryViewModel
import com.innotrepid.videoplayer.library.VideoThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class RootScreen { HOME, LIBRARY, SAVED, SETTINGS }

@Composable
fun VideoPlayerRoot() {
    val context = LocalContext.current
    val vm: VideoLibraryViewModel = viewModel()
    val videos by vm.videos.collectAsState()
    val recorder = remember { MomentumEventRecorder(context.applicationContext) }
    val prefs = remember { context.getSharedPreferences("video_player_preferences", 0) }
    val scope = rememberCoroutineScope()
    var lightMode by remember { mutableStateOf(prefs.getBoolean("light_mode", false)) }
    var screen by remember { mutableStateOf(RootScreen.HOME) }
    var previous by remember { mutableStateOf(RootScreen.HOME) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf(hasVideoPermission(context)) }
    val selected = videos.firstOrNull { it.id == selectedId }
    val player = remember { ExoPlayer.Builder(context).build() }
    val latestSelected by rememberUpdatedState(selected)
    val latestVideos by rememberUpdatedState(videos)

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
                    selectedId = nextVideo(latestVideos, video.id)?.id
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                latestSelected?.let { video -> recorder.emit(MomentumEvent.VideoError(video.id, player.currentPosition.coerceAtLeast(0L), "${error.errorCodeName}: ${error.message.orEmpty()} [cause=${error.cause?.javaClass?.name}]", System.currentTimeMillis())) }
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

    MaterialTheme(colorScheme = if (lightMode) lightColorScheme() else darkColorScheme()) {
        if (selected != null) {
            PlayerExperience(selected, player, videos, { vm.toggleFavorite(selected.id) }, {
                vm.updateProgress(selected.id, player.currentPosition, player.duration)
                selectedId = null
                player.stop()
            }, { selectedId = it.id }, { picker.launch(arrayOf("video/*")) })
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
                    (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it else it } + fadeOut())
                }, label = "screen") { target ->
                    when (target) {
                        RootScreen.HOME -> HomeRoot(videos, permission, { permissionLauncher.launch(videoPermissions()) }, { picker.launch(arrayOf("video/*")) }, vm::toggleFavorite, { selectedId = it.id }, { folder -> search = folder; previous = RootScreen.HOME; screen = RootScreen.LIBRARY })
                        RootScreen.LIBRARY -> GroupedLibraryRoot(videos, search, { search = it }, { selectedId = it.id }, vm::toggleFavorite, { picker.launch(arrayOf("video/*")) })
                        RootScreen.SAVED -> SavedRoot(videos.filter { it.isFavorite }, { selectedId = it.id }, vm::toggleFavorite)
                        RootScreen.SETTINGS -> SettingsRoot(lightMode, { lightMode = it; prefs.edit().putBoolean("light_mode", it).apply() }, permission, { permissionLauncher.launch(videoPermissions()) }, { picker.launch(arrayOf("video/*")) }, { exportLauncher.launch("video-player-diagnostics.jsonl") }, { scope.launch(Dispatchers.IO) { recorder.clear() } })
                    }
                }
                NavigationBar(Modifier.align(Alignment.BottomCenter)) {
                    NavButton("Home", screen == RootScreen.HOME) { previous = screen; screen = RootScreen.HOME }
                    NavButton("Library", screen == RootScreen.LIBRARY) { previous = screen; screen = RootScreen.LIBRARY }
                    NavButton("Saved", screen == RootScreen.SAVED) { previous = screen; screen = RootScreen.SAVED }
                    NavButton("Settings", screen == RootScreen.SETTINGS) { previous = screen; screen = RootScreen.SETTINGS }
                }
            }
        }
    }
}

private fun nextVideo(videos: List<VideoItem>, id: String): VideoItem? = videos.sortedWith(videoQueueComparator()).let { list -> list.getOrNull(list.indexOfFirst { it.id == id } + 1) }
private fun previousVideo(videos: List<VideoItem>, id: String): VideoItem? = videos.sortedWith(videoQueueComparator()).let { list -> list.getOrNull(list.indexOfFirst { it.id == id } - 1) }
private fun videoQueueComparator(): Comparator<VideoItem> = compareBy<VideoItem>({ it.folderName ?: "\uFFFF" }, { naturalVideoKey(it.title) }, { it.title.lowercase() })
private fun naturalVideoKey(title: String): String = buildString {
    Regex("\\d+").findAll(title.lowercase()).let { matches ->
        var cursor = 0
        matches.forEach { match ->
            append(title.substring(cursor, match.range.first).lowercase())
            append(match.value.toIntOrNull()?.toString()?.padStart(12, '0') ?: match.value)
            cursor = match.range.last + 1
        }
        append(title.substring(cursor).lowercase())
    }
}

@Composable private fun PlayerExperience(video: VideoItem, player: ExoPlayer, queue: List<VideoItem>, favorite: () -> Unit, back: () -> Unit, open: (VideoItem) -> Unit, add: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val next = nextVideo(queue, video.id)
    val previous = previousVideo(queue, video.id)
    var fullscreen by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var gestureOffset by remember { mutableLongStateOf(0L) }

    DisposableEffect(fullscreen, landscape) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window -> WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars()) }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("‹ Back") }
            Text(video.title, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            TextButton(onClick = favorite) { Text(if (video.isFavorite) "★" else "☆", color = Color.White, fontSize = 24.sp) }
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
            if (gestureOffset != 0L) {
                Surface(Modifier.align(Alignment.Center), RoundedCornerShape(16.dp), Color.Black.copy(alpha = .72f)) {
                    Text(if (gestureOffset > 0) "+${formatDuration(abs(gestureOffset))}" else "−${formatDuration(abs(gestureOffset))}", color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { previous?.let(open) }, enabled = previous != null) { Text("Previous") }
                TextButton(onClick = { player.seekBack() }) { Text("−10s") }
                FilledTonalButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) { Text(if (player.isPlaying) "Pause" else "Play") }
                TextButton(onClick = { player.seekForward() }) { Text("+10s") }
                TextButton(onClick = { next?.let(open) }, enabled = next != null) { Text("Next") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { fullscreen = !fullscreen }, label = { Text(if (fullscreen) "Exit full screen" else "Full screen") })
                AssistChip(onClick = { landscape = !landscape }, label = { Text(if (landscape) "Portrait" else "Landscape") })
                Box {
                    AssistChip(onClick = { showSpeedMenu = true }, label = { Text("${speed}x") })
                    DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
                            DropdownMenuItem(text = { Text("${option}x") }, onClick = { speed = option; player.setPlaybackSpeed(option); showSpeedMenu = false })
                        }
                    }
                }
            }
            if (next != null) {
                Spacer(Modifier.height(10.dp))
                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), Color.White.copy(alpha = .08f)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Up next", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .65f))
                            Text(next.title, color = Color.White, maxLines = 1)
                            next.folderName?.let { Text(it, color = Color.White.copy(alpha = .5f), fontSize = 12.sp) }
                        }
                        TextButton(onClick = { open(next) }) { Text("Play") }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Swipe horizontally over the picture to seek", color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
        }
    }
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

@Composable private fun NavButton(label: String, selected: Boolean, click: () -> Unit) { TextButton(onClick = click) { Text(if (selected) "●  $label" else "○  $label", color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable private fun HomeRoot(videos: List<VideoItem>, permission: Boolean, request: () -> Unit, add: () -> Unit, favorite: (String) -> Unit, open: (VideoItem) -> Unit, folder: (String) -> Unit) {
    val resume = videos.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    val recent = videos.filter { it.lastPlayedAtMs > 0 }.sortedByDescending { it.lastPlayedAtMs }.take(10)
    val folders = videos.mapNotNull { it.folderName }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { HeroRoot(add) }
        if (!permission) item { SettingCardRoot("Connect your device library", "Index local videos without copying them.", "Allow", request) }
        if (resume.isNotEmpty()) item { RailRoot("Continue Watching", resume, open, favorite) }
        if (folders.isNotEmpty()) item { FolderRailRoot(folders, folder) }
        if (recent.isNotEmpty()) item { RailRoot("Recently Watched", recent, open, favorite) }
        item { SectionRoot("Your feed", "A visual stream through your library") }
        itemsIndexed(videos.take(20), key = { _, v -> "feed-${v.id}" }) { i, v -> FeedCardRoot(i + 1, v, open, favorite) }
        if (videos.isEmpty()) item { SettingCardRoot("Nothing here yet", "Add a local video to start building your space.", "Add video", add) }
    }
}

@Composable private fun HeroRoot(add: () -> Unit) { Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceVariant) { Column(Modifier.padding(24.dp)) { Text("YOUR VIDEO SPACE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(10.dp)); Text("A video space that moves with you", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(8.dp)); Text("Your local library, organized around how you watch.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp)); Button(onClick = add) { Text("Add video") } } } }

@Composable private fun SavedRoot(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Column(Modifier.padding(horizontal = 20.dp)) { Text("Saved", style = MaterialTheme.typography.headlineMedium); Text("Videos you want close at hand", color = MaterialTheme.colorScheme.onSurfaceVariant) } }; items(videos, key = { it.id }) { VideoRowRoot(it, open, favorite) }; if (videos.isEmpty()) item { SettingCardRoot("Nothing saved", "Tap the star on any video to keep it here.", "") {} } } }

@Composable private fun RailRoot(title: String, videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { Column { SectionRoot(title, ""); LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(videos, key = { it.id }) { MiniCardRoot(it, open, favorite) } } } }
@Composable private fun FolderRailRoot(folders: List<Pair<String, Int>>, open: (String) -> Unit) { Column { SectionRoot("Series & Folders", ""); LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(folders) { (name, count) -> Surface(Modifier.width(170.dp).clickable { open(name) }, RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surfaceVariant) { Column(Modifier.padding(16.dp)) { Text(name, maxLines = 2); Spacer(Modifier.height(8.dp)); Text("$count videos", color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } } }
@Composable private fun SectionRoot(title: String, subtitle: String) { Column(Modifier.padding(horizontal = 20.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) } }

@Composable private fun FeedCardRoot(index: Int, video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { open(video) }) { Column { ThumbnailRoot(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f)); Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text("%02d".format(index), color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2); if (video.isResumeable) LinearProgressIndicator(progress = { video.progress }, Modifier.fillMaxWidth().padding(top = 6.dp)) }; TextButton(onClick = { favorite(video.id) }) { Text(if (video.isFavorite) "★" else "☆") } } } } }
@Composable private fun MiniCardRoot(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { Card(Modifier.width(210.dp).clickable { open(video) }) { Column { ThumbnailRoot(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f)); Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(video.title, maxLines = 2, modifier = Modifier.weight(1f)); TextButton(onClick = { favorite(video.id) }) { Text(if (video.isFavorite) "★" else "☆") } } } } }
@Composable private fun VideoRowRoot(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { open(video) }) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { ThumbnailRoot(video, Modifier.size(130.dp, 82.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2); video.folderName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }; if (video.isResumeable) LinearProgressIndicator(progress = { video.progress }, Modifier.fillMaxWidth().padding(top = 6.dp)) }; TextButton(onClick = { favorite(video.id) }) { Text(if (video.isFavorite) "★" else "☆") } } } }

@Composable private fun ThumbnailRoot(video: VideoItem, modifier: Modifier) { val context = LocalContext.current; var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }; LaunchedEffect(video.id) { bitmap = VideoThumbnailLoader.load(context, video.uri, 480, 270) }; Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text("VIDEO", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) } }
@Composable private fun SettingCardRoot(title: String, body: String, action: String, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title); Spacer(Modifier.height(4.dp)); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }; if (action.isNotBlank()) TextButton(onClick = onClick) { Text(action) } } } }

@Composable private fun SettingsRoot(light: Boolean, setLight: (Boolean) -> Unit, permission: Boolean, request: () -> Unit, add: () -> Unit, export: () -> Unit, clear: () -> Unit) { LazyColumn(contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }; item { SettingCardRoot("Appearance", if (light) "Light mode is on" else "Dark mode is on", "", {}); Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Light mode"); Switch(light, setLight) } }; item { SettingCardRoot("Device library", if (permission) "Permission granted" else "Permission needed", if (permission) "Add video" else "Allow", if (permission) add else request) }; item { SettingCardRoot("Diagnostics", "Local JSONL playback and intelligence events", "Export", export) }; item { SettingCardRoot("Reset diagnostics", "Clear the local event log", "Clear", clear) } } }

private fun hasVideoPermission(context: android.content.Context): Boolean = if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED else ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
private fun videoPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 34) arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if (Build.VERSION.SDK_INT >= 33) arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
