package com.anthonyla.paperize.core.util

/**
 * Formats Paperize can decode for both static and live wallpapers.
 *
 * SVG is rasterised through Coil's SVG decoder; everything else uses Android's ImageDecoder.
 * TIFF is not listed: Android has no TIFF decoder, so those files could never be shown.
 */
object SupportedImageFormats {
    const val SVG_MIME_TYPE = "image/svg+xml"

    val EXTENSIONS: Set<String> = setOf(
        "jpg", "jpeg", "png", "webp", "avif",
        "heic", "heif",  // HEIC/HEIF - Apple's high efficiency format
        "bmp",           // Bitmap - legacy but still used
        "gif",           // GIF - mostly for static images (first frame used)
        "svg"            // SVG - vector graphics (rasterized for wallpaper)
    )

    private val MIME_TYPES: Set<String> = setOf(
        "image/jpeg", "image/png", "image/webp", "image/avif",
        "image/heic", "image/heif", "image/bmp", "image/x-ms-bmp", "image/gif",
        SVG_MIME_TYPE
    )

    /** Image formats people commonly pick that Android cannot decode; counted when skipped. */
    private val KNOWN_UNSUPPORTED_EXTENSIONS: Set<String> = setOf(
        "tif", "tiff", "psd", "jxl", "jp2", "ico", "tga", "raw", "cr2", "nef", "arw"
    )

    /** A known extension decides; otherwise the provider's MIME type does. */
    fun isSupported(name: String?, mimeType: String?): Boolean {
        val extension = extensionOf(name)
        if (extension in EXTENSIONS) return true
        return mimeType?.lowercase() in MIME_TYPES && extension !in KNOWN_UNSUPPORTED_EXTENSIONS
    }

    /** Whether a file that [isSupported] rejects is still an image, so skipping it is worth reporting. */
    fun isImage(name: String?, mimeType: String?): Boolean =
        mimeType?.lowercase()?.startsWith("image/") == true || extensionOf(name) in KNOWN_UNSUPPORTED_EXTENSIONS

    fun isSvg(name: String?, mimeType: String?): Boolean =
        mimeType?.lowercase() == SVG_MIME_TYPE || extensionOf(name) == "svg"

    private fun extensionOf(name: String?): String =
        name?.substringAfterLast('.', "")?.lowercase().orEmpty()
}
