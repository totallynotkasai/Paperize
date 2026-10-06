package com.anthonyla.paperize.service.schedule

import android.util.Log
import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.service.worker.WallpaperScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleEventsTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 6, day, hour, minute, 0, 0, zone)

    private var stored = ScheduleSettings()
    private var mode = WallpaperMode.STATIC
    private val settings = mockk<SettingsRepository>()
    private val conditions = mockk<ChangeConditions>()
    private val state = mockk<ScheduleState>(relaxed = true)
    private val alarms = mockk<TimeOfDayAlarms>(relaxed = true)
    private val requests = mockk<ScheduledRequests>(relaxed = true)
    private val scheduler = mockk<WallpaperScheduler>(relaxed = true)
    private val events = ScheduleEvents(settings, conditions, state, alarms, requests, scheduler)
    private var lastCheck = 0L
    private var dark = false
    private val renderedDark = mutableMapOf<ScreenType, Boolean>()

    private val both = ScheduleSettings(
        enableChanger = true, homeEnabled = true, lockEnabled = true, homeAlbumId = "day", lockAlbumId = "lockDay"
    )

    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        coEvery { settings.getScheduleSettings() } answers { stored }
        coEvery { settings.getWallpaperMode() } answers { mode }
        coEvery { settings.updateNightActive(any()) } answers {
            val night = firstArg<Boolean>()
            (stored.nightActive != night).also { stored = stored.copy(nightActive = night) }
        }
        every { conditions.automaticChangeBlock(any()) } returns null
        every { conditions.isDarkTheme() } answers { dark }
        every { state.lastTimedCheck } answers { lastCheck }
        every { state.lastTimedCheck = any() } answers { lastCheck = firstArg() }
        every { state.renderedDark(any()) } answers { renderedDark[firstArg()] }
    }

    @After fun tearDown() = unmockkAll()

    @Test fun `a set time that came round changes every screen, once`() = runTest {
        stored = both.copy(scheduleType = ScheduleType.TIMES, changeTimes = listOf(7 * 60, 19 * 60))
        lastCheck = at(1, 18).toInstant().toEpochMilli()
        events.onTimeEvent(at(1, 19, 4))
        verify(exactly = 1) { requests.change(ScreenType.BOTH, requiresCharging = false) }
        // Night came too (no night album, so nothing else changes); the jobs are planned again.
        coVerify { scheduler.updateSchedules(stored, WallpaperMode.STATIC, false) }
        // The next check finds nothing new.
        events.onTimeEvent(at(1, 19, 10))
        verify(exactly = 1) { requests.change(any(), any()) }
    }

    @Test fun `the first check ever, or a clock that went back, catches up nothing`() = runTest {
        stored = both.copy(scheduleType = ScheduleType.TIMES)
        events.onTimeEvent(at(1, 19, 4))
        lastCheck = at(2, 12).toInstant().toEpochMilli()
        events.onTimeEvent(at(1, 20))
        verify(exactly = 0) { requests.change(any(), any()) }
    }

    @Test fun `at nightfall only screens with a night album change, to it`() = runTest {
        stored = both.copy(homeNightAlbumId = "night")
        events.onTimeEvent(at(1, 19, 2))
        assertTrue(stored.nightActive)
        verify(exactly = 1) { requests.change(ScreenType.HOME, requiresCharging = false) }
        events.onTimeEvent(at(1, 23))
        verify(exactly = 1) { requests.change(any(), any()) }
    }

    @Test fun `while paused or in battery saver the albums switch but nothing changes`() = runTest {
        stored = both.copy(homeNightAlbumId = "night", enableChanger = false)
        events.onTimeEvent(at(1, 19, 2))
        assertTrue(stored.nightActive)
        stored = stored.copy(enableChanger = true, pauseInBatterySaver = true)
        every { conditions.automaticChangeBlock(any()) } returns ChangeBlock.BATTERY_SAVER
        events.onTimeEvent(at(2, 7, 1))
        assertFalse(stored.nightActive)
        verify(exactly = 0) { requests.change(any(), any()) }
    }

    @Test fun `without a charger the switch waits for one`() = runTest {
        stored = both.copy(homeNightAlbumId = "night", onlyWhileCharging = true)
        every { conditions.automaticChangeBlock(any()) } returns ChangeBlock.NOT_CHARGING
        events.onTimeEvent(at(1, 19, 2))
        verify { requests.change(ScreenType.HOME, requiresCharging = true) }
    }

    @Test fun `the live wallpaper follows the switch by itself`() = runTest {
        mode = WallpaperMode.LIVE
        stored = ScheduleSettings(enableChanger = true, liveAlbumId = "day", liveNightAlbumId = "night")
        events.onTimeEvent(at(1, 19, 2))
        assertTrue(stored.nightActive)
        verify(exactly = 0) { requests.change(any(), any()) }
    }

    @Test fun `night albums that follow the dark theme switch with it`() = runTest {
        stored = both.copy(lockNightAlbumId = "night", nightTrigger = NightTrigger.DARK_MODE)
        dark = true
        assertTrue(events.onDarkThemeMaybeChanged())
        assertTrue(stored.nightActive)
        verify { requests.change(ScreenType.LOCK, requiresCharging = false) }
        // The clock leaves them alone.
        events.onTimeEvent(at(1, 12))
        assertTrue(stored.nightActive)
    }

    @Test fun `adaptive brightness redraws screens drawn for the other theme, even while paused`() = runTest {
        stored = both.copy(adaptiveBrightness = true, enableChanger = false)
        renderedDark[ScreenType.HOME] = false
        renderedDark[ScreenType.LOCK] = true
        dark = true
        assertTrue(events.onDarkThemeMaybeChanged())
        verify { requests.redraw(ScreenType.HOME) }
        verify(exactly = 0) { requests.redraw(ScreenType.LOCK) }
        verify(exactly = 0) { requests.redraw(ScreenType.BOTH) }
    }

    @Test fun `a screen changing to its night album isn't redrawn as well`() = runTest {
        stored = both.copy(adaptiveBrightness = true, homeNightAlbumId = "night", nightTrigger = NightTrigger.DARK_MODE)
        renderedDark[ScreenType.HOME] = false
        renderedDark[ScreenType.LOCK] = false
        dark = true
        events.onDarkThemeMaybeChanged()
        verify { requests.change(ScreenType.HOME, requiresCharging = false) }
        verify { requests.redraw(ScreenType.LOCK) }
        verify(exactly = 0) { requests.redraw(ScreenType.BOTH) }
    }

    @Test fun `nothing left to watch is reported, so the checks stop`() = runTest {
        stored = both
        assertFalse(events.onDarkThemeMaybeChanged())
    }

    @Test fun `syncing puts the album the clock calls for in use`() = runTest {
        stored = both.copy(homeNightAlbumId = "night")
        assertTrue(events.syncNightAlbums(at(1, 21)))
        assertTrue(stored.nightActive)
        assertFalse(events.syncNightAlbums(at(1, 22)))
        assertTrue(events.syncNightAlbums(at(2, 8)))
        assertFalse(stored.nightActive)
    }
}
