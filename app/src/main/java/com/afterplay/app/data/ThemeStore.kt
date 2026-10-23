package com.afterplay.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemePreset(val title: String, val description: String, val swatches: List<Long>, val dark: Boolean = false) {
    BURGUNDY("버건디 크림", "버건디 · 샌드 · 크림 · 미스트 블루", listOf(0xFF790D16, 0xFFE5D3AF, 0xFFF5EFE1, 0xFFAEC4D4)),
    PEACH("피치 블러시", "바닐라 · 피치 · 코랄 · 블러시", listOf(0xFFFFFAD3, 0xFFFFDBB0, 0xFFFFCCB8, 0xFFFFB1B1)),
    AMBER("허니 앰버", "버터 · 골드 · 오렌지 · 브라운", listOf(0xFFF9E6A8, 0xFFF2A900, 0xFFCC6F00, 0xFF4D2A00)),
    BLUE("클래식 블루", "스노우 · 페일 블루 · 블루 · 네이비", listOf(0xFFF9F7F7, 0xFFDBE2EF, 0xFF3F72AF, 0xFF112D4E)),
    SLATE("슬레이트", "딥 네이비 · 슬레이트 · 미스트 · 아이스", listOf(0xFF27374D, 0xFF526D82, 0xFF9DB2BF, 0xFFDDE6ED), true),
}

class ThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance_v1", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(runCatching {
        ThemePreset.valueOf(preferences.getString("preset", ThemePreset.BURGUNDY.name)!!)
    }.getOrDefault(ThemePreset.BURGUNDY))
    val state = current.asStateFlow()

    fun select(preset: ThemePreset) {
        current.value = preset
        preferences.edit().putString("preset", preset.name).apply()
    }
}
