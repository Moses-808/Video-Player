package com.innotrepid.videoplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innotrepid.videoplayer.intelligence.VideoPrediction
import com.innotrepid.videoplayer.intelligence.VideoPredictionFeedbackStore
import com.innotrepid.videoplayer.library.VideoItem

@Composable
internal fun VisonatePulse(
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
    val folders = videos.mapNotNull { it.folderKey }.distinct().sortedBy { it.lowercase() }
    val context = LocalContext.current
    val feedback = remember { VideoPredictionFeedbackStore(context.applicationContext) }
    val byId = remember(videos) { videos.associateBy { it.id } }
    val ranked = remember(predictions, videos) {
        predictions.mapNotNull { p -> byId[p.mediaId]?.let { p to it } }
            .sortedByDescending { feedback.adjustedConfidence(it.first.mediaId, it.first.confidence) }
            .take(5)
    }
    val heroPrediction = ranked.firstOrNull()?.takeIf { feedback.adjustedConfidence(it.first.mediaId, it.first.confidence) >= .45f }
    val heroVideo = heroPrediction?.second
    val heroConfidence = heroPrediction?.let { feedback.adjustedConfidence(it.first.mediaId, it.first.confidence) } ?: 0f

    LazyColumn(
        contentPadding = PaddingValues(top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            PulseIdentity(
                videoCount = videos.size,
                recent = recent.firstOrNull(),
                prediction = heroPrediction,
                confidence = heroConfidence,
                open = open
            )
        }
        if (!permission) item { PulseConnectCard(request) }
        if (heroPrediction != null && heroVideo != null) {
            item {
                VisonatePredictionHero(heroPrediction, heroVideo, heroConfidence, open, feedback)
            }
        }
        if (resume.isNotEmpty()) item { VisonateShelf("Continue your thread", "Unfinished videos stay close.", resume, open, favorite, true) }
        if (recent.isNotEmpty()) item { VisonateShelf("Your trail", "Recent attention, kept within reach.", recent, open, favorite, false) }
        if (folders.isNotEmpty()) item { VisonateSpaces(folders, videos, library) }
        if (videos.isNotEmpty()) item { VisonateOrbit(videos, open, favorite) }
        if (videos.isEmpty()) item { VisonateEmpty(library) }
    }
}

@Composable
private fun PulseIdentity(
    videoCount: Int,
    recent: VideoItem?,
    prediction: Pair<VideoPrediction, VideoItem>?,
    confidence: Float,
    open: (VideoItem) -> Unit
) {
    Box(
        Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF07070C), Color(0xFF20123A), Color(0xFF063B43))))
    ) {
        if (recent != null) {
            PulseThumb(recent, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF507080D)))))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xD4080810), Color.Transparent))))
        }
        Column(Modifier.fillMaxSize().padding(23.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("VISONATE", color = Color.White, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = .34f)) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondary))
                        Spacer(Modifier.width(6.dp)); Text("MOMENTUM LIVE", color = Color.White.copy(alpha = .78f), fontSize = 7.sp, letterSpacing = 1.sp)
                    }
                }
            }
            Column {
                Text(if (prediction != null) "Your next move is\nalready taking shape." else if (recent != null) "Stay in\nyour flow." else "Your watchspace\nis waiting.", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        prediction != null -> "Momentum has a $((confidence * 100).toInt())% read on what comes next."
                        recent != null -> "$videoCount videos • your trail is becoming a signal."
                        else -> "$videoCount videos ready to become a rhythm."
                    },
                    color = Color.White.copy(alpha = .66f), fontSize = 10.sp
                )
                if (recent != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(onClick = { open(recent) }, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .13f), contentColor = Color.White) {
                        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Continue", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisonatePredictionHero(
    prediction: Pair<VideoPrediction, VideoItem>,
    video: VideoItem,
    confidence: Float,
    open: (VideoItem) -> Unit,
    feedback: VideoPredictionFeedbackStore
) {
    val (model, _) = prediction
    LaunchedEffect(model.mediaId) { feedback.recordShown(model.mediaId) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .985f else 1f, tween(150), label = "visonatePredictionScale")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("THE NEXT MOVE", color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("I think this is next.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Not a list. A signal.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Column(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), horizontalAlignment = Alignment.End) {
                    Text("CONFIDENCE", fontSize = 7.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(confidence * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(292.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(31.dp)).clickable { pressed = true; feedback.recordAccepted(model.mediaId); open(video) }
        ) {
            VideoPredictionPreview(video, Modifier.fillMaxSize(), model.previewPositionMs)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .95f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(21.dp).padding(end = 72.dp)) {
                Text(predictionReason(model).uppercase(), color = MaterialTheme.colorScheme.secondary, fontSize = 8.sp, letterSpacing = 1.2.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(video.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(predictionExplanation(model), color = Color.White.copy(alpha = .63f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            FilledIconButton(onClick = { pressed = true; feedback.recordAccepted(model.mediaId); open(video) }, modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
                Icon(Icons.Outlined.PlayArrow, "Play predicted video")
            }
        }
    }
}

@Composable
private fun PulseConnectCard(request: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(23.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .11f)) { Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("Let Visonate see your library", style = MaterialTheme.typography.titleSmall); Text("Your videos stay on this device.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = request) { Text("CONNECT") }
        }
    }
}

@Composable
private fun VisonateShelf(title: String, subtitle: String, videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit, resume: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(videos, key = { it.id }) { PulseCard(it, open, favorite, resume) }
        }
    }
}

@Composable
private fun VisonateSpaces(folders: List<String>, videos: List<VideoItem>, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) { Text("Spaces", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text("Your folders, turned into places to return to.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(folders, key = { it }) { folder ->
                val count = videos.count { it.folderKey == folder }
                Card(Modifier.width(194.dp).clickable { open(folder) }, RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = .11f)) { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(10.dp)) }
                        Spacer(Modifier.height(15.dp)); Text(folderNameForPulse(folder), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("$count videos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisonateOrbit(videos: List<VideoItem>, open: (VideoItem) -> Unit, favorite: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) { Text("Orbit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text("Everything Visonate can draw from.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) }
        videos.take(16).forEach { video ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize().clickable { open(video) }, RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    PulseThumb(video, Modifier.size(width = 116.dp, height = 72.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall); video.folderName?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp) } }
                    IconButton(onClick = { favorite(video.id) }) { Icon(if (video.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save") }
                }
            }
        }
    }
}

@Composable
private fun VisonateEmpty(library: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(27.dp)) {
        Box(Modifier.fillMaxWidth().height(215.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .10f), MaterialTheme.colorScheme.secondary.copy(alpha = .08f))))) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(19.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(13.dp).size(25.dp)) }
                Spacer(Modifier.height(12.dp)); Text("Nothing in orbit yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp)); Text("Import a local video and Visonate will start learning your viewing rhythm.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp)); TextButton(onClick = { library("") }) { Text("OPEN LIBRARY") }
            }
        }
    }
}
