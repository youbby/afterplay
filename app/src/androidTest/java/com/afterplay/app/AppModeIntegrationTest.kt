package com.afterplay.app

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.afterplay.app.data.AppMode
import com.afterplay.app.data.AppModeStore
import com.afterplay.app.data.PomodoroConfig
import com.afterplay.app.playback.PlaybackService
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppModeIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as AfterPlayApp
    private val device get() = UiDevice.getInstance(instrumentation)
    private var oldAutoRotation: String? = null

    @Before fun prepare() {
        oldAutoRotation = device.executeShellCommand("settings get system accelerometer_rotation").trim()
        instrumentation.runOnMainSync {
            app.focusGuard.unlockAfterAuthentication()
            app.pomodoro.configure(PomodoroConfig())
        }
        selectAppModeForTest(compose, app, AppMode.PLAYBACK)
    }

    @After fun restore() {
        device.unfreezeRotation()
        oldAutoRotation?.let {
            if (it == "0" || it == "1") device.executeShellCommand("settings put system accelerometer_rotation $it")
            else device.executeShellCommand("settings delete system accelerometer_rotation")
        }
        instrumentation.runOnMainSync { app.focusGuard.unlockAfterAuthentication(); app.pomodoro.reset() }
        selectAppModeForTest(compose, app, AppMode.PLAYBACK)
    }

    @Test fun buttonSelectsPersistentModeIndependentlyOfSystemRotationWithoutStartingTimer() {
        device.executeShellCommand("settings put system accelerometer_rotation 0")
        device.setOrientationLeft()
        awaitAppModeForTest(compose, app, AppMode.PLAYBACK)
        compose.onNodeWithTag("landscape_workspace").assertDoesNotExist()
        assertButtonsAreAdjacent()
        capture("app-mode-playback.png")
        compose.onNodeWithContentDescription("뽀모도로 모드로 전환").performClick()
        awaitAppModeForTest(compose, app, AppMode.POMODORO)
        assertEquals(AppMode.POMODORO, AppModeStore(context).state.value)
        compose.onNodeWithTag("landscape_workspace").assertIsDisplayed()
        compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("25:00")
        assertFalse("Selecting the mode must not start a timer", app.pomodoro.state.value.running)
        assertButtonsAreAdjacent()
        device.setOrientationNatural()
        awaitAppModeForTest(compose, app, AppMode.POMODORO)
        compose.activityRule.scenario.recreate()
        awaitAppModeForTest(compose, app, AppMode.POMODORO)
        compose.onNodeWithTag("landscape_workspace").assertIsDisplayed()
        device.executeShellCommand("settings put system accelerometer_rotation 1")
        device.setOrientationRight()
        awaitAppModeForTest(compose, app, AppMode.POMODORO)
        assertFalse(app.pomodoro.state.value.running)
        capture("app-mode-pomodoro.png")
        compose.onNodeWithContentDescription("재생 모드로 전환").performClick()
        awaitAppModeForTest(compose, app, AppMode.PLAYBACK)
        assertEquals(AppMode.PLAYBACK, AppModeStore(context).state.value)
        compose.onNodeWithTag("landscape_workspace").assertDoesNotExist()
        device.setOrientationLeft()
        awaitAppModeForTest(compose, app, AppMode.PLAYBACK)
        compose.onNodeWithTag("pomodoro_panel").assertDoesNotExist()
        assertFalse(app.pomodoro.state.value.running)
    }

    @Test fun switchingModesPreservesMusicAndFocusButIsBlockedWhileTouchLocked() {
        Assume.assumeFalse(app.focusGuard.requiresAuthentication)
        val samples = 8_000 * 120
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
        wav.putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        val sample = File(context.cacheDir, "app-mode-test.wav").apply { writeBytes(wav.array()) }
        val future = MediaController.Builder(context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.fromUri(sample.toURI().toString()))
                controller.prepare(); controller.play()
            }
            compose.waitUntil(10_000) {
                var playing = false
                instrumentation.runOnMainSync { playing = controller.isPlaying }
                playing
            }
            compose.onNodeWithTag("app_mode_toggle").performClick()
            awaitAppModeForTest(compose, app, AppMode.POMODORO)
            val modeButton = compose.onNodeWithTag("app_mode_toggle").fetchSemanticsNode().boundsInRoot.center
            compose.onNodeWithTag("pomodoro_toggle").performClick()
            compose.onNodeWithTag("app_mode_toggle").assertDoesNotExist()
            val origin = compose.onNodeWithTag("focus_touch_blocker").fetchSemanticsNode().boundsInRoot.topLeft
            compose.onNodeWithTag("focus_touch_blocker").performTouchInput { click(modeButton - origin) }
            compose.activityRule.scenario.onActivity {
                ViewModelProvider(it)[PlayerViewModel::class.java].setAppMode(AppMode.PLAYBACK)
            }
            assertEquals(AppMode.POMODORO, app.appMode.state.value)
            assertTrue(app.focusGuard.locked.value)
            val deadline = app.pomodoro.state.value.deadlineMs
            assertNotNull(deadline)
            compose.onNodeWithTag("focus_unlock").performClick()
            compose.onNodeWithTag("app_mode_toggle").assertIsDisplayed().performClick()
            awaitAppModeForTest(compose, app, AppMode.PLAYBACK)
            assertTrue(app.pomodoro.state.value.running)
            assertEquals(deadline, app.pomodoro.state.value.deadlineMs)
            instrumentation.runOnMainSync {
                assertTrue("Changing modes must not pause music", controller.isPlaying)
                assertEquals(sample.toURI().toString(), controller.currentMediaItem?.localConfiguration?.uri.toString())
            }
            compose.onNodeWithTag("app_mode_toggle").performClick()
            awaitAppModeForTest(compose, app, AppMode.POMODORO)
            assertEquals(deadline, app.pomodoro.state.value.deadlineMs)
            assertFalse(app.focusGuard.locked.value)
            compose.onNodeWithTag("pomodoro_toggle").assertContentDescriptionEquals("뽀모도로 일시정지").performClick()
            assertFalse(app.pomodoro.state.value.running)
        } finally {
            instrumentation.runOnMainSync {
                app.focusGuard.unlockAfterAuthentication(); app.pomodoro.reset()
                controller.pause(); controller.stop(); controller.clearMediaItems()
                MediaController.releaseFuture(future)
            }
            sample.delete()
        }
    }

    private fun assertButtonsAreAdjacent() {
        val lock = compose.onNodeWithTag("focus_lock").fetchSemanticsNode().boundsInRoot
        val mode = compose.onNodeWithTag("app_mode_toggle").fetchSemanticsNode().boundsInRoot
        val palette = compose.onNodeWithContentDescription("앱 설정").fetchSemanticsNode().boundsInRoot
        assertEquals(lock.center.y, mode.center.y, 2f)
        assertTrue(mode.left >= lock.right - 1f && mode.right <= palette.left + 1f)
        assertTrue("The mode button should touch the lock button's layout slot", mode.left - lock.right <= 2f)
    }

    private fun capture(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        } } finally { bitmap.recycle() }
    }
}
