package com.afterplay.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemePreset(val title: String, val description: String, val swatches: List<Long>, val dark: Boolean = false) {
    MINT("민트", "아이스 · 아쿠아 · 민트 · 틸", listOf(0xFFE3FDFD, 0xFFCBF1F5, 0xFFA6E3E9, 0xFF71C9CE)),
    BLUE("클래식 블루", "스노우 · 페일 블루 · 블루 · 네이비", listOf(0xFFF9F7F7, 0xFFDBE2EF, 0xFF3F72AF, 0xFF112D4E)),
    ROSE("크림 로즈", "크림 · 피치 · 로즈 · 코랄", listOf(0xFFFFF5E4, 0xFFFFE3E1, 0xFFFFD1D1, 0xFFFF9494)),
    BLUSH("블러시", "아이보리 · 블러시 · 핑크 · 로즈", listOf(0xFFF9F5F6, 0xFFF8E8EE, 0xFFFDCEDF, 0xFFF2BED1)),
    LATTE("라테", "모카 · 라테 · 샌드 · 크림", listOf(0xFFAD8B73, 0xFFCEAB93, 0xFFE3CAA5, 0xFFFFFBE9)),
    PINK("핑크 그레이", "핑크 · 페일 핑크 · 스노우 · 그레이", listOf(0xFFFFC7C7, 0xFFFFE2E2, 0xFFF6F6F6, 0xFF8785A2)),
    LAVENDER("라벤더", "라일락 · 라벤더 · 페리윙클 · 퍼플", listOf(0xFFF4EEFF, 0xFFDCD6F7, 0xFFA6B1E1, 0xFF424874)),
    OCEAN("딥 오션", "차콜 · 딥 블루 · 오션 · 아이스", listOf(0xFF1B262C, 0xFF0F4C75, 0xFF3282B8, 0xFFBBE1FA), true),
    SLATE("슬레이트", "딥 네이비 · 슬레이트 · 미스트 · 아이스", listOf(0xFF27374D, 0xFF526D82, 0xFF9DB2BF, 0xFFDDE6ED), true),
    NEON("네온", "시안 · 차콜 · 핫 핑크 · 그레이", listOf(0xFF08D9D6, 0xFF252A34, 0xFFFF2E63, 0xFFEAEAEA), true),
}

class ThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance_v1", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(runCatching {
        ThemePreset.valueOf(preferences.getString("preset", ThemePreset.MINT.name)!!)
    }.getOrDefault(ThemePreset.MINT).also {
        preferences.edit().putString("preset", it.name).apply()
    })
    val state = current.asStateFlow()

    fun select(preset: ThemePreset) {
        current.value = preset
        preferences.edit().putString("preset", preset.name).apply()
    }
}
