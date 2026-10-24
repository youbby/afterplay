package com.afterplay.app.diagnostics

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiagnosticHttpTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun actualHttpErrorIsRecordedWithoutTheRequestAddress() = request(403, stall = false) { events ->
        assertTrue(events.any { it.kind == DiagnosticKind.HTTP_ERROR && it.detail.contains("HTTP=403") })
    }

    @Test fun actualReadStallIsRecordedAsNetworkError() = request(200, stall = true) { events ->
        assertTrue(events.any { it.kind == DiagnosticKind.NETWORK_ERROR && it.detail.contains("시간 초과") && it.detail.contains("본문 수신") })
    }

    private fun request(code: Int, stall: Boolean, check: (List<DiagnosticEvent>) -> Unit) {
        val log = DiagnosticLog(temporary.newFolder())
        val client = OkHttpClient.Builder().readTimeout(100, TimeUnit.MILLISECONDS)
            .eventListenerFactory { DiagnosticHttpListener(log, "오디오") }.build()
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0).use { server ->
            server.soTimeout = 5_000
            val response = executor.submit {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $code Response\r\nContent-Length: 2\r\nConnection: close\r\n\r\na".toByteArray()); flush()
                        if (stall) Thread.sleep(400) else { write('b'.code); flush() }
                    }
                }
            }
            try {
                val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/audio?token=private-secret").build()
                if (stall) assertThrows(IOException::class.java) { client.newCall(request).execute().use { it.body!!.string() } }
                else client.newCall(request).execute().use { it.body!!.string() }
                response.get(5, TimeUnit.SECONDS)
                log.awaitIdle()
                val events = log.state.value.events
                check(events)
                assertFalse(events.any { it.detail.contains("127.0.0.1") || it.detail.contains("private-secret") })
            } finally {
                executor.shutdownNow(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); log.close()
            }
        }
    }
}
