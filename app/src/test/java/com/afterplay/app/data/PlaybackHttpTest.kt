package com.afterplay.app.data

import com.afterplay.app.playback.forAudioPlayback
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlaybackHttpTest {
    @Test fun audioResponseCanOutliveMetadataDeadline() {
        val metadataClient = OkHttpClient.Builder()
            .callTimeout(100, TimeUnit.MILLISECONDS)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        val playbackClient = metadataClient.forAudioPlayback()
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0).use { server ->
            server.soTimeout = 5_000
            val response = executor.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                    val output = socket.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\na".toByteArray())
                    output.flush()
                    // Model a player retaining an open audio body past the metadata deadline.
                    Thread.sleep(300)
                    output.write('b'.code)
                    output.flush()
                }
            }
            try {
                val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/audio").build()
                playbackClient.newCall(request).execute().use {
                    assertTrue(it.isSuccessful)
                    assertEquals("ab", it.body!!.string())
                }
                response.get(5, TimeUnit.SECONDS)
                // Keep the finite read/connect timeouts so a stalled network still triggers recovery.
                assertEquals(metadataClient.readTimeoutMillis, playbackClient.readTimeoutMillis)
                assertEquals(metadataClient.connectTimeoutMillis, playbackClient.connectTimeoutMillis)
                assertEquals(100, metadataClient.callTimeoutMillis)
            } finally {
                executor.shutdownNow()
                playbackClient.connectionPool.evictAll()
                playbackClient.dispatcher.executorService.shutdown()
            }
        }
    }
}
