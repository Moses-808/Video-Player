package com.innotrepid.videoplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innotrepid.videoplayer.library.VideoItem

@Composable
fun PhaseBSavedScreen(
    videos: List<VideoItem>,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Saved", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Things you deliberately kept in your orbit.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }

        if (videos.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Icon(
                            Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Nothing saved yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Save a video from Pulse or Library and it will live here.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(videos, key = { it.id }) { video ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(video.title, Modifier.weight(1f), maxLines = 2)
                    IconButton(onClick = { favorite(video.id) }) {
                        Icon(
                            Icons.Outlined.Favorite,
                            contentDescription = "Remove from saved",
                            tint = PhaseBPink,
                        )
                    }
                    IconButton(onClick = { open(video) }) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Play")
                    }
                }
            }
        }
    }
}
