package com.afterplay.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterplay.app.PlayerViewModel
import com.afterplay.app.data.PomodoroConfig
import com.afterplay.app.data.ThemePreset

@Composable
internal fun AppSettingsDialog(model: PlayerViewModel, theme: ThemePreset, initialTab: Int, dismiss: () -> Unit) {
    val timer by model.pomodoro.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var focus by rememberSaveable { mutableStateOf(timer.config.focusMinutes.toString()) }
    var shortBreak by rememberSaveable { mutableStateOf(timer.config.shortBreakMinutes.toString()) }
    var longBreak by rememberSaveable { mutableStateOf(timer.config.longBreakMinutes.toString()) }
    val valid = (focus.toIntOrNull() ?: 0) in 1..180 && (shortBreak.toIntOrNull() ?: 0) in 1..60 && (longBreak.toIntOrNull() ?: 0) in 1..120
    AlertDialog(onDismissRequest = dismiss, title = { Text("앱 설정") },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("테마") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("뽀모도로") })
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())
                    .padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tab == 0) {
                        Text("다섯 가지 색 조합으로 화면 전체를 바꿔보세요.\n선택한 테마는 바로 저장됩니다.", fontSize = 12.sp, color = Muted)
                        for (preset in ThemePreset.entries) ThemeCard(preset, preset == theme) { model.setTheme(preset) }
                    } else {
                        Text("집중과 두 가지 휴식 시간을 정해 주세요.", fontSize = 12.sp, color = Muted)
                        DurationField("집중 시간", "focus_minutes", focus, 1..180) { focus = it }
                        DurationField("짧은 휴식", "short_break_minutes", shortBreak, 1..60) { shortBreak = it }
                        DurationField("긴 휴식", "long_break_minutes", longBreak, 1..120) { longBreak = it }
                        Text("집중 4회마다 긴 휴식으로 자동 전환합니다.", fontSize = 12.sp, color = Muted)
                        Text("시간을 저장하면 진행 중인 타이머가 초기화되고 일시정지됩니다. 음악은 계속 재생됩니다.",
                            fontSize = 12.sp, color = Muted)
                    }
                }
            }
        },
        confirmButton = {
            if (tab == 1) TextButton(enabled = valid, onClick = {
                model.configurePomodoro(PomodoroConfig(focus.toInt(), shortBreak.toInt(), longBreak.toInt()))
                dismiss()
            }) { Text("시간 저장") }
            else TextButton(onClick = dismiss) { Text("완료") }
        },
        dismissButton = { if (tab == 1) TextButton(onClick = dismiss) { Text("취소") } },
    )
}

@Composable
private fun ThemeCard(preset: ThemePreset, active: Boolean, onSelect: () -> Unit) {
    val colors = preset.colors()
    Column(Modifier.fillMaxWidth().testTag("theme_${preset.name}")
        .border(if (active) 2.dp else 1.dp, if (active) Accent else Muted.copy(alpha = .25f), RoundedCornerShape(16.dp))
        .background(colors.background, RoundedCornerShape(16.dp))
        .clickable(role = Role.RadioButton, onClick = onSelect).semantics { selected = active }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(preset.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.ink, modifier = Modifier.weight(1f))
            if (active) Icon(Icons.Rounded.CheckCircle, "선택됨", tint = colors.emphasis, modifier = Modifier.size(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            preset.swatches.forEach { color ->
                Box(Modifier.size(22.dp).background(Color(color), CircleShape).border(1.dp, colors.ink.copy(alpha = .12f), CircleShape))
            }
        }
        Text(preset.description, fontSize = 10.sp, color = colors.muted)
    }
}

@Composable
private fun DurationField(label: String, tag: String, value: String, range: IntRange, change: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val valid = (value.toIntOrNull() ?: 0) in range
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${range.first}–${range.last}분", fontSize = 11.sp, color = Muted)
        }
        OutlinedTextField(value = value, onValueChange = { change(it.filter(Char::isDigit).take(3)) },
            modifier = Modifier.width(108.dp).testTag(tag), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }), suffix = { Text("분") },
            isError = !valid, label = { Text(label, fontSize = 11.sp) })
    }
}
