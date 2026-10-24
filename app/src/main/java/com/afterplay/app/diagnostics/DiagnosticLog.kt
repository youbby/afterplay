package com.afterplay.app.diagnostics

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.SocketException
import java.net.UnknownHostException
import java.io.InterruptedIOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

enum class DiagnosticKind(val label: String) {
    SESSION("실행"), NETWORK("네트워크"), NETWORK_LOST("연결 끊김"), NETWORK_ERROR("네트워크 오류"),
    HTTP("서버 응답"), HTTP_ERROR("HTTP 오류"), SOURCE("영상 정보"), SOURCE_ERROR("영상 정보 오류"),
    BUFFERING("초기 대기/탐색"), REBUFFER("재버퍼링"), BUFFER_END("버퍼링 종료"),
    PLAYER("재생 상태"), PLAYER_ERROR("재생 오류"), SYSTEM_PAUSE("시스템 중단"),
    AUDIO_UNDERRUN("오디오 출력 지연"), AUDIO_ERROR("오디오 처리 오류"), RECOVERY("자동 복구"), SAMPLE("재생 점검"), BANDWIDTH("수신 속도"), POWER("전원 상태")
}

data class DiagnosticEvent(val time: Long, val kind: DiagnosticKind, val detail: String)
data class DiagnosticSnapshot(val events: List<DiagnosticEvent> = emptyList(), val storageError: Boolean = false)

/** Bounded, private, asynchronous journal. Playback threads never wait for disk writes. */
class DiagnosticLog(private val directory: File, private val maxFileBytes: Int = 128 * 1024,
    private val maxEntries: Int = 500, private val clock: () -> Long = System::currentTimeMillis) {
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(512),
        { task -> Thread(task, "AfterPlay-diagnostics").apply { isDaemon = true } }, ThreadPoolExecutor.DiscardOldestPolicy())
    private val current = File(directory, "playback.jsonl")
    private val previous = File(directory, "playback-previous.jsonl")
    private val gson = Gson()
    private val entries = ArrayDeque<DiagnosticEvent>()
    private val _state = MutableStateFlow(DiagnosticSnapshot())
    val state = _state.asStateFlow()
    private var storageError = false

    init {
        require(maxFileBytes >= 2048 && maxEntries > 0)
        worker.execute {
            runCatching {
                for (file in listOf(previous, current)) if (file.exists()) {
                    val bytes = ByteArray(minOf(file.length(), maxFileBytes.toLong()).toInt())
                    val count = file.inputStream().use { input ->
                        var offset = 0
                        while (offset < bytes.size) {
                            val read = input.read(bytes, offset, bytes.size - offset)
                            if (read < 0) break
                            offset += read
                        }
                        offset
                    }
                    String(bytes, 0, count, Charsets.UTF_8).lineSequence().forEach { line ->
                        runCatching {
                            val json = JsonParser.parseString(line).asJsonObject
                            DiagnosticEvent(json["time"].asLong, DiagnosticKind.valueOf(json["kind"].asString), sanitizeDiagnostic(json["detail"].asString))
                        }.getOrNull()?.let(::remember)
                    }
                }
            }.onFailure { storageError = true }
            publish()
        }
    }

    fun record(kind: DiagnosticKind, detail: String) {
        val event = DiagnosticEvent(clock(), kind, sanitizeDiagnostic(detail))
        worker.execute {
            remember(event)
            runCatching {
                check(directory.isDirectory || directory.mkdirs())
                val line = (gson.toJson(event) + "\n").toByteArray(Charsets.UTF_8)
                if (current.length() + line.size > maxFileBytes) {
                    if (previous.exists()) check(previous.delete())
                    if (current.exists()) check(current.renameTo(previous))
                }
                current.appendBytes(line)
            }.onFailure { storageError = true }
            publish()
        }
    }

    fun clear() = worker.execute {
        entries.clear()
        storageError = runCatching {
            for (file in listOf(current, previous)) if (file.exists()) check(file.delete())
        }.isFailure
        publish()
    }

    private fun remember(event: DiagnosticEvent) {
        entries.addLast(event)
        while (entries.size > maxEntries) entries.removeFirst()
    }
    private fun publish() { _state.value = DiagnosticSnapshot(entries.toList(), storageError) }

    internal fun awaitIdle() { worker.submit {}.get(5, TimeUnit.SECONDS) }
    internal fun close() { worker.shutdown(); worker.awaitTermination(5, TimeUnit.SECONDS) }
}

internal fun sanitizeDiagnostic(value: String): String = value
    .replace(Regex("(?:https?|youtube)://[^\\s]+", RegexOption.IGNORE_CASE), "[URL 제외]")
    .replace(Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "[IP 제외]")
    .replace(Regex("(?i)\\b(authorization|cookie|token|signature|sig|key)=\\S+"), "$1=[제외]")
    .replace(Regex("(?i)\\b(authorization|cookie)\\s*:\\s*.*"), "$1: [제외]")
    .replace('\n', ' ').replace('\r', ' ').take(320)

internal fun errorChain(error: Throwable): List<Throwable> {
    val result = mutableListOf<Throwable>()
    var item: Throwable? = error
    while (item != null && result.size < 8 && result.none { it === item }) {
        result.add(item); item = item.cause
    }
    return result
}

internal fun networkFailure(error: Throwable): String? = errorChain(error).firstNotNullOfOrNull {
    when (it) {
        is UnknownHostException -> "DNS 조회 실패"
        is SocketTimeoutException -> "연결/수신 시간 초과"
        is NoRouteToHostException -> "네트워크 경로 없음"
        is ConnectException -> "서버 연결 실패"
        is SocketException -> "소켓 연결 끊김"
        is SSLException -> "TLS 보안 연결 실패"
        is InterruptedIOException -> if (it.message.equals("timeout", ignoreCase = true)) "전체 요청 시간 초과" else null
        else -> null
    }
}

fun diagnosticHint(events: List<DiagnosticEvent>): String {
    val symptom = events.lastOrNull { it.kind in listOf(DiagnosticKind.REBUFFER, DiagnosticKind.PLAYER_ERROR,
        DiagnosticKind.AUDIO_UNDERRUN, DiagnosticKind.AUDIO_ERROR, DiagnosticKind.SYSTEM_PAUSE) }
    val nearby = if (symptom == null) emptyList() else events.filter { kotlin.math.abs(it.time - symptom.time) <= 15_000 }
    return when {
        symptom?.kind == DiagnosticKind.SYSTEM_PAUSE -> "시스템 제어 또는 수면 타이머로 중단된 기록이 있습니다. 해당 시각의 중단 사유를 확인해 주세요."
        nearby.any { it.kind == DiagnosticKind.NETWORK_ERROR || it.kind == DiagnosticKind.NETWORK_LOST } ->
            "끊김 시점 근처에 네트워크 오류/연결 끊김이 기록되었습니다. 네트워크 영향 가능성이 있습니다."
        nearby.any { it.kind == DiagnosticKind.HTTP_ERROR } ->
            "끊김 시점 근처에 서버 응답 오류가 있습니다. HTTP 코드와 영상 주소·서버 제한을 함께 확인해 주세요."
        symptom?.kind in listOf(DiagnosticKind.AUDIO_UNDERRUN, DiagnosticKind.AUDIO_ERROR) -> "오디오 출력/처리 지연이 기록되었습니다. 기기 부하 또는 데이터 공급 지연 가능성이 있습니다."
        symptom != null -> "끊김/버퍼링은 기록되었지만 네트워크 원인으로 단정할 근거는 아직 없습니다."
        else -> "재생 중 끊김이 다시 발생한 뒤 이 화면에서 기록을 확인해 주세요."
    }
}

private val logTime = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
fun DiagnosticEvent.displayLine(): String = "${logTime.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))} [${kind.label}] $detail"
