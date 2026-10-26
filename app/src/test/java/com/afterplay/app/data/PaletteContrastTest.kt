package com.afterplay.app.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.afterplay.app.ui.colors
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {
    @Test fun everyPresetKeepsBodyTextAndControlsLegible() {
        for (preset in ThemePreset.entries) {
            val palette = preset.colors()
            for (background in listOf(palette.background, palette.surface, palette.clockSurface)) {
                for (foreground in listOf(palette.ink, palette.muted, palette.emphasis, palette.breakAccent)) {
                    assertTrue("${preset.name} contrast ${contrast(foreground, background)}", contrast(foreground, background) >= 4.5)
                }
            }
            assertTrue("${preset.name} filled control contrast", contrast(palette.emphasis, palette.background) >= 4.5)
            assertTrue("${preset.name} artwork icon contrast", contrast(palette.onArtwork, palette.artwork) >= 4.5)
        }
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
}
