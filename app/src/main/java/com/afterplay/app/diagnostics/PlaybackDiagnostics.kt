package com.afterplay.app.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import com.afterplay.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class PlaybackDiagnostics(context: Context) {
    private val app = context.applicationContext
    val log = DiagnosticLog(File(app.filesDir, "diagnostics"))
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val power = app.getSystemService(PowerManager::class.java)
    private val _network = MutableStateFlow("네트워크 확인 중")
    val network = _network.asStateFlow()
    private var lastNetwork: Network? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { synchronized(this@PlaybackDiagnostics) { lastNetwork = network } }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            synchronized(this@PlaybackDiagnostics) { lastNetwork = network }
            updateNetwork(describe(capabilities))
        }
        override fun onLost(network: Network) {
            val lost = synchronized(this@PlaybackDiagnostics) { if (lastNetwork == network) { lastNetwork = null; true } else false }
            if (lost) updateNetwork("연결 없음", lost = true)
        }
        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            log.record(DiagnosticKind.NETWORK, "앱 네트워크 차단=$blocked")
        }
    }

    init {
        log.record(DiagnosticKind.SESSION, "앱 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android API ${Build.VERSION.SDK_INT} · ${Build.MANUFACTURER} ${Build.MODEL}")
        refresh()
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            .onFailure { log.record(DiagnosticKind.NETWORK, "네트워크 변화 감시 시작 실패: ${it.javaClass.simpleName}") }
    }

    fun refresh() {
        runCatching {
            val active = connectivity.activeNetwork
            synchronized(this) { lastNetwork = active }
            updateNetwork(active?.let(connectivity::getNetworkCapabilities)?.let(::describe) ?: "연결 없음")
        }.onFailure { log.record(DiagnosticKind.NETWORK, "네트워크 상태 조회 실패: ${it.javaClass.simpleName}") }
        log.record(DiagnosticKind.POWER, powerSummary())
    }

    private fun updateNetwork(value: String, lost: Boolean = false) {
        if (_network.value != value) {
            val disconnected = lost || (value == "연결 없음" && _network.value != "네트워크 확인 중")
            _network.value = value
            log.record(if (disconnected) DiagnosticKind.NETWORK_LOST else DiagnosticKind.NETWORK, value)
        }
    }

    private fun describe(capabilities: NetworkCapabilities): String {
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "모바일 데이터"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "유선"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "기타 연결"
        }
        val validated = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "인터넷 확인됨" else "인터넷 미확인"
        val vpn = if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && transport != "VPN") " · VPN" else ""
        val metered = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "비과금" else "과금/제한 연결"
        return "$transport · $validated · $metered$vpn"
    }

    fun powerSummary(): String = "화면=${if (power.isInteractive) "켜짐" else "꺼짐"} · 절전=${power.isPowerSaveMode} · Doze=${power.isDeviceIdleMode} · 배터리최적화제외=${power.isIgnoringBatteryOptimizations(app.packageName)} · 데이터절약=${connectivity.restrictBackgroundStatus}"

    fun report(): String = buildString {
        appendLine("AfterPlay 문제 확인 · ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android API ${Build.VERSION.SDK_INT} · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("현재 연결: ${network.value}")
        appendLine(powerSummary())
        val snapshot = log.state.value
        appendLine("기록 단서: ${diagnosticHint(snapshot.events)}")
        appendLine("기록만으로 원인을 확정하지는 않습니다. URL·IP·영상 제목은 기록하지 않습니다.")
        if (snapshot.storageError) appendLine("주의: 저장소 기록에 실패하여 일부 기록이 이번 실행에만 남아 있습니다.")
        appendLine()
        snapshot.events.forEach { appendLine(it.displayLine()) }
    }
}
