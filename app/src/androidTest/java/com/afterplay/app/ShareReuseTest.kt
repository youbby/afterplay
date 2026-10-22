package com.afterplay.app

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
class ShareReuseTest {
    @Test fun sharesFromAnotherTaskReuseTheOpenActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // ActivityScenario matches lifecycle events against the launch intent. ACTION_SEND
        // replaces that intent, so observe the actual activity instead of its stale scenario.
        val original = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync { original.moveTaskToBack(true) }
            repeat(2) { index ->
                val shared = "https://youtu.be/jNQXAC9IVRw?share=$index"
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shared)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                })
                val deadline = System.currentTimeMillis() + 5_000
                var delivered = false
                while (!delivered && System.currentTimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        delivered = original.intent.getStringExtra(Intent.EXTRA_TEXT) == shared &&
                            original.lifecycle.currentState == Lifecycle.State.RESUMED
                    }
                    if (delivered) break
                    Thread.sleep(50)
                }
                instrumentation.waitForIdleSync()
                assertTrue("The share was not delivered to the existing window", delivered)
                instrumentation.runOnMainSync {
                    assertEquals(shared, original.intent.getStringExtra(Intent.EXTRA_TEXT))
                    val resumed = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
                    assertEquals(listOf(original), resumed)
                }
            }
        } finally {
            instrumentation.runOnMainSync { original.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
