package com.innotrepid.videoplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LibraryFoundation(
    videos: List<VideoItem>,
    onOpenVideo: (VideoItem) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unfinished = videos.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    val folders = videos.mapNotNull { it.folderKey }.distinct().sorted()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Library", style = MaterialTheme.typography.headlineLarge)
                    Text("Everything you keep coming back to.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onImport) { Icon(Icons.Outlined.Add, contentDescription = "Import video") }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)); Text("Search your library", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (unfinished.isNotEmpty()) {
            item { Text("Continue watching", style = MaterialTheme.typography.titleLarge) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(unfinished.take(10), key = { it.id }) { video -> Column(Modifier.size(width = 190.dp, height = 100.dp)) { Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(6.dp)); Text("${(video.progress * 100).toInt()}% watched", color = MaterialTheme.colorScheme.primary) } } } }
        }
        if (folders.isNotEmpty()) {
            item { Text("Collections", style = MaterialTheme.typography.titleLarge) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(folders.take(10)) { folder -> Row(Modifier.size(width = 190.dp, height = 60.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Folder, null); Spacer(Modifier.size(10.dp)); Column { Text(folderDisplayName(folder), maxLines = 1); Text("${videos.count { it.folderKey == folder }} videos", color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
        }
        item { Text("All videos", style = MaterialTheme.typography.titleLarge) }
        items(videos.sortedWith(compareByDescending<VideoItem> { it.lastPlayedAtMs }.thenBy { it.title }), key = { it.id }) { video ->
            Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(video.title, maxLines = 1, style = MaterialTheme.typography.titleMedium); Text(video.folderName ?: "Local video", maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { onToggleFavorite(video) }) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save") }
            }
        }
        if (videos.isEmpty()) {
            item { Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(42.dp)); Spacer(Modifier.height(12.dp)); Text("Your library is quiet", style = MaterialTheme.typography.titleMedium); Text("Import a video to give it something to remember.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

private fun folderDisplayName(path: String): String = path.trimEnd('/').substringAfterLast('/').ifBlank { path }
