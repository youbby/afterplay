package com.afterplay.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onSettings: (Int) -> Unit,
) {
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    val locked by model.focusLocked.collectAsStateWithLifecycle()
    val appMode by model.appMode.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxSize().testTag("landscape_workspace").padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.weight(1f).fillMaxHeight().testTag("compact_player"),
            shape = RoundedCornerShape(24.dp), color = Surface) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("이어듣기", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FocusLockButton(locked, onLock = { model.lockFocus() }, tint = Muted)
                    AppModeButton(appMode, model::toggleAppMode, Muted)
                    IconButton(onClick = { onSettings(0) }) { Icon(Icons.Rounded.Palette, "앱 설정", tint = Muted) }
                    IconButton(onClick = { showLibrary = !showLibrary }) {
                        Icon(if (showLibrary) Icons.AutoMirrored.Rounded.ArrowBack else Icons.AutoMirrored.Rounded.PlaylistPlay,
                            if (showLibrary) "현재 재생으로 돌아가기" else "재생목록 열기", tint = Muted)
                    }
                }
                Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    if (showLibrary) libraryContent() else CompactPlayer(playback, model, selected)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            ClockAndPomodoro(model, onSettings = { onSettings(1) })
        }
    }
}

@Composable
private fun CompactPlayer(state: PlaybackUi, model: PlayerViewModel, selected: Playlist?) {
    var showSleep by rememberSaveable { mutableStateOf(false) }
    var dragged by remember(state.id) { mutableStateOf<Float?>(null) }
    val hasItem = state.id.isNotEmpty()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SleepTimerControls(state, onOpen = { showSleep = true }, onCancel = model::cancelSleepTimer)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(state.artwork, Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (state.buffering) "연결 중" else if (state.playing) "현재 재생 중" else "음악과 함께 집중해요",
                    color = Accent, fontSize = 10.sp, letterSpacing = .5.sp)
                Text(state.title.ifBlank { "음악을 선택해 주세요" }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.artist.ifBlank { "나만의 집중 플레이리스트" }, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column {
            Slider(value = dragged ?: state.position.toFloat().coerceIn(0f, state.duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { dragged = it }, onValueChangeFinished = { dragged?.let { model.seek(it.toLong()) }; dragged = null },
                valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f), enabled = hasItem && state.duration > 0,
                modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = "재생 위치" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clockTime(dragged?.toLong() ?: state.position), color = Muted, fontSize = 10.sp)
                Text(clockTime(state.duration), color = Muted, fontSize = 10.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Control(Icons.Rounded.Shuffle, if (state.shuffle) "셔플 켜짐" else "셔플 꺼짐", model::shuffle, state.shuffle, hasItem)
            Control(Icons.Rounded.SkipPrevious, "이전 곡", model::previous, enabled = state.canPrevious)
            FilledIconButton(onClick = {
                if (hasItem) model.togglePlay() else selected?.let { model.play(it.tracks) }
            }, enabled = state.connected && (hasItem || selected?.tracks?.isNotEmpty() == true), modifier = Modifier.size(52.dp)) {
                Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.playing) "일시정지" else "재생", Modifier.size(30.dp))
            }
            Control(Icons.Rounded.SkipNext, "다음 곡", model::next, enabled = state.canNext)
            Control(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                when (state.repeat) { Player.REPEAT_MODE_ONE -> "한 곡 반복"; Player.REPEAT_MODE_ALL -> "전체 반복"; else -> "반복 꺼짐" },
                model::repeat, state.repeat != Player.REPEAT_MODE_OFF, hasItem)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
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
            onSkip = model::skipPomodoro, onSettings = onSettings)
    }
}
