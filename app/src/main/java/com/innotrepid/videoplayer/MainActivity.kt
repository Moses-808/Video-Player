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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoLibraryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VideoPlayerApp() }
    }
}

@androidx.compose.runtime.Composable
private fun VideoPlayerApp() {
    val context = LocalContext.current
    val libraryViewModel: VideoLibraryViewModel = viewModel()
    val videos by libraryViewModel.videos.collectAsState()
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var hasMediaPermission by remember { mutableStateOf(hasVideoPermission(context)) }

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

    DisposableEffect(player) { onDispose { player.release() } }

    DisposableEffect(player, selectedVideo?.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val video = selectedVideo ?: return
                if (state == Player.STATE_READY && player.duration > 0L) {
                    libraryViewModel.updateProgress(video.id, player.currentPosition, player.duration)
                }
                if (state == Player.STATE_ENDED) libraryViewModel.markCompleted(video.id)
            }
        }
        player.addListener(listener)
        onDispose {
            selectedVideo?.let { video ->
                if (player.duration > 0L) libraryViewModel.updateProgress(video.id, player.currentPosition, player.duration)
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
                    onOpen = { picker.launch(arrayOf("video/*")) }
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

@androidx.compose.runtime.Composable
private fun LibraryScreen(
    videos: List<VideoItem>,
    hasMediaPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onVideoSelected: (VideoItem) -> Unit,
    onOpen: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Video Library", Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (hasMediaPermission) Button(onClick = onRefresh) { Text("Refresh") }
        }
        Spacer(Modifier.height(16.dp))
        if (!hasMediaPermission) {
            Text("Let Video Player find videos already stored on your device. The app indexes them; it does not copy the video files.", Color.LightGray)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequestPermission) { Text("Find my videos") }
            Spacer(Modifier.height(16.dp))
        }
        Button(onClick = onOpen, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) { Text("Add video manually") }
        Spacer(Modifier.height(24.dp))
        if (videos.isEmpty()) {
            Text("No videos indexed yet.", Color.LightGray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(videos, key = { it.id }) { video ->
                    Card(Modifier.fillMaxWidth().clickable { onVideoSelected(video) }) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(video.title, style = MaterialTheme.typography.titleMedium)
                                if (video.isResumeable) Text("Resume at ${formatTime(video.lastPositionMs)}", style = MaterialTheme.typography.bodySmall)
                                else if (video.lastPlayedAtMs > 0L) Text("Watched before", style = MaterialTheme.typography.bodySmall)
                            }
                            if (video.durationMs > 0L) Text(formatTime(video.durationMs), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerScreen(video: VideoItem, player: ExoPlayer, onBack: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Library") }
            Spacer(Modifier.width(12.dp))
            Text(video.title, Color.White, maxLines = 1, modifier = Modifier.weight(1f))
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
