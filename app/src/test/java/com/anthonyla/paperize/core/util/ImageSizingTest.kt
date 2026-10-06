package com.anthonyla.paperize.core.util

import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.constants.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSizingTest {

    @Test fun `images within the budget keep their size`() {
        assertEquals(3200 to 2400, limitPixels(3200, 2400, Constants.MAX_LIVE_DECODE_PIXELS))
    }

    @Test fun `a long panorama shrinks evenly to fit the budget`() {
        // A 20000 x 1000 panorama filling a 1080 x 2400 screen would decode at 48000 x 2400.
        val (fullWidth, fullHeight) = calculateDecodeSize(20_000, 1_000, 1080, 2400, ScalingType.FILL)
        assertEquals(48_000 to 2_400, fullWidth to fullHeight)

        val (width, height) = limitPixels(fullWidth, fullHeight, Constants.MAX_LIVE_DECODE_PIXELS)
        assertTrue(width.toLong() * height <= Constants.MAX_LIVE_DECODE_PIXELS)
        assertEquals(20f, width.toFloat() / height, 0.05f)
        // Still far wider than the screen, so the whole panorama can pan.
        assertTrue(width > 1080 * 10)
    }

    @Test fun `tiny budgets never reach zero pixels`() {
        val (width, height) = limitPixels(10_000, 10, 1)
        assertTrue(width >= 1 && height >= 1)
    }
}
