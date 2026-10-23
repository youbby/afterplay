package com.afterplay.app

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.afterplay.app.data.Playlist
import com.afterplay.app.data.Track
import com.afterplay.app.data.PomodoroConfig
import com.afterplay.app.data.ThemePreset
import com.afterplay.app.data.YoutubeLink
import com.afterplay.app.data.add
import com.afterplay.app.data.move
import com.afterplay.app.data.resumePoint
import com.afterplay.app.data.transferTrack
import com.afterplay.app.playback.PlaybackService
import com.afterplay.app.playback.toMediaItem
import com.afterplay.app.playback.DURATION_MS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackUi(
    val connected: Boolean = false,
    val id: String = "",
    val title: String = "아직 재생 중인 곡이 없어요",
    val artist: String = "재생목록에 영상을 추가해 보세요",
    val artwork: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val error: String? = null,
    val sleepTimerActive: Boolean = false,
    val sleepRemainingMs: Long = 0,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AfterPlayApp
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists = _playlists.asStateFlow()
    val selectedId = MutableStateFlow(app.playbackStore.selectedPlaylistId)
    val loadingLibrary = MutableStateFlow(true)
    val adding = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val addError = MutableStateFlow<String?>(null)
    val sharedLink = MutableStateFlow<String?>(null)
    private val _playback = MutableStateFlow(PlaybackUi())
    val playback = _playback.asStateFlow()
    val pomodoro = app.pomodoro.state
    val pomodoroNotice = app.pomodoro.notice
    val focusLocked = app.focusGuard.locked
    val needsScreenLock = MutableStateFlow(false)
    val theme = app.appearance.state
    fun setTheme(preset: ThemePreset) = app.appearance.select(preset)
    fun configurePomodoro(config: PomodoroConfig) { if (!focusLocked.value) app.pomodoro.configure(config) }
    fun togglePomodoro() {
        if (focusLocked.value) return
        if (!pomodoro.value.running && !lockFocus()) return
        app.pomodoro.toggle()
    }
    fun lockFocus(): Boolean {
        if (app.focusGuard.lock()) return true
        needsScreenLock.value = true
        return false
    }
    fun resetPomodoro() { if (!focusLocked.value) app.pomodoro.reset() }
    fun skipPomodoro() { if (!focusLocked.value) app.pomodoro.skip() }
    fun refreshPomodoro() = app.pomodoro.refresh()
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { refresh() }
    }

    init {
        viewModelScope.launch {
            try {
                app.library.playlists.collect { lists ->
                    _playlists.value = lists
                    if (lists.none { it.id == selectedId.value }) selectedId.value = lists.firstOrNull()?.id.orEmpty()
                    loadingLibrary.value = false
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                loadingLibrary.value = false
                message.value = "재생목록을 읽지 못했습니다. 앱을 다시 실행해 주세요."
            }
        }
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get().also { it.addListener(listener) }
                refresh()
            } catch (_: Exception) { message.value = "재생 서비스에 연결하지 못했습니다. 앱을 다시 실행해 주세요." }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch { while (true) { refresh(); delay(500) } }
    }

    private fun refresh() {
        val player = controller ?: return
        val metadata = player.mediaMetadata
        _playback.value = PlaybackUi(
            connected = player.isConnected,
            id = player.currentMediaItem?.mediaId.orEmpty(),
            title = metadata.title?.toString() ?: "아직 재생 중인 곡이 없어요",
            artist = metadata.artist?.toString() ?: "재생목록에 영상을 추가해 보세요",
            artwork = metadata.artworkUri?.toString().orEmpty(),
            playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED &&
                player.playbackState != Player.STATE_IDLE && player.playerError == null,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            position = player.currentPosition.coerceAtLeast(0),
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                ?: metadata.extras?.getLong(DURATION_MS)?.takeIf { it > 0 }
                ?: _playlists.value.flatMap { it.tracks }.find { it.id == player.currentMediaItem?.mediaId }?.durationSeconds?.times(1000) ?: 0,
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode,
            canPrevious = player.hasPreviousMediaItem() || player.currentPosition > 3000,
            canNext = player.hasNextMediaItem(),
            error = player.playerError?.let { "재생하지 못했어요. 연결을 확인하고 재생 버튼으로 다시 시도해 주세요. 비공개·제한 영상은 지원하지 않습니다." },
            sleepTimerActive = app.sleepTimer.active,
            sleepRemainingMs = app.sleepTimer.remainingMs,
        )
    }

    private fun edit(transform: (List<Playlist>) -> List<Playlist>, after: () -> Unit = {}) {
        viewModelScope.launch {
            try { app.library.update(transform); after() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                message.value = "저장하지 못했습니다. 저장 공간을 확인해 주세요."
            }
        }
    }

    fun createPlaylist(name: String) {
        val clean = name.trim().take(60)
        if (clean.isEmpty()) return
        val playlist = Playlist(name = clean)
        edit({ it + playlist }) { selectPlaylist(playlist.id) }
    }

    fun selectPlaylist(id: String) {
        selectedId.value = id
        app.playbackStore.selectedPlaylistId = id
    }

    fun transferTrack(sourceId: String, targetId: String, trackId: String) {
        edit({ it.transferTrack(sourceId, targetId, trackId) }) {
            message.value = "다른 재생목록으로 이동했어요."
        }
    }

    fun renamePlaylist(id: String, name: String) {
        val clean = name.trim().take(60)
        if (clean.isNotEmpty()) edit({ list -> list.map { if (it.id == id) it.copy(name = clean) else it } })
    }

    fun deletePlaylist(id: String) { edit({ list -> list.filterNot { it.id == id } }) }
    fun removeTrack(playlistId: String, trackId: String) {
        edit({ list -> list.map { if (it.id == playlistId) it.copy(tracks = it.tracks.filterNot { t -> t.id == trackId }) else it } })
    }
    fun moveTrack(playlistId: String, trackId: String, offset: Int) {
        edit({ list -> list.map { if (it.id == playlistId) it.move(trackId, offset) else it } })
    }

    fun addTrack(playlistId: String, input: String, onSuccess: () -> Unit) {
        if (adding.value) return
        val id = YoutubeLink.videoId(input)
        if (id == null) { addError.value = "유튜브 영상 링크를 입력해 주세요. 재생목록 주소만으로는 추가할 수 없습니다."; return }
        if (_playlists.value.find { it.id == playlistId }?.tracks?.any { it.id == id } == true) {
            addError.value = "이미 이 재생목록에 있는 영상이에요."; return
        }
        adding.value = true
        addError.value = null
        viewModelScope.launch {
            try {
                val track = withContext(Dispatchers.IO) { app.youtube.resolve(id).track }
                app.library.update { list ->
                    check(list.any { it.id == playlistId }) { "재생목록이 삭제되었습니다." }
                    list.map { if (it.id == playlistId) it.add(track) else it }
                }
                onSuccess()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                addError.value = "영상을 불러오지 못했어요. 인터넷 연결과 공개 영상인지 확인해 주세요. 계속 실패하면 유튜브 연동 업데이트가 필요할 수 있습니다."
            } finally { adding.value = false }
        }
    }

    fun play(tracks: List<Track>, index: Int? = null, playlistId: String = selectedId.value) {
        if (tracks.isEmpty()) return
        val player = controller ?: run { message.value = "재생 서비스를 연결하고 있어요. 잠시 후 다시 시도해 주세요."; return }
        app.playbackStore.save(player, durable = true)
        val point = resumePoint(tracks, app.playbackStore.bookmark(playlistId), index)
        player.setMediaItems(tracks.map { it.toMediaItem(playlistId) }, point.index, point.positionMs)
        player.prepare()
        player.play()
    }
    fun togglePlay() {
        controller?.let {
            if (it.mediaItemCount == 0) return
            if (it.playerError != null || it.playbackState == Player.STATE_IDLE) { it.prepare(); it.play() }
            else if (it.playbackState == Player.STATE_ENDED) { it.seekToDefaultPosition(0); it.play() }
            else if (it.playWhenReady) it.pause() else it.play()
        }
    }
    fun previous() { controller?.seekToPrevious() }
    fun next() { controller?.seekToNextMediaItem() }
    fun seek(position: Long) { controller?.seekTo(position) }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; app.playbackStore.save(it, durable = true) } }
    fun repeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3; app.playbackStore.save(it, durable = true) } }
    fun stop() {
        app.sleepTimer.cancel()
        controller?.run { app.playbackStore.save(this, durable = true); pause(); stop() }
        refresh()
    }

    fun setSleepTimer(minutes: Int) {
        if (controller?.isConnected != true) return
        app.sleepTimer.startPreset(minutes)
        refresh()
        message.value = "${minutes}분 후 재생이 종료됩니다."
    }

    fun cancelSleepTimer() {
        app.sleepTimer.cancel()
        refresh()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
