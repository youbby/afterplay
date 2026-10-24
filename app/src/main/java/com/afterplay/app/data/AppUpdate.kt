package com.afterplay.app.data

import com.google.gson.JsonParser
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

private const val REPOSITORY = "https://github.com/youbby/afterplay"
private const val LATEST_RELEASE = "https://api.github.com/repos/youbby/afterplay/releases/latest"

data class AppRelease(val version: String, val releaseUrl: String, val apkUrl: String?)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Current(val latestVersion: String) : UpdateStatus
    data class Available(val release: AppRelease) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

class UpdateCheckException(message: String) : IOException(message)

class UpdateRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).build(),
    private val endpoint: String = LATEST_RELEASE,
) {
    /** Called on the IO dispatcher, only after the user requests a check. */
    fun check(currentVersion: String): UpdateStatus {
        val request = Request.Builder().url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2026-03-10")
            .header("User-Agent", "AfterPlay/$currentVersion")
            .cacheControl(CacheControl.FORCE_NETWORK).build()
        val release = client.newCall(request).execute().use { response ->
            when (response.code) {
                404 -> throw UpdateCheckException("등록된 정식 릴리스가 아직 없습니다.")
                403, 429 -> throw UpdateCheckException("잠시 업데이트를 확인할 수 없습니다. 조금 후에 다시 시도해 주세요.")
            }
            if (!response.isSuccessful) throw IOException("Release request failed: ${response.code}")
            try { parseRelease(requireNotNull(response.body).string()) }
            catch (error: Exception) {
                throw UpdateCheckException("업데이트 정보를 읽지 못했습니다. 잠시 후 다시 확인해 주세요.")
            }
        }
        return if (compareAppVersions(release.version, currentVersion) > 0) UpdateStatus.Available(release)
            else UpdateStatus.Current(release.version)
    }
}

internal fun compareAppVersions(first: String, second: String): Int {
    val left = versionParts(first)
    val right = versionParts(second)
    for (index in 0 until maxOf(left.size, right.size)) {
        val difference = (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0)
        if (difference != 0) return difference
    }
    return 0
}

private fun versionParts(value: String): List<Int> {
    val match = requireNotNull(Regex("^[vV]?(\\d+(?:\\.\\d+){1,3})(?:\\+[0-9A-Za-z.-]+)?$").matchEntire(value))
    return match.groupValues[1].split('.').map { requireNotNull(it.toIntOrNull()) }
}

internal fun parseRelease(body: String): AppRelease {
    val release = JsonParser.parseString(body).asJsonObject
    require(release["draft"]?.asBoolean == false && release["prerelease"]?.asBoolean == false)
    val tag = requireNotNull(release["tag_name"]?.asString)
    versionParts(tag)
    val version = tag.removePrefix("v").removePrefix("V")
    val apkName = "AfterPlay-$version-release.apk"
    val apkPath = "/youbby/afterplay/releases/download/$tag/$apkName"
    val apk = release.getAsJsonArray("assets")?.firstOrNull { entry ->
        val asset = entry.asJsonObject
        asset["name"]?.asString == apkName && asset["state"]?.asString == "uploaded" && runCatching {
            val url = URI(asset["browser_download_url"].asString)
            url.scheme == "https" && url.host == "github.com" && url.userInfo == null &&
                url.port in listOf(-1, 443) && url.path == apkPath && url.query == null && url.fragment == null
        }.getOrDefault(false)
    }
    return AppRelease(version, "$REPOSITORY/releases/tag/$tag", if (apk != null) "https://github.com$apkPath" else null)
}
