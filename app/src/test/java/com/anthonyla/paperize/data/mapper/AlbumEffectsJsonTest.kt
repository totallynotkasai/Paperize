package com.anthonyla.paperize.data.mapper

import android.util.Log
import com.anthonyla.paperize.domain.model.WallpaperEffects
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumEffectsJsonTest {
    @After fun tearDown() = unmockkAll()

    @Test fun `per-album effects survive a round trip`() {
        val effects = WallpaperEffects(enableBlur = true, blurPercentage = 40, enableParallax = true, parallaxIntensity = 70)
        assertEquals(effects, decodeAlbumEffects(encodeAlbumEffects(effects)))
        assertNull(encodeAlbumEffects(null))
        assertNull(decodeAlbumEffects(null))
    }

    @Test fun `unknown keys are ignored and values are validated`() {
        val decoded = decodeAlbumEffects("""{"enableDarken":true,"darkenPercentage":250,"futureSetting":1}""")
        assertEquals(WallpaperEffects(enableDarken = true, darkenPercentage = 100), decoded)
    }

    @Test fun `unreadable effects fall back to the screen's own`() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        assertNull(decodeAlbumEffects("not json"))
    }
}
