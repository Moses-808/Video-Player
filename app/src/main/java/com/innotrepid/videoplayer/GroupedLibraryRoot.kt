package com.innotrepid.videoplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoThumbnailLoader

/**
 * A library view organized around the way local video collections are commonly stored:
 * folders act as collections, while individual files remain directly playable.
 */
@Composable
fun GroupedLibraryRoot(
    videos: List<VideoItem>,
    search: String,
    setSearch: (String) -> Unit,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
    add: () -> Unit
) {
    val filtered = remember(videos, search) {
        videos.filter {
            search.isBlank() ||
                it.title.contains(search, ignoreCase = true) ||
                it.folderName?.contains(search, ignoreCase = true) == true
        }
    }
    val groups = remember(filtered) {
        filtered.groupBy { it.folderName?.takeIf(String::isNotBlank) ?: "Unsorted" }
            .toList()
            .sortedWith(compareBy({ it.first == "Unsorted" }, { it.first.lowercase() }))
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Library", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = setSearch,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search videos or folders") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${filtered.size} videos",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = add) { Text("Add video") }
                }
            }
        }

        if (groups.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(if (search.isBlank()) "Your library is empty" else "No matches")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (search.isBlank()) "Add a local video and your collections will appear here."
                            else "Try a different title or folder name.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            groups.forEach { (folder, itemsInFolder) ->
                item(key = "header-$folder") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(folder, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Text(
                                "${itemsInFolder.size}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (folder == "Unsorted") "Videos without a folder"
                            else "Collection",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                items(
                    itemsInFolder.sortedBy { it.title.lowercase() },
                    key = { "video-${it.id}" }
                ) { video ->
                    GroupedVideoRow(video, open, favorite)
                }
            }
        }
    }
}

@Composable
private fun GroupedVideoRow(
    video: VideoItem,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable { open(video) }
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GroupedThumbnail(video, Modifier.size(130.dp, 82.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, maxLines = 2)
                video.folderName?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                if (video.isResumeable) {
                    LinearProgressIndicator(
                        progress = { video.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            }
            TextButton(onClick = { favorite(video.id) }) {
                Text(if (video.isFavorite) "★" else "☆", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun GroupedThumbnail(video: VideoItem, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(video.id, video.uri) {
        bitmap = VideoThumbnailLoader.load(context, video.uri, 480, 270)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Text("VIDEO", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}
