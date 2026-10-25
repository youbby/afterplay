package com.afterplay.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.data.AppMode
import org.junit.Assert.assertTrue

internal fun selectAppModeForTest(
    compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
    app: AfterPlayApp,
    mode: AppMode,
) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync { app.appMode.select(mode) }
    awaitAppModeForTest(compose, app, mode)
}

internal fun awaitAppModeForTest(
    compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
    app: AfterPlayApp,
    mode: AppMode,
) {
    val expected = if (mode == AppMode.POMODORO) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
    val requested = if (mode == AppMode.POMODORO) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    val until = System.currentTimeMillis() + 15_000
    var ready = false
    while (!ready && System.currentTimeMillis() < until) {
        compose.activityRule.scenario.onActivity {
            // Large-screen Android may ignore orientation requests; the selected UI mode must still apply.
            val orientationReady = it.resources.configuration.smallestScreenWidthDp >= 600 ||
                it.resources.configuration.orientation == expected
            ready = app.appMode.state.value == mode && it.requestedOrientation == requested && orientationReady &&
                it.window.decorView.isAttachedToWindow && it.window.decorView.hasWindowFocus()
        }
        if (!ready) Thread.sleep(100)
    }
    assertTrue("Selected mode should be attached and focused: $mode", ready)
    // Allow activity recreation to finish before querying Compose roots.
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    instrumentation.uiAutomation.waitForIdle(100, 3_000)
    compose.waitForIdle()
}
