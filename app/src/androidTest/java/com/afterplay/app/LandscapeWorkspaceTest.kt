package com.afterplay.app

import android.content.pm.ActivityInfo
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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
class LandscapeWorkspaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as AfterPlayApp

    @Test fun playerIsOnLeftWithClockAndPomodoroOnRight() {
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            compose.onNodeWithTag("landscape_workspace").assertIsDisplayed()
            val player = compose.onNodeWithTag("compact_player").fetchSemanticsNode().boundsInRoot
            val clock = compose.onNodeWithTag("wall_clock").fetchSemanticsNode().boundsInRoot
            assertTrue("Clock should be to the right of the compact player", clock.left >= player.right)
            compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("25:00")
            compose.onNodeWithContentDescription("재생목록 열기").performClick()
            compose.onNodeWithText("내 재생목록").assertIsDisplayed()
            compose.onNodeWithContentDescription("현재 재생으로 돌아가기").performClick()
            compose.onNodeWithContentDescription("재생목록 열기").assertIsDisplayed()
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            compose.onNodeWithTag("landscape_workspace").assertDoesNotExist()
            compose.onNodeWithText("내 재생목록").assertIsDisplayed()
        } finally { instrumentation.runOnMainSync { app.pomodoro.reset() } }
    }

    @Test fun focusSurvivesRotationAndRecreationAndCanPauseSkipAndReset() {
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            compose.onNodeWithTag("pomodoro_toggle").performClick()
            compose.waitUntil(5_000) { app.pomodoro.state.value.remainingMs < 25 * 60_000L - 1_000 }
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            compose.activityRule.scenario.recreate()
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            assertTrue(app.pomodoro.state.value.running)
            compose.onNodeWithTag("pomodoro_toggle").assertTextEquals("일시정지").performClick()
            val paused = app.pomodoro.state.value.remainingMs
            Thread.sleep(1_200)
            assertEquals(paused, app.pomodoro.state.value.remainingMs)
            compose.onNodeWithContentDescription("다음 뽀모도로 단계").performClick()
            compose.onNodeWithTag("pomodoro_phase").assertTextEquals("휴식")
            compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("5:00")
            compose.onNodeWithContentDescription("뽀모도로 초기화").performClick()
            compose.onNodeWithTag("pomodoro_phase").assertTextEquals("집중")
            compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("25:00")
        } finally { instrumentation.runOnMainSync { app.pomodoro.reset() } }
    }

    @Test fun playbackAndFocusAreIndependentInLandscape() {
        val context = instrumentation.targetContext
        val samples = 8_000 * 30
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
        wav.putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        val sample = File(context.cacheDir, "landscape-test.wav").apply { writeBytes(wav.array()) }
        val future = MediaController.Builder(context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val title = "차분한 피아노와 함께하는 집중 시간"
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.Builder().setMediaId("landscape-test").setUri(sample.toURI().toString())
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("나만의 집중 플레이리스트").build()).build())
                controller.prepare(); controller.play()
            }
            compose.waitUntil(10_000) {
                var playing = false
                instrumentation.runOnMainSync { playing = controller.isPlaying }
                playing && compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("pomodoro_toggle").performClick()
            compose.waitUntil(5_000) { app.pomodoro.state.value.remainingMs < 25 * 60_000L - 1_000 }
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(context.getExternalFilesDir(null), "landscape-preview.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { screenshot.recycle() }
            compose.onNodeWithTag("pomodoro_toggle").performClick()
            instrumentation.runOnMainSync { assertTrue("Pausing focus must not pause music", controller.isPlaying) }
            compose.onNodeWithTag("pomodoro_toggle").performClick()
            compose.onNodeWithContentDescription("일시정지").performClick()
            compose.waitUntil(5_000) {
                var paused = false
                instrumentation.runOnMainSync { paused = !controller.playWhenReady }
                paused
            }
            assertTrue("Pausing music must not pause focus", app.pomodoro.state.value.running)
        } finally {
            instrumentation.runOnMainSync {
                app.pomodoro.reset()
                controller.pause(); controller.stop(); controller.clearMediaItems()
                MediaController.releaseFuture(future)
            }
            sample.delete()
        }
    }

    private fun rotate(orientation: Int) {
        instrumentation.runOnMainSync { app.pomodoro.refresh() }
        compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
        compose.waitUntil(10_000) {
            val landscape = compose.onAllNodesWithTag("landscape_workspace").fetchSemanticsNodes().isNotEmpty()
            landscape == (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        }
        compose.waitForIdle()
    }
}
