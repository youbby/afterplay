package com.afterplay.app.data

data class PomodoroConfig(val focusMinutes: Int = 25, val shortBreakMinutes: Int = 5, val longBreakMinutes: Int = 30,
    val repeatCount: Int = 4) {
    init {
        require(focusMinutes in 20..60)
        require(shortBreakMinutes in 5..10)
        require(repeatCount in 1..4)
        require(longBreakMinutes in 30..60)
    }

    fun durationMs(phase: PomodoroPhase): Long = when (phase) {
        PomodoroPhase.FOCUS -> focusMinutes
        PomodoroPhase.SHORT_BREAK -> shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> longBreakMinutes
    } * 60_000L

    val cycleMs: Long get() = (focusMinutes * repeatCount + shortBreakMinutes * (repeatCount - 1) + longBreakMinutes) * 60_000L
}

enum class PomodoroRound(val symbol: String, val label: String) {
    PENDING("○", "아직 완료하지 않음"), COMPLETED("●", "완료"), SKIPPED("△", "중간 종료")
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
    val roundIndex: Int = 0,
    val roundResults: List<PomodoroRound> = List(config.repeatCount) { PomodoroRound.PENDING },
    val focusStarted: Boolean = false,
) {
    init { require(roundIndex in 0 until config.repeatCount); require(roundResults.size == config.repeatCount) }
    val durationMs: Long get() = config.durationMs(phase)
    val round: Int get() = roundIndex + 1

    fun start(nowMs: Long): PomodoroSession = if (running) advance(nowMs)
        else copy(running = true, deadlineMs = nowMs + remainingMs,
            focusStarted = focusStarted || phase == PomodoroPhase.FOCUS).advance(nowMs)

    fun pause(nowMs: Long): PomodoroSession = advance(nowMs).copy(running = false, deadlineMs = 0)

    fun skip(nowMs: Long): PomodoroSession {
        val current = advance(nowMs)
        val next = current.nextPhase(completed = false)
        return next.copy(deadlineMs = if (current.running) nowMs + next.durationMs else 0)
    }

    fun advance(nowMs: Long): PomodoroSession {
        if (!running) return this
        var current = this
        // A whole cycle returns to the same phase/round, with a fresh cycle's completed slots.
        val cycleMs = config.cycleMs
        val cycles = ((nowMs - current.deadlineMs) / cycleMs).coerceAtLeast(0)
        if (cycles > 0) current = current.copy(
            deadlineMs = current.deadlineMs + cycles * cycleMs,
            completedFocusSessions = (current.completedFocusSessions + cycles * config.repeatCount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            roundResults = List(config.repeatCount) { index ->
                if (index < current.roundIndex || (current.phase != PomodoroPhase.FOCUS && index == current.roundIndex))
                    PomodoroRound.COMPLETED else PomodoroRound.PENDING
            },
            focusStarted = current.phase == PomodoroPhase.FOCUS,
        )
        while (nowMs >= current.deadlineMs) {
            val next = current.nextPhase(completed = true)
            current = next.copy(deadlineMs = current.deadlineMs + next.durationMs)
        }
        return current.copy(remainingMs = current.deadlineMs - nowMs)
    }

    private fun nextPhase(completed: Boolean): PomodoroSession {
        if (phase == PomodoroPhase.FOCUS) {
            val results = roundResults.toMutableList().apply {
                this[roundIndex] = when { completed -> PomodoroRound.COMPLETED; focusStarted -> PomodoroRound.SKIPPED; else -> PomodoroRound.PENDING }
            }
            val next = if (roundIndex == config.repeatCount - 1) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
            return copy(phase = next, remainingMs = config.durationMs(next), roundResults = results, focusStarted = false,
                completedFocusSessions = if (completed) (completedFocusSessions.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else completedFocusSessions)
        }
        val newCycle = roundIndex == config.repeatCount - 1
        return copy(phase = PomodoroPhase.FOCUS, remainingMs = config.durationMs(PomodoroPhase.FOCUS),
            roundIndex = if (newCycle) 0 else roundIndex + 1,
            roundResults = if (newCycle) List(config.repeatCount) { PomodoroRound.PENDING } else roundResults,
            focusStarted = running)
    }
}
