package com.anthonyla.paperize.service.schedule

import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleConditionsTest {

    @Test fun `automatic changes wait for a charger and pause in battery saver only when asked`() {
        val off = ScheduleSettings()
        assertNull(automaticChangeBlock(off, charging = { false }, powerSave = { true }))
        val charging = off.copy(onlyWhileCharging = true)
        assertEquals(ChangeBlock.NOT_CHARGING, automaticChangeBlock(charging, { false }, { false }))
        assertNull(automaticChangeBlock(charging, { true }, { true }))
        val saver = off.copy(pauseInBatterySaver = true)
        assertEquals(ChangeBlock.BATTERY_SAVER, automaticChangeBlock(saver, { false }, { true }))
        assertNull(automaticChangeBlock(saver, { false }, { false }))
    }

    @Test fun `the phone is asked about a condition only when its setting is on`() {
        val never = { error("not asked") }
        assertNull(automaticChangeBlock(ScheduleSettings(), never, never))
        assertNull(automaticChangeBlock(ScheduleSettings(onlyWhileCharging = true), { true }, never))
    }

    @Test fun `dark-theme checks run for static adaptive brightness or night albums that follow the theme`() {
        val home = ScheduleSettings(homeEnabled = true, homeAlbumId = "day")
        assertFalse(watchesDarkTheme(home, WallpaperMode.STATIC))
        assertTrue(watchesDarkTheme(home.copy(adaptiveBrightness = true), WallpaperMode.STATIC))
        // The live wallpaper redraws by itself.
        assertFalse(watchesDarkTheme(ScheduleSettings(liveAlbumId = "day", adaptiveBrightness = true), WallpaperMode.LIVE))
        // Adaptive brightness on a turned-off screen needs nothing.
        assertFalse(watchesDarkTheme(home.copy(homeEnabled = false, adaptiveBrightness = true), WallpaperMode.STATIC))
        val night = home.copy(homeNightAlbumId = "night")
        assertFalse(watchesDarkTheme(night, WallpaperMode.STATIC))
        assertTrue(watchesDarkTheme(night.copy(nightTrigger = NightTrigger.DARK_MODE), WallpaperMode.STATIC))
        assertTrue(
            watchesDarkTheme(
                ScheduleSettings(liveAlbumId = "day", liveNightAlbumId = "night", nightTrigger = NightTrigger.DARK_MODE),
                WallpaperMode.LIVE
            )
        )
    }

    private val listening = ScheduleSettings(
        enableChanger = true, homeEnabled = true, lockEnabled = true, homeAlbumId = "a", lockAlbumId = "b",
        changeOnScreenOff = true, screenOffTarget = ScreenType.LOCK,
        changeOnUnlock = true, unlockTarget = ScreenType.HOME,
        triggerGapMinutes = 15
    )
    private val minute = 60_000L
    private val now = 1_000_000_000L

    @Test fun `each event changes the screens it targets`() {
        val never = { _: ScreenType -> 0L }
        assertEquals(setOf(ScreenType.LOCK), screensForEvent(ScreenEvent.SCREEN_OFF, listening, WallpaperMode.STATIC, now, never))
        assertEquals(setOf(ScreenType.HOME), screensForEvent(ScreenEvent.UNLOCK, listening, WallpaperMode.STATIC, now, never))
        assertEquals(
            setOf(ScreenType.HOME, ScreenType.LOCK),
            screensForEvent(ScreenEvent.UNLOCK, listening.copy(unlockTarget = ScreenType.BOTH), WallpaperMode.STATIC, now, never)
        )
        // With one screen rotating, the event changes that screen whatever the target says.
        assertEquals(
            setOf(ScreenType.HOME),
            screensForEvent(ScreenEvent.SCREEN_OFF, listening.copy(lockEnabled = false), WallpaperMode.STATIC, now, never)
        )
    }

    @Test fun `a screen that changed within the gap, by any means, stays`() {
        val changedAt = mapOf(ScreenType.HOME to now - 5 * minute, ScreenType.LOCK to now - 20 * minute)
        val both = listening.copy(unlockTarget = ScreenType.BOTH)
        assertEquals(setOf(ScreenType.LOCK), screensForEvent(ScreenEvent.UNLOCK, both, WallpaperMode.STATIC, now) { changedAt.getValue(it) })
        assertEquals(
            setOf(ScreenType.HOME, ScreenType.LOCK),
            screensForEvent(ScreenEvent.UNLOCK, both.copy(triggerGapMinutes = 0), WallpaperMode.STATIC, now) { changedAt.getValue(it) }
        )
        // A change recorded "in the future" (the clock went back) doesn't block.
        assertEquals(setOf(ScreenType.HOME), screensForEvent(ScreenEvent.UNLOCK, listening, WallpaperMode.STATIC, now) { now + minute })
    }

    @Test fun `nothing happens for an event that is off, while paused, or in live mode`() {
        val never = { _: ScreenType -> 0L }
        assertTrue(screensForEvent(ScreenEvent.UNLOCK, listening.copy(changeOnUnlock = false), WallpaperMode.STATIC, now, never).isEmpty())
        assertTrue(screensForEvent(ScreenEvent.SCREEN_OFF, listening.copy(enableChanger = false), WallpaperMode.STATIC, now, never).isEmpty())
        assertTrue(screensForEvent(ScreenEvent.SCREEN_OFF, listening, WallpaperMode.LIVE, now, never).isEmpty())
    }

    @Test fun `one request covers the screens`() {
        assertNull(requestScreenFor(emptySet()))
        assertEquals(ScreenType.HOME, requestScreenFor(setOf(ScreenType.HOME)))
        assertEquals(ScreenType.BOTH, requestScreenFor(setOf(ScreenType.HOME, ScreenType.LOCK)))
        assertEquals(ScreenType.BOTH, requestScreenFor(setOf(ScreenType.BOTH)))
        assertEquals(ScreenType.LIVE, requestScreenFor(setOf(ScreenType.LIVE)))
    }
}
