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
        assertEquals(minutes(30), timer.remainingMs)
        assertEquals(4, timer.completedFocusSessions)
        assertEquals(4, timer.round)
        val nextCycle = timer.advance(start + minutes(145))
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
        assertEquals(PomodoroPhase.FOCUS, afterLongAbsence.phase)
        assertEquals(minutes(15), afterLongAbsence.remainingMs)
        assertEquals(3, afterLongAbsence.completedFocusSessions)
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
        val restored = original.copy().advance(start + minutes(145 * 100 + 31))
        assertEquals(PomodoroPhase.FOCUS, restored.phase)
        assertEquals(minutes(24), restored.remainingMs)
        assertEquals(401, restored.completedFocusSessions)
        assertEquals(2, restored.round)
    }

    @Test fun customDurationsApplyToEveryPhaseAndCatchUpCycle() {
        val config = PomodoroConfig(40, 7, 30)
        val timer = PomodoroSession(config = config).start(start)
        assertEquals(minutes(40), timer.remainingMs)
        val shortBreak = timer.advance(start + minutes(40))
        assertEquals(PomodoroPhase.SHORT_BREAK, shortBreak.phase)
        assertEquals(minutes(7), shortBreak.remainingMs)
        val longBreak = timer.advance(start + minutes(181))
        assertEquals(PomodoroPhase.LONG_BREAK, longBreak.phase)
        assertEquals(minutes(30), longBreak.remainingMs)
        val restored = timer.advance(start + minutes(211 * 50 + 41))
        assertEquals(PomodoroPhase.SHORT_BREAK, restored.phase)
        assertEquals(minutes(6), restored.remainingMs)
        assertEquals(201, restored.completedFocusSessions)
    }

    @Test fun customPauseAndSkipRetainTheConfiguredDurations() {
        val config = PomodoroConfig(50, 5, 30)
        val paused = PomodoroSession(config = config).start(start).pause(start + minutes(12))
        assertEquals(minutes(38), paused.remainingMs)
        val skipped = paused.skip(start + minutes(50))
        assertEquals(minutes(5), skipped.remainingMs)
        assertFalse(skipped.running)
        assertEquals(minutes(50), skipped.skip(start + minutes(51)).remainingMs)
        assertEquals(config, skipped.config)
    }

    @Test fun invalidDurationSettingsAreRejected() {
        for (config in listOf({ PomodoroConfig(19, 5, 30) }, { PomodoroConfig(61, 5, 30) },
            { PomodoroConfig(25, 4, 30) }, { PomodoroConfig(25, 11, 30) }, { PomodoroConfig(25, 5, 29) },
            { PomodoroConfig(25, 5, 61) }, { PomodoroConfig(repeatCount = 0) }, { PomodoroConfig(repeatCount = 5) }))
            assertTrue(runCatching { config() }.isFailure)
        assertEquals(minutes(60), PomodoroConfig(60, 10, 60, 4).durationMs(PomodoroPhase.FOCUS))
    }

    @Test fun roundIconsDistinguishUnstartedCompletedAndInterruptedFocus() {
        val config = PomodoroConfig(20, 5, 30, 2)
        val initial = PomodoroSession(config = config)
        assertEquals(listOf(PomodoroRound.PENDING, PomodoroRound.PENDING), initial.roundResults)
        val finished = initial.start(start).advance(start + minutes(20))
        assertEquals(listOf(PomodoroRound.COMPLETED, PomodoroRound.PENDING), finished.roundResults)
        val second = finished.skip(start + minutes(20))
        assertEquals(2, second.round)
        val interrupted = second.skip(start + minutes(21))
        assertEquals(PomodoroPhase.LONG_BREAK, interrupted.phase)
        assertEquals(listOf(PomodoroRound.COMPLETED, PomodoroRound.SKIPPED), interrupted.roundResults)
        assertEquals(1, interrupted.completedFocusSessions)
        val fresh = interrupted.skip(start + minutes(22))
        assertEquals(1, fresh.round)
        assertEquals(initial.roundResults, fresh.roundResults)
        assertEquals(PomodoroRound.PENDING, initial.skip(start).roundResults.first())
    }

    @Test fun pausingDoesNotMarkTriangleUntilFocusIsActuallySkipped() {
        val paused = PomodoroSession().start(start).pause(start + minutes(1))
        assertEquals(PomodoroRound.PENDING, paused.roundResults.first())
        assertEquals(PomodoroRound.SKIPPED, paused.skip(start + minutes(2)).roundResults.first())
    }

    @Test fun eachConfiguredRepeatCountUsesLongBreakAtItsOwnBoundary() {
        for (repeat in 1..4) {
            val config = PomodoroConfig(20, 5, 30, repeat)
            var timer = PomodoroSession(config = config).start(start)
            for (round in 1..repeat) {
                timer = timer.advance(timer.deadlineMs)
                assertEquals(round, timer.round)
                assertEquals(if (round == repeat) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK, timer.phase)
                assertEquals(PomodoroRound.COMPLETED, timer.roundResults[round - 1])
                if (round != repeat) timer = timer.advance(timer.deadlineMs)
            }
            assertEquals(repeat, timer.completedFocusSessions)
            timer = timer.advance(timer.deadlineMs)
            assertEquals(1, timer.round)
            assertTrue(timer.roundResults.all { it == PomodoroRound.PENDING })
        }
    }

    @Test fun fastCatchUpMatchesStepByStepAfterSkipsForEveryRepeatCount() {
        for (repeat in 1..4) {
            val config = PomodoroConfig(20, 5, 30, repeat)
            val initial = PomodoroSession(config = config).start(start)
            for (base in listOf(initial, initial.skip(start + 1_000), initial.advance(initial.deadlineMs))) {
                for (offset in listOf(0L, config.cycleMs + 12_345, config.cycleMs * 100 + 67_890)) {
                    val now = base.deadlineMs + offset
                    var reference = base
                    while (now >= reference.deadlineMs) reference = reference.advance(reference.deadlineMs)
                    reference = reference.advance(now)
                    assertEquals(reference, base.advance(now))
                }
            }
        }
    }
}
