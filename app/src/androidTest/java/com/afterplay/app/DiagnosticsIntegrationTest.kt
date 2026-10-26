package com.afterplay.app

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.diagnostics.DiagnosticKind
import com.afterplay.app.diagnostics.diagnosticHint
import com.afterplay.app.playback.PlaybackService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DiagnosticsIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as AfterPlayApp

    @Test fun realPlaybackIsRecordedAndInfoCanCopyClearAndReopenTheJournal() {
        val samples = 8_000 * 30
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
        wav.putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        val file = File(context.cacheDir, "private-audio-test.wav").apply { writeBytes(wav.array()) }
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync { app.focusGuard.unlockAfterAuthentication(); app.pomodoro.reset() }
            val landscape = InstrumentationRegistry.getArguments().getString("landscape") == "true"
            if (landscape) {
                selectAppModeForTest(compose, app, com.afterplay.app.data.AppMode.POMODORO)
                val until = System.currentTimeMillis() + 10_000
                var ready = false
                while (!ready && System.currentTimeMillis() < until) {
                    compose.activityRule.scenario.onActivity {
                        ready = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && it.window.decorView.hasWindowFocus()
                    }
                    if (!ready) Thread.sleep(100)
                }
                assertTrue("Landscape activity should be ready", ready)
                instrumentation.waitForIdleSync()
            }
            app.diagnostics.log.clear(); app.diagnostics.log.awaitIdle()
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.Builder().setMediaId("private-media-id").setUri(file.toURI().toString())
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("private-title").build()).build())
                controller.prepare(); controller.play()
            }
            compose.waitUntil(10_000) { app.diagnostics.log.state.value.events.any { it.detail.contains("실제재생=true") } }
            val report = app.diagnostics.report()
            assertTrue(report.contains("잔여버퍼="))
            for (secret in listOf("private-media-id", "private-title", "private-audio-test.wav")) assertFalse(report.contains(secret))
            if (compose.onAllNodesWithTag("landscape_workspace").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithContentDescription("재생목록 열기").performClick()
            compose.onNodeWithContentDescription("앱 정보").performClick()
            compose.onNodeWithTag("open_diagnostics").performScrollTo().performClick()
            compose.onNodeWithTag("diagnostic_network").assertIsDisplayed()
            compose.onNodeWithTag("copy_diagnostics").performClick()
            compose.onNodeWithText("로그를 복사했습니다.").assertIsDisplayed()
            var copied = ""
            instrumentation.runOnMainSync { copied = context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString() }
            assertTrue(copied.contains("AfterPlay 문제 확인"))
            assertFalse(copied.contains("private-title"))
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("diagnostic_events").assertIsDisplayed()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            if (landscape) assertTrue("Landscape snapshot must be wide", bitmap.width > bitmap.height)
            try { File(context.getExternalFilesDir(null), "diagnostics-live.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } } finally { bitmap.recycle() }
            instrumentation.runOnMainSync { controller.pause() }
            compose.waitUntil(5_000) { app.diagnostics.log.state.value.events.any { it.detail.contains("재생요청=false") } }
            compose.onNodeWithTag("clear_diagnostics").performClick()
            compose.onNodeWithText("로그 삭제").performClick()
            compose.waitUntil(5_000) { app.diagnostics.log.state.value.events.isEmpty() }
            compose.onNodeWithContentDescription("문제 확인 닫기").performClick()
            compose.onNodeWithTag("check_updates").assertIsDisplayed()
        } finally {
            instrumentation.runOnMainSync { controller.pause(); controller.stop(); controller.clearMediaItems(); MediaController.releaseFuture(future) }
            file.delete()
        }
    }

    @Test fun realAudioHttp403IsLoggedAsServerEvidenceInsteadOfClaimingNetworkLoss() {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val response = executor.submit {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); flush()
                    }
                }
            }
            try {
                app.diagnostics.log.clear(); app.diagnostics.log.awaitIdle()
                instrumentation.runOnMainSync {
                    controller.setMediaItem(MediaItem.Builder().setMediaId("private-http-item")
                        .setUri("http://127.0.0.1:${server.localPort}/audio?sig=private-secret").build())
                    controller.prepare(); controller.play()
                }
                compose.waitUntil(10_000) { app.diagnostics.log.state.value.events.any { it.kind == DiagnosticKind.PLAYER_ERROR } }
                app.diagnostics.log.awaitIdle()
                val events = app.diagnostics.log.state.value.events
                assertTrue(events.any { it.kind == DiagnosticKind.HTTP_ERROR && it.detail.contains("HTTP=403") })
                assertTrue(diagnosticHint(events).contains("서버 응답 오류"))
                val report = app.diagnostics.report()
                assertFalse(report.contains("127.0.0.1")); assertFalse(report.contains("private-secret"))
                response.get(5, TimeUnit.SECONDS)
            } finally {
                instrumentation.runOnMainSync { controller.pause(); controller.stop(); controller.clearMediaItems(); MediaController.releaseFuture(future) }
                executor.shutdownNow()
            }
        }
    }
}
