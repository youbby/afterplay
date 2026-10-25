package com.afterplay.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterplay.app.data.PomodoroNotice
import com.afterplay.app.data.PomodoroPhase
import com.afterplay.app.data.PomodoroRound
import com.afterplay.app.data.PomodoroSession
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

internal fun pomodoroTime(remainingMs: Long): String {
    val seconds = (remainingMs.coerceAtLeast(0) + 999) / 1_000
    return "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
internal fun PomodoroDial(
    timer: PomodoroSession,
    notice: PomodoroNotice?,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    onSettings: () -> Unit,
    recordingControl: @Composable () -> Unit = {},
) {
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
    val filled by animateFloatAsState(timer.dialFraction, tween(250, easing = LinearEasing), label = "dial fill")
    val phaseLabel = if (timer.finished) "종료" else timer.phase.label
    Surface(modifier.testTag("pomodoro_panel").semantics { stateDescription = if (inverted) "완료 알림 반전" else "기본" },
        shape = RoundedCornerShape(24.dp), color = cardColor, contentColor = foreground,
        border = BorderStroke(1.dp, accent.copy(alpha = .25f))) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.TopEnd)) { recordingControl() }
            val diameter = minOf(maxWidth, maxHeight)
            val scale = (diameter.value / 280f).coerceIn(.7f, 1.5f)
            val innerSize = diameter * .72f
            val time = pomodoroTime(timer.remainingMs)
            val fontScale = LocalDensity.current.fontScale
            val timeSize = minOf(diameter.value * .21f, 100f, innerSize.value / (time.length * .62f * fontScale)).sp
            Box(Modifier.size(diameter).testTag("pomodoro_dial"), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().testTag("pomodoro_dial_ring").progressSemantics(filled).semantics {
                    contentDescription = if (timer.phase == PomodoroPhase.FOCUS) "집중 남은 시간 다이얼" else "휴식 경과 시간 다이얼"
                }) {
                    val stroke = (size.minDimension * .045f).coerceAtLeast(4.dp.toPx())
                    val inset = stroke / 2 + 2.dp.toPx()
                    val topLeft = Offset(inset, inset)
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val firstAngle = if (timer.phase == PomodoroPhase.FOCUS) (1f - filled) * 360f else 0f
                    val lastAngle = if (timer.phase == PomodoroPhase.FOCUS) 360f else filled * 360f
                    for (segment in 0 until 60) {
                        val start = segment * 6f + .65f
                        val end = (segment + 1) * 6f - .65f
                        drawArc(muted.copy(alpha = .12f), start - 90f, end - start, false,
                            topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                        val visibleStart = max(start, firstAngle)
                        val visibleEnd = min(end, lastAngle)
                        if (visibleEnd > visibleStart) drawArc(foreground, visibleStart - 90f, visibleEnd - visibleStart,
                            false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                    }
                }
                Column(Modifier.size(innerSize), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((6 * scale).dp)) {
                        timer.roundResults.forEachIndexed { index, result ->
                            Text(result.symbol, fontSize = (18 * scale).sp, fontWeight = FontWeight.SemiBold,
                                color = if (result == PomodoroRound.PENDING) muted else foreground,
                                modifier = Modifier.testTag("pomodoro_round_$index").semantics {
                                    contentDescription = "${index + 1}회차 ${result.label}"
                                })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val live = timer.running && timer.phase == PomodoroPhase.FOCUS
                        Row(Modifier.testTag("pomodoro_status_badge").semantics {
                            stateDescription = if (live) "ON AIR · 집중 진행 중" else "$phaseLabel · 일반 표시"
                        }.shadow(if (live) 6.dp else 0.dp, RoundedCornerShape(6.dp),
                            ambientColor = foreground.copy(alpha = .3f), spotColor = foreground.copy(alpha = .3f))
                            .background(if (live) foreground else foreground.copy(alpha = .07f), RoundedCornerShape(6.dp))
                            .border(1.dp, foreground.copy(alpha = if (live) 1f else .2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(phaseLabel, color = if (live) cardColor else muted,
                                fontWeight = if (live) FontWeight.ExtraBold else FontWeight.Medium, fontSize = (13 * scale).sp,
                                letterSpacing = if (live) 2.sp else 0.sp, maxLines = 1,
                                modifier = Modifier.testTag("pomodoro_phase"))
                        }
                        IconButton(onClick = onSettings, modifier = Modifier.size((30 * scale).dp)) {
                            Icon(Icons.Rounded.Tune, "뽀모도로 시간 설정", tint = muted, modifier = Modifier.size((18 * scale).dp))
                        }
                    }
                    Text(time, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                        color = foreground, fontSize = timeSize, maxLines = 1, softWrap = false,
                        modifier = Modifier.testTag("pomodoro_remaining"))
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides (40 * scale).dp) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((6 * scale).dp)) {
                            IconButton(onClick = onReset, modifier = Modifier.size((40 * scale).dp)) {
                                Icon(Icons.Rounded.RestartAlt, "뽀모도로 초기화", tint = muted)
                            }
                            val action = if (timer.running) "일시정지" else if (timer.finished) "다시 시작"
                                else if (timer.remainingMs == timer.durationMs) "${timer.phase.label} 시작" else "계속"
                            FilledIconButton(onClick = onToggle, modifier = Modifier.size((52 * scale).dp).testTag("pomodoro_toggle"),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = foreground, contentColor = cardColor)) {
                                Icon(if (timer.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    "뽀모도로 $action", modifier = Modifier.size((28 * scale).dp))
                            }
                            IconButton(onClick = onSkip, enabled = !timer.finished, modifier = Modifier.size((40 * scale).dp)) {
                                Icon(Icons.Rounded.SkipNext, "다음 뽀모도로 단계", tint = muted.copy(alpha = if (timer.finished) .35f else 1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
