package com.afterplay.app

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.data.PomodoroConfig
import com.afterplay.app.data.ThemePreset
import com.afterplay.app.data.ThemeStore
import com.afterplay.app.playback.PlaybackService
import com.afterplay.app.playback.PomodoroTimer
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as AfterPlayApp

    @Before fun defaults() { restoreDefaults() }
    @After fun restoreDefaults() {
        instrumentation.runOnMainSync {
            app.pomodoro.configure(PomodoroConfig())
            app.appearance.select(ThemePreset.BURGUNDY)
        }
    }

    @Test fun durationSettingsValidatePersistAndResetCurrentTimer() {
        landscape()
        compose.onNodeWithContentDescription("뽀모도로 시간 설정").performClick()
        compose.onNodeWithTag("focus_minutes").performTextReplacement("0")
        compose.onNodeWithText("시간 저장").assertIsNotEnabled()
        compose.onNodeWithTag("focus_minutes").performTextReplacement("40")
        compose.onNodeWithTag("short_break_minutes").performScrollTo().performTextReplacement("7")
        compose.onNodeWithTag("long_break_minutes").performScrollTo().performTextReplacement("20")
        compose.onNodeWithTag("long_break_minutes").performImeAction()
        compose.onNodeWithText("시간 저장").performClick()
        compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("40:00")
        compose.onNodeWithTag("pomodoro_toggle").performClick()
        assertTrue(app.pomodoro.state.value.running)
        compose.onNodeWithContentDescription("뽀모도로 시간 설정").performClick()
        compose.onNodeWithTag("focus_minutes").performTextReplacement("50")
        compose.onNodeWithTag("focus_minutes").performImeAction()
        compose.onNodeWithText("시간 저장").performClick()
        assertFalse(app.pomodoro.state.value.running)
        compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("50:00")
        compose.onNodeWithContentDescription("뽀모도로 초기화").performClick()
        compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("50:00")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("50:00")
        instrumentation.runOnMainSync {
            assertEquals(PomodoroConfig(50, 7, 20), PomodoroTimer(context).state.value.config)
        }
    }

    @Test fun fivePresetsApplyPersistAndKeepFocusRunning() {
        landscape()
        val samples = 8_000 * 30
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
        wav.putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        val sample = File(context.cacheDir, "theme-preview.wav").apply { writeBytes(wav.array()) }
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.Builder().setMediaId("theme-preview").setUri(sample.toURI().toString())
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("차분한 피아노와 함께하는 집중 시간")
                        .setArtist("나만의 집중 플레이리스트").build()).build())
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("차분한 피아노와 함께하는 집중 시간").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("pomodoro_toggle").performClick()
            val deadline = app.pomodoro.state.value.deadlineMs
            for (preset in ThemePreset.entries) {
                compose.onNodeWithContentDescription("앱 설정").performClick()
                compose.onNodeWithTag("theme_${preset.name}").performScrollTo().performClick().assertIsSelected()
                assertEquals(preset, app.appearance.state.value)
                assertEquals(preset, ThemeStore(context).state.value)
                compose.onNodeWithText("완료").performClick()
                assertTrue(app.pomodoro.state.value.running)
                assertEquals(deadline, app.pomodoro.state.value.deadlineMs)
                compose.waitForIdle()
                capture("theme-${preset.name.lowercase()}.png")
            }
            compose.activityRule.scenario.recreate()
            assertEquals(ThemePreset.SLATE, app.appearance.state.value)
            assertEquals(deadline, app.pomodoro.state.value.deadlineMs)
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("landscape_workspace").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("내 재생목록").assertIsDisplayed()
            compose.waitForIdle()
            capture("theme-slate-portrait.png")
        } finally {
            instrumentation.runOnMainSync {
                controller.pause(); controller.stop(); controller.clearMediaItems()
                MediaController.releaseFuture(future)
            }
            sample.delete()
        }
    }

    private fun landscape() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("landscape_workspace").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
