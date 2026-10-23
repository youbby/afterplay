package com.afterplay.app.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.afterplay.app.data.PomodoroPhase
import com.afterplay.app.data.PomodoroSession
import com.afterplay.app.data.PomodoroConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-owned so rotation and playback controls do not reset the focus session. */
class PomodoroTimer(context: Context) {
    private val preferences = context.getSharedPreferences("pomodoro_v1", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(restore().advance(System.currentTimeMillis()))
    val state = mutableState.asStateFlow()
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
        val after = before.advance(System.currentTimeMillis())
        mutableState.value = after
        if (before.phase != after.phase || before.completedFocusSessions != after.completedFocusSessions) save(after)
        scheduleTick()
    }

    private fun update(change: (PomodoroSession) -> PomodoroSession) {
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
            .putInt("long_break_minutes", state.config.longBreakMinutes).apply()
    }

    private fun restore(): PomodoroSession = runCatching {
        val phase = PomodoroPhase.valueOf(preferences.getString("phase", PomodoroPhase.FOCUS.name)!!)
        val running = preferences.getBoolean("running", false)
        val deadline = preferences.getLong("deadline_ms", 0)
        val config = PomodoroConfig(preferences.getInt("focus_minutes", 25), preferences.getInt("short_break_minutes", 5),
            preferences.getInt("long_break_minutes", 15))
        PomodoroSession(config = config, phase = phase,
            remainingMs = preferences.getLong("remaining_ms", config.durationMs(phase)).coerceIn(1, config.durationMs(phase)),
            running = running && deadline > 0, completedFocusSessions = preferences.getInt("completed", 0).coerceAtLeast(0),
            deadlineMs = deadline)
    }.getOrDefault(PomodoroSession())
}
