package com.anthonyla.paperize.service.widget

import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings

/** The screens the Shuffle widgets target, in the order the widget picker lists them. */
internal val SHUFFLE_TARGETS = listOf(ScreenType.HOME, ScreenType.LOCK, ScreenType.BOTH)

/** Why a tap on a Shuffle widget can't change anything; the widget says so instead. */
internal enum class ShuffleProblem {
    HOME_NOT_SET_UP,
    LOCK_NOT_SET_UP,
    NO_SCREEN_SET_UP,
    NO_LIVE_ALBUM,
    LIVE_NOT_SET
}

/**
 * What stops a [screen] widget from changing anything, judged from the settings alone; null when
 * it is ready. In live mode every widget changes the live wallpaper, so only the live album
 * matters. A turned-off screen keeps its album but doesn't count (see [ScheduleSettings.albumFor]).
 */
internal fun setUpProblem(screen: ScreenType, settings: ScheduleSettings, mode: WallpaperMode): ShuffleProblem? = when {
    mode == WallpaperMode.LIVE -> ShuffleProblem.NO_LIVE_ALBUM.takeIf { settings.liveAlbumId == null }
    screen == ScreenType.HOME -> ShuffleProblem.HOME_NOT_SET_UP.takeIf { settings.albumFor(ScreenType.HOME) == null }
    screen == ScreenType.LOCK -> ShuffleProblem.LOCK_NOT_SET_UP.takeIf { settings.albumFor(ScreenType.LOCK) == null }
    else -> ShuffleProblem.NO_SCREEN_SET_UP.takeIf { settings.rotatingStaticScreens().isEmpty() }
}

/** Widgets that can't change anything are drawn greyed out. */
internal fun isShuffleReady(screen: ScreenType, settings: ScheduleSettings, mode: WallpaperMode): Boolean =
    setUpProblem(screen, settings, mode) == null

/**
 * What stops a tap from changing anything. Beyond the settings, a live-mode change only shows
 * when Paperize is the live wallpaper; [liveWallpaperActive] is asked only then.
 */
internal fun shuffleProblem(
    screen: ScreenType,
    settings: ScheduleSettings,
    mode: WallpaperMode,
    liveWallpaperActive: () -> Boolean
): ShuffleProblem? = setUpProblem(screen, settings, mode)
    ?: ShuffleProblem.LIVE_NOT_SET.takeIf { mode == WallpaperMode.LIVE && !liveWallpaperActive() }

/**
 * Whether a tap changes only the lock screen, which can't be seen from the home screen where the
 * widget sits; the widget then confirms the tap with a short message.
 */
internal fun changesOnlyLockScreen(screen: ScreenType, settings: ScheduleSettings, mode: WallpaperMode): Boolean {
    if (mode == WallpaperMode.LIVE) return false
    val changed = if (screen == ScreenType.BOTH) settings.rotatingStaticScreens() else setOf(screen)
    return changed == setOf(ScreenType.LOCK)
}
