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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoThumbnailLoader

private val LibraryV2Accent = Color(0xFF8B5CF6)
private val LibraryV2Cyan = Color(0xFF22D3EE)
private val LibraryV2Pink = Color(0xFFEC4899)

private enum class LibraryV2View { HOME, COLLECTION }
private enum class LibraryV2Sort { RECENT, TITLE, PROGRESS }

@Composable
internal fun LibraryExperienceV2(
    videos: List<VideoItem>,
    search: String,
    setSearch: (String) -> Unit,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
    initialFolder: String?,
    add: () -> Unit
) {
    var view by remember(initialFolder) { mutableStateOf(if (initialFolder == null) LibraryV2View.HOME else LibraryV2View.COLLECTION) }
    var folder by remember(initialFolder) { mutableStateOf(initialFolder) }
    var sort by remember { mutableStateOf(LibraryV2Sort.RECENT) }
    var previewingKey by remember { mutableStateOf<String?>(null) }

    val normalizedFolder = folder?.trim()?.trimEnd('/')
    val filtered = remember(videos, search) {
        videos.filter { video ->
            search.isBlank() || video.title.contains(search, true) || video.folderName?.contains(search, true) == true
        }
    }
    val folders = remember(filtered) {
        filtered.mapNotNull { it.relativePath?.trim()?.trimEnd('/')?.takeIf(String::isNotBlank) }
            .distinct()
            .sortedBy { libraryV2FolderName(it).lowercase() }
    }
    val continueWatching = remember(filtered) {
        filtered.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    }
    val ordered = remember(filtered, sort) { filtered.sortedWith(libraryV2Comparator(sort)) }
    val collectionVideos = remember(videos, search, normalizedFolder, sort) {
        videos.filter { it.relativePath?.trim()?.trimEnd('/') == normalizedFolder }
            .filter { search.isBlank() || it.title.contains(search, true) }
            .sortedWith(libraryV2Comparator(sort))
    }

    LaunchedEffect(initialFolder) {
        folder = initialFolder
        view = if (initialFolder == null) LibraryV2View.HOME else LibraryV2View.COLLECTION
        previewingKey = null
        if (initialFolder != null) setSearch("")
    }

    LaunchedEffect(videos) {
        if (previewingKey != null) {
            val previewId = previewingKey!!.substringAfter(':')
            if (videos.none { it.id == previewId }) previewingKey = null
        }
    }

    BackHandler(enabled = view == LibraryV2View.COLLECTION) {
        view = LibraryV2View.HOME
        folder = null
        previewingKey = null
        setSearch("")
    }

    AnimatedContent(
        targetState = view,
        transitionSpec = {
            (slideInHorizontally { it / 2 } + fadeIn()) togetherWith
                (slideOutHorizontally { -it / 4 } + fadeOut())
        },
        label = "library-v2"
    ) { target ->
        if (target == LibraryV2View.HOME) {
            LibraryV2Home(
                videos = videos,
                filtered = filtered,
                folders = folders,
                continueWatching = continueWatching,
                ordered = ordered,
                search = search,
                setSearch = setSearch,
                sort = sort,
                setSort = { sort = it },
                open = open,
                favorite = favorite,
                openFolder = {
                    previewingKey = null
                    folder = it
                    view = LibraryV2View.COLLECTION
                    setSearch("")
                },
                add = add,
                previewingKey = previewingKey,
                setPreviewingKey = { previewingKey = it }
            )
        } else {
            LibraryV2Collection(
                folder = normalizedFolder,
                videos = collectionVideos,
                search = search,
                setSearch = setSearch,
                sort = sort,
                setSort = { sort = it },
                open = open,
                favorite = favorite,
                back = {
                    previewingKey = null
                    view = LibraryV2View.HOME
                    folder = null
                    setSearch("")
                },
                add = add,
                previewingKey = previewingKey,
                setPreviewingKey = { previewingKey = it }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryV2Home(
    videos: List<VideoItem>,
    filtered: List<VideoItem>,
    folders: List<String>,
    continueWatching: List<VideoItem>,
    ordered: List<VideoItem>,
    search: String,
    setSearch: (String) -> Unit,
    sort: LibraryV2Sort,
    setSort: (LibraryV2Sort) -> Unit,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
    openFolder: (String) -> Unit,
    add: () -> Unit,
    previewingKey: String?,
    setPreviewingKey: (String?) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { LibraryV2Masthead(videos.size, folders.size, add) }

        stickyHeader {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
            ) {
                LibraryV2Search(search, setSearch)
            }
        }

        if (search.isBlank() && continueWatching.isNotEmpty()) {
            item { LibraryV2Section("CONTINUE WATCHING", "Pick up exactly where you left off") }
            item { LibraryV2ContinueShelf(continueWatching, open, previewingKey, setPreviewingKey) }
        }

        item { LibraryV2Section("COLLECTIONS", "Your folders, reframed as destinations") }
        if (folders.isEmpty()) {
            item { LibraryV2Empty(search.isNotBlank(), add) }
        } else {
            items(folders, key = { "collection-$it" }) { path ->
                val collectionVideos = filtered.filter { it.relativePath?.trimEnd('/') == path.trimEnd('/') }
                LibraryV2CollectionCard(path, collectionVideos.size, collectionVideos.firstOrNull(), openFolder)
            }
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ALL VIDEOS", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.6.sp)
                    Text("${ordered.size} visible  •  ${videos.size} total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LibraryV2SortChip(sort, setSort)
            }
        }
        if (ordered.isEmpty()) {
            item { LibraryV2Empty(search.isNotBlank(), add) }
        } else {
            items(ordered, key = { it.id }) { video -> LibraryV2VideoCard(video, open, favorite, previewingKey, setPreviewingKey) }
        }
    }
}

@Composable
private fun LibraryV2Masthead(count: Int, folders: Int, add: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(218.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0B0A13), Color(0xFF2A164A), Color(0xFF063E49))))
            .padding(22.dp)
    ) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(LibraryV2Cyan.copy(alpha = .20f), Color.Transparent), radius = 520f)))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text("LIBRARY", color = Color.White.copy(alpha = .68f), fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            AdaptiveLibraryText("Your video world.", Color.White, MaterialTheme.typography.headlineLarge, 30.sp, 1)
            Spacer(Modifier.height(6.dp))
            Text("$count videos  •  $folders collections", color = Color.White.copy(alpha = .62f), fontSize = 10.sp)
        }
        Surface(
            modifier = Modifier.align(Alignment.TopEnd),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = .10f),
            contentColor = Color.White
        ) {
            Row(Modifier.clickable { add() }.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun LibraryV2Search(search: String, setSearch: (String) -> Unit) {
    OutlinedTextField(
        value = search,
        onValueChange = setSearch,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth(),
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
        trailingIcon = {
            if (search.isNotBlank()) {
                IconButton(onClick = { setSearch("") }) {
                    Icon(Icons.Outlined.SearchOff, contentDescription = "Clear search")
                }
            }
        },
        placeholder = { Text("Search videos or collections") },
        singleLine = true,
        shape = RoundedCornerShape(19.dp),
    )
}

@Composable
private fun LibraryV2Section(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(title, color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryV2ContinueShelf(videos: List<VideoItem>, open: (VideoItem) -> Unit, previewingKey: String?, setPreviewingKey: (String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(videos.take(8), key = { "resume-${it.id}" }) { video ->
            val key = "continue:${video.id}"
            LibraryV2ContinueCard(video, open, previewingKey == key, { setPreviewingKey(if (previewingKey == key) null else key) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryV2ContinueCard(
    video: VideoItem,
    open: (VideoItem) -> Unit,
    previewing: Boolean,
    activatePreview: () -> Unit
) {
    Card(
        Modifier
            .width(268.dp)
            .combinedClickable(onClick = { open(video) }, onLongClick = activatePreview),
        RoundedCornerShape(24.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                if (previewing) LibraryPreviewSurface(video, Modifier.fillMaxSize(), video.lastPositionMs)
                else LibraryV2Thumb(video, Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(72.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .84f)))))
                Surface(Modifier.align(Alignment.BottomEnd).padding(10.dp), RoundedCornerShape(50), color = Color.Black.copy(alpha = .56f)) {
                    Icon(Icons.Outlined.PlayArrow, "Continue", tint = Color.White, modifier = Modifier.padding(8.dp).size(19.dp))
                }
            }
            Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
                AdaptiveLibraryText(video.title, MaterialTheme.colorScheme.onSurface, MaterialTheme.typography.titleSmall, 16.sp, 1)
                Text(if (previewing) "Previewing from your position" else formatLibraryV2Progress(video), fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                LinearProgressIndicator(progress = { video.progress }, Modifier.fillMaxWidth().padding(top = 7.dp))
            }
        }
    }
}

@Composable
private fun LibraryV2CollectionCard(path: String, count: Int, preview: VideoItem?, openFolder: (String) -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(122.dp).clickable { openFolder(path) },
        shape = RoundedCornerShape(25.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (preview != null) LibraryV2Thumb(preview, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(LibraryV2Accent.copy(alpha = .14f)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .86f), Color.Black.copy(alpha = .30f), Color.Black.copy(alpha = .68f)))))
            Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .12f), contentColor = Color.White) {
                    Icon(if (preview == null) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, null, modifier = Modifier.padding(11.dp).size(24.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                    AdaptiveLibraryText(libraryV2FolderName(path), Color.White, MaterialTheme.typography.titleMedium, 20.sp, 1)
                    Text("$count videos", color = Color.White.copy(alpha = .66f), fontSize = 10.sp)
                }
                Text("OPEN", color = MaterialTheme.colorScheme.secondary, fontSize = 8.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LibraryV2SortChip(sort: LibraryV2Sort, setSort: (LibraryV2Sort) -> Unit) {
    val next = when (sort) {
        LibraryV2Sort.RECENT -> LibraryV2Sort.TITLE
        LibraryV2Sort.TITLE -> LibraryV2Sort.PROGRESS
        LibraryV2Sort.PROGRESS -> LibraryV2Sort.RECENT
    }
    Surface(modifier.clickable { setSort(next) }, RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Sort, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(libraryV2SortLabel(sort), fontSize = 9.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryV2VideoCard(
    video: VideoItem,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
    previewingKey: String?,
    setPreviewingKey: (String?) -> Unit
) {
    val key = "video:${video.id}"
    val previewing = previewingKey == key
    Card(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .combinedClickable(onClick = { open(video) }, onLongClick = { setPreviewingKey(if (previewing) null else key) }),
        RoundedCornerShape(23.dp)
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(138.dp, 86.dp).clip(RoundedCornerShape(17.dp))) {
                if (previewing) LibraryPreviewSurface(video, Modifier.fillMaxSize())
                else LibraryV2Thumb(video, Modifier.fillMaxSize())
                if (video.isResumeable) {
                    LinearProgressIndicator(progress = { video.progress }, Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                AdaptiveLibraryText(video.title, MaterialTheme.colorScheme.onSurface, MaterialTheme.typography.titleSmall, 18.sp, 2)
                video.folderName?.let { Text(it, maxLines = 1, fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary) }
                if (video.isResumeable) Text(formatLibraryV2Progress(video), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { favorite(video.id) }) {
                Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = if (video.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryV2Collection(
    folder: String?,
    videos: List<VideoItem>,
    search: String,
    setSearch: (String) -> Unit,
    sort: LibraryV2Sort,
    setSort: (LibraryV2Sort) -> Unit,
    open: (VideoItem) -> Unit,
    favorite: (String) -> Unit,
    back: () -> Unit,
    add: () -> Unit,
    previewingKey: String?,
    setPreviewingKey: (String?) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        stickyHeader {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(
                        Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, "Back") }
                        Column(Modifier.weight(1f)) {
                            AdaptiveLibraryText(libraryV2FolderName(folder.orEmpty()), MaterialTheme.colorScheme.onBackground, MaterialTheme.typography.headlineSmall, 28.sp, 2)
                            Text("${videos.size} videos in this collection", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                        Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    LibraryV2Search(search, setSearch)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.End,
                    ) { LibraryV2SortChip(sort, setSort) }
                }
            }
        }
        if (videos.isEmpty()) item { LibraryV2Empty(search.isNotBlank(), add) }
        else items(videos, key = { it.id }) { LibraryV2VideoCard(it, open, favorite, previewingKey, setPreviewingKey) }
    }
}

@Composable
private fun LibraryV2Empty(searching: Boolean, add: () -> Unit) {
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (searching) Icons.Outlined.SearchOff else Icons.Outlined.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(if (searching) "No matches" else "Nothing here yet", style = MaterialTheme.typography.titleMedium)
            Text(
                if (searching) "Try a different title or collection name." else "Import a local video to build your library.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!searching) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = add) { Text("IMPORT") }
            }
        }
    }
}

@Composable
private fun LibraryV2Thumb(video: VideoItem, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(video.id, video.uri) {
        bitmap = runCatching { VideoThumbnailLoader.load(context, video.uri, 480, 270) }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), video.title, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF181823), Color(0xFF2C2050)))))
    }
}

@Composable
private fun AdaptiveLibraryText(
    text: String,
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
    maxSize: androidx.compose.ui.unit.TextUnit,
    maxLines: Int,
) {
    Text(text, color = color, style = style.copy(fontSize = maxSize), maxLines = maxLines)
}

private fun libraryV2FolderName(path: String): String =
    path.trimEnd('/').substringAfterLast('/').ifBlank { "Unsorted" }

private fun libraryV2SortLabel(sort: LibraryV2Sort): String = when (sort) {
    LibraryV2Sort.RECENT -> "Recent"
    LibraryV2Sort.TITLE -> "Title"
    LibraryV2Sort.PROGRESS -> "Progress"
}

private fun libraryV2Comparator(sort: LibraryV2Sort): Comparator<VideoItem> = when (sort) {
    LibraryV2Sort.RECENT -> compareByDescending { it.lastPlayedAtMs.takeIf { t -> t > 0L } ?: it.addedAtMs }
    LibraryV2Sort.TITLE -> compareBy { it.title.lowercase() }
    LibraryV2Sort.PROGRESS -> compareByDescending { it.progress }
}

private fun formatLibraryV2Progress(video: VideoItem): String {
    if (video.durationMs <= 0L) return "In progress"
    val remaining = (video.durationMs - video.lastPositionMs).coerceAtLeast(0L)
    val minutes = remaining / 60_000L
    val seconds = (remaining / 1_000L) % 60L
    return "${minutes}m ${seconds.toString().padStart(2, '0')}s remaining"
}
