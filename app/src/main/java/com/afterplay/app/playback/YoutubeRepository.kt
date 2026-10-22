package com.afterplay.app.playback

import com.afterplay.app.data.Track
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.TimeUnit

class YoutubeRepository {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    // Audio response bodies stay open while the player buffers or waits for more data.
    // A whole-call deadline would cancel healthy playback, including time spent paused.
    val playbackClient: OkHttpClient = client.forAudioPlayback()

    init { NewPipe.init(HttpDownloader(client), Localization("ko", "KR")) }

    data class Resolved(val track: Track, val audioUrl: String, val cachedAt: Long)
    private val cache = LinkedHashMap<String, Resolved>()

    // Called only from an IO coroutine or ExoPlayer's loader thread. NewPipe has global state.
    @Synchronized
    fun resolve(id: String): Resolved {
        cache[id]?.takeIf { System.nanoTime() - it.cachedAt < TimeUnit.MINUTES.toNanos(10) }
            ?.let { return it }
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val stream = selectAudioStream(info.audioStreams)
            ?: throw IOException("이 영상에는 재생 가능한 오디오 스트림이 없습니다. 일반 공개 영상을 선택해 주세요.")
        val resolved = Resolved(
            Track(id, info.name, info.uploaderName, info.duration.coerceAtLeast(0),
                info.thumbnails.maxByOrNull { it.width }?.url.orEmpty()),
            stream.content,
            System.nanoTime(),
        )
        if (cache.size >= 50) cache.remove(cache.keys.first())
        cache[id] = resolved
        return resolved
    }

    @Synchronized fun invalidate(id: String) { cache.remove(id) }
}

private class HttpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
            .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        val method = request.httpMethod()
        val body = request.dataToSend()?.toRequestBody()
            ?: if (method == "POST" || method == "PUT" || method == "PATCH") ByteArray(0).toRequestBody() else null
        builder.method(method, body)
        return client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("요청이 많습니다. 잠시 후 다시 시도해 주세요.", request.url())
            Response(response.code, response.message, response.headers.toMultimap(),
                response.body?.string(), response.request.url.toString())
        }
    }
}

const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

internal fun OkHttpClient.forAudioPlayback(): OkHttpClient = newBuilder()
    .callTimeout(0, TimeUnit.MILLISECONDS)
    .build()
