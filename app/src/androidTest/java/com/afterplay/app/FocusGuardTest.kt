package com.afterplay.app

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.afterplay.app.data.FocusGuard
import com.afterplay.app.data.PomodoroConfig
import com.afterplay.app.data.PomodoroPhase
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FocusGuardTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as AfterPlayApp
    private val device get() = UiDevice.getInstance(instrumentation)

    @Before @After fun restoreDefaults() {
        instrumentation.runOnMainSync {
            app.focusGuard.unlockAfterAuthentication()
            app.pomodoro.configure(PomodoroConfig())
        }
    }

    private fun landscape() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("landscape_workspace").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun noDeviceCredentialRequestsSetupWithoutStartingOrLocking() {
        Assume.assumeFalse(app.focusGuard.canLock)
        landscape()
        compose.onNodeWithTag("pomodoro_toggle").performClick()
        compose.onNodeWithText("화면 잠금을 먼저 설정해 주세요").assertIsDisplayed()
        assertFalse(app.pomodoro.state.value.running)
        assertFalse(app.focusGuard.locked.value)
        compose.onNodeWithText("취소").performClick()
    }

    @Test fun touchGuardRequiresActualDeviceCredentialAndSurvivesRecreation() {
        Assume.assumeTrue(app.focusGuard.canLock)
        landscape()
        val lockBounds = compose.onNodeWithTag("focus_lock").fetchSemanticsNode().boundsInRoot
        val playerBounds = compose.onNodeWithTag("compact_player").fetchSemanticsNode().boundsInRoot
        val clockBounds = compose.onNodeWithTag("clock_panel").fetchSemanticsNode().boundsInRoot
        val timerBounds = compose.onNodeWithTag("pomodoro_panel").fetchSemanticsNode().boundsInRoot
        val blockedTargets = listOf(
            compose.onNodeWithTag("pomodoro_toggle").fetchSemanticsNode().boundsInRoot.center,
            compose.onNodeWithContentDescription("뽀모도로 초기화").fetchSemanticsNode().boundsInRoot.center,
            compose.onNodeWithContentDescription("다음 뽀모도로 단계").fetchSemanticsNode().boundsInRoot.center,
            compose.onNodeWithContentDescription("뽀모도로 시간 설정").fetchSemanticsNode().boundsInRoot.center,
            compose.onNodeWithContentDescription("재생목록 열기").fetchSemanticsNode().boundsInRoot.center,
        )
        compose.onNodeWithTag("pomodoro_toggle").performClick()
        compose.onNodeWithTag("focus_unlock").assertIsDisplayed()
        assertEquals("Lock button must stay in the same place", lockBounds,
            compose.onNodeWithTag("focus_unlock").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("pomodoro_toggle").assertDoesNotExist()
        val blockerOrigin = compose.onNodeWithTag("focus_touch_blocker").fetchSemanticsNode().boundsInRoot.topLeft
        compose.onNodeWithTag("focus_touch_blocker").performTouchInput {
            blockedTargets.forEach { click(it - blockerOrigin) }
        }
        compose.onNodeWithTag("focus_touch_blocker").performTouchInput { click(center); swipeLeft() }
        assertTrue(app.pomodoro.state.value.running)
        assertEquals(PomodoroPhase.FOCUS, app.pomodoro.state.value.phase)
        device.pressBack()
        compose.onNodeWithTag("focus_unlock").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("focus_unlock").assertIsDisplayed()
        assertTrue(FocusGuard(context).locked.value)
        capture("focus-locked.png")

        compose.onNodeWithTag("focus_unlock").performClick()
        assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000))
        device.pressBack()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("focus_unlock").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("Canceling authentication must leave the guard locked", app.focusGuard.locked.value)
        compose.onNodeWithTag("focus_unlock").performClick()
        val pin = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000))
        // Only the isolated emulator has this test PIN; no app credential is stored.
        pin.text = "1234"
        device.pressEnter()
        compose.waitUntil(10_000) { !app.focusGuard.locked.value }
        assertFalse(FocusGuard(context).locked.value)
        assertTrue("Unlocking controls must not pause the timer", app.pomodoro.state.value.running)
        compose.onNodeWithTag("focus_lock").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "잠금 해제됨"))
        assertEquals(playerBounds, compose.onNodeWithTag("compact_player").fetchSemanticsNode().boundsInRoot)
        assertEquals(clockBounds, compose.onNodeWithTag("clock_panel").fetchSemanticsNode().boundsInRoot)
        assertEquals(timerBounds, compose.onNodeWithTag("pomodoro_panel").fetchSemanticsNode().boundsInRoot)
        assertEquals(lockBounds, compose.onNodeWithTag("focus_lock").fetchSemanticsNode().boundsInRoot)
        capture("focus-unlocked.png")
        compose.onNodeWithTag("focus_lock").performClick()
        compose.onNodeWithTag("focus_unlock").assertIsDisplayed()
        assertTrue(app.focusGuard.locked.value)
        // Authentication was exercised above; use the fixture to finish without a second PIN prompt.
        instrumentation.runOnMainSync { app.focusGuard.unlockAfterAuthentication() }
        compose.onNodeWithTag("pomodoro_toggle").assertIsDisplayed().performClick()
        assertFalse(app.pomodoro.state.value.running)
    }

    @Test fun realFocusExpiryFlashesOnlyPomodoroAndKeepsGuardLocked() {
        Assume.assumeTrue(app.focusGuard.canLock)
        landscape()
        instrumentation.runOnMainSync { app.pomodoro.configure(PomodoroConfig(1, 1, 1)) }
        compose.onNodeWithTag("pomodoro_toggle").performClick()
        compose.waitUntil(65_000) { app.pomodoro.notice.value != null }
        assertTrue(app.focusGuard.locked.value)
        assertTrue(app.pomodoro.state.value.running)
        assertEquals(PomodoroPhase.SHORT_BREAK, app.pomodoro.state.value.phase)
        assertEquals(PomodoroPhase.FOCUS, app.pomodoro.notice.value?.completedPhase)
        instrumentation.runOnMainSync { app.focusGuard.unlockAfterAuthentication() }
        compose.waitUntil(4_000) { panelState() == "완료 알림 반전" }
        capture("pomodoro-inverted.png")
        compose.waitUntil(4_000) { panelState() == "기본" }
        capture("pomodoro-normal.png")
        compose.onNodeWithTag("clock_panel").assertIsDisplayed()
        compose.waitUntil(12_000) { app.pomodoro.notice.value == null }
        assertEquals("기본", panelState())
    }

    @Test fun lockStaysReachableWhenControlsScrollAndWhenRotatedWhilePaused() {
        Assume.assumeTrue(app.focusGuard.canLock)
        landscape()
        compose.onNodeWithTag("pomodoro_toggle").performTouchInput { swipeUp() }
        compose.onNodeWithTag("focus_lock").assertIsDisplayed().performClick()
        compose.onNodeWithTag("focus_unlock").assertIsDisplayed()
        assertTrue(app.focusGuard.locked.value)
        assertFalse(app.pomodoro.state.value.running)
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        // The portrait header also keeps the sole authentication button reachable.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("focus_unlock").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("focus_unlock").assertIsDisplayed().performClick()
        assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000))
        device.pressBack()
        assertTrue(app.focusGuard.locked.value)
    }

    private fun panelState() = compose.onNodeWithTag("pomodoro_panel").fetchSemanticsNode()
        .config[SemanticsProperties.StateDescription]

    private fun capture(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
