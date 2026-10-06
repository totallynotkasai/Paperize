package com.anthonyla.paperize.service.livewallpaper.renderer

import com.anthonyla.paperize.core.ScalingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPanTest {

    @Test fun `tall images pan up and down, wide ones side to side`() {
        assertEquals(PanAxis.VERTICAL, AutoPan.axis(ScalingType.FILL, 100f, 200f, 100f, 400f))
        assertEquals(PanAxis.HORIZONTAL, AutoPan.axis(ScalingType.FILL, 100f, 200f, 400f, 200f))
    }

    @Test fun `only Fill and None can pan, and only when something is cut off`() {
        assertEquals(PanAxis.NONE, AutoPan.axis(ScalingType.FIT, 100f, 200f, 400f, 200f))
        assertEquals(PanAxis.NONE, AutoPan.axis(ScalingType.STRETCH, 100f, 200f, 400f, 200f))
        assertEquals(PanAxis.NONE, AutoPan.axis(ScalingType.FILL, 100f, 200f, 100f, 200f))
        // Less than 1% of the screen isn't worth animating.
        assertEquals(PanAxis.NONE, AutoPan.axis(ScalingType.FILL, 100f, 200f, 100f, 201f))
        // None leaves a small image smaller than the screen.
        assertEquals(PanAxis.NONE, AutoPan.axis(ScalingType.NONE, 100f, 200f, 50f, 60f))
    }

    @Test fun `None pans along the side that overflows most`() {
        // 50% wider and 100% taller than the screen.
        assertEquals(PanAxis.VERTICAL, AutoPan.axis(ScalingType.NONE, 100f, 200f, 150f, 400f))
        // 200% wider and 50% taller.
        assertEquals(PanAxis.HORIZONTAL, AutoPan.axis(ScalingType.NONE, 100f, 200f, 300f, 300f))
    }

    @Test fun `the pan eases from one edge to the other and back`() {
        assertEquals(0f, AutoPan.position(0f), 1e-6f)
        assertEquals(0.5f, AutoPan.position(0.5f), 1e-6f)
        assertEquals(1f, AutoPan.position(1f), 1e-6f)
        assertEquals(0.5f, AutoPan.position(1.5f), 1e-6f)
        assertEquals(0f, AutoPan.position(2f), 1e-6f)
        // Slow at the edges, fast in the middle.
        assertTrue(AutoPan.position(0.1f) < 0.05f)
        assertTrue(AutoPan.position(0.6f) - AutoPan.position(0.4f) > 0.3f)
    }

    @Test fun `frames come at most about 30 times a second`() {
        // 2000 px in a 10 s sweep, mid-way: far more than half a pixel per frame.
        assertEquals(AutoPan.MIN_FRAME_INTERVAL_MS, AutoPan.frameDelayMs(2000f, 0.5f, 10_000L))
    }

    @Test fun `slow pans and turning points draw fewer frames`() {
        // 100 px in a minute: about 2.6 px/s at the fastest, so about 5 frames a second.
        val slow = AutoPan.frameDelayMs(100f, 0.5f, 60_000L)
        assertTrue("delay $slow", slow in 150L..250L)
        // At the very edges the image is at rest.
        assertEquals(AutoPan.MAX_FRAME_INTERVAL_MS, AutoPan.frameDelayMs(2000f, 0f, 60_000L))
        assertEquals(AutoPan.MAX_FRAME_INTERVAL_MS, AutoPan.frameDelayMs(2000f, 1f, 60_000L))
    }

    @Test fun `the clock counts only time between drawn frames`() {
        val clock = PanClock()
        clock.advance(nanos(1_000), 10_000L)
        assertEquals(0f, clock.phase, 1e-6f)
        clock.advance(nanos(1_100), 10_000L)
        assertEquals(0.01f, clock.phase, 1e-4f)
        // Hidden for a minute: the pan carries on from where it was.
        clock.advance(nanos(61_100), 10_000L)
        assertEquals(0.01f, clock.phase, 1e-4f)
        clock.advance(nanos(61_350), 10_000L)
        assertEquals(0.035f, clock.phase, 1e-4f)
    }

    @Test fun `a new speed carries on from the current position`() {
        val clock = PanClock()
        clock.advance(nanos(0), 10_000L)
        clock.advance(nanos(250), 10_000L)
        val before = clock.phase
        clock.advance(nanos(500), 60_000L)
        assertEquals(before + 250f / 60_000f, clock.phase, 1e-5f)
    }

    @Test fun `the phase wraps after a full there-and-back`() {
        val clock = PanClock()
        var now = 0L
        clock.advance(nanos(now), 1_000L)
        repeat(11) {
            now += 200
            clock.advance(nanos(now), 1_000L)
        }
        // 2.2 sweeps: there and back once, then a fifth of the way again.
        assertEquals(0.2f, clock.phase, 1e-4f)
    }

    private fun nanos(millis: Long) = millis * 1_000_000L
}
