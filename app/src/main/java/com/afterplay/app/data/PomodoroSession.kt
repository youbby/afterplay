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
    val finished: Boolean = false,
    val finishedAtMs: Long = 0,
    val preparationPending: Boolean = true,
    val preparationRemainingMs: Long = 0,
) {
    companion object { const val PREPARATION_MS = 10_000L }
    init { require(roundIndex in 0 until config.repeatCount); require(roundResults.size == config.repeatCount) }
    val durationMs: Long get() = config.durationMs(phase)
    val round: Int get() = roundIndex + 1
    val preparing: Boolean get() = preparationRemainingMs > 0 && !finished
    val displayRemainingMs: Long get() = if (preparing) preparationRemainingMs else remainingMs
    val dialFraction: Float get() {
        if (preparing) return (preparationRemainingMs.toDouble() / PREPARATION_MS).coerceIn(0.0, 1.0).toFloat()
        val remaining = (remainingMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
        return if (phase == PomodoroPhase.FOCUS) remaining else 1f - remaining
    }

    fun start(nowMs: Long): PomodoroSession = if (finished) PomodoroSession(config = config).start(nowMs)
        else if (running) advance(nowMs)
        else if (preparationPending && phase == PomodoroPhase.FOCUS && roundIndex == 0)
            copy(running = true, preparationPending = false, preparationRemainingMs = PREPARATION_MS,
                deadlineMs = nowMs + PREPARATION_MS)
        else copy(running = true, deadlineMs = nowMs + displayRemainingMs,
            focusStarted = focusStarted || (phase == PomodoroPhase.FOCUS && !preparing)).advance(nowMs)

    fun pause(nowMs: Long): PomodoroSession = advance(nowMs).copy(running = false, deadlineMs = 0)

    fun skip(nowMs: Long): PomodoroSession {
        val current = advance(nowMs)
        if (current.finished) return current
        val next = current.nextPhase(completed = false)
        if (next.finished) return next.copy(finishedAtMs = nowMs)
        return next.copy(deadlineMs = if (current.running) nowMs + next.durationMs else 0,
            preparationRemainingMs = 0, preparationPending = false)
    }

    fun advance(nowMs: Long): PomodoroSession {
        if (!running) return this
        var current = this
        if (current.preparing) {
            if (nowMs < current.deadlineMs) return current.copy(preparationRemainingMs = current.deadlineMs - nowMs)
            current = current.copy(preparationRemainingMs = 0, focusStarted = true,
                deadlineMs = current.deadlineMs + current.durationMs)
        }
        // A finite program has at most eight focus/rest transitions, even after a long absence.
        while (current.running && nowMs >= current.deadlineMs) {
            val next = current.nextPhase(completed = true)
            if (next.finished) return next.copy(finishedAtMs = current.deadlineMs)
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
                preparationPending = false, preparationRemainingMs = 0,
                completedFocusSessions = if (completed) (completedFocusSessions.toLong() + 1).coerceAtMost(config.repeatCount.toLong()).toInt() else completedFocusSessions)
        }
        if (roundIndex == config.repeatCount - 1) return copy(remainingMs = 0, running = false,
            deadlineMs = 0, finished = true, focusStarted = false)
        return copy(phase = PomodoroPhase.FOCUS, remainingMs = config.durationMs(PomodoroPhase.FOCUS),
            roundIndex = roundIndex + 1,
            focusStarted = running)
    }
}
