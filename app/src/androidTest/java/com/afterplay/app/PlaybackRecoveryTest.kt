package com.afterplay.app

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.playback.PlaybackLoadErrorPolicy
import com.afterplay.app.playback.PlaybackRecovery
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackRecoveryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun threeAttemptsPerItemThenStopsWithoutTryingThirdItem() = withPlayer { player, failures ->
        start(player, listOf(missing("first"), missing("second"), missing("third")))
        waitUntil { player.playerError != null && !player.playWhenReady }
        instrumentation.runOnMainSync {
            assertEquals(listOf("first", "first", "first", "second", "second", "second"), failures)
            assertEquals(1, player.currentMediaItemIndex)
        }
        // Explicitly starting again must receive a fresh budget.
        instrumentation.runOnMainSync { player.prepare(); player.play() }
        waitUntil { failures.size == 12 && !player.playWhenReady }
        instrumentation.runOnMainSync {
            assertEquals(listOf("second", "second", "second", "third", "third", "third"), failures.drop(6))
        }
    }

    @Test fun singleItemStopsAfterThreeAttemptsEvenWithRepeatOne() = withPlayer { player, failures ->
        instrumentation.runOnMainSync { player.repeatMode = Player.REPEAT_MODE_ONE }
        start(player, listOf(missing("only")))
        waitUntil { player.playerError != null && !player.playWhenReady }
        instrumentation.runOnMainSync { assertEquals(listOf("only", "only", "only"), failures) }
    }

    @Test fun repeatOneStillSkipsAFailedItem() = withPlayer { player, failures ->
        instrumentation.runOnMainSync { player.repeatMode = Player.REPEAT_MODE_ONE }
        start(player, listOf(missing("first"), missing("second"), missing("third")))
        waitUntil { player.playerError != null && !player.playWhenReady }
        instrumentation.runOnMainSync {
            assertEquals(listOf("first", "first", "first", "second", "second", "second"), failures)
            assertEquals(1, player.currentMediaItemIndex)
        }
    }

    @Test fun pauseCancelsPendingRetry() = withPlayer { player, failures ->
        start(player, listOf(missing("paused"), missing("next")))
        waitUntil { failures.size == 1 }
        instrumentation.runOnMainSync { player.pause() }
        Thread.sleep(1_500)
        instrumentation.runOnMainSync {
            assertEquals(listOf("paused"), failures)
            assertEquals(0, player.currentMediaItemIndex)
            assertFalse(player.playWhenReady)
        }
    }

    @Test fun successfulPlaybackResetsConsecutiveFailureBudget() = withPlayer { player, failures ->
        val audio = File(context.cacheDir, "recovery-test.wav")
        val samples = 8_000 * 3
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
        wav.putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { wav.putShort(0) }
        audio.writeBytes(wav.array())
        try {
            start(player, listOf(missing("first"), MediaItem.Builder().setMediaId("working")
                .setUri(audio.toURI().toString()).build(), missing("third"), missing("fourth")))
            waitUntil { player.isPlaying && player.currentMediaItemIndex == 1 }
            waitUntil { player.playerError != null && !player.playWhenReady }
            instrumentation.runOnMainSync {
                assertEquals(listOf("first", "first", "first", "third", "third", "third",
                    "fourth", "fourth", "fourth"), failures)
                assertEquals(3, player.currentMediaItemIndex)
            }
        } finally { audio.delete() }
    }

    private fun missing(id: String): MediaItem = MediaItem.Builder().setMediaId(id)
        .setUri(File(context.cacheDir, "does-not-exist-$id.wav").toURI().toString()).build()

    private fun start(player: ExoPlayer, items: List<MediaItem>) {
        instrumentation.runOnMainSync { player.setMediaItems(items); player.prepare(); player.play() }
    }

    private fun withPlayer(test: (ExoPlayer, MutableList<String>) -> Unit) {
        lateinit var player: ExoPlayer
        val failures = mutableListOf<String>()
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(context)
                .setLoadErrorHandlingPolicy(PlaybackLoadErrorPolicy())).build()
            player.addListener(PlaybackRecovery(player, Handler(Looper.getMainLooper()), failures::add))
        }
        try { test(player, failures) }
        finally { instrumentation.runOnMainSync { player.pause(); player.release() } }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(50)
        }
        fail("Playback recovery did not reach the expected state")
    }
}
