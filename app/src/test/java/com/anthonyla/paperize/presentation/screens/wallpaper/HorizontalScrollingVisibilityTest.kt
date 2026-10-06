package com.anthonyla.paperize.presentation.screens.wallpaper

import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HorizontalScrollingVisibilityTest {
    private val home = ScheduleSettings(homeEnabled = true, homeScalingType = ScalingType.FILL)

    @Test fun `offered for the home screen with Fill`() {
        assertTrue(showsHorizontalScrolling(WallpaperMode.STATIC, home))
    }

    @Test fun `hidden where it would do nothing`() {
        assertFalse(showsHorizontalScrolling(WallpaperMode.STATIC, home.copy(homeScalingType = ScalingType.FIT)))
        assertFalse(showsHorizontalScrolling(WallpaperMode.STATIC, home.copy(homeEnabled = false, lockEnabled = true)))
        assertFalse(showsHorizontalScrolling(WallpaperMode.LIVE, home))
    }
}
