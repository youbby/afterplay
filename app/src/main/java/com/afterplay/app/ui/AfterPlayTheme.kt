package com.afterplay.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.afterplay.app.data.ThemePreset

internal data class PaletteColors(
    val background: Color, val surface: Color, val clockSurface: Color,
    val primary: Color, val onPrimary: Color, val secondary: Color,
    val ink: Color, val muted: Color, val emphasis: Color, val breakAccent: Color,
    val artwork: Color, val onArtwork: Color,
)

internal fun ThemePreset.colors(): PaletteColors = when (this) {
    ThemePreset.BURGUNDY -> PaletteColors(
        Color(0xFFF5EFE1), Color(0xFFE5D3AF), lerp(Color(0xFFF5EFE1), Color(0xFFE5D3AF), .25f),
        Color(0xFF790D16), Color(0xFFF5EFE1), Color(0xFFAEC4D4),
        Color(0xFF4A1C20), Color(0xFF6F4543), Color(0xFF790D16), Color(0xFF294D65),
        Color(0xFFAEC4D4), Color(0xFF790D16))
    ThemePreset.PEACH -> PaletteColors(
        Color(0xFFFFFAD3), Color(0xFFFFDBB0), Color(0xFFFFCCB8),
        Color(0xFFFFB1B1), Color(0xFF4F3034), Color(0xFFFFCCB8),
        Color(0xFF4F3034), Color(0xFF6D4548), Color(0xFF873E4B), Color(0xFF704619),
        Color(0xFFFFB1B1), Color(0xFF4F3034))
    ThemePreset.AMBER -> PaletteColors(
        Color(0xFFF9E6A8), lerp(Color(0xFFF9E6A8), Color.White, .6f), lerp(Color(0xFFF9E6A8), Color(0xFFF2A900), .18f),
        Color(0xFFF2A900), Color(0xFF4D2A00), Color(0xFFCC6F00),
        Color(0xFF4D2A00), Color(0xFF704916), Color(0xFF4D2A00), Color(0xFF6D3B00),
        Color(0xFFCC6F00), Color(0xFFF9E6A8))
    ThemePreset.BLUE -> PaletteColors(
        Color(0xFFF9F7F7), Color(0xFFDBE2EF), lerp(Color(0xFFF9F7F7), Color(0xFFDBE2EF), .25f),
        Color(0xFF3F72AF), Color(0xFFF9F7F7), Color(0xFFDBE2EF),
        Color(0xFF112D4E), Color(0xFF435974), Color(0xFF112D4E), Color(0xFF254B7A),
        Color(0xFF3F72AF), Color(0xFFF9F7F7))
    ThemePreset.SLATE -> PaletteColors(
        Color(0xFF27374D), lerp(Color(0xFF27374D), Color(0xFF526D82), .25f), lerp(Color(0xFF27374D), Color(0xFF526D82), .15f),
        Color(0xFF9DB2BF), Color(0xFF27374D), Color(0xFF526D82),
        Color(0xFFDDE6ED), Color(0xFFB8C9D4), Color(0xFFDDE6ED), Color(0xFF9DB2BF),
        Color(0xFF526D82), Color(0xFFDDE6ED))
}

internal val LocalPalette = staticCompositionLocalOf { ThemePreset.BURGUNDY.colors() }
internal val Accent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.emphasis
internal val Background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
internal val Surface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
internal val Muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted

@Composable
internal fun AfterPlayTheme(preset: ThemePreset, content: @Composable () -> Unit) {
    val palette = remember(preset) { preset.colors() }
    val base = if (preset.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = palette.emphasis, onPrimary = palette.background,
        primaryContainer = palette.clockSurface, onPrimaryContainer = palette.ink,
        secondary = palette.emphasis, onSecondary = palette.background,
        secondaryContainer = palette.clockSurface, onSecondaryContainer = palette.ink,
        tertiary = palette.breakAccent, onTertiary = palette.background,
        tertiaryContainer = palette.clockSurface, onTertiaryContainer = palette.ink,
        background = palette.background, onBackground = palette.ink,
        surface = palette.surface, onSurface = palette.ink,
        surfaceVariant = palette.clockSurface, onSurfaceVariant = palette.muted,
        surfaceTint = palette.primary, outline = palette.muted, outlineVariant = palette.muted.copy(alpha = .3f),
        inverseSurface = palette.ink, inverseOnSurface = palette.background, inversePrimary = palette.artwork,
        surfaceBright = palette.surface, surfaceDim = palette.clockSurface,
        surfaceContainer = palette.surface, surfaceContainerLow = palette.surface,
        surfaceContainerLowest = palette.background, surfaceContainerHigh = palette.clockSurface,
        surfaceContainerHighest = palette.clockSurface,
    )
    CompositionLocalProvider(LocalPalette provides palette) { MaterialTheme(colorScheme = scheme, content = content) }
}
