package com.anthonyla.paperize.presentation.screens.wallpaper

import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.presentation.screens.wallpaper.components.autoPanStopIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPanSettingTest {
    private val stops = Constants.AUTO_PAN_SWEEP_STEPS_SECONDS

    @Test fun `the default speed is one sweep a minute, on a slider stop`() {
        assertEquals(60, stops[autoPanStopIndex(Constants.DEFAULT_AUTO_PAN_SWEEP_SECONDS)])
    }

    @Test fun `the slider runs from slow to fast`() {
        assertEquals(stops.sortedDescending(), stops)
        assertEquals(0, autoPanStopIndex(stops.max()))
        assertEquals(stops.lastIndex, autoPanStopIndex(stops.min()))
    }

    @Test fun `values between stops land on the nearest one`() {
        assertEquals(60, stops[autoPanStopIndex(58)])
        assertEquals(10, stops[autoPanStopIndex(1)])
        assertEquals(300, stops[autoPanStopIndex(10_000)])
    }

    @Test fun `auto-pan is offered only for scaling that cuts images off`() {
        assertTrue(ScalingType.FILL.canCutOff)
        assertTrue(ScalingType.NONE.canCutOff)
        assertFalse(ScalingType.FIT.canCutOff)
        assertFalse(ScalingType.STRETCH.canCutOff)
    }
}
