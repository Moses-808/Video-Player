package com.innotrepid.videoplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
internal fun PhaseBPulseMotion(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier,
        enter = fadeIn(animationSpec = tween(360)) +
            slideInVertically(
                animationSpec = tween(420),
                initialOffsetY = { it / 18 }
            )
    ) {
        content()
    }
}
