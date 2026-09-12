package com.innotrepid.videoplayer

import androidx.compose.runtime.Composable
import com.innotrepid.videoplayer.library.VideoItem

/**
 * Stable integration boundary for the Library screen.
 * Data, playback, import and favorites remain owned by the existing root;
 * the redesigned presentation lives in LibraryExperience.
 */
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
    LibraryExperience(
        videos = videos,
        search = search,
        setSearch = setSearch,
        open = open,
        favorite = favorite,
        initialFolder = initialFolder,
        add = add
    )
}
