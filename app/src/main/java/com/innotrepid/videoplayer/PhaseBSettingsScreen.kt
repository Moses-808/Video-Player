package com.innotrepid.videoplayer

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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    LazyColumn(
        contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Shape the player around the way you watch.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }

        item {
            PhaseBSettingCard("Intelligence") {
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
            PhaseBSettingCard("Playback") {
                PhaseBSettingRow(
                    Icons.Outlined.SkipNext,
                    "Up Next",
                    "Keep the next video available without covering the picture",
                ) {
                    Text("ON", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
                PhaseBSettingRow(
                    Icons.Outlined.PlayCircleOutline,
                    "Resume playback",
                    "Continue videos from their saved position",
                ) {
                    Text("ON", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            PhaseBSettingCard("Appearance") {
                PhaseBSettingRow(
                    Icons.Outlined.DarkMode,
                    "Light appearance",
                    "Use a brighter interface",
                ) { Switch(light, setLight) }
            }
        }

        item {
            PhaseBSettingCard("Library") {
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
            PhaseBSettingCard("Diagnostics") {
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
            PhaseBSettingCard("About") {
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
private fun PhaseBSettingCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                title,
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
            content()
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
