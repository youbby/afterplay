package com.afterplay.app.data

import org.junit.Assert.*
import org.junit.Test

class PomodoroSessionTest {
    private val start = 1_000_000L
    private fun minutes(value: Int) = value * 60_000L

    @Test fun deadlineStartsBreakWithoutAccumulatingTickDrift() {
        val timer = PomodoroSession().start(start)
        val at24 = timer.advance(start + minutes(24))
        assertEquals(minutes(1), at24.remainingMs)
        val afterExpiry = at24.advance(start + minutes(26))
        assertEquals(PomodoroPhase.SHORT_BREAK, afterExpiry.phase)
        assertEquals(minutes(4), afterExpiry.remainingMs)
        assertEquals(1, afterExpiry.completedFocusSessions)
        assertTrue(afterExpiry.running)
    }

    @Test fun fourthCompletedFocusStartsLongBreakThenNewCycle() {
        val timer = PomodoroSession().start(start).advance(start + minutes(115))
        assertEquals(PomodoroPhase.LONG_BREAK, timer.phase)
        assertEquals(minutes(15), timer.remainingMs)
        assertEquals(4, timer.completedFocusSessions)
        assertEquals(4, timer.round)
        val nextCycle = timer.advance(start + minutes(130))
        assertEquals(PomodoroPhase.FOCUS, nextCycle.phase)
        assertEquals(1, nextCycle.round)
        assertEquals(minutes(25), nextCycle.remainingMs)
    }

    @Test fun pauseResumePreservesElapsedTimeAndPhase() {
        val paused = PomodoroSession().start(start).pause(start + minutes(10))
        assertFalse(paused.running)
        assertEquals(minutes(15), paused.remainingMs)
        assertEquals(paused, paused.advance(start + minutes(100)))
        val resumed = paused.start(start + minutes(100)).advance(start + minutes(101))
        assertEquals(minutes(14), resumed.remainingMs)
    }

    @Test fun skippingFocusDoesNotAwardCompletedSession() {
        val next = PomodoroSession().start(start).skip(start + minutes(2))
        assertEquals(PomodoroPhase.SHORT_BREAK, next.phase)
        assertEquals(0, next.completedFocusSessions)
        assertEquals(minutes(5), next.remainingMs)
        val afterLongAbsence = next.advance(start + minutes(132))
        assertEquals(PomodoroPhase.LONG_BREAK, afterLongAbsence.phase)
        assertEquals(minutes(5), afterLongAbsence.remainingMs)
        assertEquals(4, afterLongAbsence.completedFocusSessions)
    }

    @Test fun pausedSkipWaitsForUserToStartNextPhase() {
        val next = PomodoroSession().skip(start)
        assertEquals(PomodoroPhase.SHORT_BREAK, next.phase)
        assertFalse(next.running)
        assertEquals(0, next.deadlineMs)
        assertEquals(PomodoroPhase.FOCUS, next.skip(start).phase)
    }

    @Test fun overdueRestoredSessionCatchesUpAcrossManyCycles() {
        val original = PomodoroSession().start(start)
        val restored = original.copy().advance(start + minutes(130 * 100 + 31))
        assertEquals(PomodoroPhase.FOCUS, restored.phase)
        assertEquals(minutes(24), restored.remainingMs)
        assertEquals(401, restored.completedFocusSessions)
        assertEquals(2, restored.round)
    }

    @Test fun customDurationsApplyToEveryPhaseAndCatchUpCycle() {
        val config = PomodoroConfig(40, 7, 20)
        val timer = PomodoroSession(config = config).start(start)
        assertEquals(minutes(40), timer.remainingMs)
        val shortBreak = timer.advance(start + minutes(40))
        assertEquals(PomodoroPhase.SHORT_BREAK, shortBreak.phase)
        assertEquals(minutes(7), shortBreak.remainingMs)
        val longBreak = timer.advance(start + minutes(181))
        assertEquals(PomodoroPhase.LONG_BREAK, longBreak.phase)
        assertEquals(minutes(20), longBreak.remainingMs)
        val restored = timer.advance(start + minutes(201 * 50 + 41))
        assertEquals(PomodoroPhase.SHORT_BREAK, restored.phase)
        assertEquals(minutes(6), restored.remainingMs)
        assertEquals(201, restored.completedFocusSessions)
    }

    @Test fun customPauseAndSkipRetainTheConfiguredDurations() {
        val config = PomodoroConfig(50, 3, 27)
        val paused = PomodoroSession(config = config).start(start).pause(start + minutes(12))
        assertEquals(minutes(38), paused.remainingMs)
        val skipped = paused.skip(start + minutes(50))
        assertEquals(minutes(3), skipped.remainingMs)
        assertFalse(skipped.running)
        assertEquals(minutes(50), skipped.skip(start + minutes(51)).remainingMs)
        assertEquals(config, skipped.config)
    }

    @Test fun invalidDurationSettingsAreRejected() {
        assertTrue(runCatching { PomodoroConfig(0, 5, 15) }.isFailure)
        assertTrue(runCatching { PomodoroConfig(25, 61, 15) }.isFailure)
        assertTrue(runCatching { PomodoroConfig(25, 5, 121) }.isFailure)
        assertEquals(minutes(180), PomodoroConfig(180, 60, 120).durationMs(PomodoroPhase.FOCUS))
    }
}
