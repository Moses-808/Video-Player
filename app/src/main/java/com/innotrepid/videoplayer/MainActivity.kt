package com.innotrepid.videoplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VideoPlayerApp() }
    }
}

private enum class AppScreen { HOME, LIBRARY, SAVED, SETTINGS }

@Composable
private fun VideoPlayerApp() {
    val context = LocalContext.current
    val vm: VideoLibraryViewModel = viewModel()
    val videos by vm.videos.collectAsState()
    val recorder = remember { MomentumEventRecorder(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("video_player_preferences", 0) }
    var lightMode by remember { mutableStateOf(prefs.getBoolean("light_mode", false)) }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var previousScreen by remember { mutableStateOf(AppScreen.HOME) }
    var selected by remember { mutableStateOf<VideoItem?>(null) }
    var search by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf(hasVideoPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permission = grants.values.any { it }
        if (permission) vm.scanDevice()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.add(uri)
        }
    }
    val saveLogsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/jsonl")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val text = recorder.exportText()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        permission = hasVideoPermission(context)
        if (permission) vm.scanDevice()
    }

    val player = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(selected?.id) {
        val video = selected ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(video.uri), video.lastPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(selected?.id) {
        while (isActive && selected != null) {
            delay(10_000L)
            val video = selected ?: break
            if (player.isPlaying && player.duration > 0L) {
                vm.updateProgress(video.id, player.currentPosition, player.duration)
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
            recorder.shutdown()
        }
    }

    DisposableEffect(player, selected?.id) {
        var started = false
        var completed = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val video = selected ?: return
                val position = player.currentPosition.coerceAtLeast(0L)
                val now = System.currentTimeMillis()
                if (isPlaying) {
                    recorder.emit(
                        if (started) MomentumEvent.VideoResumed(video.id, position, now)
                        else MomentumEvent.VideoStarted(video.id, position, now)
                    )
                    started = true
                } else if (started && player.playbackState != Player.STATE_ENDED) {
                    recorder.emit(MomentumEvent.VideoPaused(video.id, position, now))
                    if (player.duration > 0L) vm.updateProgress(video.id, position, player.duration)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    val video = selected ?: return
                    val from = oldPosition.positionMs.coerceAtLeast(0L)
                    val to = newPosition.positionMs.coerceAtLeast(0L)
                    if (abs(to - from) >= 1_000L) {
                        recorder.emit(MomentumEvent.VideoSeeked(video.id, from, to, System.currentTimeMillis()))
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val video = selected ?: return
                if (state == Player.STATE_READY && player.duration > 0L) {
                    vm.updateProgress(video.id, player.currentPosition, player.duration)
                }
                if (state == Player.STATE_ENDED && !completed) {
                    completed = true
                    recorder.emit(MomentumEvent.VideoCompleted(video.id, player.duration.coerceAtLeast(0L), System.currentTimeMillis()))
                    vm.markCompleted(video.id)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                selected?.let { video ->
                    recorder.emit(
                        MomentumEvent.VideoError(
                            video.id,
                            player.currentPosition.coerceAtLeast(0L),
                            "${error.errorCodeName}: ${error.message.orEmpty()} [cause=${error.cause?.javaClass?.name}]",
                            System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        player.addListener(listener)
        onDispose {
            selected?.let { video ->
                val position = player.currentPosition.coerceAtLeast(0L)
                val duration = player.duration
                if (duration > 0L) {
                    vm.updateProgress(video.id, position, duration)
                    if (!completed && started && position > 5_000L && position < duration * 0.95f) {
                        recorder.emit(MomentumEvent.VideoSkipped(video.id, position, duration, System.currentTimeMillis()))
                    }
                }
            }
            player.removeListener(listener)
        }
    }

    if (selected != null) {
        val video = selected!!
        PlayerScreen(
            video = video,
            player = player,
            back = {
                vm.updateProgress(video.id, player.currentPosition, player.duration)
                selected = null
                player.stop()
            },
            add = { picker.launch(arrayOf("video/*")) }
        )
        return
    }

    val order = listOf(AppScreen.HOME, AppScreen.LIBRARY, AppScreen.SAVED, AppScreen.SETTINGS)
    val direction = if (order.indexOf(screen) >= order.indexOf(previousScreen)) 1 else -1
    val colors = if (lightMode) lightColorScheme() else darkColorScheme()

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(screen) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (abs(dragAmount) > 100f) {
                                val index = order.indexOf(screen)
                                val next = (index + if (dragAmount < 0f) 1 else -1).coerceIn(0, order.lastIndex)
                                if (next != index) {
                                    previousScreen = screen
                                    screen = order[next]
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
                    label = "screen_transition",
                    modifier = Modifier.fillMaxSize()
                ) { target ->
                    when (target) {
                        AppScreen.HOME -> HomeScreen(
                            videos = videos,
                            permission = permission,
                            requestPermission = { permissionLauncher.launch(videoPermissions()) },
                            add = { picker.launch(arrayOf("video/*")) },
                            favorite = vm::toggleFavorite,
                            open = { selected = it },
                            openFolder = { folder ->
                                previousScreen = AppScreen.HOME
                                search = folder
                                screen = AppScreen.LIBRARY
                            }
                        )
                        AppScreen.LIBRARY -> LibraryScreen(
                            videos = videos,
                            search = search,
                            setSearch = { search = it },
                            open = { selected = it },
                            favorite = vm::toggleFavorite,
                            add = { picker.launch(arrayOf("video/*")) }
                        )
                        AppScreen.SAVED -> SavedScreen(
                            videos = videos.filter { it.isFavorite },
                            open = { selected = it },
                            favorite = vm::toggleFavorite
                        )
                        AppScreen.SETTINGS -> SettingsScreen(
                            light = lightMode,
                            setLight = { value ->
                                lightMode = value
                                prefs.edit().putBoolean("light_mode", value).apply()
                            },
                            permission = permission,
                            requestPermission = { permissionLauncher.launch(videoPermissions()) },
                            add = { picker.launch(arrayOf("video/*")) },
                            saveLogs = { saveLogsLauncher.launch("video-player-diagnostics.jsonl") },
                            clearLogs = {
                                scope.launch(Dispatchers.IO) { recorder.clear() }
                            }
                        )
                    }
                }

                NavigationBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationButton("Home", screen == AppScreen.HOME) {
                        previousScreen = screen
                        screen = AppScreen.HOME
                    }
                    NavigationButton("Library", screen == AppScreen.LIBRARY) {
                        previousScreen = screen
                        screen = AppScreen.LIBRARY
                    }
                    NavigationButton("Saved", screen == AppScreen.SAVED) {
                        previousScreen = screen
                        screen = AppScreen.SAVED
                    }
                    NavigationButton("Settings", screen == AppScreen.SETTINGS) {
                        previousScreen = screen
                        screen = AppScreen.SETTINGS
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = if (selected) "●  $label" else "○  $label",
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeScreen(
    videos: List<VideoItem>,
    permission: Boolean,
    requestPermission: () -> Unit,
    add: () -> Unit,
    favorite: (String) -> Unit,
    open: (VideoItem) -> Unit,
    openFolder: (String) -> Unit
) {
    val resume = videos.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    val recent = videos.filter { it.lastPlayedAtMs > 0L }.sortedByDescending { it.lastPlayedAtMs }.take(10)
    val folders = videos.mapNotNull { it.folderName }.groupingBy { it }.eachCount()
        .toList().sortedByDescending { it.second }

    LazyColumn(
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Hero(add) }
        if (!permission) {
            item {
                SettingCard(
                    title = "Connect your device library",
                    body = "Index local videos without copying them.",
                    action = "Allow",
                    onClick = requestPermission
                )
            }
        }
        if (resume.isNotEmpty()) item { Rail("Continue Watching", resume, open, favorite) }
        if (folders.isNotEmpty()) item { FolderRail(folders, openFolder) }
        if (recent.isNotEmpty()) item { Rail("Recently Watched", recent, open, favorite) }
        item { SectionTitle("Your feed", "A visual stream through your library") }
        itemsIndexed(videos.take(20), key = { _, video -> "feed-${video.id}" }) { index, video ->
            FeedCard(index + 1, video, open, favorite)
        }
        if (videos.isEmpty()) {
            item {
                SettingCard("Nothing here yet", "Add a local video to start building your space.", "Add video", add)
            }
        }
    }
}

@Composable
private fun Hero(add: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("YOUR VIDEO SPACE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text("A video space that moves with you", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Your local library, organized around how you watch.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Button(onClick = add) { Text("Add video") }
        }
    }
}

@Composable
private fun LibraryScreen(
    videos: List<VideoItem>,
    search: String,
    setSearch: (String) -> Unit,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
    add: () -> Unit
) {
    val filtered = videos.filter { video ->
        search.isBlank() || video.title.contains(search, true) || video.folderName?.contains(search, true) == true
    }.sortedBy { it.title.lowercase() }

    LazyColumn(
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Library", style = MaterialTheme.typography.headlineMedium)
                Text("${filtered.size} video${if (filtered.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = setSearch,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search titles and folders") }
                )
            }
        }
        if (search.isNotBlank()) {
            item { Text("Search results", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
        items(filtered, key = { it.id }) { video ->
            LibraryCard(video, open, favorite)
        }
        if (filtered.isEmpty()) {
            item { SettingCard("No matching videos", "Try another title or folder, or add a video manually.", "Add video", add) }
        }
    }
}

@Composable
private fun LibraryCard(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { open(video) }, RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) { Thumbnail(video) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                video.folderName?.let { Text(it, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (video.isResumeable) {
                    Spacer(Modifier.height(5.dp))
                    Text("Resume ${formatTime(video.lastPositionMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = { favorite(video.id) }) { Text(if (video.isFavorite) "★" else "☆", fontSize = 22.sp) }
        }
    }
}

@Composable
private fun SavedScreen(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Saved", style = MaterialTheme.typography.headlineMedium)
                Text("Videos you chose to keep close.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(videos, key = { it.id }) { video -> LibraryCard(video, open, favorite) }
        if (videos.isEmpty()) item { SettingCard("Nothing saved", "Favorite videos and they will appear here.", "Go to Library", {}) }
    }
}

@Composable
private fun Rail(title: String, videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Column {
        SectionTitle(title)
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(videos, key = { it.id }) { video -> RailCard(video, open, favorite) }
        }
    }
}

@Composable
private fun FolderRail(folders: List<Pair<String, Int>>, open: (String) -> Unit) {
    Column {
        SectionTitle("Series & Folders", "Episodes stay together")
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(folders, key = { "folder-${it.first}" }) { folder ->
                val name = folder.first
                val count = folder.second
                Surface(
                    Modifier.width(190.dp).clickable { open(name) },
                    RoundedCornerShape(16.dp),
                    MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("SERIES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(name, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                        Text("$count episode${if (count == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RailCard(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Column(Modifier.width(220.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).clickable { open(video) }) {
            Thumbnail(video)
            TextButton(onClick = { favorite(video.id) }, modifier = Modifier.align(Alignment.TopEnd)) {
                Text(if (video.isFavorite) "★" else "☆", color = Color.White, fontSize = 22.sp)
            }
            if (video.isResumeable) LinearProgressIndicator({ video.progress }, Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
        Spacer(Modifier.height(7.dp))
        Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
        video.folderName?.let { Text(it, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FeedCard(index: Int, video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { open(video) }, RoundedCornerShape(20.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Thumbnail(video)
                Text("#%02d".format(index), color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
                TextButton(onClick = { favorite(video.id) }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Text(if (video.isFavorite) "★" else "☆", color = Color.White, fontSize = 24.sp)
                }
                if (video.durationMs > 0L) {
                    Surface(Modifier.align(Alignment.BottomEnd).padding(10.dp), color = Color.Black.copy(alpha = .75f), shape = RoundedCornerShape(6.dp)) {
                        Text(formatTime(video.durationMs), color = Color.White, modifier = Modifier.padding(6.dp), fontSize = 11.sp)
                    }
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    video.folderName?.let { Text(it, Modifier.weight(1f), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    if (video.isResumeable) Text("Resume ${formatTime(video.lastPositionMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                if (video.isResumeable) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator({ video.progress }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(video: VideoItem) {
    val context = LocalContext.current
    var bitmap by remember(video.id, video.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(video.id, video.uri) {
        bitmap = VideoThumbnailLoader.load(context, video.uri, 640, 360)
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("VIDEO", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsScreen(
    light: Boolean,
    setLight: (Boolean) -> Unit,
    permission: Boolean,
    requestPermission: () -> Unit,
    add: () -> Unit,
    saveLogs: () -> Unit,
    clearLogs: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Text("Playback, library and diagnostics", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Light mode", style = MaterialTheme.typography.titleMedium)
                        Text(if (light) "Light appearance" else "Dark appearance", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = light, onCheckedChange = setLight)
                }
            }
        }
        item { SettingCard("Device library", if (permission) "Connected and ready to scan." else "Permission is required to index local videos.", if (permission) "Rescan" else "Allow", requestPermission) }
        item { SettingCard("Manual import", "Add a video through Android's document picker.", "Add video", add) }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(16.dp)) {
                    Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                    Text("Playback events are stored locally as metadata-only JSON Lines.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = saveLogs) { Text("Save logs") }
                        TextButton(onClick = clearLogs) { Text("Clear logs") }
                    }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp)) }
        item { Text("Momentum-ready: playback starts, pauses, seeks, completions, skips and errors are recorded locally for future intelligence experiments.", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SettingCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PlayerScreen(video: VideoItem, player: ExoPlayer, back: () -> Unit, add: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("Back", color = Color.White) }
            Spacer(Modifier.width(8.dp))
            Text(video.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            TextButton(onClick = add) { Text("Open", color = Color.White) }
        }
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3_000
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
    }
}

private fun hasVideoPermission(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

private fun videoPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val secs = seconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}
