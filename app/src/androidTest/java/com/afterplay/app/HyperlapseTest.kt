package com.afterplay.app

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.afterplay.app.data.*
import com.afterplay.app.hyperlapse.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class HyperlapseTest {
    @get:Rule(order = 0) val permission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as AfterPlayApp
    private fun screenshot(name: String) {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            File(compose.activity.getExternalFilesDir(null), name))
    }

    private fun pomodoro() {
        compose.runOnIdle {
            app.focusGuard.unlockAfterAuthentication()
            app.hyperlapse.disable()
            app.pomodoro.configure(PomodoroConfig())
            app.appMode.select(AppMode.POMODORO)
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("hyperlapse_toggle").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun previewCancelDoesNotEnableAndConfirmationArmsUntilFocusActuallyStarts() {
        pomodoro()
        try {
            compose.onNodeWithTag("hyperlapse_toggle").performClick()
            compose.onNodeWithTag("hyperlapse_preview_dialog").assertIsDisplayed()
            compose.onNodeWithTag("hyperlapse_cancel").performClick()
            assertFalse(app.hyperlapse.state.value.enabled)
            compose.onNodeWithTag("hyperlapse_toggle").performClick()
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag("hyperlapse_confirm").assertIsEnabled(); true }.getOrDefault(false)
            }
            screenshot("hyperlapse-preview.png")
            val previewBounds = compose.onNodeWithTag("hyperlapse_preview").fetchSemanticsNode().boundsInRoot
            assertTrue("Preview must be square", kotlin.math.abs(previewBounds.width - previewBounds.height) < 1f)
            compose.onNodeWithTag("hyperlapse_confirm").performClick()
            compose.onNodeWithTag("hyperlapse_preview_dialog").assertDoesNotExist()
            assertTrue(app.hyperlapse.state.value.enabled)
            assertFalse(app.hyperlapse.state.value.recording)
            assertFalse(app.pomodoro.state.value.running)
            compose.onNodeWithContentDescription("하이퍼랩스 활성화 · 끄기").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertTrue(app.hyperlapse.state.value.enabled)
            compose.onNodeWithTag("pomodoro_toggle").performClick()
            compose.waitUntil(15_000) { app.hyperlapse.state.value.recording }
            assertTrue(app.focusGuard.locked.value)
            compose.onNodeWithTag("focus_unlock").performClick()
            compose.onNodeWithContentDescription("하이퍼랩스 촬영 중 · 끄기").assertIsDisplayed()
            val deadline = SystemClock.elapsedRealtime() + 15_000
            val directory = File(compose.activity.filesDir, "hyperlapse")
            while (SystemClock.elapsedRealtime() < deadline && directory.walkTopDown().none { it.extension == "jpg" }) SystemClock.sleep(200)
            assertTrue("The real front camera must deliver a saved sample", directory.walkTopDown().any { it.extension == "jpg" })
            screenshot("hyperlapse-recording.png")
            val countBeforeBackground = directory.walkTopDown().count { it.extension == "jpg" }
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            SystemClock.sleep(4_000)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitForIdle()
            assertTrue("Camera service must survive leaving the Activity", app.hyperlapse.state.value.recording)
            assertTrue("Background capture must continue", directory.walkTopDown().count { it.extension == "jpg" } > countBeforeBackground)
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertTrue("Camera must survive Activity recreation", app.hyperlapse.state.value.recording)
            compose.onNodeWithTag("pomodoro_toggle").performClick()
            compose.waitUntil(5_000) { !app.hyperlapse.state.value.recording }
            assertTrue(app.hyperlapse.state.value.enabled)
            compose.onNodeWithTag("hyperlapse_toggle").performClick()
            compose.waitUntil(120_000) { !app.hyperlapse.state.value.saving }
            assertFalse(app.hyperlapse.state.value.enabled)
            assertNotNull("Partial footage must be published", app.hyperlapse.state.value.savedUri)
            compose.activity.contentResolver.openFileDescriptor(app.hyperlapse.state.value.savedUri!!, "r")!!.use { fd ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(fd.fileDescriptor)
                    assertEquals(1, extractor.trackCount)
                    assertTrue(extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION) <= 15_000_000)
                } finally { extractor.release() }
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(compose.activity, app.hyperlapse.state.value.savedUri)
                val first = requireNotNull(retriever.getFrameAtTime(0))
                File(compose.activity.getExternalFilesDir(null), "hyperlapse-actual-frame.png").outputStream().use {
                    first.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                first.recycle()
            } finally { retriever.release() }
        } finally {
            compose.runOnIdle { app.focusGuard.unlockAfterAuthentication(); app.hyperlapse.disable(); app.pomodoro.configure(PomodoroConfig()) }
        }
    }

    @Test fun twoNaturalFocusCompletionsExcludeRestAndAutomaticallySaveThirtySeconds() {
        pomodoro()
        val root = File(compose.activity.filesDir, "hyperlapse")
        try {
            compose.runOnIdle {
                app.pomodoro.configure(PomodoroConfig(focusMinutes = 60, repeatCount = 2))
                app.hyperlapse.arm(android.view.Surface.ROTATION_90)
            }
            for (round in 0..1) {
                compose.onNodeWithTag("pomodoro_toggle").performClick()
                val deadline = SystemClock.elapsedRealtime() + 20_000
                while (SystemClock.elapsedRealtime() < deadline && root.walkTopDown().none { it.name.startsWith("${round}_") }) SystemClock.sleep(200)
                assertTrue("Focus $round must capture an actual camera image", root.walkTopDown().any { it.name.startsWith("${round}_") })
                compose.onNodeWithTag("focus_unlock").performClick()
                compose.runOnIdle { app.pomodoro.refreshAt(app.pomodoro.state.value.deadlineMs) }
                compose.waitUntil(5_000) { !app.hyperlapse.state.value.recording }
                if (round == 0) {
                    assertEquals(PomodoroPhase.SHORT_BREAK, app.pomodoro.state.value.phase)
                    compose.onNodeWithTag("pomodoro_toggle").performClick() // Pause the rest.
                    val count = root.walkTopDown().count { it.extension == "jpg" }
                    SystemClock.sleep(1_000)
                    assertEquals("Rest must not contribute images", count, root.walkTopDown().count { it.extension == "jpg" })
                    compose.onNodeWithContentDescription("다음 뽀모도로 단계").performClick()
                    compose.waitForIdle()
                }
            }
            compose.waitUntil(120_000) { !app.hyperlapse.state.value.saving }
            assertEquals(PomodoroPhase.LONG_BREAK, app.pomodoro.state.value.phase)
            val uri = requireNotNull(app.hyperlapse.state.value.savedUri)
            compose.activity.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(fd.fileDescriptor)
                    assertEquals(1, extractor.trackCount)
                    assertEquals(30_000_000L, extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION))
                } finally { extractor.release() }
            }
        } finally {
            compose.runOnIdle { app.focusGuard.unlockAfterAuthentication(); app.hyperlapse.disable(); app.pomodoro.configure(PomodoroConfig()) }
        }
    }

    @Test fun interruptedCaptureRecoversItsPartialVideoWithoutRearmingTheCamera() {
        pomodoro()
        val directory = File(compose.activity.filesDir, "hyperlapse/${System.currentTimeMillis()}-recovery-test").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        File(directory, "0_089.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        lateinit var recovered: HyperlapseController
        compose.runOnIdle { recovered = HyperlapseController(compose.activity.applicationContext, app.pomodoro) }
        compose.waitUntil(60_000) { recovered.state.value.savedUri != null }
        assertFalse(recovered.state.value.enabled)
        assertFalse(recovered.state.value.recording)
        assertFalse(directory.exists())
        compose.activity.contentResolver.openFileDescriptor(recovered.state.value.savedUri!!, "r")!!.use { fd ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(fd.fileDescriptor)
                assertEquals(3_000_000L, extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION))
            } finally { extractor.release() }
        }
    }

    @Test fun encodedCompletedRoundsAreSquareSilentAndExactlyFifteenThirtyFortyFiveAndSixtySeconds() {
        pomodoro()
        val directory = File(compose.activity.cacheDir, "hyperlapse-encoder-test").apply { mkdirs() }
        val sample = File(directory, "sample.jpg")
        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.DKGRAY)
            drawRect(0f, 0f, 360f, 360f, Paint().apply { color = Color.RED })
            drawText("2026.10.25 12:34:56", 20f, 690f, Paint().apply { color = Color.WHITE; textSize = 24f })
        }
        sample.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        try {
            for (rounds in 1..4) {
                val frames = List(rounds * HyperlapsePlan.FRAMES_PER_ROUND) { sample }
                val video = File(directory, "${rounds}.mp4")
                HyperlapseEncoder.encode(frames, video)
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(video.absolutePath)
                    assertEquals("No audio track", 1, extractor.trackCount)
                    val format = extractor.getTrackFormat(0)
                    assertEquals("video/avc", format.getString(MediaFormat.KEY_MIME))
                    assertEquals(720, format.getInteger(MediaFormat.KEY_WIDTH))
                    assertEquals(720, format.getInteger(MediaFormat.KEY_HEIGHT))
                    val duration = format.getLong(MediaFormat.KEY_DURATION)
                    assertEquals("Fixed duration for $rounds rounds", rounds * 15_000_000L, duration)
                    assertTrue(duration <= 60_000_000)
                    extractor.selectTrack(0)
                    var count = 0
                    var previous = -1L
                    val buffer = ByteBuffer.allocate(2_000_000)
                    while (extractor.readSampleData(buffer, 0) >= 0) {
                        assertTrue(extractor.sampleTime > previous)
                        previous = extractor.sampleTime
                        count++
                        if (!extractor.advance()) break
                    }
                    assertEquals(frames.size, count)
                } finally { extractor.release() }
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(video.absolutePath)
                    val first = requireNotNull(retriever.getFrameAtTime(0))
                    val top = first.getPixel(100, 100)
                    val bottom = first.getPixel(100, 600)
                    assertTrue("Encoded frame must retain its top-left orientation", Color.red(top) > 180 && Color.green(top) < 60)
                    assertTrue("Bottom-left must not be vertically flipped", kotlin.math.abs(Color.red(bottom) - Color.green(bottom)) < 20)
                    first.recycle()
                } finally { retriever.release() }
                video.copyTo(File(compose.activity.getExternalFilesDir(null), "hyperlapse-$rounds-rounds.mp4"), overwrite = true)
            }
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }
}
