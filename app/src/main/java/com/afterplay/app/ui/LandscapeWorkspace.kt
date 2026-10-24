package com.afterplay.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.afterplay.app.data.PomodoroPhase
import com.afterplay.app.data.PomodoroSession
import com.afterplay.app.data.PomodoroNotice
import com.afterplay.app.data.PomodoroRound
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
    Row(Modifier.fillMaxSize().testTag("landscape_workspace").padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.weight(1f).fillMaxHeight().testTag("compact_player"),
            shape = RoundedCornerShape(24.dp), color = Surface) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("이어듣기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    FocusLockButton(locked, onLock = { model.lockFocus() }, tint = Muted)
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
    val dateFormat = remember { DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN) }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN) }
    val secondFormat = remember { DateTimeFormatter.ofPattern("ss", Locale.KOREAN) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1_000 - System.currentTimeMillis() % 1_000)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomy = maxHeight >= 560.dp
        val clockHeight = if (maxHeight < 300.dp) 80.dp else 96.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val clockModifier = if (roomy) Modifier.weight(1f) else Modifier.height(clockHeight)
            Surface(clockModifier.fillMaxWidth().testTag("clock_panel"), shape = RoundedCornerShape(24.dp),
                color = LocalPalette.current.clockSurface, border = BorderStroke(1.dp, Muted.copy(alpha = .16f))) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.align(Alignment.Center).padding(horizontal = 40.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(now.format(dateFormat), color = Muted, fontSize = 12.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(now.format(timeFormat), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                                fontSize = if (roomy) 50.sp else 40.sp, letterSpacing = (-2).sp,
                                modifier = Modifier.testTag("wall_clock"), maxLines = 1)
                            Text(now.format(secondFormat), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp,
                                color = Muted, modifier = Modifier.padding(bottom = if (roomy) 10.dp else 8.dp))
                        }
                    }
                    if (timer.running) Icon(Icons.Rounded.WbSunny, "뽀모도로 실행 중 화면 켜짐 유지",
                        tint = Accent, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(18.dp))
                }
            }
            PomodoroPanel(timer, notice, model, Modifier.weight(1f).fillMaxWidth(), onSettings)
        }
    }
}

@Composable
private fun PomodoroPanel(timer: PomodoroSession, notice: PomodoroNotice?, model: PlayerViewModel, modifier: Modifier, onSettings: () -> Unit) {
    val palette = LocalPalette.current
    val accent = if (timer.phase == PomodoroPhase.FOCUS) Accent else palette.breakAccent
    var inverted by remember { mutableStateOf(false) }
    LaunchedEffect(notice) {
        while (notice != null && notice.activeAt(System.currentTimeMillis())) {
            inverted = (System.currentTimeMillis() - notice.startedAtMs) / 1_000 % 2 == 0L
            delay(100)
        }
        inverted = false
    }
    val cardColor = if (inverted) accent else palette.surface
    val foreground = if (inverted) palette.background else accent
    val muted = if (inverted) palette.background else palette.muted
    Surface(modifier.testTag("pomodoro_panel").semantics { stateDescription = if (inverted) "완료 알림 반전" else "기본" },
        shape = RoundedCornerShape(24.dp), color = cardColor, contentColor = foreground,
        border = BorderStroke(1.dp, accent.copy(alpha = .25f))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 280.dp
            val preferredSize = if (maxHeight < 160.dp) 56f else if (compact) 64f else 88f
            val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            val digits = clockTime(timer.remainingMs + 999).length
            val timerSize = minOf(preferredSize, (maxWidth.value - 32) / (digits * .62f * scale)).sp
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp, Alignment.CenterVertically)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val live = timer.running && timer.phase == PomodoroPhase.FOCUS
                    Row(Modifier.testTag("pomodoro_status_badge").semantics {
                        stateDescription = if (live) "ON AIR · 집중 진행 중" else "${timer.phase.label} · 일반 표시"
                    }.shadow(if (live) 6.dp else 0.dp, RoundedCornerShape(6.dp),
                        ambientColor = foreground.copy(alpha = .3f), spotColor = foreground.copy(alpha = .3f))
                        .background(if (live) foreground else foreground.copy(alpha = .07f), RoundedCornerShape(6.dp))
                        .border(1.dp, foreground.copy(alpha = if (live) 1f else .2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (live) Box(Modifier.size(6.dp).background(cardColor, CircleShape))
                        Text("집중", color = if (live) cardColor else muted,
                            fontWeight = if (live) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 14.sp,
                            letterSpacing = if (live) 2.sp else 0.sp, modifier = Modifier.testTag("pomodoro_phase"))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSettings, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Tune, "뽀모도로 시간 설정", tint = muted, modifier = Modifier.size(18.dp))
                    }
                }
                // Keep the timer heading visible when its controls need to scroll.
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp)) {
                    Text(clockTime(timer.remainingMs + 999), fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium, color = foreground, fontSize = timerSize, maxLines = 1,
                        modifier = Modifier.testTag("pomodoro_remaining"))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        timer.roundResults.forEachIndexed { index, result ->
                            Text(result.symbol, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                color = if (result == PomodoroRound.PENDING) muted else foreground,
                                modifier = Modifier.testTag("pomodoro_round_$index").semantics {
                                    contentDescription = "${index + 1}회차 ${result.label}"
                                })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = model::resetPomodoro, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.RestartAlt, "뽀모도로 초기화", tint = muted)
                        }
                        Button(onClick = model::togglePomodoro, modifier = Modifier.weight(1f).height(42.dp).testTag("pomodoro_toggle"),
                            colors = ButtonDefaults.buttonColors(containerColor = foreground, contentColor = cardColor)) {
                            Icon(if (timer.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (timer.running) "일시정지" else if (timer.remainingMs == timer.durationMs) "${timer.phase.label} 시작" else "계속", fontSize = 13.sp)
                        }
                        IconButton(onClick = model::skipPomodoro, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.SkipNext, "다음 뽀모도로 단계", tint = muted)
                        }
                    }
                }
            }
        }
    }
}
