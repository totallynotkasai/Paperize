package com.anthonyla.paperize.core.util

import com.anthonyla.paperize.core.constants.Constants
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuBlurTest {

    @Test fun `blur radii convert to sigma as Android does`() {
        assertEquals(0f, blurRadiusToSigma(0f), 0f)
        assertEquals(0.57735f * Constants.MAX_BLUR_RADIUS + 0.5f, blurRadiusToSigma(Constants.MAX_BLUR_RADIUS), 1e-4f)
    }

    @Test fun `three box passes match the Gaussian's spread`() {
        listOf(1f, 2.5f, 5f, 9.3f, blurRadiusToSigma(Constants.MAX_BLUR_RADIUS)).forEach { sigma ->
            val sizes = boxSizesForGaussian(sigma)
            assertEquals(3, sizes.size)
            assertTrue("box widths must be odd: ${sizes.toList()}", sizes.all { it % 2 == 1 })
            val achieved = sqrt(sizes.sumOf { (it * it - 1) / 12.0 })
            assertTrue("sigma $sigma became $achieved", abs(achieved - sigma) <= 0.25)
        }
    }

    @Test fun `a flat image stays flat`() {
        val color = argb(255, 40, 120, 200)
        val pixels = IntArray(20 * 10) { color }
        boxBlurArgb(pixels, 20, 10, sigma = 4f)
        assertTrue(pixels.all { it == color })
    }

    @Test fun `zero sigma changes nothing`() {
        val pixels = IntArray(16) { argb(255, it * 10, 0, 0) }
        val before = pixels.copyOf()
        boxBlurArgb(pixels, 4, 4, sigma = 0f)
        assertArrayEquals(before, pixels)
    }

    @Test fun `an edge is softened symmetrically and the far sides are untouched`() {
        val width = 64
        val height = 4
        val pixels = IntArray(width * height) { i -> if (i % width < width / 2) BLACK else WHITE }
        boxBlurArgb(pixels, width, height, sigma = 3f)
        val row = (0 until width).map { red(pixels[it]) }
        assertEquals(0, row.first())
        assertEquals(255, row.last())
        assertTrue("the edge must be softened: $row", row[width / 2 - 1] in 60..195 && row[width / 2] in 60..195)
        assertTrue("brightness must rise across the edge: $row", row.zipWithNext().all { (a, b) -> b >= a })
        // Symmetric about the edge: dark side + light side = white.
        for (offset in 1..8) {
            assertTrue(abs(row[width / 2 - offset] + row[width / 2 - 1 + offset] - 255) <= 2)
        }
    }

    @Test fun `a point spreads the same way across and down`() {
        val size = 41
        val pixels = IntArray(size * size) { BLACK }
        pixels[(size / 2) * size + size / 2] = WHITE
        boxBlurArgb(pixels, size, size, sigma = 2f)
        val center = size / 2
        for (d in 1..5) {
            val across = red(pixels[center * size + center + d])
            val down = red(pixels[(center + d) * size + center])
            // Each pass rounds, so the two paths may differ by one level.
            assertTrue("across $across vs down $down at $d", abs(across - down) <= 1)
        }
        assertTrue(red(pixels[center * size + center]) < 255)
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(color: Int) = (color shr 16) and 0xff

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
