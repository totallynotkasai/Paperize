package com.anthonyla.paperize.service.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.service.schedule.DarkThemeChecks
import com.anthonyla.paperize.service.schedule.TimeOfDayAlarms
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class WallpaperScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val timeOfDayAlarms: TimeOfDayAlarms,
    private val darkThemeChecks: DarkThemeChecks
) {
    private val workManager = WorkManager.getInstance(context)
    private val mutex = Mutex()

    companion object {
        private const val TAG = "WallpaperScheduler"
    }

    /**
     * Schedule periodic wallpaper change
     *
     * @param screenType HOME, LOCK, or BOTH (for synchronized schedules)
     * @param intervalMinutes Interval between changes (minimum 15 minutes for WorkManager)
     */
    fun scheduleWallpaperChange(
        screenType: ScreenType,
        intervalMinutes: Int,
        resetInterval: Boolean = false,
        onlyIfNotScheduled: Boolean = false,
        firstRunAt: Long? = null,
        requiresCharging: Boolean = false
    ) {

        val adjustedInterval = intervalMinutes.toLong().coerceAtLeast(Constants.MIN_INTERVAL_MINUTES.toLong())
        val workName = getWorkName(screenType)

        val inputData = Data.Builder()
            .putString(Constants.EXTRA_SCREEN_TYPE, screenType.name)
            .build()

        val nextRun = firstRunAt ?: if (resetInterval) {
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(adjustedInterval)
        } else null
        val workRequest = PeriodicWorkRequestBuilder<WallpaperChangeWorker>(
            adjustedInterval,
            TimeUnit.MINUTES
        )
            .setInputData(inputData)
            .addTag(getWorkTag(screenType))
            .apply {
                // "Only while charging" (plan 6.1): a change that falls due unplugged waits for the charger.
                if (requiresCharging) setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                // New periodic work otherwise runs immediately. Unlike an initial delay, this
                // one-run deadline also survives unrelated UPDATE requests from settings edits.
                if (nextRun != null) setNextScheduleTimeOverride(nextRun)
            }
            .build()

        // Preserve the existing work and explicitly move only the next run after a manual change.
        // Replacing periodic work starts a new first period, which can otherwise run immediately.
        workManager.enqueueUniquePeriodicWork(
            workName,
            if (onlyIfNotScheduled) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.d(TAG, "Scheduled $screenType wallpaper change every $adjustedInterval minutes")
    }

    /** Reset only the automatic schedules affected by a successful manual change. */
    fun resetAfterManualChange(
        screenType: ScreenType,
        settings: ScheduleSettings,
        wallpaperMode: WallpaperMode
    ) {
        scheduledScreensToReset(screenType, settings, wallpaperMode).forEach { scheduledScreen ->
            scheduleWallpaperChange(
                screenType = scheduledScreen,
                intervalMinutes = settings.intervalMinutes(scheduledScreen),
                resetInterval = true,
                requiresCharging = settings.onlyWhileCharging
            )
        }
    }

    /**
     * Reconcile all jobs from the same policy on startup, settings edits and album selection.
     *
     * Existing jobs keep their countdown. A new job never runs straight away: one that takes over
     * from a job covering the same screen (Home and Lock merging into one shared job, or splitting
     * out of it) keeps that job's next run, and any other starts a full interval from now. Whoever
     * turns a screen on applies its first wallpaper by hand, so it never changes twice.
     */
    suspend fun updateSchedules(
        settings: ScheduleSettings,
        mode: WallpaperMode,
        onlyIfNotScheduled: Boolean = false
    ) = mutex.withLock {
        val enabled = settings.enableChanger && settings.hasRequiredAlbums(mode)
        val targets = scheduledScreens(settings, mode)
        // Read before cancelling anything, so a replacement can inherit its predecessor's timing.
        val pending = ScreenType.entries.associateWith { pendingNextRun(it) }
        val now = System.currentTimeMillis()
        for (screen in ScreenType.entries) {
            if (screen in targets) {
                val interval = settings.intervalMinutes(screen)
                val firstRunAt = if (pending[screen] != null) null else {
                    firstRunForNewJob(screen, interval, pending.filterKeys { it !in targets }, now)
                }
                scheduleWallpaperChange(
                    screen, interval, onlyIfNotScheduled = onlyIfNotScheduled, firstRunAt = firstRunAt,
                    requiresCharging = settings.onlyWhileCharging
                )
            } else {
                cancelWallpaperChange(screen)
            }
        }
        if (enabled) scheduleAlbumRefresh(onlyIfNotScheduled) else cancelAlbumRefresh()
        // Set times and the clock day/night switch use alarms; dark-theme switches have their own checks.
        timeOfDayAlarms.sync(settings, mode)
        darkThemeChecks.sync(settings, mode)
    }

    /** The next run of this screen's unfinished job, [Long.MAX_VALUE] if unknown, or null if none. */
    private suspend fun pendingNextRun(screen: ScreenType): Long? =
        workManager.getWorkInfosForUniqueWorkFlow(getWorkName(screen)).first()
            .firstOrNull { !it.state.isFinished }
            ?.nextScheduleTimeMillis

    fun cancelWallpaperChange(screenType: ScreenType) {
        val workName = getWorkName(screenType)
        workManager.cancelUniqueWork(workName)
        Log.d(TAG, "Cancelled $screenType wallpaper change schedule")
    }

    fun cancelAllWallpaperChanges() {
        workManager.cancelUniqueWork(Constants.WORK_NAME_HOME)
        workManager.cancelUniqueWork(Constants.WORK_NAME_LOCK)
        workManager.cancelUniqueWork(Constants.WORK_NAME_BOTH)
        workManager.cancelUniqueWork(Constants.WORK_NAME_LIVE)
        cancelAlbumRefresh()
        timeOfDayAlarms.cancel()
        darkThemeChecks.cancel()
        Log.d(TAG, "Cancelled all wallpaper change schedules")
    }

    /** Refresh daily, with the first run targeting 3 AM. */
    fun scheduleAlbumRefresh(onlyIfNotScheduled: Boolean = false) {
        val calendar = Calendar.getInstance()
        val nowMillis = calendar.timeInMillis
        
        if (calendar.get(Calendar.HOUR_OF_DAY) >= 3) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 3)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val initialDelay = calendar.timeInMillis - nowMillis

        val workRequest = PeriodicWorkRequestBuilder<AlbumRefreshWorker>(
            1,
            TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(Constants.WORK_TAG_REFRESH)
            .build()

        workManager.enqueueUniquePeriodicWork(
            Constants.WORK_NAME_REFRESH,
            if (onlyIfNotScheduled) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.d(TAG, "Scheduled daily album refresh")
    }

    fun cancelAlbumRefresh() {
        workManager.cancelUniqueWork(Constants.WORK_NAME_REFRESH)
        Log.d(TAG, "Cancelled daily album refresh")
    }

    private fun getWorkName(screenType: ScreenType): String {
        return when (screenType) {
            ScreenType.HOME -> Constants.WORK_NAME_HOME
            ScreenType.LOCK -> Constants.WORK_NAME_LOCK
            ScreenType.BOTH -> Constants.WORK_NAME_BOTH
            ScreenType.LIVE -> Constants.WORK_NAME_LIVE
        }
    }

    private fun getWorkTag(screenType: ScreenType): String {
        return when (screenType) {
            ScreenType.HOME -> Constants.WORK_TAG_HOME
            ScreenType.LOCK -> Constants.WORK_TAG_LOCK
            ScreenType.BOTH -> Constants.WORK_TAG_BOTH
            ScreenType.LIVE -> Constants.WORK_TAG_LIVE
        }
    }

}

/** The periodic jobs these settings call for. Short live intervals run in the visible engine, and set times use alarms. */
internal fun scheduledScreens(settings: ScheduleSettings, mode: WallpaperMode): Set<ScreenType> {
    if (!settings.enableChanger || !settings.hasRequiredAlbums(mode)) return emptySet()
    if (settings.scheduleType == ScheduleType.TIMES) return emptySet()
    return settings.activeScreens(mode).filterTo(mutableSetOf()) { screen ->
        val interval = settings.intervalMinutes(screen)
        interval > 0 && !(screen == ScreenType.LIVE && interval < Constants.MIN_INTERVAL_MINUTES)
    }
}

/**
 * When a new job first runs: at the earliest upcoming run of the jobs it replaces (those covering
 * the same screen), capped at one interval from [now]; with nothing to take over, one interval
 * from [now].
 */
internal fun firstRunForNewJob(
    screen: ScreenType,
    intervalMinutes: Int,
    replaced: Map<ScreenType, Long?>,
    now: Long
): Long {
    val fullInterval = now + TimeUnit.MINUTES.toMillis(
        intervalMinutes.toLong().coerceAtLeast(Constants.MIN_INTERVAL_MINUTES.toLong())
    )
    val inherited = replaced.filter { (other, nextRun) -> nextRun != null && screen.overlaps(other) }
        .values.filterNotNull().filter { it > now }.minOrNull()
    return inherited?.coerceAtMost(fullInterval) ?: fullInterval
}

private fun ScreenType.overlaps(other: ScreenType): Boolean = when (this) {
    ScreenType.LIVE -> other == ScreenType.LIVE
    ScreenType.BOTH -> other == ScreenType.HOME || other == ScreenType.LOCK || other == ScreenType.BOTH
    else -> other == this || other == ScreenType.BOTH
}

internal fun scheduledScreensToReset(
    manualScreen: ScreenType,
    settings: ScheduleSettings,
    wallpaperMode: WallpaperMode
): Set<ScreenType> {
    if (!settings.enableChanger || !settings.hasRequiredAlbums(wallpaperMode)) return emptySet()
    // Set times don't move when the wallpaper is changed by hand.
    if (settings.scheduleType == ScheduleType.TIMES) return emptySet()
    return settings.activeScreens(wallpaperMode).filterTo(mutableSetOf()) { screen ->
        when (screen) {
            ScreenType.LIVE -> manualScreen == ScreenType.LIVE &&
                settings.liveIntervalMinutes >= Constants.MIN_INTERVAL_MINUTES
            ScreenType.BOTH -> manualScreen != ScreenType.LIVE
            else -> manualScreen == screen || manualScreen == ScreenType.BOTH
        }
    }
}
