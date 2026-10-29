package com.afterplay.app.data

data class PomodoroNotice(val completedPhase: PomodoroPhase, val startedAtMs: Long) {
    val expiresAtMs: Long get() = startedAtMs + 10_000
    fun activeAt(nowMs: Long) = nowMs >= startedAtMs && nowMs < expiresAtMs

    companion object {
        fun between(before: PomodoroSession, after: PomodoroSession, nowMs: Long): PomodoroNotice? {
            if (!before.running || nowMs < before.deadlineMs) return null
            if (before.preparing && !after.finished && before.phase == after.phase && before.roundIndex == after.roundIndex) return null
            val phase = if (after.finished) after.phase
                else if (after.phase != PomodoroPhase.FOCUS) PomodoroPhase.FOCUS
                else if (after.roundIndex == 0) PomodoroPhase.LONG_BREAK
                else PomodoroPhase.SHORT_BREAK
            val startedAt = if (after.finished) after.finishedAtMs else after.deadlineMs - after.durationMs
            return PomodoroNotice(phase, startedAt).takeIf { it.activeAt(nowMs) }
        }
    }
}
