package com.innotrepid.videoplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    LazyColumn(
        contentPadding = PaddingValues(top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        item { PulseMasthead(videos.size, recent.firstOrNull(), open) }
        if (!permission) item { PulseLibraryNudge(request) }
        if (predictions.isNotEmpty()) item { PulseNext(videos, predictions, open) }
        if (resume.isNotEmpty()) item { PulseContinue(resume, open, favorite) }
        if (recent.isNotEmpty()) item { PulseTrail(recent, open, favorite) }
        if (folders.isNotEmpty()) item { PulseCollections(folders, library) }
        if (videos.isNotEmpty()) item { PulseOrbit(videos, open, favorite) }
        if (videos.isEmpty()) item { PulseEmpty(library) }
    }
}

@Composable
private fun PulseMasthead(count: Int, recent: VideoItem?, open: (VideoItem) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(292.dp).padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF090A12), Color(0xFF24143F), Color(0xFF063D46))))
    ) {
        if (recent != null) {
            PulseThumb(recent, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF507080D)))))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xCC080811), Color.Transparent))))
        }
        Column(Modifier.align(Alignment.TopStart).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("PULSE", color = Color.White, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(9.dp))
            Text(if (recent == null) "Your watchspace\nis waiting." else "Stay in\nyour flow.", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Text(
                if (recent == null) "$count videos ready to become a rhythm." else "$count videos • your viewing trail is becoming a signal.",
                color = Color.White.copy(alpha = .68f), fontSize = 11.sp
            )
            if (recent != null) {
                Spacer(Modifier.height(12.dp))
                Surface(onClick = { open(recent) }, shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .13f), contentColor = Color.White) {
                    Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Continue", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = .35f)
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondary))
                Spacer(Modifier.width(6.dp))
                Text("LIVE SIGNAL", color = Color.White.copy(alpha = .78f), fontSize = 8.sp, letterSpacing = 1.3.sp)
            }
        }
    }
}

@Composable
private fun PulseLibraryNudge(request: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp)) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) { Text("Let Pulse see your library", style = MaterialTheme.typography.titleSmall); Text("Nothing leaves your device.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = request) { Text("CONNECT") }
        }
    }
}

@Composable
private fun PulseNext(videos: List<VideoItem>, predictions: List<VideoPrediction>, open: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val feedback = remember { VideoPredictionFeedbackStore(context.applicationContext) }
    val byId = remember(videos) { videos.associateBy { it.id } }
    val ranked = predictions.mapNotNull { p -> byId[p.mediaId]?.let { p to it } }
        .sortedByDescending { feedback.adjustedConfidence(it.first.mediaId, it.first.confidence) }.take(5)
    if (ranked.isEmpty()) return
    val hero = ranked.first()
    val confidence = feedback.adjustedConfidence(hero.first.mediaId, hero.first.confidence)
    if (confidence < .45f) return
    LaunchedEffect(hero.first.mediaId) { feedback.recordShown(hero.first.mediaId) }
    var revealed by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(hero.first.mediaId) { revealed = true }
    val heroScale by animateFloatAsState(if (pressed) .985f else 1f, tween(160), label = "pulseHeroScale")
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        AnimatedVisibility(visible = revealed, enter = fadeIn(tween(420)) + slideInVertically(tween(420), initialOffsetY = { it / 5 })) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("THE NEXT MOVE", color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text("I have a hunch.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Built from what you actually do.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                PulseConfidence(confidence)
            }
        }
        AnimatedVisibility(visible = revealed, enter = fadeIn(tween(500)) + slideInVertically(tween(500), initialOffsetY = { it / 7 })) {
            Box(
                Modifier.fillMaxWidth().height(278.dp).padding(horizontal = 16.dp)
                    .graphicsLayer { scaleX = heroScale; scaleY = heroScale }
                    .clip(RoundedCornerShape(32.dp))
                    .clickable {
                        pressed = true
                        feedback.recordAccepted(hero.first.mediaId)
                        open(hero.second)
                    }
            ) {
                VideoPredictionPreview(hero.second, Modifier.fillMaxSize(), hero.first.previewPositionMs)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .94f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(21.dp).padding(end = 70.dp)) {
                    Text(predictionReason(hero.first).uppercase(), color = MaterialTheme.colorScheme.secondary, fontSize = 8.sp, letterSpacing = 1.2.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(hero.second.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text("Your viewing pattern points here next.", color = Color.White.copy(alpha = .62f), fontSize = 10.sp)
                }
                FilledIconButton(onClick = {
                    pressed = true
                    feedback.recordAccepted(hero.first.mediaId)
                    open(hero.second)
                }, modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)) { Icon(Icons.Outlined.PlayArrow, "Play predicted video") }
            }
        }
        if (ranked.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ranked.drop(1), key = { it.first.mediaId }) { (prediction, video) ->
                Column(Modifier.width(184.dp).clickable { open(video) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp))) { PulseThumb(video, Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(7.dp)); Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall); Text(predictionReason(prediction), color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable private fun PulseConfidence(value: Float) {
    Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalAlignment = Alignment.End) {
            Text("CONFIDENCE", fontSize = 7.sp, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(value * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun PulseContinue(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { PulseSection("Continue", "Your unfinished stories are still warm."); LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(videos, key = { it.id }) { PulseCard(it, open, favorite, true) } } }
}

@Composable private fun PulseTrail(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { PulseSection("Your trail", "The things that have been holding your attention."); LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(videos, key = { it.id }) { PulseCard(it, open, favorite, false) } } }
}

@Composable private fun PulseCollections(folders: List<Pair<String, Int>>, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PulseSection("Spaces", "Your folders, turned into places to return to.")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(folders) { (path, count) ->
                Card(Modifier.width(192.dp).clickable { open(path) }, RoundedCornerShape(23.dp)) {
                    Column(Modifier.padding(17.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = .11f)) { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(10.dp)) }
                        Spacer(Modifier.height(17.dp)); Text(path.trimEnd('/').substringAfterLast('/').ifBlank { "Unsorted" }, style = MaterialTheme.typography.titleMedium, maxLines = 1); Text("$count videos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun PulseOrbit(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PulseSection("Orbit", "Everything Pulse can draw from.")
        videos.take(16).forEach { video ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize().clickable { open(video) }, RoundedCornerShape(21.dp)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PulseThumb(video, Modifier.size(width = 116.dp, height = 72.dp).clip(RoundedCornerShape(15.dp)))
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall); video.folderName?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp) } }
                    IconButton(onClick = { favorite(video.id) }) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save") }
                }
            }
        }
    }
}

@Composable private fun PulseEmpty(library: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(28.dp)) {
        Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .10f), MaterialTheme.colorScheme.secondary.copy(alpha = .08f))))) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp).size(26.dp)) }
                Spacer(Modifier.height(14.dp)); Text("Nothing in orbit yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp)); Text("Bring in a local video and Pulse will start finding your rhythm.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(9.dp)); TextButton(onClick = { library("") }) { Text("OPEN LIBRARY") }
            }
        }
    }
}

@Composable private fun PulseSection(title: String, subtitle: String) { Column(Modifier.padding(horizontal = 16.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) } }

@Composable private fun PulseCard(video: VideoItem, open: (VideoItem) -> Unit, favorite: (String) -> Unit, progress: Boolean) {
    Column(Modifier.width(194.dp).clickable { open(video) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(21.dp))) {
            PulseThumb(video, Modifier.fillMaxSize())
            if (progress) LinearProgressIndicator(progress = { video.progress }, Modifier.fillMaxWidth().align(Alignment.BottomCenter))
            IconButton(onClick = { favorite(video.id) }, Modifier.align(Alignment.TopEnd)) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save", tint = Color.White) }
        }
        Spacer(Modifier.height(7.dp)); Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall); video.folderName?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp) }
    }
}

@Composable private fun PulseThumb(video: VideoItem, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(video.id, video.uri) { bitmap = runCatching { VideoThumbnailLoader.load(context, video.uri, 480, 270) }.getOrNull() }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), video.title, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF181823), Color(0xFF2C2050)))))
}

private fun predictionReason(prediction: VideoPrediction): String = when {
    VideoPrediction.Reason.SAME_FOLDER_CONTINUATION in prediction.reasons -> "A natural continuation"
    VideoPrediction.Reason.RESUMEABLE in prediction.reasons -> "You left this unfinished"
    VideoPrediction.Reason.REWATCHED in prediction.reasons -> "You've returned here before"
    VideoPrediction.Reason.RECENTLY_ENGAGED in prediction.reasons -> "Recent engagement"
    VideoPrediction.Reason.PREVIOUSLY_COMPLETED in prediction.reasons -> "A familiar favorite"
    VideoPrediction.Reason.RECENT_ABANDONMENT in prediction.reasons -> "Worth another look"
    else -> "A natural next step"
}
