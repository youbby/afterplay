package com.afterplay.app.diagnostics

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Only stages/codes/timings are recorded: never request URLs, headers, IPs or body contents. */
class DiagnosticHttpListener(private val log: DiagnosticLog, private val scope: String) : EventListener() {
    private val started = System.nanoTime()
    private var dnsAt = 0L
    private var tlsAt = 0L
    private var phase = "요청 시작"
    private fun elapsed() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
    override fun dnsStart(call: Call, domainName: String) { dnsAt = System.nanoTime(); phase = "DNS" }
    override fun connectStart(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) { phase = "연결" }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<java.net.InetAddress>) {
        log.record(DiagnosticKind.HTTP, "$scope DNS=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - dnsAt)}ms")
    }
    override fun secureConnectStart(call: Call) { tlsAt = System.nanoTime(); phase = "TLS" }
    override fun responseHeadersStart(call: Call) { phase = "응답 헤더 대기" }
    override fun responseBodyStart(call: Call) { phase = "본문 수신" }
    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        log.record(DiagnosticKind.HTTP, "$scope TLS=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tlsAt)}ms")
    }
    override fun responseHeadersEnd(call: Call, response: Response) {
        log.record(if (response.code < 400) DiagnosticKind.HTTP else DiagnosticKind.HTTP_ERROR,
            "$scope HTTP=${response.code} · 헤더수신=${elapsed()}ms · ${response.protocol}")
    }
    override fun responseBodyEnd(call: Call, byteCount: Long) {
        log.record(DiagnosticKind.HTTP, "$scope 수신완료=${byteCount}B · 전체경과=${elapsed()}ms(대기시간 포함)")
    }
    override fun callFailed(call: Call, ioe: IOException) {
        if (call.isCanceled() && networkFailure(ioe) == null) {
            log.record(DiagnosticKind.HTTP, "$scope 요청 취소 · ${elapsed()}ms")
        } else {
            log.record(DiagnosticKind.NETWORK_ERROR, "$scope $phase · ${networkFailure(ioe) ?: "전송 실패"} · ${elapsed()}ms · ${errorChain(ioe).joinToString(" > ") { it.javaClass.simpleName }}")
        }
    }
}
