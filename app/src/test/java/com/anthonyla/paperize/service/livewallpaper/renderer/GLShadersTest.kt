package com.anthonyla.paperize.service.livewallpaper.renderer

import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.core.util.blurRadiusToSigma
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GLShadersTest {

    @Test fun `live blur steps give the static wallpaper's sigma`() {
        // The shader's kernel has a sigma of 1.815 steps.
        val step = GLShaders.blurStepForRadius(Constants.MAX_BLUR_RADIUS)
        assertEquals(blurRadiusToSigma(Constants.MAX_BLUR_RADIUS), step * 1.815f, 1e-4f)
        assertEquals(0f, GLShaders.blurStepForRadius(0f), 0f)
    }

    @Test fun `vignette distances are fractions of the half-diagonal`() {
        val (x, y) = GLShaders.vignetteExtent(1080, 2400)
        // Screen corners (clip space ±1, ±1) are exactly one half-diagonal from the centre.
        assertEquals(1f, hypot(x, y), 1e-5f)
        // The middle of a long edge is nearer than a corner, as in the static wallpaper's circle.
        assertTrue(y < 1f && x < y)
    }

    @Test fun `an empty surface has no vignette`() {
        assertEquals(0f to 0f, GLShaders.vignetteExtent(0, 0))
    }
}
