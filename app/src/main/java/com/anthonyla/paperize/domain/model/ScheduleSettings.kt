package com.anthonyla.paperize.domain.model

import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.constants.Constants

data class ScheduleSettings(
    val enableChanger: Boolean = false,
    val separateSchedules: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val homeEnabled: Boolean = false,
    val lockEnabled: Boolean = false,
    val homeAlbumId: String? = null,
    val lockAlbumId: String? = null,
    val homeIntervalMinutes: Int = Constants.DEFAULT_INTERVAL_MINUTES,
    val lockIntervalMinutes: Int = Constants.DEFAULT_INTERVAL_MINUTES,
    val liveIntervalMinutes: Int = Constants.DEFAULT_INTERVAL_MINUTES,
    val homeScalingType: ScalingType = ScalingType.FILL,
    val lockScalingType: ScalingType = ScalingType.FILL,
    val homeScrollingEnabled: Boolean = false,
    val homeEffects: WallpaperEffects = WallpaperEffects(),
    val lockEffects: WallpaperEffects = WallpaperEffects(),
    val liveAlbumId: String? = null,
    val liveScalingType: ScalingType = ScalingType.FILL,
    val liveEffects: WallpaperEffects = WallpaperEffects(),
    val adaptiveBrightness: Boolean = false,
    // Plan 6.1: automatic changes (not manual ones) can wait for a charger or pause in battery saver.
    val onlyWhileCharging: Boolean = false,
    val pauseInBatterySaver: Boolean = false,
    // Plan 6.2: static mode can also change when the screen turns off or the phone is unlocked.
    val changeOnScreenOff: Boolean = false,
    val screenOffTarget: ScreenType = ScreenType.BOTH,
    val changeOnUnlock: Boolean = false,
    val unlockTarget: ScreenType = ScreenType.HOME,
    /** Screen-off and unlock leave a screen alone if it changed less than this long ago. */
    val triggerGapMinutes: Int = Constants.DEFAULT_TRIGGER_GAP_MINUTES,
    // Plan 6.4: set times instead of an interval, and night albums.
    val scheduleType: ScheduleType = ScheduleType.INTERVAL,
    /** Minutes after midnight, sorted, for [ScheduleType.TIMES]. */
    val changeTimes: List<Int> = Constants.DEFAULT_CHANGE_TIMES,
    val homeNightAlbumId: String? = null,
    val lockNightAlbumId: String? = null,
    val liveNightAlbumId: String? = null,
    val nightTrigger: NightTrigger = NightTrigger.CLOCK,
    val nightStartMinutes: Int = Constants.DEFAULT_NIGHT_START_MINUTES,
    val dayStartMinutes: Int = Constants.DEFAULT_DAY_START_MINUTES,
    /** Whether the night albums are in use. Only the day/night switch writes it. */
    val nightActive: Boolean = false
) {
    val effectiveLockIntervalMinutes: Int
        get() = if (homeEnabled && lockEnabled && separateSchedules) lockIntervalMinutes else homeIntervalMinutes

    /**
     * Whether anything can rotate: the live album, or at least one turned-on static screen with an
     * album. A turned-on screen still waiting for its album doesn't stop the other one.
     */
    fun hasRequiredAlbums(mode: WallpaperMode): Boolean = activeScreens(mode).isNotEmpty()

    /**
     * The album a screen rotates right now, or null while that screen is turned off. Turning a
     * screen off keeps its album, so every reader must go through this rather than the raw album
     * IDs. At night a screen with a night album rotates that one instead (plan 6.4).
     */
    fun albumFor(screen: ScreenType): String? = when (screen) {
        ScreenType.HOME -> dayOrNight(homeAlbumId, homeNightAlbumId)?.takeIf { homeEnabled }
        ScreenType.LOCK -> dayOrNight(lockAlbumId, lockNightAlbumId)?.takeIf { lockEnabled }
        ScreenType.LIVE -> dayOrNight(liveAlbumId, liveNightAlbumId)
        ScreenType.BOTH -> null
    }

    /** A night album only counts next to a day album, so a screen never rotates at night alone. */
    private fun dayOrNight(day: String?, night: String?): String? = when {
        day == null -> null
        nightActive && night != null -> night
        else -> day
    }

    /** The night album a screen would switch to, or null if it has none (or no day album, or is off). */
    fun nightAlbumFor(screen: ScreenType): String? = when (screen) {
        ScreenType.HOME -> homeNightAlbumId?.takeIf { homeEnabled && homeAlbumId != null }
        ScreenType.LOCK -> lockNightAlbumId?.takeIf { lockEnabled && lockAlbumId != null }
        ScreenType.LIVE -> liveNightAlbumId?.takeIf { liveAlbumId != null }
        ScreenType.BOTH -> null
    }

    /** Screens whose album changes when day turns to night or back. */
    fun screensWithNightAlbum(mode: WallpaperMode): Set<ScreenType> =
        if (mode == WallpaperMode.LIVE) setOfNotNull(ScreenType.LIVE.takeIf { nightAlbumFor(it) != null })
        else setOf(ScreenType.HOME, ScreenType.LOCK).filterTo(mutableSetOf()) { nightAlbumFor(it) != null }

    /** Static screens that are turned on and have an album, each listed on its own. */
    fun rotatingStaticScreens(): Set<ScreenType> = buildSet {
        if (albumFor(ScreenType.HOME) != null) add(ScreenType.HOME)
        if (albumFor(ScreenType.LOCK) != null) add(ScreenType.LOCK)
    }

    /** Both static screens rotate the same album, so they must avoid showing the same image. */
    fun screensShareAlbum(): Boolean =
        albumFor(ScreenType.HOME) != null && albumFor(ScreenType.HOME) == albumFor(ScreenType.LOCK)

    fun activeScreens(mode: WallpaperMode): Set<ScreenType> {
        if (mode == WallpaperMode.LIVE) return if (albumFor(ScreenType.LIVE) != null) setOf(ScreenType.LIVE) else emptySet()
        val screens = rotatingStaticScreens()
        if (screens.size == 2 && screensShareAlbum() && !separateSchedules) return setOf(ScreenType.BOTH)
        return screens
    }

    /**
     * The static screens a screen-off or unlock change covers. With one screen rotating it is that
     * screen, whatever [target] says; with both, the screens [target] names.
     */
    fun triggerScreens(target: ScreenType): Set<ScreenType> {
        val rotating = rotatingStaticScreens()
        if (rotating.size < 2) return rotating
        return when (target) {
            ScreenType.HOME -> setOf(ScreenType.HOME)
            ScreenType.LOCK -> setOf(ScreenType.LOCK)
            else -> rotating
        }
    }

    /** Whether screen-off or unlock changes should be listened for (static mode only). */
    fun listensForScreenEvents(mode: WallpaperMode): Boolean =
        mode == WallpaperMode.STATIC && enableChanger && (changeOnScreenOff || changeOnUnlock) &&
            rotatingStaticScreens().isNotEmpty()

    fun intervalMinutes(screen: ScreenType): Int = when (screen) {
        ScreenType.HOME, ScreenType.BOTH -> homeIntervalMinutes
        ScreenType.LOCK -> effectiveLockIntervalMinutes
        ScreenType.LIVE -> liveIntervalMinutes
    }

    fun validate(): ScheduleSettings = copy(
        homeIntervalMinutes = homeIntervalMinutes.coerceAtLeast(Constants.MIN_INTERVAL_MINUTES),
        lockIntervalMinutes = lockIntervalMinutes.coerceAtLeast(Constants.MIN_INTERVAL_MINUTES),
        liveIntervalMinutes = liveIntervalMinutes.coerceAtLeast(Constants.MIN_LIVE_INTERVAL_MINUTES),
        homeEffects = homeEffects.validate(),
        lockEffects = lockEffects.validate(),
        liveEffects = liveEffects.validate(),
        screenOffTarget = screenOffTarget.takeIf { it != ScreenType.LIVE } ?: ScreenType.BOTH,
        unlockTarget = unlockTarget.takeIf { it != ScreenType.LIVE } ?: ScreenType.HOME,
        triggerGapMinutes = triggerGapMinutes.coerceIn(0, Constants.MAX_TRIGGER_GAP_MINUTES),
        changeTimes = validChangeTimes(changeTimes),
        nightStartMinutes = nightStartMinutes.coerceIn(0, Constants.MINUTES_PER_DAY - 1),
        dayStartMinutes = dayStartMinutes.coerceIn(0, Constants.MINUTES_PER_DAY - 1)
    )

    /** Whether the background jobs, alarms or checks these settings call for differ from [other]'s. */
    fun hasSchedulingChanges(other: ScheduleSettings): Boolean {
        return enableChanger != other.enableChanger ||
               homeAlbumId != other.homeAlbumId ||
               lockAlbumId != other.lockAlbumId ||
               liveAlbumId != other.liveAlbumId ||
               homeEnabled != other.homeEnabled ||
               lockEnabled != other.lockEnabled ||
               homeIntervalMinutes != other.homeIntervalMinutes ||
               lockIntervalMinutes != other.lockIntervalMinutes ||
               separateSchedules != other.separateSchedules ||
               liveIntervalMinutes != other.liveIntervalMinutes ||
               onlyWhileCharging != other.onlyWhileCharging ||
               adaptiveBrightness != other.adaptiveBrightness ||
               scheduleType != other.scheduleType ||
               changeTimes != other.changeTimes ||
               homeNightAlbumId != other.homeNightAlbumId ||
               lockNightAlbumId != other.lockNightAlbumId ||
               liveNightAlbumId != other.liveNightAlbumId ||
               nightTrigger != other.nightTrigger ||
               nightStartMinutes != other.nightStartMinutes ||
               dayStartMinutes != other.dayStartMinutes ||
               nightActive != other.nightActive
    }

    /**
     * Whether [screen]'s wallpaper is drawn differently, so its current image needs a re-render.
     * [albumHasOwnEffects]: the screen's album uses its own effects (plan 5.3), so the screen's
     * effects don't show and changing them changes nothing.
     */
    fun hasDisplayChanges(other: ScheduleSettings, screen: ScreenType, albumHasOwnEffects: Boolean = false): Boolean = when (screen) {
        ScreenType.HOME -> homeScalingType != other.homeScalingType ||
            homeScrollingEnabled != other.homeScrollingEnabled ||
            (!albumHasOwnEffects && homeEffects != other.homeEffects) ||
            adaptiveBrightness != other.adaptiveBrightness
        ScreenType.LOCK -> lockScalingType != other.lockScalingType ||
            (!albumHasOwnEffects && lockEffects != other.lockEffects) ||
            adaptiveBrightness != other.adaptiveBrightness
        ScreenType.LIVE -> liveScalingType != other.liveScalingType ||
            liveEffects != other.liveEffects ||
            adaptiveBrightness != other.adaptiveBrightness
        ScreenType.BOTH -> hasDisplayChanges(other, ScreenType.HOME) || hasDisplayChanges(other, ScreenType.LOCK)
    }

    /** Display changes reapply the current wallpaper without rescheduling periodic work. */
    fun hasDisplayChanges(other: ScheduleSettings): Boolean {
        return homeScalingType != other.homeScalingType ||
               lockScalingType != other.lockScalingType ||
               homeScrollingEnabled != other.homeScrollingEnabled ||
               homeEffects != other.homeEffects ||
               lockEffects != other.lockEffects ||
               liveEffects != other.liveEffects ||
               liveScalingType != other.liveScalingType ||
               adaptiveBrightness != other.adaptiveBrightness
    }

}

/** Set times kept in range, without duplicates, in order, and never an empty list. */
fun validChangeTimes(times: List<Int>): List<Int> =
    times.map { it.coerceIn(0, Constants.MINUTES_PER_DAY - 1) }
        .distinct().sorted().take(Constants.MAX_CHANGE_TIMES)
        .ifEmpty { Constants.DEFAULT_CHANGE_TIMES }

/**
 * WorkManager cannot run periodic jobs more often than every 15 minutes. Short live-wallpaper
 * intervals are therefore driven by the visible wallpaper engine and stop when it is hidden.
 */
fun usesVisibleLiveTimer(intervalMinutes: Int): Boolean =
    intervalMinutes in Constants.MIN_LIVE_INTERVAL_MINUTES until Constants.MIN_INTERVAL_MINUTES
