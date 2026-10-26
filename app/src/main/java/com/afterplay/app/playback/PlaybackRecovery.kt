package com.afterplay.app.playback

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/** Three attempts per item, with at most one automatic skip until audio actually plays. */
internal class PlaybackRecovery(
    private val player: Player,
    private val handler: Handler,
    private val invalidate: (String) -> Unit,
    private val failureAttempts: (PlaybackException) -> Int = { 1 },
    private val onRecovery: (String) -> Unit = {},
) : Player.Listener {
    private var failures = 0
    private var skippedFailedItem = false
    private var pending: Runnable? = null
    private var recovering = false

    override fun onPlayerError(error: PlaybackException) {
        player.currentMediaItem?.mediaId?.let(invalidate)
        if (!player.playWhenReady) return
        cancelPending()
        failures = (failures + failureAttempts(error)).coerceAtMost(3)
        // A failed item must not trap recovery in repeat-one mode; preserve shuffle order.
        val nextIndex = player.currentTimeline.getNextWindowIndex(player.currentMediaItemIndex,
            if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode,
            player.shuffleModeEnabled).takeIf {
            it != C.INDEX_UNSET && it != player.currentMediaItemIndex
        }
        val skip = failures >= 3
        if (skip && (skippedFailedItem || nextIndex == null)) {
            onRecovery("연결 시도 $failures/3 · 자동 복구 중단")
            // Keep the terminal error visible and allow the play button to start a fresh budget.
            player.pause()
            reset()
            return
        }
        val failedItem = player.currentMediaItem
        onRecovery("연결 시도 $failures/3 · ${if (skip) "다음 곡으로 이동" else "1초 후 다시 연결"}")
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
