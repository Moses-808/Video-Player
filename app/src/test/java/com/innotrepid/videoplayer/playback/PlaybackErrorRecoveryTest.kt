package com.innotrepid.videoplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class PlaybackErrorRecoveryTest {
    @Test
    fun decoderFailureDoesNotRetry() {
        val player = mock(ExoPlayer::class.java)
        val controller = PlaybackController(player)
        val listener = ArgumentCaptor.forClass(Player.Listener::class.java)
        verify(player).addListener(listener.capture())
        val uri = mock(Uri::class.java)
        controller.setMedia(uri, autoPlay = false)
        val mediaItem = captureMediaItem(player)
        listener.value.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        clearInvocations(player)

        val error = mock(PlaybackException::class.java)
        doReturn("ERROR_CODE_DECODER_INIT_FAILED").`when`(error).errorCodeName
        doReturn("decoder initialization failed").`when`(error).message
        listener.value.onPlayerError(error)

        controller.retry()

        verify(player, org.mockito.Mockito.never()).prepare()
        verify(player, org.mockito.Mockito.never()).playWhenReady = true
        assertEquals("ERROR_CODE_DECODER_INIT_FAILED: decoder initialization failed", controller.state.value.errorMessage)
        controller.release()
    }

    @Test
    fun networkFailureCanRetryOnce() {
        val player = mock(ExoPlayer::class.java)
        val controller = PlaybackController(player)
        val listener = ArgumentCaptor.forClass(Player.Listener::class.java)
        verify(player).addListener(listener.capture())
        val uri = mock(Uri::class.java)
        controller.setMedia(uri, autoPlay = false)
        val mediaItem = captureMediaItem(player)
        doReturn(mediaItem).`when`(player).currentMediaItem
        listener.value.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        clearInvocations(player)

        val error = mock(PlaybackException::class.java)
        doReturn("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED").`when`(error).errorCodeName
        doReturn("network unavailable").`when`(error).message
        listener.value.onPlayerError(error)

        controller.retry()

        verify(player).prepare()
        verify(player).playWhenReady = true
        assertEquals(null, controller.state.value.errorMessage)
        controller.release()
    }

    private fun captureMediaItem(player: ExoPlayer): MediaItem {
        val captor = ArgumentCaptor.forClass(MediaItem::class.java)
        verify(player).setMediaItem(captor.capture(), org.mockito.ArgumentMatchers.eq(0L))
        return captor.value
    }
}
