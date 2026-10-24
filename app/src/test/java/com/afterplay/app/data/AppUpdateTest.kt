package com.afterplay.app.data

import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppUpdateTest {
    private fun release(version: String = "1.6.0", apkUrl: String = "https://github.com/youbby/afterplay/releases/download/v$version/AfterPlay-$version-release.apk") = """
        {"tag_name":"v$version","draft":false,"prerelease":false,"assets":[
        {"name":"AfterPlay-$version-release.apk","state":"uploaded","browser_download_url":"$apkUrl"}]}
    """.trimIndent()

    @Test fun comparesVersionNumbersAndDoesNotOfferDowngrades() {
        assertTrue(compareAppVersions("v1.10.0", "1.9.9") > 0)
        assertTrue(compareAppVersions("2.0.0", "1.99.99") > 0)
        assertEquals(0, compareAppVersions("1.5.3", "1.5.3.0"))
        assertEquals(0, compareAppVersions("1.5.3+build.2", "1.5.3"))
        assertTrue(compareAppVersions("1.5.2", "1.5.3") < 0)
    }

    @Test fun readsOnlyTheReleaseApkAndUsesTheOfficialReleasePage() {
        val parsed = parseRelease(release())
        assertEquals("1.6.0", parsed.version)
        assertEquals("https://github.com/youbby/afterplay/releases/tag/v1.6.0", parsed.releaseUrl)
        assertEquals("https://github.com/youbby/afterplay/releases/download/v1.6.0/AfterPlay-1.6.0-release.apk", parsed.apkUrl)
    }

    @Test fun excludesDraftsPrereleasesAndUnknownVersionFormats() {
        for (json in listOf(release().replace("\"draft\":false", "\"draft\":true"),
            release().replace("\"prerelease\":false", "\"prerelease\":true"), release("1.6.0-beta"), "{}")) {
            assertThrows(Exception::class.java) { parseRelease(json) }
        }
    }

    @Test fun missingApkAndUntrustedLinksFallBackToTheReleasePage() {
        val urls = listOf("https://example.com/update.apk", "http://github.com/youbby/afterplay/releases/download/v1.6.0/AfterPlay-1.6.0-release.apk",
            "https://github.com/another/repo/releases/download/v1.6.0/AfterPlay-1.6.0-release.apk")
        for (url in urls) assertNull(parseRelease(release(apkUrl = url)).apkUrl)
        assertNull(parseRelease("""{"tag_name":"v1.6.0","draft":false,"prerelease":false,"assets":[]}""").apkUrl)
        assertNull(parseRelease(release().replace("\"state\":\"uploaded\"", "\"state\":\"new\"")).apkUrl)
    }

    @Test fun publicApiRequestNeedsNoTokenAndFindsANewerRelease() {
        withResponse(200, release()) { repository, headers ->
            val result = repository.check("1.5.3") as UpdateStatus.Available
            assertEquals("1.6.0", result.release.version)
            assertTrue(headers.any { it.equals("Accept: application/vnd.github+json", ignoreCase = true) })
            assertTrue(headers.any { it.equals("X-GitHub-Api-Version: 2026-03-10", ignoreCase = true) })
            assertFalse(headers.any { it.startsWith("Authorization:", ignoreCase = true) })
        }
    }

    @Test fun equalOrOlderPublishedVersionShowsNoUpdate() {
        for (version in listOf("1.5.2", "1.5.3")) withResponse(200, release(version)) { repository, _ ->
            assertEquals(UpdateStatus.Current(version), repository.check("1.5.3"))
        }
    }

    @Test fun unavailableReleasesAndRateLimitsCanBeRetried() {
        for (code in listOf(403, 404, 429)) withResponse(code, "{}") { repository, _ ->
            assertThrows(UpdateCheckException::class.java) { repository.check("1.5.3") }
        }
        withResponse(503, "{}") { repository, _ -> assertThrows(IOException::class.java) { repository.check("1.5.3") } }
        withResponse(200, "not json") { repository, _ -> assertThrows(UpdateCheckException::class.java) { repository.check("1.5.3") } }
    }

    private fun withResponse(code: Int, body: String, check: (UpdateRepository, MutableList<String>) -> Unit) {
        val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0).use { server ->
            server.soTimeout = 3_000
            val headers = mutableListOf<String>()
            val response = executor.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 3_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        headers.add(line)
                    }
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $code Response\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                }
            }
            try {
                check(UpdateRepository(client, "http://127.0.0.1:${server.localPort}/releases/latest"), headers)
                response.get(3, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }
}
