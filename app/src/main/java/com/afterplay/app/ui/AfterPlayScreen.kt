package com.afterplay.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.afterplay.app.PlaybackUi
import com.afterplay.app.PlayerViewModel
import com.afterplay.app.data.Playlist
import com.afterplay.app.data.AppMode
import com.afterplay.app.data.Track
import java.util.Locale

@Composable
fun AfterPlayScreen(model: PlayerViewModel, onAddQuickSettingsTile: () -> Unit = {}, onThemeChanged: (Boolean) -> Unit = {},
    onUnlockFocus: () -> Unit = {}) {
    val appMode by model.appMode.collectAsStateWithLifecycle()
    val workspace = appMode == AppMode.POMODORO
    val lists by model.playlists.collectAsStateWithLifecycle()
    val selectedId by model.selectedId.collectAsStateWithLifecycle()
    val loading by model.loadingLibrary.collectAsStateWithLifecycle()
    val playback by model.playback.collectAsStateWithLifecycle()
    val theme by model.theme.collectAsStateWithLifecycle()
    val timer by model.pomodoro.collectAsStateWithLifecycle()
    val focusLocked by model.focusLocked.collectAsStateWithLifecycle()
    val updateStatus by model.updateStatus.collectAsStateWithLifecycle()
    val hyperlapse by model.hyperlapse.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val view = LocalView.current
    val keepScreenOn = timer.running
    DisposableEffect(view, keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }
    LifecycleResumeEffect(model) {
        model.refreshPomodoro()
        onPauseOrDispose { }
    }
    var settingsTab by rememberSaveable { mutableStateOf<Int?>(null) }
    val message by model.message.collectAsStateWithLifecycle()
    val sharedLink by model.sharedLink.collectAsStateWithLifecycle()
    val adding by model.adding.collectAsStateWithLifecycle()
    val addError by model.addError.collectAsStateWithLifecycle()
    val selected = lists.find { it.id == selectedId }
    var newList by rememberSaveable { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Playlist?>(null) }
    var delete by remember { mutableStateOf<Playlist?>(null) }
    var addTo by rememberSaveable { mutableStateOf<String?>(null) }
    var linkText by rememberSaveable { mutableStateOf("") }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var transfer by remember { mutableStateOf<Pair<String, Track>?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
    LaunchedEffect(hyperlapse.message) {
        hyperlapse.message?.let { snackbar.showSnackbar(it); model.hyperlapse.dismissMessage() }
    }
    LaunchedEffect(sharedLink, selected?.id) {
        if (sharedLink != null && selected != null) {
            linkText = sharedLink.orEmpty(); addTo = selected.id
            model.sharedLink.value = null; model.addError.value = null
        }
    }
    LaunchedEffect(theme.dark) { onThemeChanged(theme.dark) }
    AfterPlayTheme(theme) {
        val libraryHeader: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, null, tint = Accent, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text("이어듣기", fontWeight = FontWeight.Bold, fontSize = 21.sp,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!workspace) {
                    FocusLockButton(focusLocked, onLock = { model.lockFocus() }, tint = Muted)
                    AppModeButton(appMode, model::toggleAppMode, Muted)
                }
                IconButton(onClick = { settingsTab = 0 }) { Icon(Icons.Rounded.Palette, "앱 설정", tint = Muted) }
                IconButton(onClick = { showAbout = true }) { Icon(Icons.Rounded.Info, "앱 정보", tint = Muted) }
            }
        }
        val libraryContent: @Composable () -> Unit = {
            LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("내 재생목록", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Spacer(Modifier.weight(1f))
                        if (workspace) IconButton(onClick = { showAbout = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Info, "앱 정보", tint = Muted, modifier = Modifier.size(20.dp))
                        }
                        TextButton(onClick = { newList = true }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text(" 새 목록")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(lists, key = { it.id }) { list ->
                            FilterChip(selected = list.id == selectedId, onClick = { model.selectPlaylist(list.id) },
                                border = if (list.id == playback.playlistId) BorderStroke(1.dp, Accent) else null,
                                modifier = Modifier.testTag("playlist_${list.id}").semantics {
                                    if (list.id == playback.playlistId) stateDescription = "현재 재생 목록"
                                },
                                label = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, Modifier.size(18.dp)) })
                        }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (!loading && selected == null) item {
                    EmptyState("나만의 목록을 만들어 보세요", "새 목록을 만든 뒤 유튜브 영상 링크를 추가하세요.")
                }
                selected?.let { list ->
                    item {
                        var menu by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(list.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${list.tracks.size}곡 · ${totalTime(list.tracks)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, "재생목록 관리") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("이름 변경") }, onClick = { rename = list; menu = false })
                                    DropdownMenuItem(text = { Text("재생목록 삭제") }, onClick = { delete = list; menu = false })
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { model.play(list.tracks) }, enabled = list.tracks.isNotEmpty() && playback.connected,
                                modifier = Modifier.weight(1f).height(48.dp)) {
                                Icon(Icons.Rounded.PlayArrow, null); Text("전체 재생")
                            }
                            OutlinedButton(onClick = { addTo = list.id; linkText = ""; model.addError.value = null },
                                modifier = Modifier.weight(1f).height(48.dp)) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)); Text("목록추가")
                            }
                        }
                    }
                    if (list.tracks.isEmpty()) item {
                        EmptyState("첫 번째 곡을 담아 보세요", "유튜브 링크를 붙여넣거나\n유튜브 앱에서 공유 → 이어듣기를 선택하세요.")
                    }
                    items(list.tracks, key = { it.id }) { track ->
                        TrackRow(track, list.tracks.indexOf(track), list.tracks.size, playback.id == track.id,
                            onPlay = { model.play(list.tracks, list.tracks.indexOf(track)) },
                            onMove = { model.moveTrack(list.id, track.id, it) },
                            onTransfer = { transfer = list.id to track },
                            onRemove = { model.removeTrack(list.id, track.id) })
                    }
                    if (list.tracks.isNotEmpty()) item {
                        Text("목록 편집은 다음 전체 재생부터 적용돼요.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        FocusTouchGuard(focusLocked, onUnlockFocus) {
        Scaffold(containerColor = Background, snackbarHost = { SnackbarHost(snackbar) },
            topBar = { if (!workspace) Box(Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) { libraryHeader() } },
            bottomBar = { if (!workspace) PlayerPanel(playback, model) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (workspace) LandscapeWorkspace(playback, model, selected, libraryContent,
                    onSettings = { settingsTab = it })
                else libraryContent()
            }
        }
        }
        if (!focusLocked) {
        settingsTab?.let { tab -> AppSettingsDialog(model, theme, tab) { settingsTab = null } }
        if (newList) NameDialog("새 재생목록", "", { newList = false }) { model.createPlaylist(it); newList = false }
        rename?.let { list -> NameDialog("이름 변경", list.name, { rename = null }) { model.renamePlaylist(list.id, it); rename = null } }
        delete?.let { list ->
            AlertDialog(onDismissRequest = { delete = null }, title = { Text("재생목록을 삭제할까요?") },
                text = { Text("‘${list.name}’ 목록과 저장한 영상 링크가 삭제됩니다.") },
                confirmButton = { TextButton(onClick = { model.deletePlaylist(list.id); delete = null }) { Text("삭제") } },
                dismissButton = { TextButton(onClick = { delete = null }) { Text("취소") } })
        }
        transfer?.let { (sourceId, track) ->
            val targets = lists.filterNot { it.id == sourceId }
            AlertDialog(onDismissRequest = { transfer = null },
                title = { Text("다른 재생목록으로 이동") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 13.sp)
                        if (targets.isEmpty()) Text("먼저 다른 재생목록을 만들어 주세요.", fontSize = 13.sp)
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(targets, key = { it.id }) { target ->
                                TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                                    model.transferTrack(sourceId, target.id, track.id)
                                    transfer = null
                                }) {
                                    Text(target.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (target.tracks.any { it.id == track.id }) Text("이미 있음", fontSize = 11.sp, color = Muted)
                                }
                            }
                        }
                        Text("현재 목록에서 옮깁니다. 대상 목록에 이미 있으면 중복으로 추가하지 않아요.", fontSize = 11.sp, color = Muted)
                    }
                }, confirmButton = { TextButton(onClick = { transfer = null }) { Text("취소") } })
        }
        addTo?.let { target ->
            AlertDialog(onDismissRequest = { if (!adding) addTo = null },
                title = { Text("유튜브 영상 추가") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("공개 영상의 링크를 붙여넣어 주세요.\n제목과 채널명은 자동으로 가져옵니다.", color = Muted, fontSize = 13.sp)
                        OutlinedTextField(value = linkText, onValueChange = { linkText = it; model.addError.value = null },
                            label = { Text("영상 링크") }, placeholder = { Text("https://youtu.be/…") },
                            enabled = !adding, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                            isError = addError != null)
                        addError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        if (adding) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("영상 정보를 불러오는 중…", fontSize = 12.sp, color = Muted) }
                    }
                },
                confirmButton = { TextButton(enabled = !adding && linkText.isNotBlank(), onClick = {
                    model.addTrack(target, linkText) { addTo = null; linkText = "" }
                }) { Text("추가") } },
                dismissButton = { TextButton(enabled = !adding, onClick = { addTo = null }) { Text("취소") } })
        }
        if (showAbout) AppInfoDialog(updateStatus, model::checkForUpdates, uriHandler::openUri,
            onAddQuickSettingsTile, onDismiss = { showAbout = false },
            onShowDiagnostics = { showAbout = false; showDiagnostics = true })
        if (showDiagnostics) {
            val snapshot by model.diagnostics.collectAsStateWithLifecycle()
            val network by model.diagnosticNetwork.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { model.refreshDiagnostics() }
            DiagnosticsDialog(snapshot, network, model::refreshDiagnostics,
                onCopy = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AfterPlay 문제 확인", model.diagnosticReport())) },
                onClear = model::clearDiagnostics, onDismiss = { showDiagnostics = false; showAbout = true })
        }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(60.dp).background(Surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = Accent, modifier = Modifier.size(28.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, fontSize = 12.sp, color = Muted, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun TrackRow(track: Track, index: Int, size: Int, active: Boolean, onPlay: () -> Unit, onMove: (Int) -> Unit, onTransfer: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onPlay).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Artwork(track.artwork, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = if (active) Accent else MaterialTheme.colorScheme.onSurface)
            Text("${track.artist} · ${clockTime(track.durationSeconds * 1000)}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 11.sp)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "${track.title} 관리", tint = Muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("위로 이동") }, enabled = index > 0, onClick = { menu = false; onMove(-1) })
                DropdownMenuItem(text = { Text("아래로 이동") }, enabled = index < size - 1, onClick = { menu = false; onMove(1) })
                DropdownMenuItem(text = { Text("다른 재생목록으로 이동") }, onClick = { menu = false; onTransfer() })
                DropdownMenuItem(text = { Text("목록에서 삭제") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
internal fun Artwork(url: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(LocalPalette.current.artwork), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = LocalPalette.current.onArtwork)
        if (url.isNotEmpty()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun PlayerPanel(state: PlaybackUi, model: PlayerViewModel) {
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    Surface(color = Surface, tonalElevation = 4.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            SleepTimerControls(state, onOpen = { showSleepTimer = true }, onCancel = model::cancelSleepTimer)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(state.artwork, Modifier.size(42.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(if (state.buffering) "오디오를 불러오는 중…" else state.artist,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Muted)
                }
            }
            if (state.id.isNotEmpty()) {
                var dragged by remember(state.id) { mutableStateOf<Float?>(null) }
                Slider(value = dragged ?: state.position.toFloat().coerceIn(0f, state.duration.toFloat().coerceAtLeast(1f)),
                    onValueChange = { dragged = it }, onValueChangeFinished = { dragged?.let { model.seek(it.toLong()) }; dragged = null },
                    valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f), enabled = state.duration > 0,
                    modifier = Modifier.fillMaxWidth().height(32.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clockTime(dragged?.toLong() ?: state.position), fontSize = 10.sp, color = Muted)
                    Text(clockTime(state.duration), fontSize = 10.sp, color = Muted)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, maxLines = 3) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Control(Icons.Rounded.Shuffle, if (state.shuffle) "셔플 켜짐" else "셔플 꺼짐", model::shuffle, active = state.shuffle)
                    Control(Icons.Rounded.SkipPrevious, "이전 곡", model::previous, enabled = state.canPrevious)
                    FilledIconButton(onClick = model::togglePlay, modifier = Modifier.size(52.dp)) {
                        Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (state.playing) "일시정지" else "재생", modifier = Modifier.size(30.dp))
                    }
                    Control(Icons.Rounded.SkipNext, "다음 곡", model::next, enabled = state.canNext)
                    Control(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        when(state.repeat) { Player.REPEAT_MODE_ONE -> "한 곡 반복"; Player.REPEAT_MODE_ALL -> "전체 반복"; else -> "반복 꺼짐" },
                        model::repeat, active = state.repeat != Player.REPEAT_MODE_OFF)
                }
            }
        }
    }
    if (showSleepTimer) SleepTimerDialog(state, model) { showSleepTimer = false }
}

@Composable
internal fun SleepTimerControls(state: PlaybackUi, onOpen: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().testTag("sleep_timer_controls"), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onOpen, enabled = state.connected,
            contentPadding = PaddingValues(horizontal = 0.dp)) {
            Icon(Icons.Rounded.Bedtime, "수면 모드 설정", Modifier.size(18.dp),
                tint = if (state.sleepTimerActive) Accent else Muted)
            Spacer(Modifier.width(6.dp))
            Text("수면 모드", color = if (state.sleepTimerActive) Accent else Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        if (state.sleepTimerActive) {
            Text("${clockTime(state.sleepRemainingMs + 999)} 후 종료", color = Accent, fontSize = 12.sp)
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "수면 타이머 해제", tint = Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun SleepTimerDialog(state: PlaybackUi, model: PlayerViewModel, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss,
        title = { Text("수면 모드") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("설정한 시간이 지나면 재생을 종료합니다.", color = Muted, fontSize = 13.sp)
                for (minutes in listOf(20, 40, 60)) {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        model.setSleepTimer(minutes)
                        dismiss()
                    }) { Text("${minutes}분") }
                }
                if (state.sleepTimerActive) Text("남은 시간 ${clockTime(state.sleepRemainingMs + 999)}",
                    color = Accent, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("닫기") } },
        dismissButton = {
            if (state.sleepTimerActive) TextButton(onClick = {
                model.cancelSleepTimer(); dismiss()
            }) { Text("타이머 해제") }
        })
}

@Composable
internal fun Control(icon: ImageVector, description: String, action: () -> Unit, active: Boolean = false, enabled: Boolean = true) {
    IconButton(onClick = action, enabled = enabled) {
        Icon(icon, description, tint = if (!enabled) Muted.copy(alpha = .3f) else if (active) Accent else Muted)
    }
}

@Composable
private fun NameDialog(title: String, initial: String, dismiss: () -> Unit, confirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it.take(60) }, singleLine = true,
            label = { Text("재생목록 이름") }, supportingText = { Text("${value.length}/60") }) },
        confirmButton = { TextButton(onClick = { confirm(value) }, enabled = value.isNotBlank()) { Text("저장") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("취소") } })
}

internal fun clockTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
private fun totalTime(tracks: List<Track>): String {
    val minutes = tracks.sumOf { it.durationSeconds } / 60
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}
