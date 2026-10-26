package com.afterplay.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.afterplay.app.data.ThemePreset

internal data class PaletteColors(
    val background: Color, val surface: Color, val clockSurface: Color,
    val primary: Color, val onPrimary: Color, val secondary: Color,
    val ink: Color, val muted: Color, val emphasis: Color, val breakAccent: Color,
    val artwork: Color, val onArtwork: Color,
)

internal fun ThemePreset.colors(): PaletteColors {
    val (a, b, c, d) = swatches.map { Color(it) }
    return when (this) {
        ThemePreset.MINT -> palette(a, b, c, d, c)
        ThemePreset.BLUE -> palette(a, b, lerp(a, b, .25f), d, c)
        ThemePreset.ROSE -> palette(a, b, c, d, c)
        ThemePreset.BLUSH -> palette(a, b, c, d, c)
        ThemePreset.LATTE -> palette(d, c, lerp(d, c, .25f), a, b)
        ThemePreset.PINK -> palette(c, b, a, d, a)
        ThemePreset.LAVENDER -> palette(a, b, lerp(a, b, .25f), d, c)
        ThemePreset.OCEAN -> palette(a, lerp(a, b, .25f), lerp(a, b, .15f), d, c, dark = true)
        ThemePreset.SLATE -> palette(a, lerp(a, b, .25f), lerp(a, b, .15f), d, c, dark = true)
        ThemePreset.NEON -> palette(b, lerp(b, a, .08f), lerp(b, a, .05f), c, a, dark = true, text = d)
    }
}

private fun palette(
    background: Color, surface: Color, clockSurface: Color,
    primary: Color, secondary: Color, dark: Boolean = false, text: Color = primary,
): PaletteColors {
    val surfaces = listOf(background, surface, clockSurface)
    val emphasis = primary.readableOn(surfaces, dark)
    val ink = text.readableOn(surfaces, dark)
    val muted = lerp(ink, background, .18f).readableOn(surfaces, dark)
    val breakAccent = secondary.readableOn(surfaces, dark)
    return PaletteColors(background, surface, clockSurface, primary, background, secondary,
        ink, muted, emphasis, breakAccent, secondary,
        if (secondary.luminance() > .179f) Color.Black else Color.White)
}

// Pastel swatches need darker text; dark presets need lighter text on every panel.
private fun Color.readableOn(backgrounds: List<Color>, dark: Boolean): Color {
    val target = if (dark) Color.White else Color.Black
    for (step in 0..20) {
        val candidate = lerp(this, target, step / 20f)
        if (backgrounds.all { background ->
            (maxOf(candidate.luminance(), background.luminance()) + .05f) /
                (minOf(candidate.luminance(), background.luminance()) + .05f) >= 4.5f
        }) return candidate
    }
    return target
}

internal val LocalPalette = staticCompositionLocalOf { ThemePreset.MINT.colors() }
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
