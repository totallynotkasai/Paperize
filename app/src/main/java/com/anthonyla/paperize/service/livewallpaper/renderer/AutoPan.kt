package com.anthonyla.paperize.service.livewallpaper.renderer

import com.anthonyla.paperize.core.ScalingType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The direction auto-pan moves an image in. */
enum class PanAxis { NONE, HORIZONTAL, VERTICAL }

/**
 * Auto-pan: an image that Fill or None cuts off moves slowly from one edge to the other and back,
 * easing in and out at each end. Positions run from 0 (top or left edge on screen) to 1 (bottom or
 * right edge). A pan's phase counts sweeps: 0 → 1 is the first pass, 1 → 2 the way back.
 */
object AutoPan {
    /** Less overflow than this share of the screen isn't worth animating. */
    const val MIN_OVERFLOW_FRACTION = 0.01f

    /** At most about 30 frames per second. */
    const val MIN_FRAME_INTERVAL_MS = 33L

    /** Near the turning points the image barely moves, so frames can be this far apart. */
    const val MAX_FRAME_INTERVAL_MS = 250L

    /** Frames come often enough that the image moves at most this far between two of them. */
    const val MAX_STEP_PX = 0.5f

    /** A longer gap between frames means the wallpaper was hidden; the pan resumes where it was. */
    const val MAX_FRAME_GAP_MS = 500L

    /**
     * Which way [scalingType] lets the image pan, from its size on screen before any parallax zoom.
     * Fit and Stretch never cut anything off. When None cuts off both ways, the image pans along
     * the side it overflows most, relative to the screen.
     */
    fun axis(
        scalingType: ScalingType,
        viewWidth: Float,
        viewHeight: Float,
        scaledWidth: Float,
        scaledHeight: Float
    ): PanAxis {
        if (!scalingType.canCutOff) return PanAxis.NONE
        val overflowX = (scaledWidth - viewWidth) / viewWidth
        val overflowY = (scaledHeight - viewHeight) / viewHeight
        return when {
            overflowX < MIN_OVERFLOW_FRACTION && overflowY < MIN_OVERFLOW_FRACTION -> PanAxis.NONE
            overflowY >= overflowX -> PanAxis.VERTICAL
            else -> PanAxis.HORIZONTAL
        }
    }

    /** Where the image is at [phase]: a cosine ease, at rest at each edge and fastest mid-way. */
    fun position(phase: Float): Float = ((1.0 - cos(PI * phase)) / 2.0).toFloat()

    /**
     * How long to wait before the next frame, so that the image moves at most [MAX_STEP_PX] in
     * between, but no more often than about 30 times a second. Slow pans and small overflows
     * therefore draw far fewer frames.
     */
    fun frameDelayMs(overflowPx: Float, phase: Float, sweepMs: Long): Long {
        // d(position)/dt = π / (2 · sweep) · sin(π · phase)
        val pxPerMs = overflowPx * (PI / (2.0 * sweepMs)) * abs(sin(PI * phase))
        if (pxPerMs <= 0.0) return MAX_FRAME_INTERVAL_MS
        return (MAX_STEP_PX / pxPerMs).toLong().coerceIn(MIN_FRAME_INTERVAL_MS, MAX_FRAME_INTERVAL_MS)
    }
}

/**
 * How far an image's pan has got. Each image starts at the top or left edge. Only time between
 * drawn frames counts, so the pan stands still while the wallpaper is hidden, and a new speed
 * carries on from the current position instead of jumping. GL thread only.
 */
class PanClock {
    var phase = 0f
        private set
    private var lastFrameNanos = 0L
    private var started = false

    fun advance(nowNanos: Long, sweepMs: Long) {
        if (started && sweepMs > 0) {
            val gapMs = (nowNanos - lastFrameNanos) / 1_000_000f
            if (gapMs > 0f && gapMs <= AutoPan.MAX_FRAME_GAP_MS) {
                phase = (phase + gapMs / sweepMs) % 2f
            }
        }
        lastFrameNanos = nowNanos
        started = true
    }
}
