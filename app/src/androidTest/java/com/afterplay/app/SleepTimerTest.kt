package com.afterplay.app

import android.content.ComponentName
import android.os.PowerManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.playback.PlaybackService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SleepTimerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as AfterPlayApp

    @Test fun presetsCanBeChangedAndCancelledThroughScreen() {
        try {
            waitUntil { app.sleepTimer.runCatching { startPreset(20) }.isSuccess }
            instrumentation.runOnMainSync { app.sleepTimer.cancel() }
            for (minutes in listOf(20, 40, 60)) {
                compose.onNodeWithContentDescription("수면 모드 설정").performClick()
                compose.onNodeWithText("20분").assertIsDisplayed()
                compose.onNodeWithText("40분").assertIsDisplayed()
                compose.onNodeWithText("60분").assertIsDisplayed()
                compose.onNodeWithText("${minutes}분").performClick()
                instrumentation.runOnMainSync {
                    assertTrue(app.sleepTimer.active)
                    assertTrue(app.sleepTimer.remainingMs in (minutes * 60_000L - 3_000)..(minutes * 60_000L))
                }
            }
            compose.onNodeWithContentDescription("수면 타이머 해제").performClick()
            instrumentation.runOnMainSync { assertFalse(app.sleepTimer.active) }
        } finally {
            instrumentation.runOnMainSync { app.sleepTimer.cancel() }
        }
    }

    @Test fun expiresWithScreenOffAndSavesResumePosition() {
        val context = instrumentation.targetContext
        val sampleRate = 8_000
        val samples = sampleRate * 30
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
        wav.putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { wav.putShort(0) }
        val file = File(context.cacheDir, "sleep-timer-test.wav").apply { writeBytes(wav.array()) }
        val future = MediaController.Builder(context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.Builder().setMediaId("sleep-timer-test")
                    .setUri(file.toURI().toString()).build())
                controller.repeatMode = Player.REPEAT_MODE_ONE
                controller.shuffleModeEnabled = true
                controller.prepare(); controller.play()
            }
            waitUntil { controller.isPlaying }
            instrumentation.runOnMainSync { app.sleepTimer.schedule(3_500) }
            compose.activityRule.scenario.onActivity { it.moveTaskToBack(true) }
            instrumentation.uiAutomation.executeShellCommand("input keyevent 223").close()
            waitUntil { !context.getSystemService(PowerManager::class.java).isInteractive }
            waitUntil { !app.sleepTimer.active && controller.playbackState == Player.STATE_IDLE }
            instrumentation.runOnMainSync {
                assertFalse(controller.isPlaying)
                assertFalse(controller.playWhenReady)
                assertEquals("sleep-timer-test", controller.currentMediaItem?.mediaId)
                assertEquals(Player.REPEAT_MODE_ONE, controller.repeatMode)
                assertTrue(controller.shuffleModeEnabled)
                val checkpoint = requireNotNull(app.playbackStore.checkpoint())
                assertEquals("sleep-timer-test", checkpoint.items[checkpoint.index].id)
                assertTrue("Stopped playback should preserve its elapsed position", checkpoint.positionMs in 2_000..7_000)
            }
        } finally {
            instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
            instrumentation.runOnMainSync {
                app.sleepTimer.cancel()
                controller.pause(); controller.stop(); controller.clearMediaItems()
                MediaController.releaseFuture(future)
            }
            file.delete()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < until) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Sleep timer or player did not reach the expected state")
    }
}
