package com.afterplay.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.afterplay.app.PlaybackUi
import com.afterplay.app.PlayerViewModel
import com.afterplay.app.data.Playlist
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun LandscapeWorkspace(
    playback: PlaybackUi,
    model: PlayerViewModel,
    selected: Playlist?,
    libraryContent: @Composable () -> Unit,
    onThemeSettings: () -> Unit,
    onPomodoroSettings: () -> Unit,
    onInfo: () -> Unit,
) {
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    val locked by model.focusLocked.collectAsStateWithLifecycle()
    val appMode by model.appMode.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 360.dp
        Row(Modifier.fillMaxSize().testTag("landscape_workspace").padding(if (compactHeight) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.weight(1f).fillMaxHeight().testTag("compact_player"),
                shape = RoundedCornerShape(24.dp), color = Surface) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = if (compactHeight) 6.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactHeight) 4.dp else 8.dp, Alignment.CenterVertically)) {
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("이어듣기", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        FocusLockButton(locked, onLock = { model.lockFocus() }, tint = Accent)
                        AppModeButton(appMode, model::toggleAppMode, Muted)
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                            IconButton(onClick = onThemeSettings, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Palette, "앱 설정", tint = Muted) }
                            IconButton(onClick = onInfo, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Info, "앱 정보", tint = Muted) }
                            IconButton(onClick = { showLibrary = !showLibrary }, modifier = Modifier.size(40.dp)) {
                                Icon(if (showLibrary) Icons.AutoMirrored.Rounded.ArrowBack else Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    if (showLibrary) "현재 재생으로 돌아가기" else "재생목록 열기", tint = Muted)
                            }
                        }
                    }
                    Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                        if (showLibrary) libraryContent() else CompactPlayer(playback, model, selected)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                ClockAndPomodoro(model, onSettings = onPomodoroSettings)
            }
        }
    }
}

@Composable
private fun CompactPlayer(state: PlaybackUi, model: PlayerViewModel, selected: Playlist?) {
    var showSleep by rememberSaveable { mutableStateOf(false) }
    var dragged by remember(state.id) { mutableStateOf<Float?>(null) }
    val hasItem = state.id.isNotEmpty()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tight = maxHeight < 230.dp
        val fontScale = LocalDensity.current.fontScale
        val gap = if (tight) 4.dp else 6.dp
        val sleepHeight = (if (tight) 36.dp else 40.dp).coerceAtLeast((16 * fontScale + 16).dp)
        val playSize = if (tight) 48.dp else 52.dp
        val seekHeight = 32.dp + (14 * fontScale).dp
        val errorHeight = if (state.error != null) 18.dp + gap else 0.dp
        val artworkSize = (maxHeight - sleepHeight - playSize - seekHeight - gap * 3 - errorHeight).coerceIn(32.dp, 72.dp)
        val showStatus = !tight && artworkSize >= (68 * fontScale).dp
        val titleLines = if (!showStatus && artworkSize >= (64 * fontScale).dp) 2 else 1
        Column(Modifier.fillMaxWidth().testTag("compact_player_content"),
            verticalArrangement = Arrangement.spacedBy(gap)) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                SleepTimerControls(state, onOpen = { showSleep = true }, onCancel = model::cancelSleepTimer,
                    modifier = Modifier.height(sleepHeight))
            }
            Row(Modifier.height(artworkSize), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Artwork(state.artwork, Modifier.size(artworkSize))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (showStatus) Text(if (state.buffering) "연결 중" else if (state.playing) "현재 재생 중" else "음악과 함께 집중해요",
                        color = Accent, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.title.ifBlank { "음악을 선택해 주세요" }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp,
                        maxLines = titleLines, overflow = TextOverflow.Ellipsis)
                    if (artworkSize >= (36 * fontScale + 4).dp) Text(state.artist.ifBlank { "나만의 집중 플레이리스트" }, color = Muted,
                        fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.height(seekHeight)) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                    Slider(value = dragged ?: state.position.toFloat().coerceIn(0f, state.duration.toFloat().coerceAtLeast(1f)),
                        onValueChange = { dragged = it }, onValueChangeFinished = { dragged?.let { model.seek(it.toLong()) }; dragged = null },
                        valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f), enabled = hasItem && state.duration > 0,
                        modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = "재생 위치" })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clockTime(dragged?.toLong() ?: state.position), color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    Text(clockTime(state.duration), color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                Row(Modifier.fillMaxWidth().height(playSize).testTag("compact_playback_controls"), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Control(Icons.Rounded.Shuffle, if (state.shuffle) "셔플 켜짐" else "셔플 꺼짐", model::shuffle, state.shuffle, hasItem, Modifier.size(40.dp))
                    Control(Icons.Rounded.SkipPrevious, "이전 곡", model::previous, enabled = state.canPrevious, modifier = Modifier.size(40.dp))
                    FilledIconButton(onClick = {
                        if (hasItem) model.togglePlay() else selected?.let { model.play(it.tracks) }
                    }, enabled = state.connected && (hasItem || selected?.tracks?.isNotEmpty() == true), modifier = Modifier.size(playSize)) {
                        Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (state.playing) "일시정지" else "재생", Modifier.size(30.dp))
                    }
                    Control(Icons.Rounded.SkipNext, "다음 곡", model::next, enabled = state.canNext, modifier = Modifier.size(40.dp))
                    Control(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        when (state.repeat) { Player.REPEAT_MODE_ONE -> "한 곡 반복"; Player.REPEAT_MODE_ALL -> "전체 반복"; else -> "반복 꺼짐" },
                        model::repeat, state.repeat != Player.REPEAT_MODE_OFF, hasItem, Modifier.size(40.dp))
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(18.dp)) }
        }
    }
    if (showSleep) SleepTimerDialog(state, model) { showSleep = false }
}

@Composable
internal fun ClockAndPomodoro(model: PlayerViewModel, onSettings: () -> Unit) {
    val timer by model.pomodoro.collectAsStateWithLifecycle()
    val notice by model.pomodoroNotice.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val dateTimeFormat = remember { DateTimeFormatter.ofPattern("M월 d일 EEEE  HH:mm:ss", Locale.KOREAN) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1_000 - System.currentTimeMillis() % 1_000)
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.height(36.dp).fillMaxWidth().testTag("clock_panel"), shape = RoundedCornerShape(12.dp),
            color = LocalPalette.current.clockSurface, border = BorderStroke(1.dp, Muted.copy(alpha = .16f))) {
            Box(Modifier.fillMaxSize()) {
                Text(now.format(dateTimeFormat), color = Muted, fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp).testTag("wall_clock"))
                if (timer.running) Icon(Icons.Rounded.WbSunny, "뽀모도로 실행 중 화면 켜짐 유지",
                    tint = Accent, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(16.dp))
            }
        }
        PomodoroDial(timer, notice, Modifier.weight(1f).fillMaxWidth(),
            onToggle = model::togglePomodoro, onReset = model::resetPomodoro,
            onSkip = model::skipPomodoro, onSettings = onSettings,
            recordingControl = { HyperlapseButton(model) })
    }
}
