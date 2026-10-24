package com.afterplay.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.afterplay.app.diagnostics.DiagnosticKind
import com.afterplay.app.diagnostics.DiagnosticSnapshot
import com.afterplay.app.diagnostics.diagnosticHint
import com.afterplay.app.diagnostics.displayLine

@Composable
internal fun DiagnosticsDialog(snapshot: DiagnosticSnapshot, network: String, onRefresh: () -> Unit,
    onCopy: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f).fillMaxHeight(.92f), shape = RoundedCornerShape(24.dp), color = Surface) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("문제 확인", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "문제 확인 닫기") }
                }
                Text(network, color = Accent, fontSize = 13.sp, modifier = Modifier.testTag("diagnostic_network"))
                Text(diagnosticHint(snapshot.events), fontSize = 12.sp, color = Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        message = runCatching { onCopy(); "로그를 복사했습니다." }.getOrElse { "로그를 복사하지 못했습니다." }
                    }, modifier = Modifier.weight(1f).testTag("copy_diagnostics")) { Text("로그 복사", fontSize = 12.sp) }
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("새로고침", fontSize = 12.sp) }
                    OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.weight(1f).testTag("clear_diagnostics")) { Text("지우기", fontSize = 12.sp) }
                }
                message?.let { Text(it, fontSize = 12.sp, color = Accent) }
                if (snapshot.storageError) Text("저장소 기록에 실패하여 일부 로그가 이번 실행에만 남아 있습니다.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("diagnostic_events"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        val events = snapshot.events
                        Text("네트워크 오류 ${events.count { it.kind == DiagnosticKind.NETWORK_ERROR }} · HTTP 오류 ${events.count { it.kind == DiagnosticKind.HTTP_ERROR }} · 재버퍼링 ${events.count { it.kind == DiagnosticKind.REBUFFER }}", fontSize = 12.sp)
                        Text("기록은 원인 판단의 단서입니다. 끊긴 직후 로그를 복사해 확인해 주세요. URL·IP·영상 제목은 저장하지 않습니다.", fontSize = 11.sp, color = Muted)
                        Text("최근 ${minOf(100, events.size)}건 표시 · 복사 시 보관 중인 전체 ${events.size}건 포함", fontSize = 11.sp, color = Muted)
                    }
                    if (snapshot.events.isEmpty()) item { Text("아직 기록이 없습니다. 재생 후 다시 확인해 주세요.", fontSize = 13.sp) }
                    items(snapshot.events.takeLast(100).asReversed()) { event ->
                        SelectionContainer { Text(event.displayLine(), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = if (event.kind in listOf(DiagnosticKind.NETWORK_ERROR, DiagnosticKind.HTTP_ERROR, DiagnosticKind.PLAYER_ERROR)) MaterialTheme.colorScheme.error else Muted) }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("로그를 지울까요?") },
        text = { Text("현재 보관된 로그를 지웁니다. 이후 재생 기록은 다시 쌓입니다.") },
        confirmButton = { TextButton(onClick = { onClear(); message = null; confirmClear = false }) { Text("로그 삭제") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } })
}
