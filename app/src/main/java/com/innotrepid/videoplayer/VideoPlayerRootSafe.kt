package com.innotrepid.videoplayer

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.innotrepid.videoplayer.intelligence.VideoPrediction
import com.innotrepid.videoplayer.intelligence.VideoPredictionEngine
import com.innotrepid.videoplayer.intelligence.VideoPredictionFeedbackStore
import com.innotrepid.videoplayer.library.VideoLibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun VideoPlayerRootSafe() {
    val context = LocalContext.current
    val vm: VideoLibraryViewModel = viewModel()
    val videos by vm.videos.collectAsState()
    val session = rememberPlaybackSession(context, vm)
    val feedbackStore = remember { VideoPredictionFeedbackStore(context.applicationContext) }
    val prefs = remember { context.getSharedPreferences("video_player_preferences", 0) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(PhaseBScreen.PULSE) }
    var screenDirection by remember { mutableIntStateOf(1) }
    var search by remember { mutableStateOf("") }
    var lightMode by remember { mutableStateOf(prefs.getBoolean("light_mode", false)) }
    var intelligenceEnabled by remember { mutableStateOf(prefs.getBoolean("intelligence_enabled", true)) }
    var learnFromHistory by remember { mutableStateOf(prefs.getBoolean("learn_from_history", true)) }
    var autoAdvance by remember { mutableStateOf(prefs.getBoolean("auto_advance", true)) }
    var permission by remember { mutableStateOf(hasPhaseBVideoPermission(context)) }
    var pendingFolder by remember { mutableStateOf<String?>(null) }
    var predictions by remember { mutableStateOf<List<VideoPrediction>>(emptyList()) }
    var showAbout by remember { mutableStateOf(false) }

    val selected = videos.firstOrNull { it.id == session.selectedId }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permission = result.values.any { it }
        if (permission) vm.scanDevice()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.add(uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/jsonl"),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(session.recorder.exportText().toByteArray())
                    }
                }
            }
        }
    }

    fun navigateTo(target: PhaseBScreen) {
        if (target == screen) return
        val order = PhaseBScreen.entries
        screenDirection = if (order.indexOf(target) > order.indexOf(screen)) 1 else -1
        screen = target
    }

    // Initial scan
    LaunchedEffect(Unit) {
        if (permission) vm.scanDevice()
    }

    // Predictions
    LaunchedEffect(videos, intelligenceEnabled, learnFromHistory) {
        if (!intelligenceEnabled) {
            predictions = emptyList()
        } else {
            val currentId = videos
                .filter { it.lastPlayedAtMs > 0L }
                .maxByOrNull { it.lastPlayedAtMs }
                ?.id
            val events = if (learnFromHistory) {
                withContext(Dispatchers.IO) { session.recorder.recentEvents(200) }
            } else {
                emptyList()
            }
            predictions = VideoPredictionEngine.predict(
                videos,
                events,
                currentMediaId = currentId,
            )
        }
    }

    // Playback side-effects (lifecycle, media load, events, auto-advance)
    PlaybackSessionEffects(
        session = session,
        videos = videos,
        autoAdvance = autoAdvance,
    )

    val scheme = if (lightMode) {
        lightColorScheme(
            primary = Color(0xFF6D28D9),
            secondary = Color(0xFF0891B2),
            tertiary = Color(0xFFDB2777),
        )
    } else {
        darkColorScheme(
            primary = PhaseBViolet,
            secondary = PhaseBCyan,
            tertiary = PhaseBPink,
            background = Color(0xFF07070C),
            surface = Color(0xFF101018),
            surfaceVariant = Color(0xFF171724),
        )
    }

    MaterialTheme(colorScheme = scheme) {
        if (selected != null) {
            PhaseBPlayerScreen(
                video = selected,
                player = session.player,
                controller = session.controller,
                queue = session.queue,
                favorite = { vm.toggleFavorite(selected.id) },
                back = { session.closePlayer(selected) },
                open = { video -> session.openSession(videos, video, selected) },
            )
        } else {
            val order = PhaseBScreen.entries
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) {
                        var dragDistance = 0f
                        var swipeTriggered = false
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragDistance = 0f
                                swipeTriggered = false
                            },
                            onDragEnd = {
                                dragDistance = 0f
                                swipeTriggered = false
                            },
                            onDragCancel = {
                                dragDistance = 0f
                                swipeTriggered = false
                            },
                            onHorizontalDrag = { _, amount ->
                                if (!swipeTriggered) {
                                    dragDistance += amount
                                    if (abs(dragDistance) >= 80f) {
                                        val index = order.indexOf(screen)
                                        val next = (index + if (dragDistance < 0f) 1 else -1)
                                            .coerceIn(0, order.lastIndex)
                                        if (next != index) navigateTo(order[next])
                                        swipeTriggered = true
                                    }
                                }
                            },
                        )
                    },
            ) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = screenDirection > 0
                        (slideInHorizontally(
                            animationSpec = tween(320),
                            initialOffsetX = { width -> if (forward) width else -width },
                        ) + fadeIn(animationSpec = tween(220))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(320),
                                targetOffsetX = { width -> if (forward) -width else width },
                            ) + fadeOut(animationSpec = tween(180)))
                    },
                    label = "phase-b-screen",
                ) { target ->
                    when (target) {
                        PhaseBScreen.PULSE -> VisonatePulse(
                            videos = videos,
                            predictions = predictions,
                            permission = permission,
                            request = { permissionLauncher.launch(phaseBVideoPermissions()) },
                            favorite = vm::toggleFavorite,
                            open = { video -> session.openVideo(videos, video) },
                            library = { folder ->
                                pendingFolder = folder
                                navigateTo(PhaseBScreen.LIBRARY)
                            },
                        )

                        PhaseBScreen.LIBRARY -> GroupedLibraryRoot(
                            videos = videos,
                            search = search,
                            setSearch = { search = it },
                            open = { video -> session.openVideo(videos, video) },
                            favorite = vm::toggleFavorite,
                            initialFolder = pendingFolder,
                            add = { picker.launch(arrayOf("video/*")) },
                        )

                        PhaseBScreen.SAVED -> PhaseBSavedScreen(
                            videos = videos.filter { it.isFavorite },
                            open = { video -> session.openVideo(videos, video) },
                            favorite = vm::toggleFavorite,
                        )

                        PhaseBScreen.SETTINGS -> PhaseBSettingsScreen(
                            light = lightMode,
                            setLight = {
                                lightMode = it
                                prefs.edit().putBoolean("light_mode", it).apply()
                            },
                            intelligenceEnabled = intelligenceEnabled,
                            setIntelligenceEnabled = {
                                intelligenceEnabled = it
                                prefs.edit().putBoolean("intelligence_enabled", it).apply()
                            },
                            learnFromHistory = learnFromHistory,
                            setLearnFromHistory = {
                                learnFromHistory = it
                                prefs.edit().putBoolean("learn_from_history", it).apply()
                            },
                            autoAdvance = autoAdvance,
                            setAutoAdvance = {
                                autoAdvance = it
                                prefs.edit().putBoolean("auto_advance", it).apply()
                            },
                            permission = permission,
                            requestPermission = {
                                permissionLauncher.launch(phaseBVideoPermissions())
                            },
                            importVideo = { picker.launch(arrayOf("video/*")) },
                            exportDiagnostics = {
                                exportLauncher.launch("video-player-diagnostics.jsonl")
                            },
                            clearDiagnostics = {
                                scope.launch(Dispatchers.IO) { session.recorder.clear() }
                            },
                            clearLearning = {
                                feedbackStore.clear()
                                predictions = emptyList()
                            },
                            openAbout = { showAbout = true },
                        )
                    }
                }

                PhaseBBottomBar(screen) { target ->
                    if (target != PhaseBScreen.LIBRARY) pendingFolder = null
                    navigateTo(target)
                }
            }
        }

        if (showAbout) {
            VisonateAboutDialog { showAbout = false }
        }
    }
}

private fun hasPhaseBVideoPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            "android.permission.READ_MEDIA_VIDEO",
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            "android.permission.READ_EXTERNAL_STORAGE",
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

private fun phaseBVideoPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf("android.permission.READ_MEDIA_VIDEO")
    } else {
        arrayOf("android.permission.READ_EXTERNAL_STORAGE")
    }
