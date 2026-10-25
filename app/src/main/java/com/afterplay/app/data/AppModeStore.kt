package com.afterplay.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppMode { PLAYBACK, POMODORO }

class AppModeStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_mode_v1", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(runCatching {
        AppMode.valueOf(preferences.getString("mode", AppMode.PLAYBACK.name)!!)
    }.getOrDefault(AppMode.PLAYBACK))
    val state = current.asStateFlow()

    fun select(mode: AppMode) {
        preferences.edit().putString("mode", mode.name).apply()
        current.value = mode
    }
}
