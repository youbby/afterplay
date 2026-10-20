package com.afterplay.app.data

import com.afterplay.app.playback.USER_AGENT
import com.afterplay.app.playback.YoutubeRepository
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class YoutubeIntegrationTest {
    @Test fun resolvesPublicVideoAndReadsAudioBytes() {
        assumeTrue(System.getProperty("youtubeNetwork") == "true")
        val repository = YoutubeRepository()
        val audio = repository.resolve("jNQXAC9IVRw")
        assertTrue(audio.track.title.isNotBlank())
        val request = Request.Builder().url(audio.audioUrl).header("User-Agent", USER_AGENT)
            .header("Range", "bytes=0-1023").build()
        repository.client.newCall(request).execute().use { response ->
            assertTrue("HTTP ${response.code}", response.isSuccessful)
            assertTrue(response.body!!.source().readByteArray(128).isNotEmpty())
        }
    }
}
