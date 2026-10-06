package com.anthonyla.paperize.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperEffectsTest {
    @Test
    fun `auto-pan speed stays within the slider's range`() {
        assertEquals(300, WallpaperEffects(autoPanSweepSeconds = 3600).validate().autoPanSweepSeconds)
        assertEquals(10, WallpaperEffects(autoPanSweepSeconds = 0).validate().autoPanSweepSeconds)
        assertEquals(45, WallpaperEffects(autoPanSweepSeconds = 45).validate().autoPanSweepSeconds)
        assertEquals(60, WallpaperEffects().autoPanSweepSeconds)
    }

    @Test
    fun `validation clamps every percentage and preserves effect toggles`() {
        listOf(-1 to 0, 0 to 0, 50 to 50, 100 to 100, 101 to 100).forEach { (input, expected) ->
            val effects = WallpaperEffects(
                enableBlur = true,
                enableParallax = true,
                darkenPercentage = input,
                blurPercentage = input,
                vignettePercentage = input,
                grayscalePercentage = input,
                parallaxIntensity = input
            )
            assertEquals(
                effects.copy(
                    darkenPercentage = expected,
                    blurPercentage = expected,
                    vignettePercentage = expected,
                    grayscalePercentage = expected,
                    parallaxIntensity = expected
                ),
                effects.validate()
            )
        }
    }
}
