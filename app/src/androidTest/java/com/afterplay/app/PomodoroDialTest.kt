package com.afterplay.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.data.*
import com.afterplay.app.playback.PomodoroTimer
import com.afterplay.app.ui.AfterPlayTheme
import com.afterplay.app.ui.PomodoroDial
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PomodoroDialTest {
    @get:Rule val compose = createComposeRule()

    @Test fun countdownAndRestUseOppositeProgressAndKeepEveryControlInsideTheCircle() {
        var session by mutableStateOf(PomodoroSession())
        var preset by mutableStateOf(ThemePreset.BURGUNDY)
        compose.setContent {
            AfterPlayTheme(preset) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PomodoroDial(session, null, Modifier.size(300.dp), {}, {}, {}, {})
                }
            }
        }
        fun assertFraction(value: Float) {
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("pomodoro_dial_ring").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(value, 0f..1f)))
        }
        assertFraction(1f)
        compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("25:00")
        compose.runOnIdle { session = session.copy(remainingMs = 12 * 60_000L + 30_000) }
        assertFraction(.5f)
        compose.runOnIdle { session = PomodoroSession(phase = PomodoroPhase.SHORT_BREAK) }
        assertFraction(0f)
        compose.onNodeWithTag("pomodoro_phase").assertTextEquals("휴식")
        compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("05:00")
        compose.runOnIdle { session = session.copy(remainingMs = 2 * 60_000L + 30_000) }
        assertFraction(.5f)
        compose.runOnIdle { session = session.copy(remainingMs = 0) }
        assertFraction(1f)
        for (theme in ThemePreset.entries) {
            compose.runOnIdle { preset = theme }
            val dial = compose.onNodeWithTag("pomodoro_dial").fetchSemanticsNode().boundsInRoot
            val center = dial.center
            val radius = dial.width / 2
            val controls = listOf(compose.onNodeWithTag("pomodoro_remaining"), compose.onNodeWithTag("pomodoro_toggle"),
                compose.onNodeWithTag("pomodoro_status_badge"), compose.onNodeWithContentDescription("뽀모도로 초기화"),
                compose.onNodeWithContentDescription("다음 뽀모도로 단계"), compose.onNodeWithContentDescription("뽀모도로 시간 설정")) +
                (0 until 4).map { compose.onNodeWithTag("pomodoro_round_$it") }
            controls.forEach { node ->
                node.assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                for (corner in listOf(bounds.topLeft, bounds.topRight, bounds.bottomLeft, bounds.bottomRight))
                    assertTrue("Control must stay inside the circular dial: $bounds", (corner - center).getDistance() < radius)
            }
        }
    }

    @Test fun finalLongRestStopsAndPersistsWithoutRestartingUntilStartIsPressed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var timer: PomodoroTimer
        instrumentation.runOnMainSync {
            context.getSharedPreferences("pomodoro_v1", Context.MODE_PRIVATE).edit().clear()
                .putString("phase", "LONG_BREAK").putLong("remaining_ms", 1_500).putBoolean("running", true)
                .putLong("deadline_ms", System.currentTimeMillis() + 1_500).putInt("completed", 1)
                .putInt("repeat_count", 1).putInt("round_index", 0).putString("round_results", "COMPLETED").commit()
            timer = PomodoroTimer(context)
        }
        try {
            compose.setContent {
                val state by timer.state.collectAsState()
                val notice by timer.notice.collectAsState()
                AfterPlayTheme(ThemePreset.BURGUNDY) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PomodoroDial(state, notice, Modifier.size(300.dp), timer::toggle, timer::reset, timer::skip, {})
                    }
                }
            }
            compose.waitUntil(8_000) { timer.state.value.finished }
            compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("00:00")
            compose.onNodeWithTag("pomodoro_phase").assertTextEquals("종료")
            compose.onNodeWithTag("pomodoro_round_0").assertTextEquals("●")
            compose.onNodeWithContentDescription("다음 뽀모도로 단계").assertIsNotEnabled()
            assertFalse(timer.state.value.running)
            assertEquals(PomodoroPhase.LONG_BREAK, timer.notice.value?.completedPhase)
            instrumentation.runOnMainSync {
                val restored = PomodoroTimer(context).state.value
                assertTrue(restored.finished)
                assertFalse(restored.running)
                assertEquals(0, restored.remainingMs)
                assertEquals(timer.state.value.roundResults, restored.roundResults)
            }
            compose.onNodeWithTag("pomodoro_toggle").assertContentDescriptionEquals("뽀모도로 다시 시작").performClick()
            assertTrue(timer.state.value.running)
            assertFalse(timer.state.value.finished)
            compose.onNodeWithTag("pomodoro_round_0").assertTextEquals("○")
            compose.onNodeWithTag("pomodoro_toggle").assertContentDescriptionEquals("뽀모도로 일시정지").performClick()
            compose.onNodeWithContentDescription("뽀모도로 초기화").performClick()
            compose.onNodeWithTag("pomodoro_remaining").assertTextEquals("25:00")
            assertFalse(timer.state.value.running)
        } finally {
            instrumentation.runOnMainSync { timer.configure(PomodoroConfig()) }
        }
    }
}
