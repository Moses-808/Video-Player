package com.innotrepid.videoplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PlaybackControllerTest {
    private val player = mock(ExoPlayer::class.java)

    @Test
    fun seekClampsNegativePosition() {
        val controller = PlaybackController(player)

        controller.seekTo(-500L)

        verify(player).seekTo(0L)
        controller.release()
    }

    @Test
    fun seekByClampsResultAtZero() {
        `when`(player.currentPosition).thenReturn(300L)
        val controller = PlaybackController(player)

        controller.seekBy(-1_000L)

        verify(player).seekTo(0L)
        controller.release()
    }

    @Test
    fun speedAndVolumeAreClampedToSafeRanges() {
        val controller = PlaybackController(player)

        controller.setSpeed(0f)
        controller.setSpeed(10f)
        controller.setVolume(-1f)
        controller.setVolume(2f)

        verify(player).setPlaybackSpeed(0.25f)
        verify(player).setPlaybackSpeed(4f)
        verify(player).volume = 0f
        verify(player).volume = 1f
        controller.release()
    }

    @Test
    fun toggleUsesCurrentPlayerState() {
        `when`(player.isPlaying).thenReturn(true)
        val controller = PlaybackController(player)
        controller.togglePlayPause()
        verify(player).pause()
        controller.release()

        val secondPlayer = mock(ExoPlayer::class.java)
        `when`(secondPlayer.isPlaying).thenReturn(false)
        val secondController = PlaybackController(secondPlayer)
        secondController.togglePlayPause()
        verify(secondPlayer).play()
        secondController.release()
    }

    @Test
    fun clearMediaStopsAndUnloadsWithoutReleasingController() {
        val controller = PlaybackController(player)

        controller.clearMedia()
        controller.play()

        verify(player).stop()
        verify(player).clearMediaItems()
        verify(player).play()
        controller.release()
    }

    @Test
    fun setMediaPassesPersistedPositionAndAutoplayToPlayer() {
        val uri = mock(Uri::class.java)
        val controller = PlaybackController(player)

        controller.setMedia(uri, startPositionMs = 42_000L, autoPlay = true)

        verify(player).setMediaItem(MediaItem.fromUri(uri), 42_000L)
        verify(player).prepare()
        verify(player).playWhenReady = true
        controller.release()
    }

    @Test
    fun clearMediaResetsPublishedPlaybackState() {
        `when`(player.isPlaying).thenReturn(true)
        `when`(player.currentPosition).thenReturn(15_000L)
        `when`(player.duration).thenReturn(120_000L)
        val controller = PlaybackController(player)

        controller.clearMedia()

        assertEquals(PlaybackUiState(), controller.state.value)
        assertEquals(null, controller.currentMediaUri())
        controller.release()
    }

    @Test
    fun lateCallbacksAfterClearDoNotResurrectPlaybackState() {
        val controller = PlaybackController(player)
        val listener = ArgumentCaptor.forClass(Player.Listener::class.java)
        verify(player).addListener(listener.capture())

        controller.clearMedia()
        listener.value.onIsPlayingChanged(true)
        listener.value.onPlaybackStateChanged(Player.STATE_ENDED)

        assertEquals(PlaybackUiState(), controller.state.value)
        controller.release()
    }

    @Test
    fun releaseIsIdempotentAndCommandsAfterReleaseAreIgnored() {
        val controller = PlaybackController(player)

        controller.release()
        controller.release()
        controller.play()
        controller.pause()
        controller.seekTo(10_000L)
        controller.clearMedia()
        controller.refresh()

        verify(player).release()
        verify(player, never()).play()
        verify(player, never()).pause()
        verify(player, never()).seekTo(10_000L)
        verify(player, never()).stop()
        verify(player, never()).clearMediaItems()
        assertEquals(0L, controller.currentPositionMs())
        assertEquals(0L, controller.durationMs())
    }
}
