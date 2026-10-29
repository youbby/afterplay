package com.afterplay.app.data

import org.junit.Assert.*
import org.junit.Test

class PomodoroPreparationTest {
    private val now = 1_000_000L

    @Test fun preparationDoesNotConsumeFocusTimeOrStartFocus() {
        val initial = PomodoroSession()
        val preparing = initial.start(now)
        assertTrue(preparing.running)
        assertTrue(preparing.preparing)
        assertFalse(preparing.focusStarted)
        assertEquals(10_000L, preparing.displayRemainingMs)
        val almost = preparing.advance(now + 9_999)
        assertEquals(1L, almost.displayRemainingMs)
        assertEquals(initial.durationMs, almost.remainingMs)
        val focused = almost.advance(now + 10_000)
        assertFalse(focused.preparing)
        assertTrue(focused.focusStarted)
        assertEquals(initial.durationMs, focused.remainingMs)
        assertEquals(now + 10_000 + initial.durationMs, focused.deadlineMs)
        assertNull(PomodoroNotice.between(preparing, focused, now + 10_000))
    }

    @Test fun pausingPreparationResumesOnlyTheRemainingPreparation() {
        val paused = PomodoroSession().start(now).pause(now + 3_000)
        assertTrue(paused.preparing)
        assertFalse(paused.running)
        assertEquals(7_000L, paused.preparationRemainingMs)
        assertEquals(paused, paused.advance(now + 100_000))
        val resumed = paused.start(now + 100_000)
        assertEquals(now + 107_000, resumed.deadlineMs)
        val focused = resumed.advance(now + 107_000)
        assertFalse(focused.preparing)
        assertEquals(focused.durationMs, focused.remainingMs)
        val continued = focused.pause(now + 108_000).start(now + 200_000)
        assertFalse(continued.preparing)
        assertEquals(focused.durationMs - 1_000, continued.remainingMs)
    }

    @Test fun delayedTickKeepsOriginalFocusDeadlineAndNotice() {
        val preparing = PomodoroSession().start(now)
        val focused = preparing.advance(now + 12_345)
        assertEquals(focused.durationMs - 2_345, focused.remainingMs)
        val rest = preparing.advance(now + 10_000 + focused.durationMs + 500)
        assertEquals(PomodoroPhase.SHORT_BREAK, rest.phase)
        assertEquals(rest.durationMs - 500, rest.remainingMs)
        assertEquals(PomodoroPhase.FOCUS,
            PomodoroNotice.between(preparing, rest, now + 10_000 + focused.durationMs + 500)?.completedPhase)
    }

    @Test fun skippingPreparationDoesNotCountAsAnInterruptedFocus() {
        val rest = PomodoroSession().start(now).skip(now + 2_000)
        assertFalse(rest.preparing)
        assertEquals(PomodoroRound.PENDING, rest.roundResults.first())
        val second = rest.advance(rest.deadlineMs)
        assertFalse(second.preparing)
        assertTrue(second.focusStarted)
    }

    @Test fun preparationRunsOncePerProgramAndReturnsOnExplicitRestart() {
        for (repeat in 1..4) {
            val config = PomodoroConfig(repeatCount = repeat)
            var session = PomodoroSession(config = config).start(now).advance(now + 10_000)
            while (!session.finished) {
                assertFalse(session.preparing)
                session = session.advance(session.deadlineMs)
            }
            assertEquals(now + 10_000 + config.cycleMs, session.finishedAtMs)
            assertTrue(session.start(now + 10_000 + config.cycleMs).preparing)
        }
    }
}
