package com.anthonyla.paperize.service.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.ScheduleSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Whether set times drive the changes: chosen, changing is on, and something can rotate. */
internal fun usesSetTimes(settings: ScheduleSettings, mode: WallpaperMode): Boolean =
    settings.scheduleType == ScheduleType.TIMES && settings.enableChanger && settings.hasRequiredAlbums(mode)

/** Whether the clock switches albums: a night album in use, clock trigger, and a night of some length. */
internal fun usesClockSwitch(settings: ScheduleSettings, mode: WallpaperMode): Boolean =
    settings.nightTrigger == NightTrigger.CLOCK && settings.nightStartMinutes != settings.dayStartMinutes &&
        settings.screensWithNightAlbum(mode).isNotEmpty()

/** The next set time or clock day/night switch after [now], or null when neither applies. */
internal fun nextTimedEvent(settings: ScheduleSettings, mode: WallpaperMode, now: ZonedDateTime): ZonedDateTime? {
    val times = buildSet {
        if (usesSetTimes(settings, mode)) addAll(settings.changeTimes)
        if (usesClockSwitch(settings, mode)) {
            add(settings.nightStartMinutes)
            add(settings.dayStartMinutes)
        }
    }
    return nextOccurrence(now, times)
}

/**
 * One inexact alarm for the next set time or clock day/night switch (plan 6.4). Inexact alarms need
 * no special permission and let Android save battery: it fires within [Constants.TIMED_ALARM_WINDOW_MS]
 * after the time while the phone is in use; while it sleeps untouched, Android may hold it until it
 * next wakes up. Each alarm arms the next one; restarts and time-zone changes re-arm it.
 */
@Singleton
class TimeOfDayAlarms @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val state: ScheduleState
) {
    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    fun sync(settings: ScheduleSettings, mode: WallpaperMode, now: ZonedDateTime = ZonedDateTime.now()) {
        // Only times that come round while this list is in use count: starting set times, resuming
        // changing or editing the list doesn't make up for times that came round before. A restart
        // keeps the list, so a time missed while the phone was off is still caught up.
        val inUse = if (usesSetTimes(settings, mode)) settings.changeTimes.joinToString(",") else null
        if (inUse == null || inUse != state.setTimesInUse) {
            state.lastTimedCheck = now.toInstant().toEpochMilli()
            state.setTimesInUse = inUse
        }
        val next = nextTimedEvent(settings, mode, now)
        if (next == null) {
            cancel()
            return
        }
        val at = next.toInstant().toEpochMilli()
        alarmManager?.setWindow(AlarmManager.RTC_WAKEUP, at, Constants.TIMED_ALARM_WINDOW_MS, pendingIntent())
        Log.d(TAG, "Next set time or day/night switch at $next")
    }

    fun cancel() {
        val intent = pendingIntent()
        alarmManager?.cancel(intent)
        intent.cancel()
    }

    /** Whether an alarm is armed (for tests); cancelling also drops the PendingIntent. */
    internal fun isArmed(): Boolean = PendingIntent.getBroadcast(
        context, REQUEST_CODE, timeOfDayIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
    ) != null

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        timeOfDayIntent(),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun timeOfDayIntent() = Intent(context, TimeOfDayReceiver::class.java).setAction(ACTION_TIME_OF_DAY)

    companion object {
        private const val TAG = "TimeOfDayAlarms"
        private const val REQUEST_CODE = 6004
        const val ACTION_TIME_OF_DAY = "com.anthonyla.paperize.ACTION_TIME_OF_DAY"
    }
}
