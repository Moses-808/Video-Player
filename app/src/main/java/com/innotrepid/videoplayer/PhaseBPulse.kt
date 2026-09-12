package com.innotrepid.videoplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.innotrepid.videoplayer.intelligence.VideoPrediction
import com.innotrepid.videoplayer.intelligence.VideoPredictionFeedbackStore
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoThumbnailLoader

@Composable
internal fun PhaseBPulse(
    videos: List<VideoItem>,
    predictions: List<VideoPrediction>,
    permission: Boolean,
    request: () -> Unit,
    favorite: (String) -> Unit,
    open: (VideoItem) -> Unit,
    library: (String) -> Unit
) {
    val resume = videos.filter { it.isResumeable }.sortedByDescending { it.lastPlayedAtMs }
    val recent = videos.filter { it.lastPlayedAtMs > 0L }.sortedByDescending { it.lastPlayedAtMs }.take(10)
    val folders = videos.mapNotNull { it.folderName }.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }
    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { PhaseBPulseHeader(videos.size, recent.firstOrNull(), open) }
        if (!permission) item { PhaseBConnect(request) }
        if (predictions.isNotEmpty()) item { PhaseBPredictionStage(videos, predictions, open) }
        if (resume.isNotEmpty()) item { PhaseBContinue(resume, open, favorite) }
        if (recent.isNotEmpty()) item { PhaseBRecent(recent, open, favorite) }
        if (folders.isNotEmpty()) item { PhaseBCollections(folders, library) }
        item { PhaseBAllVideos(videos, open, favorite) }
        if (videos.isEmpty()) item { PhaseBEmpty(library) }
    }
}

@Composable private fun PhaseBPulseHeader(count: Int, recent: VideoItem?, open: (VideoItem) -> Unit) {
    Box(Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(34.dp)).background(Brush.linearGradient(listOf(Color(0xFF11111C), Color(0xFF2B1B4D), Color(0xFF073D4A))))) {
        if (recent != null) {
            PhaseBThumb(recent, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE08080D)))))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("PULSE", color = Color.White, fontSize = 11.sp, letterSpacing = 2.4.sp) }
            Spacer(Modifier.height(7.dp))
            Text(if (recent == null) "Your watchspace\nis waiting." else "Keep the\nmomentum.", color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text(if (recent == null) "$count videos ready to become a rhythm." else "$count videos • your viewing history is shaping what comes next.", color = Color.White.copy(alpha = .66f), fontSize = 11.sp)
        }
        if (recent != null) FilledTonalIconButton(onClick = { open(recent) }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Icons.Outlined.PlayArrow, "Continue") }
    }
}

@Composable private fun PhaseBConnect(request: () -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(24.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Bring your library into Pulse", style = MaterialTheme.typography.titleSmall); Text("Your videos stay on your device.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = request) { Text("CONNECT") } } } }

@Composable private fun PhaseBPredictionStage(videos: List<VideoItem>, predictions: List<VideoPrediction>, open: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val feedback = remember { VideoPredictionFeedbackStore(context.applicationContext) }
    val byId = videos.associateBy { it.id }
    val ranked = predictions.mapNotNull { p -> byId[p.mediaId]?.let { p to it } }.sortedByDescending { feedback.adjustedConfidence(it.first.mediaId, it.first.confidence) }.take(5)
    if (ranked.isEmpty()) return
    val hero = ranked.first()
    val confidence = feedback.adjustedConfidence(hero.first.mediaId, hero.first.confidence)
    if (confidence < .45f) return
    LaunchedEffect(hero.first.mediaId) { feedback.recordShown(hero.first.mediaId) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Text("NEXT", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, letterSpacing = 2.sp); Text("A hunch, backed by your habits.", style = MaterialTheme.typography.titleLarge) }
            Text("${(confidence * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(30.dp)).clickable { feedback.recordAccepted(hero.first.mediaId); open(hero.second) }) {
            VideoPredictionPreview(hero.second, Modifier.fillMaxSize(), hero.first.previewPositionMs)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .90f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text(predictionReason(hero.first).uppercase(), color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, letterSpacing = 1.2.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp)); Text(hero.second.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                Text("The player is learning what you tend to do next.", color = Color.White.copy(alpha = .62f), fontSize = 10.sp)
            }
            FilledIconButton(onClick = { feedback.recordAccepted(hero.first.mediaId); open(hero.second) }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Icons.Outlined.PlayArrow, "Play predicted video") }
        }
        if (ranked.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(ranked.drop(1), key = { it.first.mediaId }) { (prediction, video) -> Column(Modifier.width(180.dp).clickable { open(video) }) { Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp))) { PhaseBThumb(video, Modifier.fillMaxSize()) }; Spacer(Modifier.height(7.dp)); Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall); Text(predictionReason(prediction), color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, maxLines = 1) } } }
    }
}

@Composable private fun PhaseBContinue(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { PhaseBSectionTitle("Continue", "Pick up exactly where you left off."); LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(videos, key = { it.id }) { PhaseBCard(it, open, favorite, true) } } } }
@Composable private fun PhaseBRecent(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { PhaseBSectionTitle("Your trail", "What has been moving through your attention."); LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(videos, key = { it.id }) { PhaseBCard(it, open, favorite, false) } } } }
@Composable private fun PhaseBCollections(folders: List<Pair<String, Int>>, open: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { PhaseBSectionTitle("Collections", "Your device's structure, interpreted as a space."); LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(folders) { (path, count) -> Card(Modifier.width(190.dp).clickable { open(path) }, RoundedCornerShape(22.dp)) { Column(Modifier.padding(16.dp)) { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)); Spacer(Modifier.height(18.dp)); Text(path.trimEnd('/').substringAfterLast('/').ifBlank { "Unsorted" }, style = MaterialTheme.typography.titleMedium, maxLines = 1); Text("$count videos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) } } } } } }
@Composable private fun PhaseBAllVideos(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { PhaseBSectionTitle("Orbit", "Everything available to your next decision."); videos.take(16).forEach { video -> Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { open(video) }, RoundedCornerShape(20.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { PhaseBThumb(video, Modifier.size(width = 116.dp, height = 72.dp).clip(RoundedCornerShape(14.dp))); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall); video.folderName?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp) } }; IconButton(onClick = { favorite(video.id) }) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save") } } } } } }
@Composable private fun PhaseBEmpty(library: (String) -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(24.dp)) { Column(Modifier.padding(22.dp)) { Text("Nothing in orbit yet", style = MaterialTheme.typography.titleMedium); Text("Import a local video and Pulse will start learning from your viewing patterns.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp); TextButton(onClick = { library("") }) { Text("OPEN LIBRARY") } } } }
@Composable private fun PhaseBSectionTitle(title: String, subtitle: String) { Column(Modifier.padding(horizontal = 16.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) } }
@Composable private fun PhaseBCard(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit, progress: Boolean) { Column(Modifier.width(190.dp).clickable { open(video) }) { Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp))) { PhaseBThumb(video, Modifier.fillMaxSize()); if (progress) LinearProgressIndicator(progress = { video.progress }, Modifier.fillMaxWidth().align(Alignment.BottomCenter)); IconButton(onClick = { favorite(video.id) }, Modifier.align(Alignment.TopEnd)) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = Color.White) } }; Spacer(Modifier.height(7.dp)); Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall); video.folderName?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp) } } }
@Composable private fun PhaseBThumb(video: VideoItem, modifier: Modifier) { val context = LocalContext.current; var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }; LaunchedEffect(video.id, video.uri) { bitmap = runCatching { VideoThumbnailLoader.load(context, video.uri, 480, 270) }.getOrNull() }; if (bitmap != null) Image(bitmap!!.asImageBitmap(), video.title, modifier, contentScale = ContentScale.Crop) else Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF181823), Color(0xFF2C2050))))) }
private fun predictionReason(prediction: VideoPrediction): String = when { VideoPrediction.Reason.SAME_FOLDER_CONTINUATION in prediction.reasons -> "A natural continuation"; VideoPrediction.Reason.RESUMEABLE in prediction.reasons -> "You left this unfinished"; VideoPrediction.Reason.REWATCHED in prediction.reasons -> "You've returned here before"; VideoPrediction.Reason.RECENTLY_ENGAGED in prediction.reasons -> "Recent engagement"; VideoPrediction.Reason.PREVIOUSLY_COMPLETED in prediction.reasons -> "A familiar watch"; else -> "Natural viewing order" }
