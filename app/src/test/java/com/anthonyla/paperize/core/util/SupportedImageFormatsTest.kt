package com.anthonyla.paperize.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedImageFormatsTest {
    @Test fun `extensions decide regardless of case`() {
        assertTrue(SupportedImageFormats.isSupported("photo.JPG", null))
        assertTrue(SupportedImageFormats.isSupported("logo.svg", null))
        assertFalse(SupportedImageFormats.isSupported("scan.tiff", "image/tiff"))
        assertFalse(SupportedImageFormats.isSupported("scan.TIF", null))
    }

    @Test fun `files without a known extension fall back to the MIME type`() {
        assertTrue(SupportedImageFormats.isSupported("IMG_1234", "image/jpeg"))
        assertTrue(SupportedImageFormats.isSupported(null, "image/svg+xml"))
        assertFalse(SupportedImageFormats.isSupported("IMG_1234", "image/tiff"))
        assertFalse(SupportedImageFormats.isSupported("notes", null))
        // A known unsupported extension wins over a misleading type.
        assertFalse(SupportedImageFormats.isSupported("scan.tif", "image/jpeg"))
    }

    @Test fun `only images count as skipped`() {
        assertTrue(SupportedImageFormats.isImage("scan.tiff", null))
        assertTrue(SupportedImageFormats.isImage("raw-photo", "image/x-adobe-dng"))
        assertFalse(SupportedImageFormats.isImage("notes.txt", "text/plain"))
        assertFalse(SupportedImageFormats.isImage(".nomedia", null))
    }

    @Test fun `svg is recognised by type or name`() {
        assertTrue(SupportedImageFormats.isSvg("primary:Pictures/logo.svg", null))
        assertTrue(SupportedImageFormats.isSvg("image:42", "image/svg+xml"))
        assertFalse(SupportedImageFormats.isSvg("image:42", "image/png"))
    }
}
