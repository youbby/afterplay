package com.afterplay.app

import android.os.ParcelFileDescriptor
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickSettingsTest {
    @Test fun systemTileOpensTheExistingWindow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val component = "com.afterplay.app/.QuickSettingsService"
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        val original = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync { original.moveTaskToBack(true) }
            instrumentation.waitForIdleSync()
            shell("cmd statusbar add-tile $component")
            try {
                val deadline = System.currentTimeMillis() + 15_000
                var resumed = false
                do {
                    shell("cmd statusbar click-tile $component")
                    Thread.sleep(500)
                    instrumentation.runOnMainSync {
                        resumed = original.lifecycle.currentState == Lifecycle.State.RESUMED
                    }
                } while (!resumed && System.currentTimeMillis() < deadline)
                assertTrue("The Quick Settings tile did not open the app", resumed)
                instrumentation.runOnMainSync {
                    val resumedActivities = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
                    assertEquals(listOf(original), resumedActivities)
                }
            } finally {
                shell("cmd statusbar remove-tile $component")
                shell("cmd statusbar collapse")
            }
        } finally {
            instrumentation.runOnMainSync { original.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
