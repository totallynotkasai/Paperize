package com.anthonyla.paperize.service.wallpaper

import com.anthonyla.paperize.core.ScreenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRequestTest {

    @Test fun `every request survives the trip through a background job's data`() {
        listOf(
            WallpaperRequest.Change(ScreenType.LOCK),
            WallpaperRequest.Change(ScreenType.HOME, keepSchedule = true),
            WallpaperRequest.Change(ScreenType.BOTH, followMode = true),
            // A Shuffle Lock widget's tap.
            WallpaperRequest.Change(ScreenType.LOCK, followMode = true),
            // A set time or day/night switch.
            WallpaperRequest.Change(ScreenType.BOTH, automatic = true),
            WallpaperRequest.ApplySpecific("wallpaper", ScreenType.BOTH),
            WallpaperRequest.Reapply(ScreenType.HOME)
        ).forEach { request ->
            assertEquals(request, request.toData(report = false).toWallpaperRequest())
        }
    }

    @Test fun `the report flag travels with the request`() {
        assertTrue(WallpaperRequest.Change(ScreenType.HOME).toData(report = true).isReported())
        assertFalse(WallpaperRequest.Change(ScreenType.HOME).toData(report = false).isReported())
    }

    @Test fun `tile and shortcut requests without a screen change both static screens`() {
        assertEquals(
            WallpaperRequest.Change(ScreenType.BOTH, followMode = true),
            wallpaperRequest(WallpaperChangeService.ACTION_CHANGE_WALLPAPER_AUTO, null, keepSchedule = false, wallpaperId = null)
        )
    }

    @Test fun `unknown actions are ignored`() {
        assertNull(wallpaperRequest("something.else", "HOME", keepSchedule = false, wallpaperId = null))
        assertNull(wallpaperRequest(null, "HOME", keepSchedule = false, wallpaperId = null))
    }
}
