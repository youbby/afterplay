package com.afterplay.app

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.data.ThemePreset
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class WorkspaceLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun panelsFillAvailableSpaceAndTimerIsLargerThanClock() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as AfterPlayApp
        instrumentation.runOnMainSync { app.focusGuard.unlockAfterAuthentication(); app.pomodoro.reset() }
        val tablet = context.resources.configuration.smallestScreenWidthDp >= 600
        if (!tablet) compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("landscape_workspace").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val player = compose.onNodeWithTag("compact_player").fetchSemanticsNode().boundsInRoot
        val clock = compose.onNodeWithTag("clock_panel").fetchSemanticsNode().boundsInRoot
        val timer = compose.onNodeWithTag("pomodoro_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(player.width - timer.width) <= 2f)
        assertTrue(clock.right <= player.right + player.width + 100f)
        assertTrue(clock.bottom < timer.top)
        assertTrue(abs(timer.bottom - player.bottom) <= 2f)
        if (tablet) assertTrue("Large displays should split clock and timer evenly", abs(clock.height - timer.height) <= 2f)
        val clockText = compose.onNodeWithTag("wall_clock").fetchSemanticsNode().boundsInRoot
        val timerText = compose.onNodeWithTag("pomodoro_remaining").fetchSemanticsNode().boundsInRoot
        assertTrue(timerText.height > clockText.height)
        assertTrue(abs(timerText.center.y - timer.center.y) < timer.height * .3f)
        for (theme in listOf(ThemePreset.BURGUNDY, ThemePreset.SLATE)) {
            instrumentation.runOnMainSync { app.appearance.select(theme) }
            compose.waitForIdle()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { File(context.getExternalFilesDir(null), "workspace-${if (tablet) "tablet" else "phone"}-${theme.name.lowercase()}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        instrumentation.runOnMainSync { app.appearance.select(ThemePreset.BURGUNDY) }
    }
}
