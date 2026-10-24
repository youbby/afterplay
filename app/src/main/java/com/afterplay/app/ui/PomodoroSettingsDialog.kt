package com.afterplay.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterplay.app.PlayerViewModel
import com.afterplay.app.data.PomodoroConfig

@Composable
internal fun PomodoroSettingsDialog(model: PlayerViewModel, dismiss: () -> Unit) {
    val timer by model.pomodoro.collectAsStateWithLifecycle()
    var focus by rememberSaveable { mutableStateOf(timer.config.focusMinutes.toString()) }
    var rest by rememberSaveable { mutableStateOf(timer.config.shortBreakMinutes.toString()) }
    var repeat by rememberSaveable { mutableStateOf(timer.config.repeatCount.toString()) }
    var longRest by rememberSaveable { mutableStateOf(timer.config.longBreakMinutes.toString()) }
    val valid = (focus.toIntOrNull() ?: 0) in 20..60 && (rest.toIntOrNull() ?: 0) in 5..10 &&
        (repeat.toIntOrNull() ?: 0) in 1..4 && (longRest.toIntOrNull() ?: 0) in 30..60
    AlertDialog(onDismissRequest = dismiss, title = { Text("뽀모도로 설정") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DurationField("집중 시간", "focus_minutes", focus, 20..60) { focus = it }
                DurationField("휴식 시간", "short_break_minutes", rest, 5..10) { rest = it }
                DurationField("반복 횟수", "repeat_count", repeat, 1..4, unit = "회") { repeat = it }
                DurationField("긴 휴식 시간", "long_break_minutes", longRest, 30..60) { longRest = it }
            }
        }, confirmButton = {
            TextButton(enabled = valid, onClick = {
                model.configurePomodoro(PomodoroConfig(focus.toInt(), rest.toInt(), longRest.toInt(), repeat.toInt()))
                dismiss()
            }) { Text("저장") }
        }, dismissButton = { TextButton(onClick = dismiss) { Text("취소") } })
}
