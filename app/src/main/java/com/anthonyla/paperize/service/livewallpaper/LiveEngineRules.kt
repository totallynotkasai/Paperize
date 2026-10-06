package com.anthonyla.paperize.service.livewallpaper

import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.model.usesVisibleLiveTimer
import com.anthonyla.paperize.service.livewallpaper.renderer.LiveSelection

/*
 * The live wallpaper engines' decisions, kept apart from Android's wallpaper classes so they can be
 * tested. [PaperizeLiveWallpaperService] carries them out.
 */

/**
 * The engines the system has created, in creation order, and which of them leads. Only the leader
 * consumes the queue: the first real (non-preview) engine, preferring the one drawing the home
 * screen (Android 14+ can run separate home and lock engines). Main thread only.
 */
internal class EngineRoster<E : Any>(
    private val isPreview: (E) -> Boolean,
    private val drawsHomeScreen: (E) -> Boolean
) {
    private val attached = mutableListOf<E>()

    val engines: List<E> get() = attached.toList()

    var leader: E? = null
        private set

    /** Each returns whether the leader changed. */
    fun attach(engine: E): Boolean {
        attached += engine
        return elect()
    }

    fun detach(engine: E): Boolean {
        attached -= engine
        return elect()
    }

    /** Elect again, e.g. after an engine started or stopped drawing the home screen. */
    fun elect(): Boolean {
        val candidates = attached.filterNot(isPreview)
        val elected = candidates.firstOrNull(drawsHomeScreen) ?: candidates.firstOrNull()
        if (elected === leader) return false
        leader = elected
        return true
    }

    /** Every engine but the leader; they show what the leader recorded. */
    fun followers(): List<E> = attached.filterNot { it === leader }
}

/** Previews and followers only look; the leader takes what is pending (plan 1.7). */
internal fun liveSelectionFor(preview: Boolean, leader: Boolean, pending: LiveSelection): LiveSelection =
    if (preview || !leader) LiveSelection.PEEK else pending

/**
 * Whether an engine records [shownId] as the live wallpaper's current image (plan 1.5): only the
 * leader, only once the image is on screen, only an image it took from the queue, and only once.
 */
internal fun recordsShownImage(leader: Boolean, selection: LiveSelection, recordedId: String?, shownId: String): Boolean =
    leader && selection != LiveSelection.PEEK && recordedId != shownId

/**
 * Whether the leader runs the short-interval timer: intervals under WorkManager's 15 minutes run
 * on the visible engine while changing is on (plan 6.1 then decides each turn).
 */
internal fun runsLiveTimer(visible: Boolean, leader: Boolean, mode: WallpaperMode, settings: ScheduleSettings): Boolean =
    visible &&
        leader &&
        mode == WallpaperMode.LIVE &&
        settings.enableChanger &&
        settings.albumFor(ScreenType.LIVE) != null &&
        settings.scheduleType == ScheduleType.INTERVAL &&
        usesVisibleLiveTimer(settings.liveIntervalMinutes)

/**
 * Whether turning the screen off changes the live wallpaper. It is an automatic change, so it
 * waits while changing is paused or the battery settings hold changes back (plans 6.1 and 6.4);
 * [automaticChangeAllowed] says whether they do. Only the leader changes, once.
 */
internal fun changesOnScreenOff(settings: ScheduleSettings, leader: Boolean, automaticChangeAllowed: Boolean): Boolean =
    settings.liveEffects.enableChangeOnScreenOff && leader && automaticChangeAllowed

/** Whether a double-tap moves the live wallpaper on. It is a manual change, so it always may. */
internal fun changesOnDoubleTap(settings: ScheduleSettings, preview: Boolean, hasLeader: Boolean): Boolean =
    !preview && settings.liveEffects.enableDoubleTap && hasLeader

/** What an engine does about the album after a settings update. */
internal enum class LiveAlbumChange {
    /** Same album as before (or the first update, whose album the engine already started on). */
    NONE,

    /** A new album is in use: show its next image. */
    RELOAD,

    /**
     * Day turned to night or back while automatic changes may not happen: keep the image on
     * screen; the next change comes from the album now in use, as for a static screen.
     */
    HELD_BACK
}

internal data class LiveSettingsUpdate(
    /** The short-interval timer's configuration changed, so it starts counting again. */
    val restartTimer: Boolean,
    /** Live mode is on. Otherwise the engine shows nothing and the rest doesn't apply. */
    val live: Boolean = false,
    /** The scaling changed while the album stayed: reload the same image at the new scaling. */
    val reloadForScaling: Boolean = false,
    val album: LiveAlbumChange = LiveAlbumChange.NONE
)

/** Follows the settings updates one engine sees and says what each one asks of it. */
internal class LiveSettingsTracker {
    var settings = ScheduleSettings()
        private set
    var mode = WallpaperMode.STATIC
        private set
    private var observed = false
    private var albumId: String? = null
    private var scalingType: ScalingType? = null

    /** [automaticChangeAllowed] is asked only for a day/night switch. */
    fun update(
        newSettings: ScheduleSettings,
        newMode: WallpaperMode,
        automaticChangeAllowed: (ScheduleSettings) -> Boolean
    ): LiveSettingsUpdate {
        val restartTimer = mode != newMode ||
            settings.enableChanger != newSettings.enableChanger ||
            settings.albumFor(ScreenType.LIVE) != newSettings.albumFor(ScreenType.LIVE) ||
            settings.liveIntervalMinutes != newSettings.liveIntervalMinutes ||
            settings.scheduleType != newSettings.scheduleType
        // The same albums picked, but the other one now in use: day turned to night or back.
        val dayNightSwitch = settings.liveAlbumId == newSettings.liveAlbumId &&
            settings.liveNightAlbumId == newSettings.liveNightAlbumId
        settings = newSettings
        mode = newMode
        if (newMode != WallpaperMode.LIVE) return LiveSettingsUpdate(restartTimer)

        val newAlbumId = newSettings.albumFor(ScreenType.LIVE)
        val albumChanged = observed && newAlbumId != albumId
        observed = true
        albumId = newAlbumId

        val newScaling = newSettings.liveScalingType
        val scalingChanged = scalingType != null && scalingType != newScaling
        scalingType = newScaling

        val album = when {
            !albumChanged -> LiveAlbumChange.NONE
            dayNightSwitch && !automaticChangeAllowed(newSettings) -> LiveAlbumChange.HELD_BACK
            else -> LiveAlbumChange.RELOAD
        }
        return LiveSettingsUpdate(
            restartTimer = restartTimer,
            live = true,
            reloadForScaling = scalingChanged && !albumChanged && newAlbumId != null,
            album = album
        )
    }
}
