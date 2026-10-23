package com.afterplay.app.data

data class PomodoroConfig(val focusMinutes: Int = 25, val shortBreakMinutes: Int = 5, val longBreakMinutes: Int = 15) {
    init {
        require(focusMinutes in 1..180)
        require(shortBreakMinutes in 1..60)
        require(longBreakMinutes in 1..120)
    }

    fun durationMs(phase: PomodoroPhase): Long = when (phase) {
        PomodoroPhase.FOCUS -> focusMinutes
        PomodoroPhase.SHORT_BREAK -> shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> longBreakMinutes
    } * 60_000L

    val cycleMs: Long get() = (focusMinutes * 4 + shortBreakMinutes * 3 + longBreakMinutes) * 60_000L
}

enum class PomodoroPhase(val label: String) {
    FOCUS("집중"),
    SHORT_BREAK("휴식"),
    LONG_BREAK("긴 휴식"),
}

/** Deadlines, rather than decremented seconds, preserve time across delayed ticks and recreation. */
data class PomodoroSession(
    val config: PomodoroConfig = PomodoroConfig(),
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val remainingMs: Long = config.durationMs(phase),
    val running: Boolean = false,
    val completedFocusSessions: Int = 0,
    val deadlineMs: Long = 0,
) {
    val durationMs: Long get() = config.durationMs(phase)
    val round: Int get() = if (phase == PomodoroPhase.FOCUS) completedFocusSessions % 4 + 1
        else (completedFocusSessions - 1).coerceAtLeast(0) % 4 + 1

    fun start(nowMs: Long): PomodoroSession = if (running) advance(nowMs)
        else copy(running = true, deadlineMs = nowMs + remainingMs).advance(nowMs)

    fun pause(nowMs: Long): PomodoroSession = advance(nowMs).copy(running = false, deadlineMs = 0)

    fun skip(nowMs: Long): PomodoroSession {
        val current = advance(nowMs)
        val next = current.nextPhase(completed = false)
        return next.copy(deadlineMs = if (current.running) nowMs + next.durationMs else 0)
    }

    fun advance(nowMs: Long): PomodoroSession {
        if (!running) return this
        var current = this
        // Skipping an uncompleted focus can create a short break at the cycle boundary.
        if (current.phase == PomodoroPhase.SHORT_BREAK && current.completedFocusSessions % 4 == 0 && nowMs >= current.deadlineMs) {
            val next = current.nextPhase(completed = true)
            current = next.copy(deadlineMs = current.deadlineMs + next.durationMs)
        }
        // A full cycle includes four focus periods, three short breaks and one long break.
        val cycleMs = config.cycleMs
        val cycles = if (current.phase == PomodoroPhase.SHORT_BREAK && current.completedFocusSessions % 4 == 0) 0L
            else ((nowMs - current.deadlineMs) / cycleMs).coerceAtLeast(0)
        if (cycles > 0) current = current.copy(
            deadlineMs = current.deadlineMs + cycles * cycleMs,
            completedFocusSessions = (current.completedFocusSessions + cycles * 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        while (nowMs >= current.deadlineMs) {
            val next = current.nextPhase(completed = true)
            current = next.copy(deadlineMs = current.deadlineMs + next.durationMs)
        }
        return current.copy(remainingMs = current.deadlineMs - nowMs)
    }

    private fun nextPhase(completed: Boolean): PomodoroSession {
        val count = completedFocusSessions + if (completed && phase == PomodoroPhase.FOCUS) 1 else 0
        val next = when (phase) {
            PomodoroPhase.FOCUS -> if (completed && count % 4 == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
            else -> PomodoroPhase.FOCUS
        }
        return copy(phase = next, remainingMs = config.durationMs(next), completedFocusSessions = count)
    }
}
