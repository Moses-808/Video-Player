package com.innotrepid.videoplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoThumbnailLoader

private val folderAccent = Color(0xFF8B5CF6)
private val folderCyan = Color(0xFF22D3EE)

@Composable
fun GroupedLibraryRoot(
    videos: List<VideoItem>,
    search: String,
    setSearch: (String) -> Unit,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
    initialFolder: String? = null,
    add: () -> Unit
) {
    var openedFolder by remember(initialFolder) { mutableStateOf(initialFolder) }
    val matchingVideos = remember(videos, search) {
        videos.filter { search.isBlank() || it.title.contains(search, true) || it.folderName?.contains(search, true) == true }
    }
    val groups = remember(matchingVideos) {
        matchingVideos
            .mapNotNull { video -> video.relativePath?.takeIf(String::isNotBlank)?.let { it to video } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .toList()
            .sortedBy { it.first.lowercase() }
    }
    val folderVideoIds = remember(groups) { groups.flatMap { it.second }.map { it.id }.toSet() }
    val standaloneVideos = remember(matchingVideos, folderVideoIds) {
        matchingVideos.filterNot { it.id in folderVideoIds }
    }
    val visible = remember(matchingVideos, openedFolder) {
        if (openedFolder == null) emptyList() else matchingVideos.filter { it.relativePath == openedFolder }
    }

    BackHandler(enabled = openedFolder != null) {
        openedFolder = null
        setSearch("")
    }

    AnimatedContent(targetState = openedFolder, transitionSpec = {
        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 2 } + fadeOut())
    }, label = "library-folder") { folder ->
        if (folder == null) {
            LazyColumn(contentPadding = PaddingValues(top = 18.dp, bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text("Library", style = MaterialTheme.typography.headlineMedium)
                        Text("Your collections and videos, kept in their natural folders.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(value = search, onValueChange = setSearch, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { setSearch("") }) { Icon(Icons.Outlined.Close, "Clear") } }, placeholder = { Text("Search your library") }, singleLine = true, shape = RoundedCornerShape(18.dp))
                    }
                }
                item { Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text("${groups.size} collections", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)); Text("${videos.size} videos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) } }
                if (groups.isEmpty() && standaloneVideos.isEmpty()) item { EmptyLibrary(add, search.isNotBlank()) }
                groups.forEach { (path, folderVideos) -> item(key = "folder-$path") { FolderTile(folderDisplayName(path), folderVideos) { openedFolder = path } } }
                items(standaloneVideos, key = { it.id }) { video -> LibraryVideoRow(video, open, favorite) }
            }
        } else {
            val folderVideos = visible.sortedWith(compareBy({ naturalKey(it.title) }, { it.title.lowercase() }))
            LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { openedFolder = null; setSearch("") }) { Icon(Icons.Outlined.ArrowBack, "Back") }
                            Column(Modifier.weight(1f)) { Text(folderDisplayName(folder), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface); Text("${folderVideos.size} videos", color = folderCyan, fontSize = 11.sp) }
                            Icon(Icons.Outlined.FolderOpen, null, tint = folderAccent)
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(value = search, onValueChange = setSearch, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Search this folder") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    }
                }
                if (folderVideos.isEmpty()) item { EmptyLibrary(add, true) }
                else items(folderVideos, key = { it.id }) { video -> LibraryVideoRow(video, open, favorite) }
            }
        }
    }
}

private fun folderDisplayName(path: String): String = path.trimEnd('/').substringAfterLast('/').ifBlank { "Unsorted" }

@Composable private fun FolderTile(name: String, videos: List<VideoItem>, onClick: () -> Unit) {
    val preview = videos.firstOrNull()
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { onClick() }, RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)) {
        Row(Modifier.height(118.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(128.dp).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(folderAccent.copy(alpha = .75f), folderCyan.copy(alpha = .35f))))) {
                preview?.let { FolderThumbnail(it, Modifier.fillMaxSize()) }
                Surface(Modifier.align(Alignment.TopStart).padding(10.dp), RoundedCornerShape(10.dp), Color.Black.copy(alpha = .55f)) { Icon(Icons.Outlined.Folder, null, tint = Color.White, modifier = Modifier.padding(6.dp).size(18.dp)) }
            }
            Column(Modifier.padding(horizontal = 16.dp).weight(1f)) { Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2); Spacer(Modifier.height(5.dp)); Text("${videos.size} videos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Text("OPEN COLLECTION", color = folderCyan, fontSize = 10.sp, letterSpacing = 1.sp) }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 14.dp))
        }
    }
}

@Composable private fun LibraryVideoRow(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { open(video) }, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            FolderThumbnail(video, Modifier.size(128.dp, 80.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2, color = MaterialTheme.colorScheme.onSurface); video.folderName?.let { Text(it, color = folderCyan, fontSize = 10.sp) }; if (video.isResumeable) LinearProgressIndicator(progress = { video.progress }, Modifier.fillMaxWidth().padding(top = 6.dp)) }
            IconButton(onClick = { favorite(video.id) }) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = if (video.isFavorite) Color(0xFFEC4899) else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun FolderThumbnail(video: VideoItem, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(video.id, video.uri) { bitmap = VideoThumbnailLoader.load(context, video.uri, 480, 270) }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { bitmap?.let { Image(it.asImageBitmap(), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Icon(Icons.Outlined.Movie, "Video", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable private fun EmptyLibrary(add: () -> Unit, searching: Boolean) {
    Surface(Modifier.fillMaxWidth().padding(20.dp), RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (searching) Icons.Outlined.SearchOff else Icons.Outlined.VideoLibrary, null, tint = folderAccent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp)); Text(if (searching) "Nothing matches" else "Your library is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(if (searching) "Try another title or folder." else "Import a local video to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (!searching) { Spacer(Modifier.height(12.dp)); Button(onClick = add) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Import video") } }
        }
    }
}

private fun naturalKey(title: String): String = buildString {
    var cursor = 0
    Regex("\\d+").findAll(title.lowercase()).forEach { match ->
        append(title.substring(cursor, match.range.first).lowercase())
        append(match.value.toLongOrNull()?.toString()?.padStart(12, '0') ?: match.value)
        cursor = match.range.last + 1
    }
    append(title.substring(cursor).lowercase())
}