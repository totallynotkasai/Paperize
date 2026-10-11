package com.anthonyla.paperize.core

/** How automatic changes are timed. */
enum class ScheduleType {
    /** Every N minutes, from a background job (or the visible live engine for short intervals). */
    INTERVAL,

    /** At set times of day, from inexact alarms. */
    TIMES;

    companion object {
        fun fromString(value: String?): ScheduleType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: INTERVAL
    }
}

/** What switches between the day and night albums. */
enum class NightTrigger {
    /** Night starts and ends at two clock times. */
    CLOCK,

    /** Night follows the phone's dark theme. */
    DARK_MODE;

    companion object {
        fun fromString(value: String?): NightTrigger =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: CLOCK
    }
}
