package com.innotrepid.videoplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
internal fun VisonateAboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } },
        icon = { Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Visonate") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("A local video player built to become more useful the more naturally you use it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Version 0.4.0", style = MaterialTheme.typography.labelLarge)
                    }
                }
                item { AboutHeading("How to use") }
                items(
                    listOf(
                        "Pulse is your starting point. Continue something unfinished, follow Momentum's next suggestion, or browse your orbit.",
                        "Library is for finding, importing, searching and organizing your videos into collections.",
                        "Saved keeps videos you deliberately want to return to.",
                        "While watching, tap the picture for controls. Use the scrubber, ±10 seconds, speed, volume, orientation, fullscreen and Up Next controls as needed.",
                        "Momentum learns locally from viewing behavior when Intelligence and Learn from viewing are enabled."
                    )
                ) { AboutBullet(it) }
                item { AboutHeading("What is implemented") }
                items(
                    listOf(
                        "Local Media3/ExoPlayer playback with resume, seeking, speed, volume, mute, orientation, fullscreen, retry and buffering feedback.",
                        "Folder/series-aware natural episode ordering and guarded automatic next-video playback.",
                        "Device scanning, manual import, persistent metadata, favorites, search, collections and missing-media reconciliation.",
                        "Pulse prediction with explainable confidence, contextual preview loops, prediction exposure, acceptance and learned ranking.",
                        "Momentum behavioral telemetry for starts, resumes, pauses, seeks, completions, skips and errors.",
                        "Local diagnostics export and clearing, with playback and intelligence controls in Settings.",
                        "Light and dark appearance with theme-aware text and controls, plus horizontal screen navigation."
                    )
                ) { AboutBullet(it) }
                item { AboutHeading("Momentum") }
                item {
                    Text(
                        "Momentum is the intelligence layer behind Visonate. It observes viewing behavior, interprets signals, predicts likely next actions, presents those predictions and learns from what you accept or ignore. The goal is anticipation without taking control away from you.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item { AboutHeading("Privacy & data") }
                item {
                    Text(
                        "Your video files are not copied into the app's private library. Library metadata, playback history, Momentum events and learned prediction preferences are stored locally. Diagnostics leave the device only when you explicitly export them.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item { AboutHeading("License") }
                item {
                    Text(
                        "Visonate is proprietary software. © 2026 Innotrepid. All rights reserved. See the repository LICENSE file for the full terms.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Spacer(Modifier.height(2.dp))
                    Text("Developed by Innotrepid", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    )
}

@Composable
private fun AboutHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun AboutBullet(text: String) {
    Text("• $text", color = MaterialTheme.colorScheme.onSurfaceVariant)
}
