package com.innotrepid.videoplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
internal fun VideoPredictionPulse(
    videos: List<VideoItem>,
    predictions: List<VideoPrediction>,
    open: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val feedback = remember { VideoPredictionFeedbackStore(context.applicationContext) }
    val byId = videos.associateBy { it.id }
    val ranked = predictions.mapNotNull { prediction -> byId[prediction.mediaId]?.let { prediction to it } }.take(5)
    if (ranked.isEmpty()) return

    val hero = ranked.first()
    val adjustedHeroConfidence = feedback.adjustedConfidence(hero.first.mediaId, hero.first.confidence)
    if (adjustedHeroConfidence < 0.45f) return

    LaunchedEffect(hero.first.mediaId) {
        feedback.recordShown(hero.first.mediaId)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("I think you'll watch this next", style = MaterialTheme.typography.titleMedium)
                Text(predictionReason(hero.first), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        VideoPredictionHero(hero.second, hero.first, feedback, open)
        if (ranked.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ranked.drop(1), key = { it.first.mediaId }) { (prediction, video) -> VideoPredictionCard(prediction, video, open) }
            }
        }
    }
}

@Composable
private fun VideoPredictionHero(
    video: VideoItem,
    prediction: VideoPrediction,
    feedback: VideoPredictionFeedbackStore,
    open: (VideoItem) -> Unit
) {
    fun accept() {
        feedback.recordAccepted(prediction.mediaId)
        open(video)
    }

    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp)).clickable { accept() }) {
        VideoPredictionPreview(video, Modifier.fillMaxSize(), prediction.previewPositionMs)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text("PREDICTED NEXT", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(5.dp))
            Text(video.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(predictionReason(prediction), color = Color.White.copy(alpha = .72f), fontSize = 11.sp, maxLines = 2)
        }
        FilledIconButton(onClick = { accept() }, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = "Play")
        }
    }
}

@Composable
private fun VideoPredictionCard(prediction: VideoPrediction, video: VideoItem, open: (VideoItem) -> Unit) {
    Column(Modifier.width(190.dp).clickable { open(video) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp))) { PredictionThumb(video, Modifier.fillMaxSize()) }
        Spacer(Modifier.height(7.dp))
        Text(video.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
        Text(predictionReason(prediction), color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun PredictionThumb(video: VideoItem, modifier: Modifier) {
    val context = LocalContext.current
    val bitmapState = remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val bitmap = bitmapState.value
    LaunchedEffect(video.id, video.uri) {
        bitmapState.value = runCatching { VideoThumbnailLoader.load(context, video.uri, 480, 270) }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = video.title, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))))
    }
}

private fun predictionReason(prediction: VideoPrediction): String = when {
    VideoPrediction.Reason.SAME_FOLDER_CONTINUATION in prediction.reasons -> "It follows the pattern of what you were watching."
    VideoPrediction.Reason.RESUMEABLE in prediction.reasons -> "You already started this one."
    VideoPrediction.Reason.REWATCHED in prediction.reasons -> "You've returned to this video before."
    VideoPrediction.Reason.RECENTLY_ENGAGED in prediction.reasons -> "You've been engaging with it recently."
    VideoPrediction.Reason.PREVIOUSLY_COMPLETED in prediction.reasons -> "You've watched it before."
    else -> "It is next in your natural viewing order."
}
