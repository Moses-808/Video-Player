package com.innotrepid.videoplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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

private enum class LibraryTab { HOME, LIBRARY, FAVORITES }

@Composable
private fun VideoPlayerApp() {
    val context = LocalContext.current
    val libraryViewModel: VideoLibraryViewModel = viewModel()
    val videos by libraryViewModel.videos.collectAsState()
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var tab by remember { mutableStateOf(LibraryTab.HOME) }
    var librarySearch by remember { mutableStateOf("") }
    var hasMediaPermission by remember { mutableStateOf(hasVideoPermission(context)) }
    val eventRecorder = remember { MomentumEventRecorder(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasMediaPermission = grants.values.any { it }
        if (hasMediaPermission) libraryViewModel.scanDevice()
    }

    LaunchedEffect(Unit) {
        hasMediaPermission = hasVideoPermission(context)
        if (hasMediaPermission) libraryViewModel.scanDevice()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            libraryViewModel.add(uri)
        }
    }

    val player = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(selectedVideo?.id) {
        selectedVideo?.let { video ->
            player.setMediaItem(MediaItem.fromUri(video.uri), video.lastPositionMs)
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(selectedVideo?.id) {
        while (isActive && selectedVideo != null) {
            delay(10_000L)
            val video = selectedVideo ?: break
            if (player.isPlaying && player.duration > 0L) {
                libraryViewModel.updateProgress(video.id, player.currentPosition, player.duration)
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
            eventRecorder.shutdown()
        }
    }

    DisposableEffect(player, selectedVideo?.id) {
        var hasStarted = false
        var completed = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val video = selectedVideo ?: return
                val position = player.currentPosition.coerceAtLeast(0L)
                val duration = player.duration
                if (isPlaying) {
                    eventRecorder.emit(if (hasStarted) MomentumEvent.VideoResumed(video.id, position, System.currentTimeMillis()) else MomentumEvent.VideoStarted(video.id, position, System.currentTimeMillis()))
                    hasStarted = true
                } else if (hasStarted && player.playbackState != Player.STATE_ENDED) {
                    eventRecorder.emit(MomentumEvent.VideoPaused(video.id, position, System.currentTimeMillis()))
                    if (duration > 0L) libraryViewModel.updateProgress(video.id, position, duration)
                }
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                val video = selectedVideo ?: return
                val from = oldPosition.positionMs.coerceAtLeast(0L)
                val to = newPosition.positionMs.coerceAtLeast(0L)
                if (abs(to - from) >= 1_000L) {
                    eventRecorder.emit(MomentumEvent.VideoSeeked(video.id, from, to, System.currentTimeMillis()))
                    if (player.duration > 0L) libraryViewModel.updateProgress(video.id, to, player.duration)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val video = selectedVideo ?: return
                if (state == Player.STATE_READY && player.duration > 0L) libraryViewModel.updateProgress(video.id, player.currentPosition, player.duration)
                if (state == Player.STATE_ENDED && !completed) {
                    completed = true
                    eventRecorder.emit(MomentumEvent.VideoCompleted(video.id, player.duration.coerceAtLeast(0L), System.currentTimeMillis()))
                    libraryViewModel.markCompleted(video.id)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val video = selectedVideo ?: return
                val cause = error.cause?.javaClass?.name
                val message = buildString {
                    append(error.errorCodeName)
                    error.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                    cause?.let { append(" [cause=").append(it).append(']') }
                }
                eventRecorder.emit(MomentumEvent.VideoError(video.id, player.currentPosition.coerceAtLeast(0L), message, System.currentTimeMillis()))
            }
        }
        player.addListener(listener)
        onDispose {
            selectedVideo?.let { video ->
                val position = player.currentPosition.coerceAtLeast(0L)
                val duration = player.duration
                if (duration > 0L) {
                    libraryViewModel.updateProgress(video.id, position, duration)
                    if (!completed && hasStarted && position > 5_000L && position < duration * 0.95f) eventRecorder.emit(MomentumEvent.VideoSkipped(video.id, position, duration, System.currentTimeMillis()))
                }
            }
            player.removeListener(listener)
        }
    }

    MaterialTheme {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            if (selectedVideo == null) {
                VideoLibraryRoot(
                    videos = videos,
                    tab = tab,
                    onTabChange = { tab = it },
                    search = librarySearch,
                    onSearchChange = { librarySearch = it },
                    hasMediaPermission = hasMediaPermission,
                    onRequestPermission = {
                        val permissions = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        permissionLauncher.launch(permissions)
                    },
                    onRefresh = { if (hasMediaPermission) libraryViewModel.scanDevice() },
                    onVideoSelected = { selectedVideo = it },
                    onOpen = { picker.launch(arrayOf("video/*")) },
                    onToggleFavorite = libraryViewModel::toggleFavorite,
                    onFolderSelected = {
                        librarySearch = it
                        tab = LibraryTab.LIBRARY
                    },
                    onExportDiagnostics = {
                        coroutineScope.launch {
                            val file = withContext(Dispatchers.IO) { eventRecorder.exportToCache() }
                            if (file != null) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export diagnostics"))
                            }
                        }
                    }
                )
            } else {
                PlayerScreen(selectedVideo!!, player, {
                    if (player.duration > 0L) libraryViewModel.updateProgress(selectedVideo!!.id, player.currentPosition, player.duration)
                    selectedVideo = null
                    player.stop()
                }, { picker.launch(arrayOf("video/*")) })
            }
        }
    }
}

@Composable
private fun VideoLibraryRoot(
    videos: List<VideoItem>,
    tab: LibraryTab,
    onTabChange: (LibraryTab) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    hasMediaPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onVideoSelected: (VideoItem) -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onFolderSelected: (String) -> Unit,
    onExportDiagnostics: () -> Unit
) {
    val recent = videos.sortedByDescending { it.lastPlayedAtMs }.filter { it.lastPlayedAtMs > 0L }.take(10)
    val continueWatching = videos.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    val favorites = videos.filter { it.isFavorite }.sortedByDescending { it.lastPlayedAtMs }
    val folders = videos.mapNotNull { it.folderName }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Header(tab, onTabChange, videos.size)
        when (tab) {
            LibraryTab.HOME -> HomeScreen(
                videos = videos,
                continueWatching = continueWatching,
                recent = recent,
                folders = folders,
                hasMediaPermission = hasMediaPermission,
                onRequestPermission = onRequestPermission,
                onRefresh = onRefresh,
                onVideoSelected = onVideoSelected,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
                onFolderSelected = onFolderSelected,
                onExportDiagnostics = onExportDiagnostics
            )
            LibraryTab.LIBRARY -> LibraryBrowseScreen(
                videos = videos,
                search = search,
                onSearchChange = onSearchChange,
                onVideoSelected = onVideoSelected,
                onToggleFavorite = onToggleFavorite,
                onOpen = onOpen,
                onExportDiagnostics = onExportDiagnostics
            )
            LibraryTab.FAVORITES -> FavoritesScreen(favorites, onVideoSelected, onToggleFavorite)
        }
    }
}

@Composable
private fun Header(tab: LibraryTab, onTabChange: (LibraryTab) -> Unit, count: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Vibe", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                Text("Your videos. Your flow.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Text("$count", color = Color.Gray, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NavChip("Home", tab == LibraryTab.HOME) { onTabChange(LibraryTab.HOME) }
            NavChip("Library", tab == LibraryTab.LIBRARY) { onTabChange(LibraryTab.LIBRARY) }
            NavChip("Saved", tab == LibraryTab.FAVORITES) { onTabChange(LibraryTab.FAVORITES) }
        }
    }
}

@Composable
private fun NavChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun HomeScreen(
    videos: List<VideoItem>,
    continueWatching: List<VideoItem>,
    recent: List<VideoItem>,
    folders: List<Pair<String, Int>>,
    hasMediaPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onVideoSelected: (VideoItem) -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onFolderSelected: (String) -> Unit,
    onExportDiagnostics: () -> Unit
) {
    val feed = videos.sortedWith(compareByDescending<VideoItem> { it.lastPlayedAtMs > 0L }.thenByDescending { it.addedAtMs })
    LazyColumn(contentPadding = PaddingValues(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            HeroPanel(
                title = when {
                    continueWatching.isNotEmpty() -> "Pick up where you left off"
                    videos.isNotEmpty() -> "Your library is ready"
                    else -> "Build your library"
                },
                subtitle = when {
                    continueWatching.isNotEmpty() -> "One tap and you're back in the moment."
                    videos.isNotEmpty() -> "A local-first video space designed around how you watch."
                    else -> "Bring in your local videos and let Vibe organize the rest."
                },
                actionLabel = if (videos.isEmpty()) "Add your first video" else "Add video",
                onAction = onOpen
            )
        }
        if (!hasMediaPermission) {
            item { PermissionPanel(onRequestPermission) }
        }
        if (continueWatching.isNotEmpty()) {
            item { Rail("Continue Watching", continueWatching, onVideoSelected, onToggleFavorite) }
        }
        if (folders.isNotEmpty()) {
            item {
                Section("Series & Folders", "VLC-style grouping without losing the visual experience")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(folders, key = { "folder-${it.first}" }) { (folder, count) ->
                        FolderCard(folder, count, onFolderSelected)
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { Rail("Recently Watched", recent, onVideoSelected, onToggleFavorite) }
        }
        item {
            Section("Your feed", "A calm vertical stream of everything in your library")
        }
        itemsIndexed(feed.take(20), key = { _, it -> "feed-${it.id}" }) { index, video ->
            FeedVideoCard(index + 1, video, onVideoSelected, onToggleFavorite)
        }
        if (videos.isEmpty()) {
            item { EmptyPanel("No videos yet", "Add a video manually or allow access to videos already stored on this device.", onOpen, onExportDiagnostics) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpen) { Text("Add video") }
                TextButton(onClick = onRefresh) { Text("Refresh library") }
                TextButton(onClick = onExportDiagnostics) { Text("Diagnostics") }
            }
        }
    }
}

@Composable
private fun LibraryBrowseScreen(
    videos: List<VideoItem>,
    search: String,
    onSearchChange: (String) -> Unit,
    onVideoSelected: (VideoItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpen: () -> Unit,
    onExportDiagnostics: () -> Unit
) {
    val filtered = videos.filter {
        search.isBlank() || it.title.contains(search.trim(), true) || it.folderName?.contains(search.trim(), true) == true
    }.sortedBy { it.title.lowercase() }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search titles, folders, series") },
                label = { Text("Search library") }
            )
        }
        item { Text("${filtered.size} video${if (filtered.size == 1) "" else "s"}", color = Color.Gray, style = MaterialTheme.typography.labelLarge) }
        if (filtered.isEmpty()) {
            item { EmptyPanel("Nothing found", "Try another title or folder name.", onOpen, onExportDiagnostics) }
        } else {
            items(filtered, key = { "library-${it.id}" }) { video ->
                FeedVideoCard(0, video, onVideoSelected, onToggleFavorite)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpen) { Text("Add video") }
                TextButton(onClick = onExportDiagnostics) { Text("Export diagnostics") }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(favorites: List<VideoItem>, onVideoSelected: (VideoItem) -> Unit, onToggleFavorite: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Section("Saved for later", "Your personal shelf") }
        if (favorites.isEmpty()) {
            item { Text("Tap ☆ on any video to save it here.", color = Color.Gray) }
        } else {
            items(favorites, key = { "saved-${it.id}" }) { video -> FeedVideoCard(0, video, onVideoSelected, onToggleFavorite) }
        }
    }
}

@Composable
private fun HeroPanel(title: String, subtitle: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF171717)
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("VIBE / NOW", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun PermissionPanel(onRequestPermission: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFF101010)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Connect device library", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Index local videos without copying them.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRequestPermission) { Text("Allow") }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Rail(title: String, videos: List<VideoItem>, onVideoSelected: (VideoItem) -> Unit, onToggleFavorite: (String) -> Unit) {
    Column {
        Section(title)
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(videos, key = { "rail-${it.id}" }) { video ->
                RailCard(video, onVideoSelected, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun RailCard(video: VideoItem, onClick: (VideoItem) -> Unit, onToggleFavorite: (String) -> Unit) {
    Column(Modifier.width(220.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).clickable { onClick(video) }) {
            VideoThumbnail(video)
            if (video.progress > 0f && video.isResumeable) {
                LinearProgressIndicator(progress = { video.progress }, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp))
            }
            Text(if (video.isFavorite) "★" else "☆", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clickable { onToggleFavorite(video.id) }, fontSize = 20.sp)
        }
        Spacer(Modifier.height(7.dp))
        Text(video.title, color = Color.White, maxLines = 2, style = MaterialTheme.typography.titleSmall)
        video.folderName?.let { Text(it, color = Color.Gray, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FolderCard(folder: String, count: Int, onClick: (String) -> Unit) {
    Surface(Modifier.width(190.dp).clickable { onClick(folder) }, shape = RoundedCornerShape(16.dp), color = Color(0xFF171717)) {
        Column(Modifier.padding(16.dp)) {
            Text("FOLDER", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Text(folder, color = Color.White, maxLines = 2, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("$count episode${if (count == 1) "" else "s"}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FeedVideoCard(index: Int, video: VideoItem, onClick: (VideoItem) -> Unit, onToggleFavorite: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick(video) }, shape = RoundedCornerShape(20.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                VideoThumbnail(video)
                if (index > 0) {
                    Surface(Modifier.align(Alignment.TopStart).padding(10.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.65f)) {
                        Text("#%02d".format(index), color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(if (video.isFavorite) "★" else "☆", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clickable { onToggleFavorite(video.id) }, fontSize = 24.sp)
                if (video.durationMs > 0L) {
                    Surface(Modifier.align(Alignment.BottomEnd).padding(10.dp), color = Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(6.dp)) {
                        Text(formatTime(video.durationMs), color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), fontSize = 11.sp)
                    }
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(video.title, color = Color.White, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    video.folderName?.let { Text(it, color = Color.Gray, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall) }
                    if (video.isResumeable) Text("Resume ${formatTime(video.lastPositionMs)}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    else if (video.lastPlayedAtMs > 0L) Text("Watched", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                if (video.isResumeable) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { video.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun EmptyPanel(title: String, body: String, onOpen: () -> Unit, onExportDiagnostics: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFF101010)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(5.dp))
            Text(body, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) { Text("Add video") }
                TextButton(onClick = onExportDiagnostics) { Text("Diagnostics") }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(video: VideoItem) {
    val context = LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(video.id, video.uri) { bitmap = VideoThumbnailLoader.load(context, video.uri, 640, 360) }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = video.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) { Text("VIDEO", color = Color.LightGray, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun PlayerScreen(video: VideoItem, player: ExoPlayer, onBack: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Library") }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, maxLines = 1)
                video.folderName?.let { Text(it, color = Color.Gray, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onOpen) { Text("Open") }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AndroidView(
                factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = true; controllerShowTimeoutMs = 3_000; controllerHideOnTouch = true } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun hasVideoPermission(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
