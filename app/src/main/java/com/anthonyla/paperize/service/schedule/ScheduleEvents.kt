package com.anthonyla.paperize.service.schedule

import android.content.Context
import android.util.Log
import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.service.wallpaper.WallpaperRequest
import com.anthonyla.paperize.service.wallpaper.WallpaperRequestWorker
import com.anthonyla.paperize.service.worker.WallpaperScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Hands automatic changes and theme redraws to background jobs; separate so tests can see them. */
class ScheduledRequests @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** [requiresCharging]: wait for the charger before changing. */
    fun change(screen: ScreenType, requiresCharging: Boolean) = WallpaperRequestWorker.enqueueAutomatic(
        context, WallpaperRequest.Change(screen, automatic = true), requiresCharging
    )

    fun redraw(screen: ScreenType) = WallpaperRequestWorker.enqueueRedraw(context, screen)
}

/**
 * Set times, the day/night switch and dark-theme redraws (plans 6.3 and 6.4).
 *
 * The night albums are in use while [ScheduleSettings.nightActive] is set; this class is the only
 * thing that sets it. When it switches, each static screen with a night album changes to the
 * album now in use, as an automatic change: not while changing is paused or in battery saver (with
 * that setting on), and after plugging in with "Only while charging" (plan 6.1). If the switch
 * can't change a screen, its next change draws from the right album anyway. The live wallpaper
 * follows the album in use by itself.
 */
@Singleton
class ScheduleEvents @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val conditions: ChangeConditions,
    private val state: ScheduleState,
    private val alarms: TimeOfDayAlarms,
    private val requests: ScheduledRequests,
    private val scheduler: WallpaperScheduler
) {
    private val mutex = Mutex()

    /**
     * A set time or clock switch came round (the alarm), or Paperize is catching up after a
     * restart, a time change or an alarm Android dropped. Times that came round since the last
     * check come to one change. Arms the next alarm.
     */
    suspend fun onTimeEvent(now: ZonedDateTime = ZonedDateTime.now()) = mutex.withLock {
        val settings = settingsRepository.getScheduleSettings()
        val mode = settingsRepository.getWallpaperMode()
        val nowMillis = now.toInstant().toEpochMilli()
        val lastCheck = state.lastTimedCheck
        state.lastTimedCheck = nowMillis
        val timeDue = usesSetTimes(settings, mode) && lastCheck in 1 until nowMillis &&
            timePassedBetween(ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastCheck), now.zone), now, settings.changeTimes)
        val switched = settings.nightTrigger == NightTrigger.CLOCK && switchAlbums(isNight(settings, now) { false })
        val updated = if (switched) settingsRepository.getScheduleSettings() else settings
        when {
            // Every screen changes at a set time, those with a night album from the album now in use.
            timeDue -> requestChange(updated.activeScreens(mode), updated, "set time")
            switched -> requestChange(nightAlbumScreens(settings, mode), updated, "day/night switch")
        }
        if (switched) replanJobs(updated, mode) else alarms.sync(updated, mode, now)
    }

    /**
     * The dark theme may have switched: the application heard of it, the theme setting changed, or
     * the 15-minute check ran. Switches albums that follow the theme and redraws static screens
     * whose adaptive brightness was worked out for the other theme. Returns whether anything still
     * depends on the theme.
     */
    suspend fun onDarkThemeMaybeChanged(): Boolean = mutex.withLock {
        val settings = settingsRepository.getScheduleSettings()
        val mode = settingsRepository.getWallpaperMode()
        val dark = conditions.isDarkTheme()
        val switched = settings.nightTrigger == NightTrigger.DARK_MODE && switchAlbums(dark)
        val updated = if (switched) settingsRepository.getScheduleSettings() else settings
        val changedNow = if (switched) requestChange(nightAlbumScreens(settings, mode), updated, "dark theme switch") else emptySet()
        if (switched) replanJobs(updated, mode)
        // Redrawing keeps the image, so it isn't held back like a change: it happens while paused
        // too, as effect edits do (plan 2.3).
        val redraw = screensToRedraw(updated, mode, dark) - changedNow
        requestScreenFor(redraw)?.let {
            Log.d(TAG, "Redrawing $it for the ${if (dark) "dark" else "light"} theme")
            requests.redraw(it)
        }
        watchesDarkTheme(updated, mode)
    }

    /**
     * Put the albums the clock or theme calls for in use now, after the night settings changed in
     * the app. Returns whether that switched them; the caller changes the affected screens.
     */
    suspend fun syncNightAlbums(now: ZonedDateTime = ZonedDateTime.now()): Boolean = mutex.withLock {
        val settings = settingsRepository.getScheduleSettings()
        settingsRepository.updateNightActive(isNight(settings, now, conditions::isDarkTheme))
    }

    /** Static screens last drawn for the other theme, while adaptive brightness is on. */
    private fun screensToRedraw(settings: ScheduleSettings, mode: WallpaperMode, dark: Boolean): Set<ScreenType> {
        if (mode != WallpaperMode.STATIC || !settings.adaptiveBrightness) return emptySet()
        return settings.rotatingStaticScreens().filterTo(mutableSetOf()) { screen ->
            state.renderedDark(screen)?.let { it != dark } == true
        }
    }

    /**
     * Screens that share a day album but not a night album (or the other way round) move between
     * one shared job and a job each; existing countdowns carry over. Also re-arms the alarm.
     */
    private suspend fun replanJobs(settings: ScheduleSettings, mode: WallpaperMode) =
        scheduler.updateSchedules(settings, mode)

    /** Store the switch; returns whether the albums in use changed. */
    private suspend fun switchAlbums(night: Boolean): Boolean {
        if (!settingsRepository.updateNightActive(night)) return false
        Log.d(TAG, if (night) "Night albums now in use" else "Day albums now in use")
        return true
    }

    /** The static screens a switch changes; the live wallpaper follows it by itself. */
    private fun nightAlbumScreens(settings: ScheduleSettings, mode: WallpaperMode): Set<ScreenType> =
        settings.screensWithNightAlbum(mode) - ScreenType.LIVE

    /**
     * Ask for an automatic change of [screens]. Returns the screens that change now; with "Only
     * while charging" and no charger, the change waits for one instead.
     */
    private fun requestChange(screens: Set<ScreenType>, settings: ScheduleSettings, reason: String): Set<ScreenType> {
        val screen = requestScreenFor(screens) ?: return emptySet()
        if (!settings.enableChanger) {
            Log.d(TAG, "Not changing $screen for the $reason: changing is paused")
            return emptySet()
        }
        return when (conditions.automaticChangeBlock(settings)) {
            ChangeBlock.BATTERY_SAVER -> {
                Log.d(TAG, "Not changing $screen for the $reason: battery saver is on")
                emptySet()
            }
            ChangeBlock.NOT_CHARGING -> {
                Log.d(TAG, "The $reason change of $screen waits for the charger")
                requests.change(screen, requiresCharging = true)
                emptySet()
            }
            null -> {
                Log.d(TAG, "Changing $screen for the $reason")
                requests.change(screen, requiresCharging = settings.onlyWhileCharging)
                screen.staticScreens().toSet().ifEmpty { setOf(screen) }
            }
        }
    }

    private companion object {
        const val TAG = "ScheduleEvents"
    }
}
