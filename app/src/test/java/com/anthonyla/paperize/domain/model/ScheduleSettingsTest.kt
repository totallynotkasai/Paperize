package com.anthonyla.paperize.domain.model

import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSettingsTest {
    @Test
    fun `validation applies distinct static and live minimums and validates all screens`() {
        val effects = WallpaperEffects(darkenPercentage = 150, blurPercentage = -10)
        val settings = ScheduleSettings(
            homeIntervalMinutes = 5,
            lockIntervalMinutes = 120,
            liveIntervalMinutes = 0,
            homeEffects = effects,
            lockEffects = effects,
            liveEffects = effects
        )
        val validEffects = effects.copy(darkenPercentage = 100, blurPercentage = 0)
        assertEquals(
            settings.copy(
                homeIntervalMinutes = 15,
                liveIntervalMinutes = 1,
                homeEffects = validEffects,
                lockEffects = validEffects,
                liveEffects = validEffects
            ),
            settings.validate()
        )
    }

    @Test
    fun `a turned-off screen keeps its album but is not rotated`() {
        val settings = ScheduleSettings(homeEnabled = true, lockEnabled = false, homeAlbumId = "a", lockAlbumId = "b")
        assertEquals("a", settings.albumFor(ScreenType.HOME))
        assertNull(settings.albumFor(ScreenType.LOCK))
        assertEquals(setOf(ScreenType.HOME), settings.rotatingStaticScreens())
        assertEquals(setOf(ScreenType.HOME), settings.activeScreens(WallpaperMode.STATIC))
        assertEquals(setOf(ScreenType.HOME, ScreenType.LOCK), settings.copy(lockEnabled = true).activeScreens(WallpaperMode.STATIC))
    }

    @Test
    fun `screens share an album only while both are on`() {
        val shared = ScheduleSettings(homeEnabled = true, lockEnabled = true, homeAlbumId = "a", lockAlbumId = "a")
        assertTrue(shared.screensShareAlbum())
        assertEquals(setOf(ScreenType.BOTH), shared.activeScreens(WallpaperMode.STATIC))
        assertEquals(setOf(ScreenType.HOME, ScreenType.LOCK), shared.copy(separateSchedules = true).activeScreens(WallpaperMode.STATIC))
        assertFalse(shared.copy(lockEnabled = false).screensShareAlbum())
        assertFalse(shared.copy(lockAlbumId = "b").screensShareAlbum())
    }

    @Test
    fun `anything rotates once one turned-on screen has an album`() {
        val waiting = ScheduleSettings(homeEnabled = true, lockEnabled = true, homeAlbumId = "a")
        assertTrue(waiting.hasRequiredAlbums(WallpaperMode.STATIC))
        assertFalse(waiting.copy(homeEnabled = false).hasRequiredAlbums(WallpaperMode.STATIC))
        assertFalse(ScheduleSettings(homeAlbumId = "a").hasRequiredAlbums(WallpaperMode.STATIC))
        assertTrue(ScheduleSettings(liveAlbumId = "live").hasRequiredAlbums(WallpaperMode.LIVE))
    }

    @Test
    fun `display edits name the screens whose image must be re-rendered`() {
        val current = ScheduleSettings()
        val lockBlur = current.copy(lockEffects = WallpaperEffects(enableBlur = true))
        assertTrue(lockBlur.hasDisplayChanges(current, ScreenType.LOCK))
        assertFalse(lockBlur.hasDisplayChanges(current, ScreenType.HOME))
        val scrolling = current.copy(homeScrollingEnabled = true)
        assertTrue(scrolling.hasDisplayChanges(current, ScreenType.HOME))
        assertFalse(scrolling.hasDisplayChanges(current, ScreenType.LOCK))
        val brightness = current.copy(adaptiveBrightness = true)
        assertTrue(brightness.hasDisplayChanges(current, ScreenType.HOME) && brightness.hasDisplayChanges(current, ScreenType.LOCK))
    }

    @Test
    fun `visible live timer is used only below WorkManager minimum`() {
        assertFalse(usesVisibleLiveTimer(0))
        assertTrue(usesVisibleLiveTimer(1))
        assertTrue(usesVisibleLiveTimer(14))
        assertFalse(usesVisibleLiveTimer(15))
    }

    @Test
    fun `schedule edits require rescheduling without reapplying display effects`() {
        val current = ScheduleSettings()
        listOf(
            current.copy(enableChanger = true),
            current.copy(homeAlbumId = "home"),
            current.copy(lockAlbumId = "lock"),
            current.copy(liveAlbumId = "live"),
            current.copy(homeEnabled = true),
            current.copy(lockEnabled = true),
            current.copy(homeIntervalMinutes = 120),
            current.copy(lockIntervalMinutes = 120),
            current.copy(liveIntervalMinutes = 120),
            current.copy(separateSchedules = true)
        ).forEach { edited ->
            assertTrue("Scheduling: $edited", current.hasSchedulingChanges(edited))
            assertFalse("Display: $edited", current.hasDisplayChanges(edited))
        }
    }

    @Test
    fun `display edits require reapplication without resetting schedules`() {
        val current = ScheduleSettings()
        val effects = WallpaperEffects(enableBlur = true)
        listOf(
            current.copy(homeScalingType = ScalingType.FIT),
            current.copy(lockScalingType = ScalingType.STRETCH),
            current.copy(liveScalingType = ScalingType.NONE),
            current.copy(homeScrollingEnabled = true),
            current.copy(homeEffects = effects),
            current.copy(lockEffects = effects),
            current.copy(liveEffects = effects)
        ).forEach { edited ->
            assertTrue("Display: $edited", current.hasDisplayChanges(edited))
            assertFalse("Scheduling: $edited", current.hasSchedulingChanges(edited))
        }
        // Adaptive brightness also starts or stops the dark-theme checks (plan 6.3); updating the
        // schedules keeps every existing countdown.
        current.copy(adaptiveBrightness = true).let { edited ->
            assertTrue(current.hasDisplayChanges(edited))
            assertTrue(current.hasSchedulingChanges(edited))
        }
        assertFalse(current.hasSchedulingChanges(current))
        assertFalse(current.hasDisplayChanges(current))
    }
}
