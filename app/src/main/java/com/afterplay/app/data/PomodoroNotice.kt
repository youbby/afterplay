package com.afterplay.app.data

data class PomodoroNotice(val completedPhase: PomodoroPhase, val startedAtMs: Long) {
    val expiresAtMs: Long get() = startedAtMs + 10_000
    fun activeAt(nowMs: Long) = nowMs >= startedAtMs && nowMs < expiresAtMs

    companion object {
        fun between(before: PomodoroSession, after: PomodoroSession, nowMs: Long): PomodoroNotice? {
            if (!before.running || nowMs < before.deadlineMs) return null
            val phase = if (after.phase != PomodoroPhase.FOCUS) PomodoroPhase.FOCUS
                else if (after.roundIndex == 0) PomodoroPhase.LONG_BREAK
                else PomodoroPhase.SHORT_BREAK
            return PomodoroNotice(phase, after.deadlineMs - after.durationMs).takeIf { it.activeAt(nowMs) }
        }
    }
}
