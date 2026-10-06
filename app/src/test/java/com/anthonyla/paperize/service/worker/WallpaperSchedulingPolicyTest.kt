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
        // A turned-off screen keeps its album but gets no job.
        assertEquals(setOf(ScreenType.LOCK), scheduledScreens(settings.copy(homeEnabled = false), WallpaperMode.STATIC))
        // A turned-on screen still waiting for its album doesn't stop the other one.
        assertEquals(setOf(ScreenType.LOCK), scheduledScreens(settings.copy(homeAlbumId = null), WallpaperMode.STATIC))
        assertEquals(emptySet<ScreenType>(), scheduledScreens(settings.copy(enableChanger = false), WallpaperMode.STATIC))
    }

    @Test
    fun `a new job takes over the countdown of the job it replaces`() {
        val now = 1_000_000L
        val hour = 60 * 60_000L
        val homeNext = now + 10 * 60_000L
        // Home and Lock merge into the shared job, which keeps Home's next run.
        assertEquals(homeNext, firstRunForNewJob(ScreenType.BOTH, 60, mapOf(ScreenType.HOME to homeNext, ScreenType.LIVE to null), now))
        // Splitting the shared job: each screen keeps its next run.
        assertEquals(homeNext, firstRunForNewJob(ScreenType.LOCK, 60, mapOf(ScreenType.BOTH to homeNext), now))
        // Nothing to take over, or only another screen's job: a full interval from now.
        assertEquals(now + hour, firstRunForNewJob(ScreenType.LOCK, 60, emptyMap(), now))
        assertEquals(now + hour, firstRunForNewJob(ScreenType.LOCK, 60, mapOf(ScreenType.HOME to homeNext), now))
        // A run already due (the job is running now) or further off than the new interval doesn't carry over.
        assertEquals(now + hour, firstRunForNewJob(ScreenType.HOME, 60, mapOf(ScreenType.BOTH to now - 1), now))
        assertEquals(now + hour, firstRunForNewJob(ScreenType.HOME, 60, mapOf(ScreenType.BOTH to Long.MAX_VALUE), now))
        // Intervals below WorkManager's minimum count as the minimum.
        assertEquals(now + 15 * 60_000L, firstRunForNewJob(ScreenType.LIVE, 5, emptyMap(), now))
    }

    @Test
    fun `short live intervals run in the engine instead of a job`() {
        val settings = ScheduleSettings(enableChanger = true, liveAlbumId = "live", liveIntervalMinutes = 5)
        assertEquals(emptySet<ScreenType>(), scheduledScreens(settings, WallpaperMode.LIVE))
        assertEquals(setOf(ScreenType.LIVE), scheduledScreens(settings.copy(liveIntervalMinutes = 30), WallpaperMode.LIVE))
    }
}
