package com.afterplay.app.data

import org.junit.Assert.*
import org.junit.Test

class PomodoroNoticeTest {
    private val start = 1_000_000L

    @Test fun automaticFocusAndBreakExpiryProduceCorrectCompletionNotice() {
        val focus = PomodoroSession().start(start)
        val breakTime = focus.deadlineMs
        val rest = focus.advance(breakTime)
        val notice = requireNotNull(PomodoroNotice.between(focus, rest, breakTime))
        assertEquals(PomodoroPhase.FOCUS, notice.completedPhase)
        assertTrue(notice.activeAt(breakTime + 9_999))
        assertFalse(notice.activeAt(breakTime + 10_000))
        val nextFocus = rest.advance(rest.deadlineMs)
        assertEquals(PomodoroPhase.SHORT_BREAK,
            PomodoroNotice.between(rest, nextFocus, rest.deadlineMs)?.completedPhase)
    }

    @Test fun oldBackgroundTransitionsDoNotFlashOnReturn() {
        val focus = PomodoroSession().start(start)
        val now = focus.deadlineMs + 15_000
        assertNull(PomodoroNotice.between(focus, focus.advance(now), now))
    }

    @Test fun manualSkipAndNormalTicksDoNotProduceCompletionNotice() {
        val focus = PomodoroSession().start(start)
        assertNull(PomodoroNotice.between(focus, focus.skip(start + 1_000), start + 1_000))
        assertNull(PomodoroNotice.between(focus, focus.advance(start + 1_000), start + 1_000))
    }

    @Test fun longBreakCompletionIsIdentifiedAfterFourFocusSessions() {
        val rest = PomodoroSession().start(start).advance(start + 115 * 60_000)
        val next = rest.advance(rest.deadlineMs)
        assertEquals(PomodoroPhase.LONG_BREAK, PomodoroNotice.between(rest, next, rest.deadlineMs)?.completedPhase)
    }

    @Test fun longBreakNoticeUsesConfiguredRoundsEvenWhenOneWasSkipped() {
        val initial = PomodoroSession(config = PomodoroConfig(20, 5, 30, 2)).start(start)
        val second = initial.skip(start + 1_000).skip(start + 2_000)
        val longBreak = second.advance(second.deadlineMs)
        assertEquals(1, longBreak.completedFocusSessions)
        assertEquals(PomodoroPhase.LONG_BREAK, longBreak.phase)
        val next = longBreak.advance(longBreak.deadlineMs)
        assertEquals(PomodoroPhase.LONG_BREAK,
            PomodoroNotice.between(longBreak, next, longBreak.deadlineMs)?.completedPhase)
    }
}
