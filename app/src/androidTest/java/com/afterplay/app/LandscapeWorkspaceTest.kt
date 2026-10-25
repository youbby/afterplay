package com.afterplay.app

import android.content.pm.ActivityInfo
import android.content.ComponentName
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.afterplay.app.playback.PlaybackService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.After
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

    @Before @After fun restoreFocusState() {
        instrumentation.runOnMainSync {
            app.focusGuard.unlockAfterAuthentication()
            app.pomodoro.configure(com.afterplay.app.data.PomodoroConfig())
        }
    }

    private fun togglePomodoro() {
        compose.onNodeWithTag("pomodoro_toggle").performClick()
        // Other tests cover the real credential flow; this fixture isolates layout/timer behavior.
        instrumentation.runOnMainSync { app.focusGuard.unlockAfterAuthentication() }
        compose.waitForIdle()
    }

    @Test fun onlyRunningPomodoroKeepsScreenOnAndSelectedModeControlsOrientation() {
        try {
            instrumentation.runOnMainSync { app.pomodoro.reset() }
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            assertScreenKeptOn(false)
            compose.onNodeWithContentDescription("뽀모도로 실행 중 화면 켜짐 유지").assertDoesNotExist()
            togglePomodoro()
            assertScreenKeptOn(true)
            compose.onNodeWithContentDescription("뽀모도로 실행 중 화면 켜짐 유지").assertIsDisplayed()
            UiDevice.getInstance(instrumentation).setOrientationNatural()
            instrumentation.uiAutomation.waitForIdle(100, 3_000)
            assertLandscapeLocked()
            compose.onNodeWithContentDescription("다음 뽀모도로 단계").performClick()
            assertEquals(com.afterplay.app.data.PomodoroPhase.SHORT_BREAK, app.pomodoro.state.value.phase)
            assertScreenKeptOn(true)
            compose.activityRule.scenario.recreate()
            assertScreenKeptOn(true)
            assertLandscapeLocked()
            togglePomodoro()
            assertScreenKeptOn(false)
            compose.onNodeWithContentDescription("뽀모도로 실행 중 화면 켜짐 유지").assertDoesNotExist()
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            assertScreenKeptOn(false)
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            togglePomodoro()
            assertScreenKeptOn(true)
            compose.onNodeWithContentDescription("뽀모도로 초기화").performClick()
            assertScreenKeptOn(false)
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            assertScreenKeptOn(false)
        } finally {
            UiDevice.getInstance(instrumentation).unfreezeRotation()
            instrumentation.runOnMainSync { app.pomodoro.reset() }
        }
    }

    private fun assertLandscapeLocked() {
        compose.activityRule.scenario.onActivity {
            assertEquals(android.content.res.Configuration.ORIENTATION_LANDSCAPE, it.resources.configuration.orientation)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, it.requestedOrientation)
        }
    }

    private fun assertScreenKeptOn(expected: Boolean) {
        fun View.keepsScreenOn(): Boolean = keepScreenOn ||
            (this is ViewGroup && (0 until childCount).any { getChildAt(it).keepsScreenOn() })
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity {
            assertEquals("App view should reflect the effective screen timeout policy", expected,
                it.window.decorView.keepsScreenOn())
        }
    }

    @Test fun playerIsOnLeftWithClockAndPomodoroOnRight() {
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            compose.onNodeWithTag("landscape_workspace").assertIsDisplayed()
            val player = compose.onNodeWithTag("compact_player").fetchSemanticsNode().boundsInRoot
            val clock = compose.onNodeWithTag("wall_clock").fetchSemanticsNode().boundsInRoot
            assertTrue("Clock should be to the right of the compact player", clock.left >= player.right)
            val lock = compose.onNodeWithTag("focus_lock").fetchSemanticsNode().boundsInRoot
            val palette = compose.onNodeWithContentDescription("앱 설정").fetchSemanticsNode().boundsInRoot
            assertTrue("Lock belongs beside the player palette", lock.left >= player.left && lock.right <= palette.left)
            compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("25:00")
            compose.onNodeWithContentDescription("재생목록 열기").performClick()
            compose.onNodeWithText("내 재생목록").assertIsDisplayed()
            compose.onNodeWithTag("focus_lock").assertIsDisplayed()
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
            togglePomodoro()
            compose.waitUntil(5_000) { app.pomodoro.state.value.remainingMs < 25 * 60_000L - 1_000 }
            compose.activityRule.scenario.recreate()
            assertLandscapeLocked()
            assertTrue(app.pomodoro.state.value.running)
            compose.onNodeWithTag("pomodoro_toggle").assertContentDescriptionEquals("뽀모도로 일시정지")
            togglePomodoro()
            val paused = app.pomodoro.state.value.remainingMs
            Thread.sleep(1_200)
            assertEquals(paused, app.pomodoro.state.value.remainingMs)
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            compose.onNodeWithContentDescription("다음 뽀모도로 단계").performClick()
            compose.onNodeWithTag("pomodoro_phase").assertTextEquals("휴식")
            compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("05:00")
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
            compose.onNodeWithContentDescription("재생 종료").assertDoesNotExist()
            assertTrue(compose.onNodeWithTag("sleep_timer_controls").fetchSemanticsNode().boundsInRoot.bottom <=
                compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top)
            togglePomodoro()
            compose.waitUntil(5_000) { app.pomodoro.state.value.remainingMs < 25 * 60_000L - 1_000 }
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(context.getExternalFilesDir(null), "landscape-preview.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { screenshot.recycle() }
            togglePomodoro()
            instrumentation.runOnMainSync { assertTrue("Pausing focus must not pause music", controller.isPlaying) }
            togglePomodoro()
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
        selectAppModeForTest(compose, app, if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            com.afterplay.app.data.AppMode.POMODORO else com.afterplay.app.data.AppMode.PLAYBACK)
    }
}
