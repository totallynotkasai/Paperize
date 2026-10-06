package com.anthonyla.paperize.domain.model

import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.data.datastore.parseChangeTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightAlbumSettingsTest {
    private val day = ScheduleSettings(
        homeEnabled = true, lockEnabled = true, homeAlbumId = "homeDay", lockAlbumId = "lockDay",
        homeNightAlbumId = "homeNight", liveAlbumId = "liveDay", liveNightAlbumId = "liveNight"
    )

    @Test fun `a screen rotates its night album only at night`() {
        assertEquals("homeDay", day.albumFor(ScreenType.HOME))
        val night = day.copy(nightActive = true)
        assertEquals("homeNight", night.albumFor(ScreenType.HOME))
        // Lock has no night album, so it keeps its own.
        assertEquals("lockDay", night.albumFor(ScreenType.LOCK))
        assertEquals("liveNight", night.albumFor(ScreenType.LIVE))
    }

    @Test fun `a night album counts only next to a day album, and not on a turned-off screen`() {
        val noDay = day.copy(homeAlbumId = null, nightActive = true)
        assertNull(noDay.albumFor(ScreenType.HOME))
        assertNull(noDay.nightAlbumFor(ScreenType.HOME))
        val off = day.copy(homeEnabled = false, nightActive = true)
        assertNull(off.albumFor(ScreenType.HOME))
        assertEquals(setOf(ScreenType.HOME), day.screensWithNightAlbum(WallpaperMode.STATIC))
        assertTrue(off.screensWithNightAlbum(WallpaperMode.STATIC).isEmpty())
        assertEquals(setOf(ScreenType.LIVE), day.screensWithNightAlbum(WallpaperMode.LIVE))
    }

    @Test fun `screens that share a day album split at night when only one has a night album`() {
        val shared = day.copy(lockAlbumId = "homeDay")
        assertEquals(setOf(ScreenType.BOTH), shared.activeScreens(WallpaperMode.STATIC))
        assertEquals(setOf(ScreenType.HOME, ScreenType.LOCK), shared.copy(nightActive = true).activeScreens(WallpaperMode.STATIC))
    }

    @Test fun `the night albums and the switch are scheduling changes`() {
        listOf(
            day.copy(homeNightAlbumId = "other"),
            day.copy(nightActive = true),
            day.copy(nightStartMinutes = 20 * 60),
            day.copy(scheduleType = ScheduleType.TIMES),
            day.copy(changeTimes = listOf(8 * 60)),
            day.copy(onlyWhileCharging = true)
        ).forEach { assertTrue("$it", day.hasSchedulingChanges(it)) }
        // These take effect when they are used, with no job to reschedule.
        listOf(
            day.copy(pauseInBatterySaver = true),
            day.copy(changeOnUnlock = true),
            day.copy(triggerGapMinutes = 60)
        ).forEach { assertFalse("$it", day.hasSchedulingChanges(it)) }
    }

    @Test fun `screen-off and unlock listen only in static mode while changing is on`() {
        val on = day.copy(enableChanger = true, changeOnUnlock = true)
        assertTrue(on.listensForScreenEvents(WallpaperMode.STATIC))
        assertFalse(on.listensForScreenEvents(WallpaperMode.LIVE))
        assertFalse(on.copy(enableChanger = false).listensForScreenEvents(WallpaperMode.STATIC))
        assertFalse(on.copy(changeOnUnlock = false).listensForScreenEvents(WallpaperMode.STATIC))
        assertFalse(on.copy(homeEnabled = false, lockEnabled = false).listensForScreenEvents(WallpaperMode.STATIC))
    }

    @Test fun `validation keeps set times in order, unique and never empty, and targets static`() {
        val valid = day.copy(
            changeTimes = listOf(19 * 60, 7 * 60, 19 * 60, 99_999, -5),
            screenOffTarget = ScreenType.LIVE,
            unlockTarget = ScreenType.LIVE,
            triggerGapMinutes = 10_000
        ).validate()
        assertEquals(listOf(0, 7 * 60, 19 * 60, Constants.MINUTES_PER_DAY - 1), valid.changeTimes)
        assertEquals(ScreenType.BOTH, valid.screenOffTarget)
        assertEquals(ScreenType.HOME, valid.unlockTarget)
        assertEquals(Constants.MAX_TRIGGER_GAP_MINUTES, valid.triggerGapMinutes)
        assertEquals(Constants.DEFAULT_CHANGE_TIMES, day.copy(changeTimes = emptyList()).validate().changeTimes)
        assertEquals(Constants.MAX_CHANGE_TIMES, validChangeTimes((0 until 30).toList()).size)
    }

    @Test fun `stored set times read back, skipping anything unreadable`() {
        assertEquals(listOf(7 * 60, 19 * 60), parseChangeTimes("1140,420"))
        assertEquals(listOf(420), parseChangeTimes(" 420 , x,"))
        assertEquals(Constants.DEFAULT_CHANGE_TIMES, parseChangeTimes(""))
    }
}
