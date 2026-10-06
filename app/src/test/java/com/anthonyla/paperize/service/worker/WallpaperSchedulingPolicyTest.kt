package com.anthonyla.paperize.service.worker

import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperSchedulingPolicyTest {
    @Test
    fun `manual synchronized change resets the shared job`() {
        val settings = ScheduleSettings(
            enableChanger = true,
            homeEnabled = true,
            lockEnabled = true,
            homeAlbumId = "album",
            lockAlbumId = "album"
        )

        assertEquals(
            setOf(ScreenType.BOTH),
            scheduledScreensToReset(ScreenType.HOME, settings, WallpaperMode.STATIC)
        )
    }

    @Test
    fun `manual both change resets each independent job`() {
        val settings = ScheduleSettings(
            enableChanger = true,
            separateSchedules = true,
            homeEnabled = true,
            lockEnabled = true,
            homeAlbumId = "home",
            lockAlbumId = "lock"
        )

        assertEquals(
            setOf(ScreenType.HOME, ScreenType.LOCK),
            scheduledScreensToReset(ScreenType.BOTH, settings, WallpaperMode.STATIC)
        )
    }

    @Test
    fun `manual change does not create a disabled schedule`() {
        assertEquals(
            emptySet<ScreenType>(),
            scheduledScreensToReset(
                ScreenType.BOTH,
                ScheduleSettings(enableChanger = false),
                WallpaperMode.STATIC
            )
        )
    }

    @Test
    fun `short live interval belongs to visible engine instead of WorkManager`() {
        val settings = ScheduleSettings(
            enableChanger = true,
            liveAlbumId = "live",
            liveIntervalMinutes = 14
        )

        assertEquals(
            emptySet<ScreenType>(),
            scheduledScreensToReset(ScreenType.LIVE, settings, WallpaperMode.LIVE)
        )
        assertEquals(
            setOf(ScreenType.LIVE),
            scheduledScreensToReset(
                ScreenType.LIVE,
                settings.copy(liveIntervalMinutes = 15),
                WallpaperMode.LIVE
            )
        )
    }

    @Test
    fun `jobs follow the album selection that an empty album cleared`() {
        val settings = ScheduleSettings(
            enableChanger = true, separateSchedules = true, homeEnabled = true, lockEnabled = true,
            homeAlbumId = "home", lockAlbumId = "lock"
        )
        assertEquals(setOf(ScreenType.HOME, ScreenType.LOCK), scheduledScreens(settings, WallpaperMode.STATIC))
        assertEquals(setOf(ScreenType.LOCK), scheduledScreens(settings.copy(homeEnabled = false, homeAlbumId = null), WallpaperMode.STATIC))
        // An enabled screen without an album stops every job until plan item 2.1 changes this.
        assertEquals(emptySet<ScreenType>(), scheduledScreens(settings.copy(homeAlbumId = null), WallpaperMode.STATIC))
        assertEquals(emptySet<ScreenType>(), scheduledScreens(settings.copy(enableChanger = false), WallpaperMode.STATIC))
    }

    @Test
    fun `short live intervals run in the engine instead of a job`() {
        val settings = ScheduleSettings(enableChanger = true, liveAlbumId = "live", liveIntervalMinutes = 5)
        assertEquals(emptySet<ScreenType>(), scheduledScreens(settings, WallpaperMode.LIVE))
        assertEquals(setOf(ScreenType.LIVE), scheduledScreens(settings.copy(liveIntervalMinutes = 30), WallpaperMode.LIVE))
    }
}
