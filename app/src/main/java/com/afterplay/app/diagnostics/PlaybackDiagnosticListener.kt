package com.afterplay.app.diagnostics

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackDiagnosticListener(private val player: Player, private val diagnostics: PlaybackDiagnostics) : Player.Listener, AnalyticsListener {
    private val log get() = diagnostics.log
    private var played = false
    private var seekingUntil = 0L
    private var bufferingAt: Long? = null
    private var lastSample = 0L
    private var lastBandwidth = 0L

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_BUFFERING) {
            bufferingAt = SystemClock.elapsedRealtime()
            log.record(if (played && SystemClock.elapsedRealtime() >= seekingUntil && player.playWhenReady) DiagnosticKind.REBUFFER else DiagnosticKind.BUFFERING, sampleText())
        } else {
            bufferingAt?.let { log.record(DiagnosticKind.BUFFER_END, "대기=${SystemClock.elapsedRealtime() - it}ms · ${sampleText()}") }
            bufferingAt = null
            if (playbackState == Player.STATE_READY) seekingUntil = 0L
            log.record(DiagnosticKind.PLAYER, sampleText())
        }
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) played = true
        log.record(DiagnosticKind.PLAYER, "실제재생=$isPlaying · ${sampleText()}")
    }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        played = player.isPlaying; seekingUntil = 0L
        log.record(DiagnosticKind.PLAYER, "곡 전환 사유=$reason · 대기열위치=${player.currentMediaItemIndex}")
    }
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            seekingUntil = SystemClock.elapsedRealtime() + 2_000
            log.record(DiagnosticKind.PLAYER, "사용자 탐색 · 위치=${newPosition.positionMs}ms")
        }
    }
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        val system = !playWhenReady && reason in listOf(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
        val why = when (reason) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "사용자/미디어 제어 요청"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "오디오 포커스 상실(다른 소리/통화 등)"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "이어폰/오디오 경로 분리"
            else -> "사유코드=$reason"
        }
        log.record(if (system) DiagnosticKind.SYSTEM_PAUSE else DiagnosticKind.PLAYER, "재생요청=$playWhenReady · $why")
    }
    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        log.record(if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) DiagnosticKind.SYSTEM_PAUSE else DiagnosticKind.PLAYER,
            "재생억제=$playbackSuppressionReason · ${sampleText()}")
    }
    override fun onPlayerError(error: PlaybackException) {
        log.record(DiagnosticKind.PLAYER_ERROR, "${error.errorCodeName} · ${sampleText()}")
        recordError(error)
    }
    private fun recordError(error: Throwable) {
        val chain = errorChain(error)
        val http = chain.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        val network = networkFailure(error)
        val audio = error is PlaybackException && (error.errorCodeName.contains("AUDIO") || error.errorCodeName.contains("DECOD"))
        log.record(when { http != null -> DiagnosticKind.HTTP_ERROR; network != null -> DiagnosticKind.NETWORK_ERROR; audio -> DiagnosticKind.AUDIO_ERROR; else -> DiagnosticKind.SOURCE_ERROR },
            "${http?.let { "HTTP=${it.responseCode}" } ?: network ?: "원인 추가 확인 필요"} · ${chain.joinToString(" > ") { it.javaClass.simpleName }} · ${diagnostics.network.value}")
    }
    override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
        if (!wasCanceled) {
            log.record(DiagnosticKind.PLAYER, "오디오 로드 실패 · 수신=${loadEventInfo.bytesLoaded}B · 경과=${loadEventInfo.loadDurationMs}ms")
            recordError(error)
        }
    }
    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBandwidth >= 10_000) {
            lastBandwidth = now
            log.record(DiagnosticKind.BANDWIDTH, "추정=${bitrateEstimate / 1000}kbps · 수신=${totalBytesLoaded}B · 로드=${totalLoadTimeMs}ms")
        }
    }
    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        log.record(DiagnosticKind.AUDIO_UNDERRUN, "출력버퍼=${bufferSize}B/${bufferSizeMs}ms · 마지막공급=${elapsedSinceLastFeedMs}ms · ${sampleText()}")
    }
    fun sample() {
        val now = SystemClock.elapsedRealtime()
        if (player.playWhenReady && now - lastSample >= 10_000) {
            lastSample = now
            log.record(DiagnosticKind.SAMPLE, sampleText() + " · " + diagnostics.powerSummary())
        }
    }
    private fun sampleText(): String = "상태=${when (player.playbackState) { Player.STATE_BUFFERING -> "BUFFERING"; Player.STATE_READY -> "READY"; Player.STATE_ENDED -> "ENDED"; else -> "IDLE" }} · 요청=${player.playWhenReady} · 재생=${player.isPlaying} · 위치=${player.currentPosition}ms · 잔여버퍼=${player.totalBufferedDuration}ms · ${diagnostics.network.value}"
}
