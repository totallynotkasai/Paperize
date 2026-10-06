package com.anthonyla.paperize.service.livewallpaper.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.core.util.calculateDecodeSize
import com.anthonyla.paperize.core.util.limitPixels
import com.anthonyla.paperize.core.util.decodeSvg
import com.anthonyla.paperize.core.util.isSvgDocument

sealed interface ImageLoader {
    /**
     * Decode on a worker thread. The caller owns the returned bitmap. A loader may be asked
     * again after a surface change and must then produce the same image.
     */
    suspend fun load(targetWidth: Int, targetHeight: Int): Bitmap?
}

object EmptyImageLoader : ImageLoader {
    override suspend fun load(targetWidth: Int, targetHeight: Int): Bitmap? = null
}

class ContentUriImageLoader(
    private val context: Context,
    private val uri: Uri,
    private val scalingType: ScalingType = ScalingType.FILL
) : ImageLoader {
    override suspend fun load(targetWidth: Int, targetHeight: Int): Bitmap? = decode(targetWidth, targetHeight)

    /** Blocking decode, sized for the renderer's scaling; null if the image cannot be read. */
    fun decode(targetWidth: Int, targetHeight: Int): Bitmap? {
        if (isSvgDocument(context, uri)) {
            return decodeSvg(context, uri, targetWidth, targetHeight, scalingType)
        }
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // ImageDecoder reports dimensions after applying EXIF orientation.
                val (fullWidth, fullHeight) = calculateDecodeSize(
                    info.size.width, info.size.height, targetWidth, targetHeight, scalingType
                )
                // None draws pixel for pixel, so only Fill may be decoded smaller than it is shown.
                val (width, height) = if (scalingType == ScalingType.FILL) {
                    limitPixels(fullWidth, fullHeight, Constants.MAX_LIVE_DECODE_PIXELS)
                } else {
                    fullWidth to fullHeight
                }
                decoder.setTargetSize(width, height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            Log.e("ContentUriImageLoader", "Failed to load image from $uri", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e("ContentUriImageLoader", "OOM loading image from $uri", e)
            null
        }
    }
}
