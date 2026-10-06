package com.anthonyla.paperize.domain.model

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
    val adaptiveBrightness: Boolean = false
) {
    val effectiveLockIntervalMinutes: Int
        get() = if (homeEnabled && lockEnabled && separateSchedules) lockIntervalMinutes else homeIntervalMinutes

    /**
     * Whether anything can rotate: the live album, or at least one turned-on static screen with an
     * album. A turned-on screen still waiting for its album doesn't stop the other one.
     */
    fun hasRequiredAlbums(mode: WallpaperMode): Boolean = activeScreens(mode).isNotEmpty()

    /**
     * The album a screen rotates, or null while that screen is turned off. Turning a screen off
     * keeps its album, so every reader must go through this rather than the raw album IDs.
     */
    fun albumFor(screen: ScreenType): String? = when (screen) {
        ScreenType.HOME -> homeAlbumId?.takeIf { homeEnabled }
        ScreenType.LOCK -> lockAlbumId?.takeIf { lockEnabled }
        ScreenType.LIVE -> liveAlbumId
        ScreenType.BOTH -> null
    }

    /** Static screens that are turned on and have an album, each listed on its own. */
    fun rotatingStaticScreens(): Set<ScreenType> = buildSet {
        if (albumFor(ScreenType.HOME) != null) add(ScreenType.HOME)
        if (albumFor(ScreenType.LOCK) != null) add(ScreenType.LOCK)
    }

    /** Both static screens rotate the same album, so they must avoid showing the same image. */
    fun screensShareAlbum(): Boolean =
        albumFor(ScreenType.HOME) != null && albumFor(ScreenType.HOME) == albumFor(ScreenType.LOCK)

    fun activeScreens(mode: WallpaperMode): Set<ScreenType> {
        if (mode == WallpaperMode.LIVE) return if (liveAlbumId != null) setOf(ScreenType.LIVE) else emptySet()
        val screens = rotatingStaticScreens()
        if (screens.size == 2 && screensShareAlbum() && !separateSchedules) return setOf(ScreenType.BOTH)
        return screens
    }

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
        liveEffects = liveEffects.validate()
    )

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
               liveIntervalMinutes != other.liveIntervalMinutes
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

/**
 * WorkManager cannot run periodic jobs more often than every 15 minutes. Short live-wallpaper
 * intervals are therefore driven by the visible wallpaper engine and stop when it is hidden.
 */
fun usesVisibleLiveTimer(intervalMinutes: Int): Boolean =
    intervalMinutes in Constants.MIN_LIVE_INTERVAL_MINUTES until Constants.MIN_INTERVAL_MINUTES
