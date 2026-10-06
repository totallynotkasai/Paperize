package com.anthonyla.paperize.core.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import coil3.SingletonImageLoader
import coil3.executeBlocking
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.svg.SvgDecoder
import coil3.toBitmap
import com.anthonyla.paperize.core.ScalingType

private const val TAG = "SvgDecoding"

/** Android's ImageDecoder cannot read SVG, so these files take the Coil path instead. */
fun isSvgDocument(context: Context, uri: Uri): Boolean {
    val mimeType = try {
        context.contentResolver.getType(uri)
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the type of $uri", e)
        null
    }
    return SupportedImageFormats.isSvg(uri.lastPathSegment, mimeType)
}

/**
 * Rasterise an SVG with the decoder Coil already bundles, sized to cover the target for FILL and
 * STRETCH, or to fit it for FIT and NONE (a vector has no native pixel size). Blocks the calling
 * thread; returns null if the file cannot be read or parsed.
 */
fun decodeSvg(context: Context, uri: Uri, width: Int, height: Int, scaling: ScalingType): Bitmap? = try {
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(width.coerceAtLeast(1), height.coerceAtLeast(1))
        .scale(if (scaling == ScalingType.FIT || scaling == ScalingType.NONE) Scale.FIT else Scale.FILL)
        .precision(Precision.EXACT)
        .decoderFactory(SvgDecoder.Factory())
        // Wallpaper-sized bitmaps would only crowd thumbnails out of the shared caches.
        .memoryCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .allowHardware(false)
        .build()
    when (val result = SingletonImageLoader.get(context).executeBlocking(request)) {
        is SuccessResult -> result.image.toBitmap()
        else -> {
            Log.w(TAG, "Could not decode SVG $uri")
            null
        }
    }
} catch (e: Exception) {
    Log.e(TAG, "Error decoding SVG $uri", e)
    null
} catch (e: OutOfMemoryError) {
    Log.e(TAG, "OOM decoding SVG $uri", e)
    null
}
