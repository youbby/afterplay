package com.afterplay.app

import android.content.ComponentName
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.diagnostics.DiagnosticKind
import com.afterplay.app.playback.PlaybackService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real loopback HTTP, TCP resets and byte ranges through the production playback service. */
@RunWith(AndroidJUnit4::class)
class BufferedNetworkRecoveryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as AfterPlayApp

    @Test fun disconnectedReadResumesBeforeBufferRunsOutWithoutPausingOrSeeking() = withController { player ->
        FaultServer(prefixSeconds = 20, failResume = false).use { server ->
            var rebuffered = false
            var started = false
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) started = true }
                override fun onPlaybackStateChanged(state: Int) { if (started && state == Player.STATE_BUFFERING) rebuffered = true }
            }
            instrumentation.runOnMainSync {
                player.addListener(listener)
                player.setMediaItem(server.item("first")); player.prepare(); player.play()
            }
            try {
                await { player.isPlaying && player.totalBufferedDuration >= 15_000 }
                var before = 0L
                instrumentation.runOnMainSync { before = player.currentPosition }
                val droppedAt = SystemClock.elapsedRealtime()
                server.disconnect.countDown()
                await { server.ranges.size >= 2 && player.totalBufferedDuration > 25_000 }
                instrumentation.runOnMainSync {
                    assertTrue("Must reopen the missing byte range while buffered audio remains", server.ranges[1] > 0)
                    assertTrue("Reconnect must not wait for the 20-second buffer to empty", SystemClock.elapsedRealtime() - droppedAt < 10_000)
                    assertTrue(player.isPlaying)
                    assertNull(player.playerError)
                    assertFalse("A read reconnect must preserve READY playback", rebuffered)
                    assertTrue(player.currentPosition >= before)
                }
                Thread.sleep(1_000)
                app.diagnostics.log.awaitIdle()
                val events = app.diagnostics.log.state.value.events
                assertTrue(events.any { it.kind == DiagnosticKind.RECOVERY && it.detail.contains("수신 복구 2/3") })
                assertFalse(events.any { it.kind == DiagnosticKind.PLAYER_ERROR })
                val report = app.diagnostics.report()
                assertFalse(report.contains("127.0.0.1"))
            } finally { instrumentation.runOnMainSync { player.removeListener(listener) } }
        }
    }

    @Test fun exhaustedReadRetriesShareTheThreeAttemptBudgetAndSkipOnlyOneItem() = withController { player ->
        FaultServer(prefixSeconds = 4, failResume = true).use { server ->
            instrumentation.runOnMainSync {
                player.setMediaItems(listOf(server.item("first"), server.item("second"), server.item("third")))
                player.prepare(); player.play()
            }
            await { player.isPlaying && player.totalBufferedDuration >= 2_500 }
            server.disconnect.countDown()
            await { player.playerError != null && !player.playWhenReady }
            instrumentation.runOnMainSync {
                assertEquals("Read retries must not receive another three prepare attempts", 3, server.paths.count { it == "/first" })
                assertEquals(3, server.paths.count { it == "/second" })
                assertEquals(0, server.paths.count { it == "/third" })
                assertEquals(1, player.currentMediaItemIndex)
            }
            app.diagnostics.log.awaitIdle()
            val events = app.diagnostics.log.state.value.events
            assertTrue(events.any { it.kind == DiagnosticKind.RECOVERY && it.detail.contains("수신 복구 3/3") })
            assertTrue(events.any { it.kind == DiagnosticKind.RECOVERY && it.detail.contains("다음 곡으로 이동") })
            assertTrue("Terminal load errors must also be recorded", events.any { it.detail.contains("수신재시도=false") })
        }
    }

    @Test fun initialHttpFailureStillGetsExactlyThreeAttempts() = withController { player ->
        FaultServer(prefixSeconds = 0, failResume = true).use { server ->
            instrumentation.runOnMainSync {
                player.setMediaItem(server.item("second")); player.prepare(); player.play()
            }
            await { player.playerError != null && !player.playWhenReady }
            assertEquals(3, server.paths.count { it == "/second" })
        }
    }

    @Test fun partialDataBeforeFirstPlaybackDoesNotAddAnotherRetryBudget() = withController { player ->
        FaultServer(prefixSeconds = 0, failResume = false, initialFailures = 2).use { server ->
            instrumentation.runOnMainSync {
                player.setMediaItem(server.item("first")); player.prepare(); player.play()
            }
            await { server.paths.size == 3 }
            Thread.sleep(300) // Let the extractor consume the WAV header, without providing any audio samples.
            instrumentation.runOnMainSync { assertFalse(player.isPlaying) }
            server.disconnect.countDown()
            await { player.playerError != null && !player.playWhenReady }
            assertEquals("Partial initial data must not turn three attempts into five", 3, server.paths.size)
            app.diagnostics.log.awaitIdle()
            assertTrue("The final initial attempt must actually have received a partial header",
                app.diagnostics.log.state.value.events.any { it.detail.contains("수신=44B") })
        }
    }

    private fun withController(test: (MediaController) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use {
            val future = MediaController.Builder(context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            val player = future.get(15, TimeUnit.SECONDS)
            try {
                instrumentation.runOnMainSync {
                    player.pause(); player.stop(); player.clearMediaItems()
                    player.repeatMode = Player.REPEAT_MODE_OFF; player.shuffleModeEnabled = false
                }
                app.diagnostics.log.clear(); app.diagnostics.log.awaitIdle()
                test(player)
            } finally {
                instrumentation.runOnMainSync {
                    player.pause(); player.stop(); player.clearMediaItems()
                    MediaController.releaseFuture(future)
                }
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(25)
        }
        fail("Expected network recovery state was not reached")
    }

    private class FaultServer(private val prefixSeconds: Int, private val failResume: Boolean,
        private val initialFailures: Int = 0) : AutoCloseable {
        private val server = ServerSocket(0).apply { soTimeout = 20_000 }
        private val executor = Executors.newCachedThreadPool()
        private val sockets = CopyOnWriteArrayList<Socket>()
        val disconnect = CountDownLatch(1)
        val paths = CopyOnWriteArrayList<String>()
        val ranges = CopyOnWriteArrayList<Int>()
        private val wav = ByteBuffer.allocate(44 + 8_000 * 45 * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(capacity() - 8).put("WAVEfmt ".toByteArray())
            putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
            putShort(2).putShort(16).put("data".toByteArray()).putInt(capacity() - 44)
        }.array()

        init {
            executor.submit {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    sockets.add(socket)
                    executor.submit { socket.use { respond(it) } }
                }
            }
        }

        fun item(id: String): MediaItem = MediaItem.Builder().setMediaId(id)
            .setUri("http://127.0.0.1:${server.localPort}/$id").build()

        private fun respond(socket: Socket) {
            socket.soTimeout = 20_000
            val reader = socket.getInputStream().bufferedReader()
            val request = reader.readLine() ?: return
            val path = request.split(' ')[1]
            var offset = 0
            while (true) {
                val line = reader.readLine().orEmpty()
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) offset = line.substringAfter("bytes=").substringBefore('-').toInt()
            }
            paths.add(path)
            if (path != "/first") {
                socket.getOutputStream().apply {
                    write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); flush()
                }
                return
            }
            ranges.add(offset)
            if (ranges.size <= initialFailures || (ranges.size > 1 && failResume)) {
                socket.getOutputStream().apply {
                    write("HTTP/1.1 503 Unavailable\r\nRetry-After: 1\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); flush()
                }
                return
            }
            val partial = offset > 0
            val headers = "HTTP/1.1 ${if (partial) "206 Partial Content" else "200 OK"}\r\n" +
                "Content-Type: audio/wav\r\nContent-Length: ${wav.size - offset}\r\nAccept-Ranges: bytes\r\n" +
                (if (partial) "Content-Range: bytes $offset-${wav.lastIndex}/${wav.size}\r\n" else "") + "Connection: close\r\n\r\n"
            val output = socket.getOutputStream()
            output.write(headers.toByteArray())
            if (!partial) {
                output.write(wav, 0, 44 + prefixSeconds * 16_000); output.flush()
                check(disconnect.await(15, TimeUnit.SECONDS))
                socket.setSoLinger(true, 0) // TCP reset during an active, partially consumed response.
            } else {
                output.write(wav, offset, wav.size - offset); output.flush()
            }
        }

        override fun close() {
            disconnect.countDown()
            server.close()
            sockets.forEach { runCatching { it.close() } }
            executor.shutdownNow()
        }
    }
}
