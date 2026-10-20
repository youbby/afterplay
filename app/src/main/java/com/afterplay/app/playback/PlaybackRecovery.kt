package com.afterplay.app.playback

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/** The listener owns the retry budget; disable ExoPlayer's additional loader retries. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(0) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long = C.TIME_UNSET
}

/** Three attempts per item, with at most one automatic skip until audio actually plays. */
internal class PlaybackRecovery(
    private val player: Player,
    private val handler: Handler,
    private val invalidate: (String) -> Unit,
) : Player.Listener {
    private var failures = 0
    private var skippedFailedItem = false
    private var pending: Runnable? = null
    private var recovering = false

    override fun onPlayerError(error: PlaybackException) {
        player.currentMediaItem?.mediaId?.let(invalidate)
        if (!player.playWhenReady) return
        cancelPending()
        failures++
        // A failed item must not trap recovery in repeat-one mode; preserve shuffle order.
        val nextIndex = player.currentTimeline.getNextWindowIndex(player.currentMediaItemIndex,
            if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode,
            player.shuffleModeEnabled).takeIf {
            it != C.INDEX_UNSET && it != player.currentMediaItemIndex
        }
        val skip = failures >= 3
        if (skip && (skippedFailedItem || nextIndex == null)) {
            // Keep the terminal error visible and allow the play button to start a fresh budget.
            player.pause()
            reset()
            return
        }
        val failedItem = player.currentMediaItem
        val failedIndex = player.currentMediaItemIndex
        pending = Runnable {
            pending = null
            if (!player.playWhenReady || player.playerError == null ||
                player.currentMediaItem != failedItem || player.currentMediaItemIndex != failedIndex) {
                reset()
                return@Runnable
            }
            recovering = true
            try {
                if (skip) {
                    skippedFailedItem = true
                    failures = 0
                    player.seekToDefaultPosition(requireNotNull(nextIndex))
                }
                // Preparing an errored player retains the position for retries of the same item.
                player.prepare()
            } finally {
                recovering = false
            }
        }.also { handler.postDelayed(it, 1_000) }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) reset()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!recovering) reset()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) reset()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (!recovering && (playbackState == Player.STATE_BUFFERING ||
                (playbackState == Player.STATE_IDLE && player.playerError == null))) reset()
    }

    private fun cancelPending() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }

    private fun reset() {
        cancelPending()
        failures = 0
        skippedFailedItem = false
    }
}
