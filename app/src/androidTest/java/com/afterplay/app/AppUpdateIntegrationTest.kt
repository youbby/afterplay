package com.afterplay.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.data.UpdateStatus
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppUpdateIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun infoChecksThePublicReleaseAndKeepsTheResultAfterRecreation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("githubNetwork") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as AfterPlayApp
        instrumentation.runOnMainSync { app.focusGuard.unlockAfterAuthentication(); app.pomodoro.reset() }
        lateinit var model: PlayerViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[PlayerViewModel::class.java] }
        compose.onNodeWithContentDescription("앱 정보").performClick()
        compose.onNodeWithTag("check_updates").performClick()
        compose.waitUntil(30_000) { model.updateStatus.value != UpdateStatus.Checking && model.updateStatus.value != UpdateStatus.Idle }
        val result = model.updateStatus.value
        assertTrue("GitHub check should succeed: $result", result is UpdateStatus.Current || result is UpdateStatus.Available)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("update_status").performScrollTo().assertIsDisplayed()
        assertEquals(result, model.updateStatus.value)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(instrumentation.targetContext.getExternalFilesDir(null), "app-info-live-update-check.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        } } finally { bitmap.recycle() }
    }
}
