package com.anthonyla.paperize.core.util

import com.anthonyla.paperize.core.ScalingType
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun calculateDecodeSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    scaling: ScalingType
): Pair<Int, Int> {
    val widthRatio = targetWidth.toFloat() / sourceWidth
    val heightRatio = targetHeight.toFloat() / sourceHeight
    val scale = when (scaling) {
        ScalingType.FILL -> maxOf(widthRatio, heightRatio)
        ScalingType.FIT -> minOf(widthRatio, heightRatio)
        // Keep native size unless decoding would exceed twice the screen size.
        ScalingType.NONE -> minOf(1f, widthRatio * 2, heightRatio * 2)
        ScalingType.STRETCH -> return targetWidth to targetHeight
    }
    return (sourceWidth * scale).roundToInt().coerceAtLeast(1) to
        (sourceHeight * scale).roundToInt().coerceAtLeast(1)
}

/**
 * Shrinks [width] × [height] evenly, keeping its shape, so it holds at most [maxPixels]. The live
 * wallpaper keeps the whole of a Fill image for parallax and auto-pan, and a long panorama could
 * otherwise need hundreds of megabytes; the GPU scales the smaller image back up.
 */
internal fun limitPixels(width: Int, height: Int, maxPixels: Long): Pair<Int, Int> {
    val pixels = width.toLong() * height
    if (pixels <= maxPixels) return width to height
    val scale = sqrt(maxPixels.toDouble() / pixels)
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}
