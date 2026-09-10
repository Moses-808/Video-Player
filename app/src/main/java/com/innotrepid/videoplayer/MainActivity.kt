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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
private fun VideoPlayerApp() {
    val context = LocalContext.current
    val libraryViewModel: VideoLibraryViewModel = viewModel()
    val videos by libraryViewModel.videos.collectAsState()
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var hasMediaPermission by remember { mutableStateOf(hasVideoPermission(context)) }
    val eventRecorder = remember { MomentumEventRecorder(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
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
                    eventRecorder.emit(
                        if (hasStarted) MomentumEvent.VideoResumed(video.id, position, System.currentTimeMillis())
                        else MomentumEvent.VideoStarted(video.id, position, System.currentTimeMillis())
                    )
                    hasStarted = true
                } else if (hasStarted && player.playbackState != Player.STATE_ENDED) {
                    eventRecorder.emit(MomentumEvent.VideoPaused(video.id, position, System.currentTimeMillis()))
                    if (duration > 0L) libraryViewModel.updateProgress(video.id, position, duration)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
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
                if (state == Player.STATE_READY && player.duration > 0L) {
                    libraryViewModel.updateProgress(video.id, player.currentPosition, player.duration)
                }
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
                eventRecorder.emit(
                    MomentumEvent.VideoError(
                        mediaId = video.id,
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        message = message,
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }
        }
        player.addListener(listener)
        onDispose {
            selectedVideo?.let { video ->
                val position = player.currentPosition.coerceAtLeast(0L)
                val duration = player.duration
                if (duration > 0L) {
                    libraryViewModel.updateProgress(video.id, position, duration)
                    if (!completed && hasStarted && position > 5_000L && position < duration * 0.95f) {
                        eventRecorder.emit(MomentumEvent.VideoSkipped(video.id, position, duration, System.currentTimeMillis()))
                    }
                }
            }
            player.removeListener(listener)
        }
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            if (selectedVideo == null) {
                LibraryScreen(
                    videos = videos,
                    hasMediaPermission = hasMediaPermission,
                    onRequestPermission = {
                        val permissions = if (Build.VERSION.SDK_INT >= 33) {
                            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        permissionLauncher.launch(permissions)
                    },
                    onRefresh = { if (hasMediaPermission) libraryViewModel.scanDevice() },
                    onVideoSelected = { selectedVideo = it },
                    onOpen = { picker.launch(arrayOf("video/*")) },
                    onExportDiagnostics = {
                        coroutineScope.launch {
                            val file = withContext(Dispatchers.IO) { eventRecorder.exportToCache() }
                            if (file != null) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
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
private fun LibraryScreen(
    videos: List<VideoItem>,
    hasMediaPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onVideoSelected: (VideoItem) -> Unit,
    onOpen: () -> Unit,
    onExportDiagnostics: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }

    val matchingVideos = videos.filter { video ->
        query.isBlank() || video.title.contains(query.trim(), ignoreCase = true)
    }
    val orderedVideos = if (newestFirst) {
        matchingVideos.sortedByDescending { it.addedAtMs }
    } else {
        matchingVideos.sortedBy { it.title.lowercase() }
    }
    val continueWatching = orderedVideos.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    val recentlyAdded = orderedVideos.take(12)

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Video Player", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (videos.isEmpty()) "Your local library" else "${videos.size} video${if (videos.size == 1) "" else "s"} on this device",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (hasMediaPermission) Button(onClick = onRefresh) { Text("Refresh") }
        }
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search videos") },
            label = { Text("Library search") }
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sort", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { newestFirst = true }) { Text(if (newestFirst) "✓ Newest" else "Newest") }
            TextButton(onClick = { newestFirst = false }) { Text(if (!newestFirst) "✓ A–Z" else "A–Z") }
            Spacer(Modifier.weight(1f))
            if (query.isNotBlank()) {
                Text("${orderedVideos.size} match${if (orderedVideos.size == 1) "" else "es"}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (!hasMediaPermission) {
            Text(
                "Let Video Player find videos already stored on your device. The app indexes them; it does not copy the video files.",
                color = Color.LightGray
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRequestPermission) { Text("Find my videos") }
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpen, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)) {
                Text("Add video manually")
            }
            Button(onClick = onExportDiagnostics) { Text("Export Diagnostics") }
        }
        Spacer(Modifier.height(18.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (continueWatching.isNotEmpty() && query.isBlank()) {
                item { SectionTitle("Continue Watching") }
                items(continueWatching, key = { "continue-${it.id}" }) { video ->
                    VideoCard(video, onVideoSelected)
                }
            }

            item { SectionTitle(if (query.isBlank()) "Recently Added" else "Search Results") }
            if (recentlyAdded.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "No videos indexed yet." else "No videos match \"$query\".",
                        color = Color.LightGray
                    )
                }
            } else {
                items(recentlyAdded, key = { "recent-${it.id}" }) { video ->
                    VideoCard(video, onVideoSelected)
                }
            }

            if (query.isBlank()) {
                item { SectionTitle("All Videos") }
                items(orderedVideos, key = { "all-${it.id}" }) { video ->
                    VideoCard(video, onVideoSelected)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun VideoCard(video: VideoItem, onClick: (VideoItem) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick(video) }) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                VideoThumbnail(video)
                if (video.durationMs > 0L) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(formatTime(video.durationMs), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                when {
                    video.isResumeable -> Text("Resume at ${formatTime(video.lastPositionMs)}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    video.lastPlayedAtMs > 0L -> Text("Watched before", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    video.relativePath != null -> Text(video.relativePath.orEmpty(), color = Color.Gray, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                if (video.progress > 0f && video.isResumeable) {
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(progress = { video.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(video: VideoItem) {
    val context = LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(video.id, video.uri) {
        bitmap = VideoThumbnailLoader.load(context, video.uri, 640, 360)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
            Text("VIDEO", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PlayerScreen(video: VideoItem, player: ExoPlayer, onBack: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Library") }
            Spacer(Modifier.width(12.dp))
            Text(video.title, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
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
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
