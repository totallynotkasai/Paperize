package com.anthonyla.paperize.core.util

import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Gaussian sigma Android uses for a blur radius (HWUI's Blur::convertRadiusToSigma), so the CPU
 * fallback and the live wallpaper blur as strongly as RenderEffect.createBlurEffect does.
 */
internal fun blurRadiusToSigma(radius: Float): Float = if (radius > 0f) 0.57735f * radius + 0.5f else 0f

/**
 * Widths of [passes] box blurs that together approximate a Gaussian of [sigma]: each width is odd,
 * and the mix of the two nearest widths matches the Gaussian's variance.
 */
internal fun boxSizesForGaussian(sigma: Float, passes: Int = 3): IntArray {
    val variance = 12.0 * sigma * sigma
    var lower = floor(sqrt(variance / passes + 1)).toInt()
    if (lower % 2 == 0) lower--
    lower = lower.coerceAtLeast(1)
    val upper = lower + 2
    val lowerCount = ((variance - passes * lower * lower - 4.0 * passes * lower - 3.0 * passes) / (-4.0 * lower - 4))
        .roundToInt()
        .coerceIn(0, passes)
    return IntArray(passes) { if (it < lowerCount) lower else upper }
}

/**
 * Blur ARGB [pixels] ([width] × [height]) in place: three box blurs each way approximate a Gaussian
 * of [sigma]. Edges repeat their last pixel, like the GPU path's clamp tile mode.
 */
internal fun boxBlurArgb(pixels: IntArray, width: Int, height: Int, sigma: Float) {
    require(pixels.size >= width * height)
    if (sigma <= 0f || width <= 0 || height <= 0) return
    val scratch = IntArray(width * height)
    for (size in boxSizesForGaussian(sigma)) {
        val radius = (size - 1) / 2
        if (radius == 0) continue
        boxBlurPass(pixels, scratch, lineCount = height, lineLength = width, step = 1, lineStride = width, radius = radius)
        boxBlurPass(scratch, pixels, lineCount = width, lineLength = height, step = width, lineStride = 1, radius = radius)
    }
}

/**
 * One box blur along every line of [src] into [dst]. Line `l` starts at `l * lineStride`, and
 * neighbours along it are [step] apart: rows for a horizontal pass, columns for a vertical one.
 */
private fun boxBlurPass(
    src: IntArray,
    dst: IntArray,
    lineCount: Int,
    lineLength: Int,
    step: Int,
    lineStride: Int,
    radius: Int
) {
    val window = 2 * radius + 1
    val half = window / 2
    val last = lineLength - 1
    for (line in 0 until lineCount) {
        val start = line * lineStride
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val p = src[start + i.coerceIn(0, last) * step]
            a += p ushr 24
            r += (p shr 16) and 0xff
            g += (p shr 8) and 0xff
            b += p and 0xff
        }
        for (i in 0..last) {
            dst[start + i * step] = ((a + half) / window shl 24) or
                ((r + half) / window shl 16) or
                ((g + half) / window shl 8) or
                ((b + half) / window)
            val added = src[start + (i + radius + 1).coerceAtMost(last) * step]
            val removed = src[start + (i - radius).coerceAtLeast(0) * step]
            a += (added ushr 24) - (removed ushr 24)
            r += ((added shr 16) and 0xff) - ((removed shr 16) and 0xff)
            g += ((added shr 8) and 0xff) - ((removed shr 8) and 0xff)
            b += (added and 0xff) - (removed and 0xff)
        }
    }
}

/** A blur this strong loses nothing by running on a smaller copy, which saves time and memory. */
private const val SIGMA_PER_DOWNSCALE_STEP = 4f
private const val MAX_DOWNSCALE = 4

/**
 * CPU blur for when the GPU effects pipeline can't run, as strong as the GPU's [radius] (pixels).
 * Strong blurs run on a copy up to [MAX_DOWNSCALE] times smaller and are scaled back up. Returns a
 * new bitmap; the caller still owns [source].
 */
internal fun blurBitmapCpu(source: Bitmap, radius: Float): Bitmap {
    val sigma = blurRadiusToSigma(radius)
    val downscale = (sigma / SIGMA_PER_DOWNSCALE_STEP).toInt().coerceIn(1, MAX_DOWNSCALE)
    val workWidth = (source.width / downscale).coerceAtLeast(1)
    val workHeight = (source.height / downscale).coerceAtLeast(1)
    val work = if (downscale > 1) source.scale(workWidth, workHeight) else
        source.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(workWidth * workHeight)
    work.getPixels(pixels, 0, workWidth, 0, 0, workWidth, workHeight)
    boxBlurArgb(pixels, workWidth, workHeight, sigma / downscale)
    val blurred = if (work.isMutable) work else work.copy(Bitmap.Config.ARGB_8888, true).also { work.recycle() }
    blurred.setPixels(pixels, 0, workWidth, 0, 0, workWidth, workHeight)
    if (downscale == 1) return blurred
    return blurred.scale(source.width, source.height).also { if (it !== blurred) blurred.recycle() }
}
