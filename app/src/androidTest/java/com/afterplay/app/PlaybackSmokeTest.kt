package com.afterplay.app

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afterplay.app.playback.PlaybackService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaybackSmokeTest {
    @Test fun audioContinuesAfterActivityIsBackgrounded() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val sampleRate = 16000
        val samples = sampleRate * 20
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
        wav.putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { wav.putShort((kotlin.math.sin(it * 2.0 * Math.PI * 440 / sampleRate) * 1000).toInt().toShort()) }
        val file = File(context.cacheDir, "background-test.wav").apply { writeBytes(wav.array()) }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                // This test verifies ordered auto-advance, independently of persisted user modes.
                controller.repeatMode = Player.REPEAT_MODE_OFF
                controller.shuffleModeEnabled = false
                controller.setMediaItems(listOf(MediaItem.fromUri(file.toURI().toString()), MediaItem.fromUri(file.toURI().toString())))
                controller.prepare(); controller.play()
            }
            waitUntil { controller.playbackState == Player.STATE_READY && controller.isPlaying }
            scenario.onActivity { it.moveTaskToBack(true) }
            var before = 0L
            instrumentation.runOnMainSync { before = controller.currentPosition }
            Thread.sleep(2000)
            instrumentation.runOnMainSync {
                assertTrue(controller.isPlaying)
                assertTrue(controller.currentPosition > before + 1000)
                controller.seekTo(19_000)
            }
            waitUntil { controller.currentMediaItemIndex == 1 && controller.isPlaying }
            instrumentation.runOnMainSync { controller.seekToPreviousMediaItem() }
            // MediaController commands are acknowledged asynchronously by the session.
            waitUntil { controller.currentMediaItemIndex == 0 &&
                controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
            instrumentation.runOnMainSync { controller.seekToNextMediaItem() }
            waitUntil { controller.currentMediaItemIndex == 1 }
            instrumentation.runOnMainSync {
                controller.pause()
                assertFalse(controller.playWhenReady)
            }
        } finally {
            instrumentation.runOnMainSync { controller.stop(); controller.clearMediaItems(); MediaController.releaseFuture(future) }
            scenario.close()
            file.delete()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val until = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < until) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Player did not reach ready/playing within 15 seconds")
    }
}
