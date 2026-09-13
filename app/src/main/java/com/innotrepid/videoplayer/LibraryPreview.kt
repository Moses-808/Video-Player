package com.innotrepid.videoplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innotrepid.videoplayer.intelligence.VideoPreviewMoment
import com.innotrepid.videoplayer.library.VideoItem

@Composable
internal fun LibraryPreviewSurface(
    video: VideoItem,
    modifier: Modifier = Modifier,
    previewPositionMs: Long? = null
) {
    Box(modifier.clip(RoundedCornerShape(17.dp))) {
        VideoPredictionPreview(
            video = video,
            modifier = Modifier.fillMaxSize(),
            previewPositionMs = previewPositionMs ?: VideoPreviewMoment.startPositionMs(
                durationMs = video.durationMs,
                resumePositionMs = video.lastPositionMs
            )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = .08f),
                            Color.Transparent,
                            Color.Black.copy(alpha = .62f)
                        )
                    )
                )
        )
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(9.dp),
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = .58f),
            contentColor = Color.White
        ) {
            Text(
                "HOLD TO PREVIEW",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                fontSize = 8.sp,
                letterSpacing = 1.2.sp,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
