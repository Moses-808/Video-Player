package com.innotrepid.videoplayer

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innotrepid.videoplayer.intelligence.VideoPrediction
import com.innotrepid.videoplayer.intelligence.VideoPredictionFeedbackStore
import com.innotrepid.videoplayer.library.VideoItem
import com.innotrepid.videoplayer.library.VideoThumbnailLoader

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
    val learnedHero = ranked.firstOrNull()?.takeIf { feedback.adjustedConfidence(it.first.mediaId, it.first.confidence) >= .45f }
    val defaultHeroVideo = videos
        .filter { it.durationMs >= 5 * 60 * 1000L }
        .maxByOrNull { it.durationMs }
    val heroPrediction = learnedHero ?: defaultHeroVideo?.let { video ->
        VideoPrediction(
            mediaId = video.id,
            score = 0f,
            confidence = 0f,
            reasons = emptyList(),
            previewPositionMs = null
        )
    }?.let { prediction -> prediction to (byId[prediction.mediaId] ?: defaultHeroVideo) }
    val heroVideo = heroPrediction?.second
    val isDefaultHero = learnedHero == null && heroVideo != null
    val heroConfidence = heroPrediction?.let { feedback.adjustedConfidence(it.first.mediaId, it.first.confidence) } ?: 0f

    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { PulseIdentity(videos.size, recent.firstOrNull(), heroPrediction, heroConfidence, open) }
        if (!permission) item { PulseConnectCard(request) }
        if (heroPrediction != null && heroVideo != null) item { VisonatePredictionHero(heroPrediction, heroVideo, heroConfidence, open, feedback, isDefaultHero) }
        if (resume.isNotEmpty()) item { VisonateShelf("Continue your thread", "Unfinished videos stay close.", resume, open, favorite, true) }
        if (recent.isNotEmpty()) item { VisonateShelf("Your trail", "Recent attention, kept within reach.", recent, open, favorite, false) }
        if (folders.isNotEmpty()) item { VisonateSpaces(folders, videos, library) }
        if (videos.isNotEmpty()) item { VisonateOrbit(videos, open, favorite) }
        if (videos.isEmpty()) item { VisonateEmpty(library) }
    }
}

@Composable private fun PulseIdentity(videoCount: Int, recent: VideoItem?, prediction: Pair<VideoPrediction, VideoItem>?, confidence: Float, open: (VideoItem) -> Unit) {
    Box(Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(34.dp)).background(Brush.linearGradient(listOf(Color(0xFF07070C), Color(0xFF20123A), Color(0xFF063B43))))) {
        if (recent != null) {
            VisonateThumb(recent, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF507080D)))))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xD4080810), Color.Transparent))))
        }
        Column(Modifier.fillMaxSize().padding(23.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp))
                Text("VISONATE", color = Color.White, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = .34f)) { Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondary)); Spacer(Modifier.width(6.dp)); Text("MOMENTUM LIVE", color = Color.White.copy(alpha = .78f), fontSize = 7.sp, letterSpacing = 1.sp) } }
            }
            Column {
                Text(if (prediction != null) "Your next move is\nalready taking shape." else if (recent != null) "Stay in\nyour flow." else "Your watchspace\nis waiting.", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp)); Text(if (prediction != null) "Momentum has a ${(confidence * 100).toInt()}% read on what comes next." else if (recent != null) "$videoCount videos • your trail is becoming a signal." else "$videoCount videos ready to become a rhythm.", color = Color.White.copy(alpha = .66f), fontSize = 10.sp)
                if (recent != null) { Spacer(Modifier.height(10.dp)); Surface(onClick = { open(recent) }, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .13f), contentColor = Color.White) { Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Continue", fontSize = 11.sp) } } }
            }
        }
    }
}
