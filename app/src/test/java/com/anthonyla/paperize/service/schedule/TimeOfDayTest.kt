package com.anthonyla.paperize.service.schedule

import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeOfDayTest {
    private val london = ZoneId.of("Europe/London")
    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 6) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, london)

    @Test fun `night can run past midnight`() {
        val (night, day) = 19 * 60 to 7 * 60
        assertTrue(isNightAt(19 * 60, night, day))
        assertTrue(isNightAt(23 * 60 + 59, night, day))
        assertTrue(isNightAt(0, night, day))
        assertTrue(isNightAt(6 * 60 + 59, night, day))
        assertFalse(isNightAt(7 * 60, night, day))
        assertFalse(isNightAt(18 * 60 + 59, night, day))
    }

    @Test fun `night can sit inside one day, and equal times mean no night`() {
        assertTrue(isNightAt(2 * 60, 60, 6 * 60))
        assertFalse(isNightAt(7 * 60, 60, 6 * 60))
        assertFalse(isNightAt(0, 60, 6 * 60))
        assertFalse(isNightAt(12 * 60, 12 * 60, 12 * 60))
    }

    @Test fun `night follows the clock or the dark theme as chosen`() {
        val clock = ScheduleSettings(nightTrigger = NightTrigger.CLOCK)
        assertTrue(isNight(clock, at(1, 22)) { false })
        assertFalse(isNight(clock, at(1, 12)) { true })
        val theme = clock.copy(nightTrigger = NightTrigger.DARK_MODE)
        assertTrue(isNight(theme, at(1, 12)) { true })
        assertFalse(isNight(theme, at(1, 22)) { false })
    }

    @Test fun `the next occurrence is later today or tomorrow, never now`() {
        val times = listOf(7 * 60, 19 * 60)
        assertEquals(at(1, 19), nextOccurrence(at(1, 12), times))
        assertEquals(at(2, 7), nextOccurrence(at(1, 19), times))
        assertEquals(at(2, 7), nextOccurrence(at(1, 23, 30), times))
        assertNull(nextOccurrence(at(1, 12), emptyList()))
    }

    @Test fun `a time skipped by the clocks going forward happens just after the jump`() {
        // 29 March 2026: London jumps from 01:00 to 02:00.
        val before = ZonedDateTime.of(2026, 3, 29, 0, 30, 0, 0, london)
        val next = nextOccurrence(before, listOf(60 + 30))!!
        assertEquals(29, next.dayOfMonth)
        assertEquals(2, next.hour)
        assertEquals(30, next.minute)
    }

    @Test fun `a time passed only if it came round after the last check and by now`() {
        val times = listOf(7 * 60, 19 * 60)
        assertTrue(timePassedBetween(at(1, 18), at(1, 19, 5), times))
        assertTrue(timePassedBetween(at(1, 18), at(2, 9), times))
        assertFalse(timePassedBetween(at(1, 8), at(1, 18), times))
        // Exactly at the time counts; the last check itself doesn't.
        assertTrue(timePassedBetween(at(1, 18), at(1, 19), times))
        assertFalse(timePassedBetween(at(1, 19), at(1, 19, 5), times))
    }

    @Test fun `alarms cover set times and the clock switch only while each applies`() {
        val base = ScheduleSettings(
            enableChanger = true, homeEnabled = true, homeAlbumId = "day",
            scheduleType = ScheduleType.TIMES, changeTimes = listOf(9 * 60)
        )
        assertEquals(at(1, 9), nextTimedEvent(base, WallpaperMode.STATIC, at(1, 8)))
        // Paused: no set times. No night album: no switch.
        assertNull(nextTimedEvent(base.copy(enableChanger = false), WallpaperMode.STATIC, at(1, 8)))
        val night = base.copy(enableChanger = false, homeNightAlbumId = "night")
        // The switch still happens while paused, so a change by hand uses the right album.
        assertEquals(at(1, 19), nextTimedEvent(night, WallpaperMode.STATIC, at(1, 8)))
        assertEquals(at(1, 9), nextTimedEvent(night.copy(enableChanger = true), WallpaperMode.STATIC, at(1, 8)))
        // A dark-theme switch needs no alarm, and neither does a night of no length.
        assertNull(nextTimedEvent(night.copy(nightTrigger = NightTrigger.DARK_MODE), WallpaperMode.STATIC, at(1, 8)))
        assertNull(nextTimedEvent(night.copy(nightStartMinutes = 60, dayStartMinutes = 60), WallpaperMode.STATIC, at(1, 8)))
        // An interval schedule has no set times.
        assertNull(nextTimedEvent(base.copy(scheduleType = ScheduleType.INTERVAL), WallpaperMode.STATIC, at(1, 8)))
    }
}
