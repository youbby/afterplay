package com.afterplay.app.playback

import com.afterplay.app.data.Track
import com.afterplay.app.diagnostics.DiagnosticLog
import com.afterplay.app.diagnostics.DiagnosticKind
import com.afterplay.app.diagnostics.DiagnosticHttpListener
import com.afterplay.app.diagnostics.errorChain
import com.afterplay.app.diagnostics.networkFailure
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

class YoutubeRepository(private val diagnostics: DiagnosticLog? = null) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .apply { diagnostics?.let { log -> eventListenerFactory { DiagnosticHttpListener(log, "영상 정보") } } }
        .build()

    // Audio response bodies stay open while the player buffers or waits for more data.
    // A whole-call deadline would cancel healthy playback, including time spent paused.
    val playbackClient: OkHttpClient = client.forAudioPlayback().let { base ->
        diagnostics?.let { log -> base.newBuilder().eventListenerFactory { DiagnosticHttpListener(log, "오디오") }.build() } ?: base
    }

    init { NewPipe.init(HttpDownloader(client), Localization("ko", "KR")) }

    data class Resolved(val track: Track, val audioUrl: String, val cachedAt: Long)
    private val cache = LinkedHashMap<String, Resolved>()

    // Called only from an IO coroutine or ExoPlayer's loader thread. NewPipe has global state.
    @Synchronized
    fun resolve(id: String): Resolved {
        cache[id]?.takeIf { System.nanoTime() - it.cachedAt < TimeUnit.MINUTES.toNanos(10) }
            ?.let { diagnostics?.record(DiagnosticKind.SOURCE, "영상 정보 캐시 사용"); return it }
        val started = System.nanoTime()
        diagnostics?.record(DiagnosticKind.SOURCE, "영상 정보/오디오 주소 조회 시작")
        try {
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
            diagnostics?.record(DiagnosticKind.SOURCE, "오디오 주소 조회 완료 · ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}ms")
            return resolved
        } catch (error: Exception) {
            diagnostics?.record(if (networkFailure(error) != null) DiagnosticKind.NETWORK_ERROR else DiagnosticKind.SOURCE_ERROR,
                "영상 정보 조회 실패 · ${networkFailure(error) ?: "영상 제한/주소/연동 확인 필요"} · ${errorChain(error).joinToString(" > ") { it.javaClass.simpleName }}")
            throw error
        }
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
