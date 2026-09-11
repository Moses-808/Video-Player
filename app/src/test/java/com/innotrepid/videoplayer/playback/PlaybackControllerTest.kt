package com.innotrepid.videoplayer.playback

import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test
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
