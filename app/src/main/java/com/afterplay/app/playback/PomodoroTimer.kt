package com.afterplay.app.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.afterplay.app.data.PomodoroPhase
import com.afterplay.app.data.PomodoroSession
import com.afterplay.app.data.PomodoroConfig
import com.afterplay.app.data.PomodoroNotice
import com.afterplay.app.data.PomodoroRound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-owned so rotation and playback controls do not reset the focus session. */
class PomodoroTimer(context: Context) {
    private val preferences = context.getSharedPreferences("pomodoro_v1", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(restore())
    val state = mutableState.asStateFlow()
    private val mutableNotice = MutableStateFlow(restoreNotice())
    val notice = mutableNotice.asStateFlow()
    private val ticker = Runnable { refresh() }

    init { refresh() }

    fun toggle() = update {
        if (it.running) it.pause(System.currentTimeMillis()) else it.start(System.currentTimeMillis())
    }

    fun skip() = update { it.skip(System.currentTimeMillis()) }
    fun reset() = update { PomodoroSession(config = it.config) }
    fun configure(config: PomodoroConfig) = update { PomodoroSession(config = config) }

    fun refresh() {
        val before = mutableState.value
        val now = System.currentTimeMillis()
        val after = before.advance(now)
        if (before.running && now >= before.deadlineMs) {
            mutableNotice.value = PomodoroNotice.between(before, after, now)
            saveNotice()
        } else if (mutableNotice.value?.activeAt(now) == false) {
            mutableNotice.value = null
            saveNotice()
        }
        mutableState.value = after
        if (before.phase != after.phase || before.completedFocusSessions != after.completedFocusSessions ||
            before.roundIndex != after.roundIndex || before.roundResults != after.roundResults || before.finished != after.finished) save(after)
        scheduleTick()
    }

    private fun update(change: (PomodoroSession) -> PomodoroSession) {
        mutableNotice.value = null
        saveNotice()
        mutableState.value = change(mutableState.value)
        save(mutableState.value)
        scheduleTick()
    }

    private fun scheduleTick() {
        handler.removeCallbacks(ticker)
        if (mutableState.value.running) handler.postDelayed(ticker, 250)
    }

    private fun save(state: PomodoroSession) {
        preferences.edit().putString("phase", state.phase.name)
            .putLong("remaining_ms", state.remainingMs).putBoolean("running", state.running)
            .putInt("completed", state.completedFocusSessions).putLong("deadline_ms", state.deadlineMs)
            .putInt("focus_minutes", state.config.focusMinutes).putInt("short_break_minutes", state.config.shortBreakMinutes)
            .putInt("long_break_minutes", state.config.longBreakMinutes).putInt("repeat_count", state.config.repeatCount)
            .putInt("round_index", state.roundIndex).putString("round_results", state.roundResults.joinToString(",") { it.name })
            .putBoolean("focus_started", state.focusStarted)
            .putBoolean("finished", state.finished).putLong("finished_at", state.finishedAtMs).apply()
    }

    private fun restore(): PomodoroSession = runCatching {
        val phase = PomodoroPhase.valueOf(preferences.getString("phase", PomodoroPhase.FOCUS.name)!!)
        val running = preferences.getBoolean("running", false)
        val finished = preferences.getBoolean("finished", false)
        val config = PomodoroConfig(preferences.getInt("focus_minutes", 25).coerceIn(20, 60),
            preferences.getInt("short_break_minutes", 5).coerceIn(5, 10),
            preferences.getInt("long_break_minutes", 30).coerceIn(30, 60), preferences.getInt("repeat_count", 4).coerceIn(1, 4))
        val now = System.currentTimeMillis()
        val deadline = preferences.getLong("deadline_ms", 0).let { if (it > now + config.durationMs(phase)) now + config.durationMs(phase) else it }
        val legacyCompleted = preferences.getInt("completed", 0).coerceAtLeast(0)
        val completed = legacyCompleted.coerceAtMost(config.repeatCount)
        val legacyRound = if (phase == PomodoroPhase.FOCUS) legacyCompleted % config.repeatCount else (legacyCompleted - 1).coerceAtLeast(0) % config.repeatCount
        val round = preferences.getInt("round_index", legacyRound).coerceIn(0, config.repeatCount - 1)
        val results = runCatching { preferences.getString("round_results", "")!!.split(',').map(PomodoroRound::valueOf)
            .takeIf { it.size == config.repeatCount } }.getOrNull()
            ?: List(config.repeatCount) { if (completed > 0 && (it < round || (phase != PomodoroPhase.FOCUS && it == round))) PomodoroRound.COMPLETED else PomodoroRound.PENDING }
        PomodoroSession(config = config, phase = phase,
            remainingMs = if (finished) 0 else preferences.getLong("remaining_ms", config.durationMs(phase)).coerceIn(1, config.durationMs(phase)),
            running = !finished && running && deadline > 0, completedFocusSessions = completed, deadlineMs = if (finished) 0 else deadline,
            roundIndex = round, roundResults = results,
            finished = finished, finishedAtMs = preferences.getLong("finished_at", 0).coerceAtLeast(0),
            focusStarted = phase == PomodoroPhase.FOCUS && preferences.getBoolean("focus_started",
                running || preferences.getLong("remaining_ms", config.durationMs(phase)) <
                    preferences.getInt("focus_minutes", 25).toLong() * 60_000L))
    }.getOrDefault(PomodoroSession())

    private fun saveNotice() {
        preferences.edit().putString("notice_phase", mutableNotice.value?.completedPhase?.name)
            .putLong("notice_at", mutableNotice.value?.startedAtMs ?: 0).apply()
    }

    private fun restoreNotice(): PomodoroNotice? = runCatching {
        PomodoroNotice(PomodoroPhase.valueOf(preferences.getString("notice_phase", "")!!),
            preferences.getLong("notice_at", 0)).takeIf { it.activeAt(System.currentTimeMillis()) }
    }.getOrNull()
}
