package com.afterplay.app.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.afterplay.app.diagnostics.errorChain
import com.afterplay.app.diagnostics.networkFailure
import java.util.WeakHashMap

/** Resume interrupted HTTP loads in place, keeping the already decoded audio available. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackLoadErrorPolicy(
    private val canResumeLoads: () -> Boolean = { false },
    private val onRecovery: (String) -> Unit = {},
) : DefaultLoadErrorHandlingPolicy(2) {
    private data class Attempt(var bytes: Long = 0, var failures: Int = 0)
    private val loads = mutableMapOf<Long, Attempt>()
    // A buffered load's fatal error can surface much later, or be discarded after a seek/skip.
    private val terminalAttempts = WeakHashMap<Throwable, Int>()

    @Synchronized
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val event = loadErrorInfo.loadEventInfo
        val attempt = loads.getOrPut(event.loadTaskId) { Attempt() }
        attempt.failures = if (event.bytesLoaded > attempt.bytes) 1 else attempt.failures + 1
        attempt.bytes = event.bytesLoaded
        val http = errorChain(loadErrorInfo.exception).filterIsInstance<HttpDataSource.HttpDataSourceException>().firstOrNull()
        val transient = when (http) {
            is HttpDataSource.InvalidResponseCodeException -> http.responseCode == 408 || http.responseCode in 500..599
            null -> false
            else -> http.type == HttpDataSource.HttpDataSourceException.TYPE_READ || networkFailure(http) != null
        }
        if (canResumeLoads() && event.bytesLoaded > 0 && transient && attempt.failures < 3) {
            onRecovery("오디오 수신 복구 ${attempt.failures + 1}/3 · 1초 후 이어받기 · 남은 버퍼 유지")
            return 1_000L
        }
        terminalAttempts[loadErrorInfo.exception] = attempt.failures.coerceAtMost(3)
        return C.TIME_UNSET
    }

    @Synchronized
    override fun onLoadTaskConcluded(loadTaskId: Long) {
        loads.remove(loadTaskId)
        super.onLoadTaskConcluded(loadTaskId)
    }

    /** Count loader retries in the same three-attempt budget as the final player recovery. */
    @Synchronized
    fun failedAttempts(error: Throwable): Int = errorChain(error).firstNotNullOfOrNull { terminalAttempts.remove(it) } ?: 1
}
