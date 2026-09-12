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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private val LibraryAccent = Color(0xFF8B5CF6)
private val LibraryCyan = Color(0xFF22D3EE)
private val LibraryPink = Color(0xFFEC4899)

private enum class LibraryView { HOME, COLLECTION }
private enum class LibrarySortMode { RECENT, TITLE, PROGRESS }

@Composable
internal fun LibraryExperience(
    videos: List<VideoItem>,
    search: String,
    setSearch: (String) -> Unit,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
    initialFolder: String?,
    add: () -> Unit
) {
    var view by remember(initialFolder) { mutableStateOf(if (initialFolder == null) LibraryView.HOME else LibraryView.COLLECTION) }
    var folder by remember(initialFolder) { mutableStateOf(initialFolder) }
    var sortMode by remember { mutableStateOf(LibrarySortMode.RECENT) }

    val filtered = remember(videos, search) {
        videos.filter { video ->
            search.isBlank() || video.title.contains(search, true) || video.folderName?.contains(search, true) == true
        }
    }
    val folders = remember(filtered) {
        filtered.mapNotNull { video ->
            video.relativePath?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        }.distinct().sortedBy { folderName(it).lowercase() }
    }
    val continueWatching = remember(filtered) {
        filtered.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    }
    val orderedVideos = remember(filtered, sortMode) {
        when (sortMode) {
            LibrarySortMode.RECENT -> filtered.sortedByDescending { it.lastPlayedAtMs }
            LibrarySortMode.TITLE -> filtered.sortedWith(compareBy({ naturalLibraryKey(it.title) }, { it.title.lowercase() }))
            LibrarySortMode.PROGRESS -> filtered.sortedByDescending { it.progress }
        }
    }
    val folderVideos = remember(filtered, folder, sortMode) {
        filtered.filter { it.relativePath?.trimEnd('/') == folder?.trimEnd('/') }.sortedWith(libraryComparator(sortMode))
    }

    BackHandler(enabled = view == LibraryView.COLLECTION) {
        view = LibraryView.HOME
        folder = null
        setSearch("")
    }

    AnimatedContent(
        targetState = view,
        transitionSpec = {
            (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                (slideOutHorizontally { width -> -width / 3 } + fadeOut())
        },
        label = "library-experience"
    ) { target ->
        if (target == LibraryView.HOME) {
            LibraryHome(
                videos = videos,
                filtered = filtered,
                folders = folders,
                continueWatching = continueWatching,
                orderedVideos = orderedVideos,
                search = search,
                setSearch = setSearch,
                sortMode = sortMode,
                setSortMode = { sortMode = it },
                open = open,
                favorite = favorite,
                openFolder = {
                    folder = it
                    view = LibraryView.COLLECTION
                    setSearch("")
                },
                add = add
            )
        } else {
            LibraryCollection(
                folder = folder,
                videos = folderVideos,
                search = search,
                setSearch = setSearch,
                sortMode = sortMode,
                setSortMode = { sortMode = it },
                open = open,
                favorite = favorite,
                back = {
                    view = LibraryView.HOME
                    folder = null
                    setSearch("")
                },
                add = add
            )
        }
    }
}

@Composable
private fun LibraryHome(
    videos: List<VideoItem>,
    filtered: List<VideoItem>,
    folders: List<String>,
    continueWatching: List<VideoItem>,
    orderedVideos: List<VideoItem>,
    search: String,
    setSearch: (String) -> Unit,
    sortMode: LibrarySortMode,
    setSortMode: (LibrarySortMode) -> Unit,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
    openFolder: (String) -> Unit,
    add: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { LibraryMasthead(videos.size, folders.size, add) }
        item { LibrarySearch(search, setSearch) }
        if (search.isBlank() && continueWatching.isNotEmpty()) {
            item { LibrarySectionLabel("CONTINUE WATCHING", "Still warm") }
            item { ContinueShelf(continueWatching.take(8), open) }
        }
        item { LibrarySectionLabel("COLLECTIONS", "Your folders, without the file-browser feel") }
        if (folders.isEmpty()) {
            item { LibraryEmpty(search.isNotBlank(), add) }
        } else {
            items(folders, key = { "folder-$it" }) { path ->
                val count = filtered.count { it.relativePath?.trimEnd('/') == path.trimEnd('/') }
                CollectionRow(path, count) { openFolder(path) }
            }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ALL VIDEOS", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp)
                    Text("${orderedVideos.size} of ${videos.size} in view", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SortChooser(sortMode, setSortMode)
            }
        }
        if (orderedVideos.isEmpty()) {
            item { LibraryEmpty(search.isNotBlank(), add) }
        } else {
            items(orderedVideos, key = { it.id }) { video ->
                LibraryVideoCard(video, open, favorite)
            }
        }
    }
}

@Composable
private fun LibraryMasthead(count: Int, folders: Int, add: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(188.dp)
                .background(
                    Brush.linearGradient(
                        listOf(LibraryAccent.copy(alpha = .88f), LibraryCyan.copy(alpha = .38f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
                    ),
                    RoundedCornerShape(30.dp)
                )
                .padding(22.dp)
        ) {
            Column(Modifier.align(Alignment.BottomStart)) {
                Text("LIBRARY", fontSize = 12.sp, letterSpacing = 3.sp, color = Color.White.copy(alpha = .78f))
                Text("Your video world.", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("$count videos  •  $folders collections", fontSize = 11.sp, color = Color.White.copy(alpha = .78f))
            }
            Button(onClick = add, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Import")
            }
        }
    }
}

@Composable
private fun LibrarySearch(search: String, setSearch: (String) -> Unit) {
    OutlinedTextField(
        value = search,
        onValueChange = setSearch,
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        placeholder = { Text("Search videos or collections") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun LibrarySectionLabel(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp)
        Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContinueShelf(videos: List<VideoItem>, open: (VideoItem) -> Unit) {
    LazyColumn(modifier = Modifier.height(174.dp), userScrollEnabled = false) {
        items(videos.take(3), key = { "continue-${it.id}" }) { video ->
            ContinueRow(video, open)
        }
    }
}

@Composable
private fun ContinueRow(video: VideoItem, open: (VideoItem) -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable { open(video) },
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LibraryThumb(video, Modifier.size(112.dp, 64.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(video.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                Text("${formatLibraryProgress(video)} remaining", fontSize = 10.sp, color = LibraryCyan)
                LinearProgressIndicator(progress = { video.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            Icon(Icons.Outlined.PlayArrow, "Continue", tint = LibraryAccent)
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun CollectionRow(path: String, count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)
    ) {
        Row(Modifier.height(82.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = LibraryAccent.copy(alpha = .14f)) {
                Icon(Icons.Outlined.Folder, null, tint = LibraryAccent, modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(folderName(path), style = MaterialTheme.typography.titleSmall)
                Text("$count videos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("OPEN", fontSize = 9.sp, letterSpacing = 1.3.sp, color = LibraryCyan)
        }
    }
}

@Composable
private fun SortChooser(mode: LibrarySortMode, setMode: (LibrarySortMode) -> Unit) {
    val next = when (mode) {
        LibrarySortMode.RECENT -> LibrarySortMode.TITLE
        LibrarySortMode.TITLE -> LibrarySortMode.PROGRESS
        LibrarySortMode.PROGRESS -> LibrarySortMode.RECENT
    }
    Surface(
        modifier = Modifier.clickable { setMode(next) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Sort, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(sortLabel(mode), fontSize = 10.sp)
        }
    }
}

@Composable
private fun LibraryVideoCard(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable { open(video) },
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            LibraryThumb(video, Modifier.size(132.dp, 82.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                video.folderName?.let { Text(it, maxLines = 1, fontSize = 10.sp, color = LibraryCyan) }
                if (video.isResumeable) {
                    Text(formatLibraryProgress(video), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { video.progress }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                }
            }
            IconButton(onClick = { favorite(video.id) }) {
                val icon = if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder
                val tint = if (video.isFavorite) LibraryPink else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(icon, "Save", tint = tint)
            }
        }
    }
}

@Composable
private fun LibraryCollection(
    folder: String?,
    videos: List<VideoItem>,
    search: String,
    setSearch: (String) -> Unit,
    sortMode: LibrarySortMode,
    setSortMode: (LibrarySortMode) -> Unit,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
    back: () -> Unit,
    add: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(folderName(folder ?: ""), style = MaterialTheme.typography.headlineSmall)
                        Text("${videos.size} videos", fontSize = 11.sp, color = LibraryCyan)
                    }
                    Icon(Icons.Outlined.FolderOpen, null, tint = LibraryAccent)
                }
                Spacer(Modifier.height(8.dp))
                LibrarySearch(search, setSearch)
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SortChooser(sortMode, setSortMode) }
            }
        }
        if (videos.isEmpty()) {
            item { LibraryEmpty(true, add) }
        } else {
            items(videos, key = { it.id }) { video -> LibraryVideoCard(video, open, favorite) }
        }
    }
}

@Composable
private fun LibraryEmpty(searching: Boolean, add: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val icon = if (searching) Icons.Outlined.SearchOff else Icons.Outlined.VideoLibrary
            Icon(icon, null, tint = LibraryAccent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text(if (searching) "Nothing matches" else "Your library is empty", style = MaterialTheme.typography.titleMedium)
            Text(
                if (searching) "Try another title or collection." else "Import a local video to begin.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            if (!searching) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = add) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Import video")
                }
            }
        }
    }
}

@Composable
private fun LibraryThumb(video: VideoItem, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(video.id, video.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(video.id, video.uri) {
        bitmap = VideoThumbnailLoader.load(context, video.uri, 480, 270)
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Outlined.Movie, "Video", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun folderName(path: String): String = path.trimEnd('/').substringAfterLast('/').ifBlank { "Unsorted" }

private fun sortLabel(mode: LibrarySortMode): String = when (mode) {
    LibrarySortMode.RECENT -> "Recent"
    LibrarySortMode.TITLE -> "A–Z"
    LibrarySortMode.PROGRESS -> "Progress"
}

private fun libraryComparator(mode: LibrarySortMode): Comparator<VideoItem> = when (mode) {
    LibrarySortMode.RECENT -> compareByDescending<VideoItem> { it.lastPlayedAtMs }.thenBy { it.title.lowercase() }
    LibrarySortMode.TITLE -> compareBy<VideoItem> { naturalLibraryKey(it.title) }.thenBy { it.title.lowercase() }
    LibrarySortMode.PROGRESS -> compareByDescending<VideoItem> { it.progress }.thenBy { it.title.lowercase() }
}

private fun naturalLibraryKey(title: String): String = buildString {
    var cursor = 0
    Regex("\\d+").findAll(title.lowercase()).forEach { match ->
        append(title.substring(cursor, match.range.first).lowercase())
        append(match.value.toLongOrNull()?.toString()?.padStart(12, '0') ?: match.value)
        cursor = match.range.last + 1
    }
    append(title.substring(cursor).lowercase())
}

private fun formatLibraryProgress(video: VideoItem): String {
    if (video.durationMs <= 0L) return "Resume available"
    val remaining = (video.durationMs - video.lastPositionMs).coerceAtLeast(0L)
    val minutes = remaining / 60_000L
    val seconds = (remaining / 1_000L) % 60L
    return "${minutes}m ${seconds.toString().padStart(2, '0')}s remaining"
}
