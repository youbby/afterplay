package com.afterplay.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterplay.app.BuildConfig
import com.afterplay.app.data.UpdateStatus

@Composable
internal fun AppInfoDialog(status: UpdateStatus, onCheckUpdates: () -> Unit, onOpenUrl: (String) -> Unit,
    onAddQuickSettingsTile: () -> Unit, onDismiss: () -> Unit, onShowDiagnostics: () -> Unit = {}) {
    var linkError by remember { mutableStateOf<String?>(null) }
    var more by rememberSaveable { mutableStateOf(false) }
    val openLink: (String) -> Unit = { url ->
        linkError = try { onOpenUrl(url); null }
        catch (_: Exception) { "링크를 열지 못했습니다. 브라우저가 설치되어 있는지 확인해 주세요." }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("이어듣기 · AFTERPLAY") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).testTag("app_info_content"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("현재 버전 ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("app_version"))
                OutlinedButton(onClick = { onDismiss(); onAddQuickSettingsTile() }, modifier = Modifier.fillMaxWidth().testTag("add_quick_settings")) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("퀵 설정에 실행 버튼 추가")
                }
                OutlinedButton(onClick = { linkError = null; onCheckUpdates() }, enabled = status != UpdateStatus.Checking,
                    modifier = Modifier.fillMaxWidth().testTag("check_updates")) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (status == UpdateStatus.Checking) "업데이트 확인 중…" else "업데이트 확인")
                }
                OutlinedButton(onClick = onShowDiagnostics, modifier = Modifier.fillMaxWidth().testTag("open_diagnostics")) {
                    Icon(Icons.Rounded.BugReport, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("문제 확인")
                }
                when (status) {
                    UpdateStatus.Idle -> Unit
                    UpdateStatus.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth().testTag("update_check_progress"))
                    is UpdateStatus.Current -> Text("사용 가능한 업데이트가 없습니다.", color = Muted, modifier = Modifier.testTag("update_status"))
                    is UpdateStatus.Error -> Text(status.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("update_status"))
                    is UpdateStatus.Available -> {
                        val release = status.release
                        Text("새 버전 ${release.version}이 있습니다.", fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("update_status"))
                        release.apkUrl?.let { url ->
                            Button(onClick = { openLink(url) }, modifier = Modifier.fillMaxWidth().testTag("download_update")) { Text("APK 다운로드") }
                            Text("브라우저에서 APK를 내려받아 설치하세요.", fontSize = 12.sp, color = Muted)
                        }
                        TextButton(onClick = { openLink(release.releaseUrl) }, modifier = Modifier.testTag("open_release")) {
                            Text(if (release.apkUrl == null) "릴리스 페이지 열기" else "변경 내용 보기")
                        }
                    }
                }
                linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { more = !more }, modifier = Modifier.testTag("app_info_more")) {
                    Icon(if (more) Icons.Rounded.Remove else Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (more) "접기" else "더보기")
                }
                if (more) {
                    HorizontalDivider()
                    Text("기능 설명", fontWeight = FontWeight.SemiBold)
                    Text("재생목록의 오디오를 화면이 꺼지거나 다른 앱을 사용하는 동안에도 재생합니다. 수면 타이머, 가로 시계·뽀모도로, 집중 잠금, 색상 프리셋을 지원합니다.", fontSize = 12.sp, color = Muted)
                    Text("유의사항", fontWeight = FontWeight.SemiBold)
                    Text("인터넷 연결이 필요합니다. 공개된 일반 영상만 지원하며, 비공개·유료·로그인 필요 영상과 실시간 방송은 지원하지 않습니다. YouTube 공식 앱이 아닙니다.", fontSize = 12.sp, color = Muted)
                    Text("YouTube 정책 변경", fontWeight = FontWeight.SemiBold)
                    Text("YouTube 정책이나 서비스 구조가 바뀌면 재생이 중단될 수 있습니다. 이 경우 업데이트 확인에서 새 버전을 확인해 주세요.", fontSize = 12.sp, color = Muted)
                    Text("오픈소스", fontWeight = FontWeight.SemiBold)
                    Text("NewPipe Extractor (GPL-3.0-or-later), AndroidX Media3 / Compose, OkHttp, Coil (Apache-2.0).", fontSize = 11.sp, color = Muted)
                    Text("앱 소스", fontWeight = FontWeight.SemiBold)
                    Text("AfterPlay · GPL-3.0-or-later", fontSize = 12.sp, color = Muted)
                    TextButton(onClick = { openLink("https://github.com/youbby/afterplay") }) { Text("GitHub 앱 소스 보기") }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } })
}
