package com.afterplay.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.afterplay.app.AfterPlayApp
import com.afterplay.app.MainActivity
import com.afterplay.app.data.Track
import com.afterplay.app.diagnostics.DiagnosticKind
import com.afterplay.app.diagnostics.PlaybackDiagnosticListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var diagnosticListener: PlaybackDiagnosticListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val checkpointTask = object : Runnable {
        override fun run() {
            sleepTimer.checkDeadline()
            diagnosticListener?.sample()
            session?.player?.let { if (it.isPlaying) store.save(it) }
            handler.postDelayed(this, 2000)
        }
    }
    private val store get() = (application as AfterPlayApp).playbackStore
    private val sleepTimer get() = (application as AfterPlayApp).sleepTimer

    override fun onCreate() {
        super.onCreate()
        val repository = (application as AfterPlayApp).youtube
        val diagnostics = (application as AfterPlayApp).diagnostics
        diagnostics.log.record(DiagnosticKind.SESSION, "재생 서비스 시작")
        val http = OkHttpDataSource.Factory(repository.playbackClient).setUserAgent(USER_AGENT)
        val resolving = ResolvingDataSource.Factory(http) { spec ->
            if (spec.uri.scheme != "youtube") spec else {
                val id = spec.uri.host ?: throw IOException("영상 주소가 올바르지 않습니다.")
                try {
                    // Resolve each item when it is loaded, not when the playlist is created.
                    // This avoids expiring URLs in long playlists and survives screen-off playback.
                    spec.withUri(Uri.parse(repository.resolve(id).audioUrl))
                } catch (error: Exception) {
                    throw IOException("YouTube 오디오를 불러오지 못했습니다. 네트워크 또는 영상 공개 상태를 확인해 주세요.", error)
                }
            }
        }
        val bufferedPlayback = AtomicBoolean(false)
        val loadErrorPolicy = PlaybackLoadErrorPolicy(canResumeLoads = bufferedPlayback::get) {
            diagnostics.log.record(DiagnosticKind.RECOVERY, it)
        }
        val player = ExoPlayer.Builder(this)
            .setTrackSelector(DefaultTrackSelector(this).apply {
                setParameters(buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true))
            })
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(60_000, 120_000, 2_500, 5_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, resolving))
                .setLoadErrorHandlingPolicy(loadErrorPolicy))
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.shuffleModeEnabled = store.shuffle
        player.repeatMode = store.repeat
        store.checkpoint()?.let { saved ->
            player.setMediaItems(saved.items.map { it.mediaItem(saved.playlistId) }, saved.index, saved.positionMs)
            // Do not prepare or auto-play on app startup; the user starts resumed playback.
        }
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { bufferedPlayback.set(isPlaying) }
            override fun onEvents(player: Player, events: Player.Events) {
                sleepTimer.checkDeadline()
                store.save(player, durable = events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                    events.contains(Player.EVENT_POSITION_DISCONTINUITY))
            }
        })
        diagnosticListener = PlaybackDiagnosticListener(player, diagnostics).also {
            player.addListener(it); player.addAnalyticsListener(it)
        }
        player.addListener(PlaybackRecovery(player, handler, repository::invalidate, loadErrorPolicy::failedAttempts) {
            diagnostics.log.record(DiagnosticKind.RECOVERY, it)
        })
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(activity).build()
        sleepTimer.attach {
            diagnostics.log.record(DiagnosticKind.SYSTEM_PAUSE, "수면 타이머 만료로 재생 종료(예정된 종료)")
            store.save(player, durable = true)
            player.pause()
            player.stop()
        }
        handler.postDelayed(checkpointTask, 2000)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.let { store.save(it, durable = true) }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        (application as AfterPlayApp).diagnostics.log.record(DiagnosticKind.SESSION, "재생 서비스 정상 종료")
        diagnosticListener = null
        sleepTimer.detach()
        handler.removeCallbacksAndMessages(null)
        session?.player?.let { store.save(it, durable = true) }
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

fun Track.toMediaItem(playlistId: String = ""): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri("youtube://$id")
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
        .setExtras(Bundle().apply { putString(PLAYLIST_ID, playlistId); putLong(DURATION_MS, durationSeconds * 1000) })
        .setArtworkUri(artwork.takeIf(String::isNotBlank)?.let(Uri::parse)).build())
    .build()
