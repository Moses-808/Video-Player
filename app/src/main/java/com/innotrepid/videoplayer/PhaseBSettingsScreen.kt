package com.innotrepid.videoplayer

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PhaseBSettingsScreen(
    light: Boolean,
    setLight: (Boolean) -> Unit,
    intelligenceEnabled: Boolean,
    setIntelligenceEnabled: (Boolean) -> Unit,
    learnFromHistory: Boolean,
    setLearnFromHistory: (Boolean) -> Unit,
    autoAdvance: Boolean,
    setAutoAdvance: (Boolean) -> Unit,
    permission: Boolean,
    requestPermission: () -> Unit,
    importVideo: () -> Unit,
    exportDiagnostics: () -> Unit,
    clearDiagnostics: () -> Unit,
    clearLearning: () -> Unit,
    openAbout: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("video_player_preferences", 0)
    }

    // Playback defaults
    var defaultSpeed by remember {
        mutableFloatStateOf(prefs.getFloat("default_playback_speed", 1f))
    }
    var resumePlayback by remember {
        mutableStateOf(prefs.getBoolean("resume_playback", true))
    }
    var autoPip by remember {
        mutableStateOf(prefs.getBoolean("auto_pip_on_leave", true))
    }

    // Subtitle defaults
    var subtitleBg by remember {
        mutableStateOf(prefs.getBoolean("subtitle_background", true))
    }
    var subtitleSizeSp by remember {
        mutableFloatStateOf(prefs.getFloat("subtitle_text_size_sp", 18f))
    }

    // Gesture defaults
    var gesturesEnabled by remember {
        mutableStateOf(prefs.getBoolean("gestures_enabled", true))
    }
    var doubleTapSeek by remember {
        mutableStateOf(prefs.getBoolean("double_tap_seek", true))
    }
    var longPressSpeed by remember {
        mutableStateOf(prefs.getBoolean("long_press_speed_boost", true))
    }
    var swipeBrightnessVolume by remember {
        mutableStateOf(prefs.getBoolean("swipe_brightness_volume", true))
    }
    var horizontalSeekScrub by remember {
        mutableStateOf(prefs.getBoolean("horizontal_seek_scrub", true))
    }

    // Expand state — intelligence starts open; others collapsed
    var openIntelligence by remember { mutableStateOf(true) }
    var openPlayback by remember { mutableStateOf(false) }
    var openSubtitles by remember { mutableStateOf(false) }
    var openAudio by remember { mutableStateOf(false) }
    var openGestures by remember { mutableStateOf(false) }
    var openAppearance by remember { mutableStateOf(false) }
    var openLibrary by remember { mutableStateOf(false) }
    var openDiagnostics by remember { mutableStateOf(false) }
    var openAbout by remember { mutableStateOf(false) }

    fun persist(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Grouped by area — expand a section to configure it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }

        item {
            PhaseBExpandableSettingCard(
                title = "Intelligence",
                subtitle = "Momentum predictions and learning",
                expanded = openIntelligence,
                onToggle = { openIntelligence = !openIntelligence },
            ) {
                PhaseBSettingRow(
                    Icons.Outlined.AutoAwesome,
                    "Momentum intelligence",
                    "Predict what you are likely to watch next",
                ) { Switch(intelligenceEnabled, setIntelligenceEnabled) }

                PhaseBSettingRow(
                    Icons.Outlined.Psychology,
                    "Learn from viewing",
                    "Use local watch behavior to improve predictions",
                ) { Switch(learnFromHistory, setLearnFromHistory) }

                PhaseBSettingRow(
                    Icons.Outlined.QueuePlayNext,
                    "Auto-advance",
                    "Continue into the predicted queue after completion",
                ) { Switch(autoAdvance, setAutoAdvance) }

                PhaseBSettingRow(
                    Icons.Outlined.RestartAlt,
                    "Reset learned preferences",
                    "Forget prediction acceptance patterns; playback history stays",
                ) { TextButton(onClick = clearLearning) { Text("RESET") } }
            }
        }

        item {
            PhaseBExpandableSettingCard(
                title = "Playback",
                subtitle = "Resume, speed, Up Next, PiP",
                expanded = openPlayback,
                onToggle = { openPlayback = !openPlayback },
            ) {
                PhaseBSettingRow(
                    Icons.Outlined.PlayCircleOutline,
                    "Resume playback",
                    "Continue videos from their saved position",
                ) {
                    Switch(resumePlayback, {
                        resumePlayback = it
                        persist { putBoolean("resume_playback", it) }
                    })
                }
                PhaseBSettingRow(
                    Icons.Outlined.SkipNext,
                    "Up Next",
                    "Show the next item without covering the picture",
                ) {
                    Text("ON", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
                PhaseBSettingRow(
                    Icons.Outlined.Speed,
                    "Default speed",
                    "Applied when a new video starts (${defaultSpeed}x)",
                ) {
                    Row {
                        listOf(0.75f, 1f, 1.25f, 1.5f).forEach { speed ->
                            TextButton(
                                onClick = {
                                    defaultSpeed = speed
                                    persist { putFloat("default_playback_speed", speed) }
                                },
                            ) {
                                Text(
                                    "${speed}x",
                                    color = if (kotlin.math.abs(defaultSpeed - speed) < 0.01f) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
                PhaseBSettingRow(
                    Icons.Outlined.PictureInPictureAlt,
                    "Auto PiP on leave",
                    "Enter picture-in-picture when leaving while playing",
                ) {
                    Switch(autoPip, {
                        autoPip = it
                        persist { putBoolean("auto_pip_on_leave", it) }
                    })
                }
            }
        }

        item {
            PhaseBExpandableSettingCard(
                title = "Subtitles",
                subtitle = "Default size and background",
                expanded = openSubtitles,
                onToggle = { openSubtitles = !openSubtitles },
            ) {
                PhaseBSettingRow(
                    Icons.Outlined.TextFields,
                    "Default text size",
                    "S / M / L for the player overlay",
                ) {
                    Row {
                        listOf(14f to "S", 18f to "M", 24f to "L").forEach { (size, label) ->
                            TextButton(
                                onClick = {
                                    subtitleSizeSp = size
                                    persist { putFloat("subtitle_text_size_sp", size) }
                                },
                            ) {
                                Text(
                                    label,
                                    color = if (kotlin.math.abs(subtitleSizeSp - size) < 0.1f) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
                PhaseBSettingRow(
                    Icons.Outlined.ClosedCaption,
                    "Background plate",
                    "Semi-transparent box behind subtitle text",
                ) {
                    Switch(subtitleBg, {
                        subtitleBg = it
                        persist { putBoolean("subtitle_background", it) }
                    })
                }
                PhaseBSettingRow(
                    Icons.Outlined.ClosedCaption,
                    "Delay & external files",
                    "Adjust delay and load SRT/VTT from the player CC menu",
                ) {
                    Text("In player", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            PhaseBExpandableSettingCard(
                title = "Audio",
                subtitle = "Volume and effects",
                expanded = openAudio,
                onToggle = { openAudio = !openAudio },
            ) {
                PhaseBSettingRow(
                    Icons.Outlined.VolumeUp,
                    "Volume control",
                    "Player volume slider and mute; swipe right edge while watching",
                ) {
                    Text("In player", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PhaseBSettingRow(
                    Icons.Outlined.Equalizer,
                    "Equalizer",
                    "Audio effects are not implemented yet",
                ) {
                    Text("Soon", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            PhaseBExpandableSettingCard(
                title = "Gestures",
                subtitle = "Taps, seeks, brightness, volume",
                expanded = openGestures,
                onToggle = { openGestures = !openGestures },
            ) {
                PhaseBSettingRow(
                    Icons.Outlined.Gesture,
                    "Enable gestures",
                    "Master switch for player touch gestures",
                ) {
                    Switch(gesturesEnabled, {
                        gesturesEnabled = it
                        persist { putBoolean("gestures_enabled", it) }
                    })
                }
                PhaseBSettingRow(
                    Icons.Outlined.TouchApp,
                    "Double-tap seek",
                    "Left −10s · right +10s · center play/pause",
                ) {
                    Switch(doubleTapSeek, {
                        doubleTapSeek = it
                        persist { putBoolean("double_tap_seek", it) }
                    })
                }
                PhaseBSettingRow(
                    Icons.Outlined.Speed,
                    "Long-press 2× speed",
                    "Hold to boost; release to restore",
                ) {
                    Switch(longPressSpeed, {
                        longPressSpeed = it
                        persist { putBoolean("long_press_speed_boost", it) }
                    })
                }
                PhaseBSettingRow(
                    Icons.Outlined.Brightness6,
                    "Swipe brightness / volume",
                    "Left half brightness · right half volume",
                ) {
                    Switch(swipeBrightnessVolume, {
                        swipeBrightnessVolume = it
                        persist { putBoolean("swipe_brightness_volume", it) }
                    })
                }
                PhaseBSettingRow(
                    Icons.Outlined.Gesture,
                    "Horizontal seek scrub",
                    "Drag sideways to scrub about ±90 seconds",
                ) {
                    Switch(horizontalSeekScrub, {
                        horizontalSeekScrub = it
                        persist { putBoolean("horizontal_seek_scrub", it) }
                    })
                }
            }
        }

        item {
            PhaseBExpandableSettingCard(
                title = "Appearance",
                subtitle = "Theme",
                expanded = openAppearance,
                onToggle = { openAppearance = !openAppearance },
            ) {
                PhaseBSettingRow(
                    Icons.Outlined.DarkMode,
                    "Light appearance",
                    "Use a brighter interface",
                ) { Switch(light, setLight) }
            }
        }

        item {
            PhaseBExpandableSettingCard(
                title = "Library",
                subtitle = "Access and import",
                expanded = openLibrary,
                onToggle = { openLibrary = !openLibrary },
            ) {
                PhaseBSettingRow(
                    Icons.Outlined.Storage,
                    "Device library",
                    if (permission) "Permission granted" else "Permission required",
                ) {
                    if (!permission) {
                        TextButton(onClick = requestPermission) { Text("ALLOW") }
                    }
                }
                PhaseBSettingRow(
                    Icons.Outlined.VideoLibrary,
                    "Import video",
                    "Add a video without copying it",
                ) {
                    TextButton(onClick = importVideo) { Text("IMPORT") }
                }
            }
        }

        item {
            PhaseBExpandableSettingCard(
                title = "Diagnostics",
                subtitle = "Export and clear local events",
                expanded = openDiagnostics,
                onToggle = { openDiagnostics = !openDiagnostics },
            ) {
                PhaseBSettingRow(
                    Icons.Outlined.BugReport,
                    "Playback diagnostics",
                    "Export local playback and Momentum events",
                ) {
                    TextButton(onClick = exportDiagnostics) { Text("EXPORT") }
                }
                PhaseBSettingRow(
                    Icons.Outlined.DeleteSweep,
                    "Clear diagnostics",
                    "Delete local event history",
                ) {
                    TextButton(onClick = clearDiagnostics) { Text("CLEAR") }
                }
            }
        }

        item {
            PhaseBExpandableSettingCard(
                title = "About",
                subtitle = "Visonate info and privacy",
                expanded = openAbout,
                onToggle = { openAbout = !openAbout },
            ) {
                PhaseBSettingRow(
                    Icons.Outlined.Info,
                    "About Visonate",
                    "How to use it, what is implemented, privacy and licence",
                ) {
                    TextButton(onClick = openAbout) { Text("VIEW") }
                }
            }
        }
    }
}

@Composable
private fun PhaseBExpandableSettingCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(bottom = 8.dp), content = content)
            }
        }
    }
}

@Composable
private fun PhaseBSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: @Composable (() -> Unit),
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}
