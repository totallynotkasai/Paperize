package com.anthonyla.paperize.service.schedule

import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.ScheduleSettings
import java.time.LocalTime
import java.time.ZonedDateTime

/** Set times and day/night clock times are minutes after midnight, local time (plan 6.4). */
internal val ZonedDateTime.minuteOfDay: Int get() = hour * Constants.MINUTES_PER_HOUR + minute

/**
 * Whether [minuteOfDay] falls in the night that starts at [nightStart] and ends at [dayStart].
 * Night may run past midnight (19:00–07:00) or not (01:00–06:00); equal times mean no night.
 */
fun isNightAt(minuteOfDay: Int, nightStart: Int, dayStart: Int): Boolean = when {
    nightStart == dayStart -> false
    nightStart < dayStart -> minuteOfDay in nightStart until dayStart
    else -> minuteOfDay >= nightStart || minuteOfDay < dayStart
}

/**
 * Whether the night albums should be in use at [now]: by the clock, or by the phone's dark theme
 * ([darkTheme] is asked only then).
 */
fun isNight(settings: ScheduleSettings, now: ZonedDateTime, darkTheme: () -> Boolean): Boolean =
    when (settings.nightTrigger) {
        NightTrigger.CLOCK -> isNightAt(now.minuteOfDay, settings.nightStartMinutes, settings.dayStartMinutes)
        NightTrigger.DARK_MODE -> darkTheme()
    }

/**
 * The first moment strictly after [after] whose local time is one of [minutesOfDay], or null for
 * none. A time skipped by a daylight-saving jump happens just after the jump.
 */
fun nextOccurrence(after: ZonedDateTime, minutesOfDay: Collection<Int>): ZonedDateTime? {
    if (minutesOfDay.isEmpty()) return null
    val today = after.toLocalDate()
    return (0L..2L).asSequence()
        .flatMap { days ->
            minutesOfDay.asSequence().map { minutes ->
                ZonedDateTime.of(
                    today.plusDays(days),
                    LocalTime.of(minutes / Constants.MINUTES_PER_HOUR, minutes % Constants.MINUTES_PER_HOUR),
                    after.zone
                )
            }
        }
        .filter { it.isAfter(after) }
        .minOrNull()
}

/** Whether one of [minutesOfDay] came round after [since] and no later than [now]. */
fun timePassedBetween(since: ZonedDateTime, now: ZonedDateTime, minutesOfDay: Collection<Int>): Boolean {
    val next = nextOccurrence(since, minutesOfDay) ?: return false
    return !next.isAfter(now)
}
