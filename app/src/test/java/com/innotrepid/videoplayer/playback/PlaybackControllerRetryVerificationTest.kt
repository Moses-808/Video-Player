package com.innotrepid.videoplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PlaybackControllerRetryVerificationTest {
    @Test
    fun retryPreparesCurrentFailedMediaAndAutoplaysOnce() {
        val player = mock(ExoPlayer::class.java)
        val controller = PlaybackController(player)
        val listener = ArgumentCaptor.forClass(androidx.media3.common.Player.Listener::class.java)
        verify(player).addListener(listener.capture())
        `when`(player.currentMediaItem).thenReturn(MediaItem.fromUri(Uri.parse("content://video/1")))

        listener.value.onPlayerError(mock(PlaybackException::class.java))

        controller.retry()

        verify(player).prepare()
        verify(player).playWhenReady = true
        controller.release()
    }
}
