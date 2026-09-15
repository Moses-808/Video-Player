package com.innotrepid.videoplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PhaseBScreen { PULSE, LIBRARY, SAVED, SETTINGS }

val PhaseBViolet = Color(0xFF8B5CF6)
val PhaseBCyan = Color(0xFF22D3EE)
val PhaseBPink = Color(0xFFEC4899)

@Composable
fun PhaseBBottomBar(selected: PhaseBScreen, onSelect: (PhaseBScreen) -> Unit) {
    NavigationBar(tonalElevation = 12.dp) {
        PhaseBNav(PhaseBScreen.PULSE, selected, Icons.Outlined.AutoAwesome, "Pulse", onSelect)
        PhaseBNav(PhaseBScreen.LIBRARY, selected, Icons.Outlined.VideoLibrary, "Library", onSelect)
        PhaseBNav(PhaseBScreen.SAVED, selected, Icons.Outlined.BookmarkBorder, "Saved", onSelect)
        PhaseBNav(PhaseBScreen.SETTINGS, selected, Icons.Outlined.Settings, "Settings", onSelect)
    }
}

@Composable
private fun RowScope.PhaseBNav(
    item: PhaseBScreen,
    selected: PhaseBScreen,
    icon: ImageVector,
    label: String,
    onSelect: (PhaseBScreen) -> Unit,
) {
    val active = item == selected
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { onSelect(item) }) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                label,
                fontSize = 10.sp,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
