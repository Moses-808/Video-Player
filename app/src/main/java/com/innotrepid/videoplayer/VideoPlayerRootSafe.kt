package com.innotrepid.videoplayer

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import com.innotrepid.videoplayer.intelligence.MomentumEvent
import com.innotrepid.videoplayer.intelligence.MomentumEventRecorder
import com.innotrepid.videoplayer.intelligence.PlaybackTransitionCoordinator
import com.innotrepid.videoplayer.intelligence.VideoPrediction
import com.innotrepid.videoplayer.intelligence.VideoPredictionEngine
import com.innotrepid.videoplayer.intelligence.VideoPredictionFeedbackStore
import com.innotrepid.videoplayer.intelligence.VideoSessionQueue
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoLibraryViewModel
import com.innotrepid.videoplayer.playback.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class PhaseBScreen { PULSE, LIBRARY, SAVED, SETTINGS }
private val PhaseBViolet = Color(0xFF8B5CF6)
private val PhaseBCyan = Color(0xFF22D3EE)
private val PhaseBPink = Color(0xFFEC4899)

@Composable
fun VideoPlayerRootSafe() {
    val context = LocalContext.current
    val vm: VideoLibraryViewModel = viewModel()
    val videos by vm.videos.collectAsState()
    val recorder = remember { MomentumEventRecorder(context.applicationContext) }
    val feedbackStore = remember { VideoPredictionFeedbackStore(context.applicationContext) }
    val prefs = remember { context.getSharedPreferences("video_player_preferences", 0) }
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(context).build() }
    val controller = remember(player) { PlaybackController(player) }
    var screen by remember { mutableStateOf(PhaseBScreen.PULSE) }
    var previousScreen by remember { mutableStateOf(PhaseBScreen.PULSE) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var queue by remember { mutableStateOf<VideoSessionQueue?>(null) }
    var search by remember { mutableStateOf("") }
    var lightMode by remember { mutableStateOf(prefs.getBoolean("light_mode", false)) }
    var intelligenceEnabled by remember { mutableStateOf(prefs.getBoolean("intelligence_enabled", true)) }
    var learnFromHistory by remember { mutableStateOf(prefs.getBoolean("learn_from_history", true)) }
    var autoAdvance by remember { mutableStateOf(prefs.getBoolean("auto_advance", true)) }
    var permission by remember { mutableStateOf(hasPhaseBVideoPermission(context)) }
    var pendingFolder by remember { mutableStateOf<String?>(null) }
    var predictions by remember { mutableStateOf<List<VideoPrediction>>(emptyList()) }
    var showAbout by remember { mutableStateOf(false) }
    val selected = videos.firstOrNull { it.id == selectedId }
    val latestSelected by rememberUpdatedState(selected)
    val latestQueue by rememberUpdatedState(queue)
    val latestVideos by rememberUpdatedState(videos)
    val persist: (VideoItem) -> Unit = { video -> val duration = controller.durationMs(); if (duration > 0L) vm.updateProgress(video.id, controller.currentPositionMs(), duration) }
    val openVideo: (VideoItem) -> Unit = remember(videos) { { video -> queue = VideoSessionQueue.create(videos, video.id); selectedId = video.id } }
    val openSession: (VideoItem) -> Unit = remember(controller) { { video -> if (video.id != latestSelected?.id) { latestSelected?.let(persist); queue = queue?.moveTo(video.id) ?: VideoSessionQueue.create(latestVideos, video.id); selectedId = video.id } } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> permission = result.values.any { it }; if (permission) vm.scanDevice() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; vm.add(uri) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/jsonl")) { uri -> if (uri != null) scope.launch(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(recorder.exportText().toByteArray()) } } } }
    LaunchedEffect(Unit) { if (permission) vm.scanDevice() }
    LaunchedEffect(videos, intelligenceEnabled, learnFromHistory) {
        if (!intelligenceEnabled) {
            predictions = emptyList()
        } else {
            val currentId = videos.filter { it.lastPlayedAtMs > 0L }.maxByOrNull { it.lastPlayedAtMs }?.id
            val events = if (learnFromHistory) withContext(Dispatchers.IO) { recorder.recentEvents(200) } else emptyList()
            predictions = VideoPredictionEngine.predict(videos, events, currentMediaId = currentId)
        }
    }
    DisposableEffect(player) { onDispose { controller.release(); recorder.shutdown() } }
    LaunchedEffect(selectedId) { val video = selected ?: return@LaunchedEffect; controller.setMedia(video.uri, video.lastPositionMs.coerceAtLeast(0L), true) }
    LaunchedEffect(selectedId) { while (isActive && selectedId != null) { delay(8_000L); latestSelected?.let { video -> val duration = controller.durationMs(); if (duration > 0L) vm.updateProgress(video.id, controller.currentPositionMs(), duration) } } }
    LaunchedEffect(controller) { controller.events.collect { event -> val video = latestSelected ?: return@collect; if (controller.currentMediaUri() != video.uri) return@collect; val now = System.currentTimeMillis(); when (event) { is PlaybackController.Event.Started -> recorder.emit(MomentumEvent.VideoStarted(video.id, event.positionMs, now)); is PlaybackController.Event.Resumed -> recorder.emit(MomentumEvent.VideoResumed(video.id, event.positionMs, now)); is PlaybackController.Event.Paused -> recorder.emit(MomentumEvent.VideoPaused(video.id, event.positionMs, now)); is PlaybackController.Event.Seeked -> recorder.emit(MomentumEvent.VideoSeeked(video.id, event.fromPositionMs, event.toPositionMs, now)); is PlaybackController.Event.Completed -> { recorder.emit(MomentumEvent.VideoCompleted(video.id, event.durationMs, now)); vm.markCompleted(video.id); if (autoAdvance) { val next = PlaybackTransitionCoordinator.next(latestQueue, video.id); if (next != null) { queue = next; selectedId = next.current?.id } else selectedId = null } else selectedId = null }; is PlaybackController.Event.Error -> recorder.emit(MomentumEvent.VideoError(video.id, event.positionMs, event.message, now)) } } }
    val scheme = if (lightMode) lightColorScheme(primary = Color(0xFF6D28D9), secondary = Color(0xFF0891B2), tertiary = Color(0xFFDB2777)) else darkColorScheme(primary = PhaseBViolet, secondary = PhaseBCyan, tertiary = PhaseBPink, background = Color(0xFF07070C), surface = Color(0xFF101018), surfaceVariant = Color(0xFF171724))
    MaterialTheme(colorScheme = scheme) {
        if (selected != null) PhaseBPlayerScreen(selected, player, controller, queue, { vm.toggleFavorite(selected.id) }, { persist(selected); controller.clearMedia(); selectedId = null }, openSession)
        else {
            val order = PhaseBScreen.entries
            val direction = if (order.indexOf(screen) >= order.indexOf(previousScreen)) 1 else -1
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(screen) { detectHorizontalDragGestures { _, amount -> if (abs(amount) > 100f) { val index = order.indexOf(screen); val next = (index + if (amount < 0) 1 else -1).coerceIn(0, order.lastIndex); if (next != index) { previousScreen = screen; screen = order[next] } } } }) {
                AnimatedContent(targetState = screen, transitionSpec = { val forward = direction > 0; (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith (slideOutHorizontally { if (forward) -it else it } + fadeOut()) }, label = "phase-b-screen") { target ->
                    when (target) {
                        PhaseBScreen.PULSE -> PhaseBPulse(videos, predictions, permission, { permissionLauncher.launch(phaseBVideoPermissions()) }, vm::toggleFavorite, openVideo) { folder -> pendingFolder = folder; previousScreen = screen; screen = PhaseBScreen.LIBRARY }
                        PhaseBScreen.LIBRARY -> GroupedLibraryRoot(videos, search, { search = it }, openVideo, vm::toggleFavorite, pendingFolder) { picker.launch(arrayOf("video/*")) }
                        PhaseBScreen.SAVED -> PhaseBSaved(videos.filter { it.isFavorite }, openVideo, vm::toggleFavorite)
                        PhaseBScreen.SETTINGS -> PhaseBSettings(lightMode, { lightMode = it; prefs.edit().putBoolean("light_mode", it).apply() }, intelligenceEnabled, { intelligenceEnabled = it; prefs.edit().putBoolean("intelligence_enabled", it).apply() }, learnFromHistory, { learnFromHistory = it; prefs.edit().putBoolean("learn_from_history", it).apply() }, autoAdvance, { autoAdvance = it; prefs.edit().putBoolean("auto_advance", it).apply() }, permission, { permissionLauncher.launch(phaseBVideoPermissions()) }, { picker.launch(arrayOf("video/*")) }, { exportLauncher.launch("video-player-diagnostics.jsonl") }, { scope.launch(Dispatchers.IO) { recorder.clear() } }, { feedbackStore.clear(); predictions = emptyList() }, { showAbout = true })
                    }
                }
                PhaseBBottomBar(screen) { previousScreen = screen; if (it != PhaseBScreen.LIBRARY) pendingFolder = null; screen = it }
            }
        }
        if (showAbout) PhaseBAboutDialog { showAbout = false }
    }
}

@Composable private fun PhaseBBottomBar(selected: PhaseBScreen, onSelect: (PhaseBScreen) -> Unit) { NavigationBar(tonalElevation = 12.dp) { PhaseBNav(PhaseBScreen.PULSE, selected, Icons.Outlined.AutoAwesome, "Pulse", onSelect); PhaseBNav(PhaseBScreen.LIBRARY, selected, Icons.Outlined.VideoLibrary, "Library", onSelect); PhaseBNav(PhaseBScreen.SAVED, selected, Icons.Outlined.BookmarkBorder, "Saved", onSelect); PhaseBNav(PhaseBScreen.SETTINGS, selected, Icons.Outlined.Settings, "Settings", onSelect) } }
@Composable private fun RowScope.PhaseBNav(item: PhaseBScreen, selected: PhaseBScreen, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onSelect: (PhaseBScreen) -> Unit) { val active = item == selected; Box(Modifier.weight(1f).padding(vertical = 5.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = { onSelect(item) }) { Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }; Text(label, fontSize = 10.sp, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun PhaseBSaved(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { LazyColumn(contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Column(Modifier.padding(horizontal = 20.dp)) { Text("Saved", style = MaterialTheme.typography.headlineMedium); Text("Things you deliberately kept in your orbit.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) } }; if (videos.isEmpty()) item { Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(24.dp)) { Column(Modifier.padding(20.dp)) { Icon(Icons.Outlined.BookmarkBorder, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(10.dp)); Text("Nothing saved yet", style = MaterialTheme.typography.titleMedium); Text("Save a video from Pulse or Library and it will live here.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }; items(videos, key = { it.id }) { video -> Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(22.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(video.title, Modifier.weight(1f), maxLines = 2); IconButton(onClick = { favorite(video.id) }) { Icon(Icons.Outlined.Favorite, "Remove from saved", tint = PhaseBPink) }; IconButton(onClick = { open(video) }) { Icon(Icons.Outlined.PlayArrow, "Play") } } } } } }

@Composable private fun PhaseBSettings(light: Boolean, setLight: (Boolean) -> Unit, intelligenceEnabled: Boolean, setIntelligenceEnabled: (Boolean) -> Unit, learnFromHistory: Boolean, setLearnFromHistory: (Boolean) -> Unit, autoAdvance: Boolean, setAutoAdvance: (Boolean) -> Unit, permission: Boolean, requestPermission: () -> Unit, importVideo: () -> Unit, exportDiagnostics: () -> Unit, clearDiagnostics: () -> Unit, clearLearning: () -> Unit, openAbout: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Column(Modifier.padding(horizontal = 20.dp)) { Text("Settings", style = MaterialTheme.typography.headlineMedium); Text("Shape the player around the way you watch.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) } }
        item { PhaseBSettingCard("Intelligence") {
            PhaseBSettingRow(Icons.Outlined.AutoAwesome, "Momentum intelligence", "Predict what you are likely to watch next") { Switch(intelligenceEnabled, setIntelligenceEnabled) }
            PhaseBSettingRow(Icons.Outlined.Psychology, "Learn from viewing", "Use local watch behavior to improve predictions") { Switch(learnFromHistory, setLearnFromHistory) }
            PhaseBSettingRow(Icons.Outlined.QueuePlayNext, "Auto-advance", "Continue into the predicted queue after completion") { Switch(autoAdvance, setAutoAdvance) }
            PhaseBSettingRow(Icons.Outlined.RestartAlt, "Reset learned preferences", "Forget prediction acceptance patterns; playback history stays") { TextButton(onClick = clearLearning) { Text("RESET") } }
        } }
        item { PhaseBSettingCard("Playback") {
            PhaseBSettingRow(Icons.Outlined.SkipNext, "Up Next", "Keep the next video available without covering the picture") { Text("ON", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) }
            PhaseBSettingRow(Icons.Outlined.PlayCircleOutline, "Resume playback", "Continue videos from their saved position") { Text("ON", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) }
        } }
        item { PhaseBSettingCard("Appearance") { PhaseBSettingRow(Icons.Outlined.DarkMode, "Light appearance", "Use a brighter interface") { Switch(light, setLight) } } }
        item { PhaseBSettingCard("Library") {
            PhaseBSettingRow(Icons.Outlined.Storage, "Device library", if (permission) "Permission granted" else "Permission required") { if (!permission) TextButton(onClick = requestPermission) { Text("ALLOW") } }
            PhaseBSettingRow(Icons.Outlined.VideoLibrary, "Import video", "Add a video without copying it") { TextButton(onClick = importVideo) { Text("IMPORT") } }
        } }
        item { PhaseBSettingCard("Diagnostics") {
            PhaseBSettingRow(Icons.Outlined.BugReport, "Playback diagnostics", "Export local playback and Momentum events") { TextButton(onClick = exportDiagnostics) { Text("EXPORT") } }
            PhaseBSettingRow(Icons.Outlined.DeleteSweep, "Clear diagnostics", "Delete local event history") { TextButton(onClick = clearDiagnostics) { Text("CLEAR") } }
        } }
        item { PhaseBSettingCard("About") {
            PhaseBSettingRow(Icons.Outlined.Info, "About", "Version, product identity and credits") { TextButton(onClick = openAbout) { Text("VIEW") } }
        } }
    }
}

@Composable private fun PhaseBAboutDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } }, icon = { Icon(Icons.Outlined.AutoAwesome, null, tint = PhaseBViolet) }, title = { Text("Video Player") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("A local video player designed to become more useful the more naturally you use it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Momentum", style = MaterialTheme.typography.titleMedium)
        Text("Anticipatory intelligence for your viewing flow.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("Developed by Innotrepid", style = MaterialTheme.typography.labelLarge)
        Text("© 2026 Innotrepid. All rights reserved.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Your library, playback history and intelligence data stay local to the device unless you explicitly export diagnostics.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } })
}

@Composable private fun PhaseBSettingCard(title: String, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), RoundedCornerShape(24.dp)) { Column(Modifier.padding(vertical = 8.dp)) { Text(title, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = PhaseBViolet, style = MaterialTheme.typography.titleMedium); content() } } }
@Composable private fun PhaseBSettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: @Composable (() -> Unit)) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title); Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; action() } }
private fun hasPhaseBVideoPermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 33) androidx.core.content.ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VIDEO") == android.content.pm.PackageManager.PERMISSION_GRANTED else androidx.core.content.ContextCompat.checkSelfPermission(context, "android.permission.READ_EXTERNAL_STORAGE") == android.content.pm.PackageManager.PERMISSION_GRANTED
private fun phaseBVideoPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) arrayOf("android.permission.READ_MEDIA_VIDEO") else arrayOf("android.permission.READ_EXTERNAL_STORAGE")
