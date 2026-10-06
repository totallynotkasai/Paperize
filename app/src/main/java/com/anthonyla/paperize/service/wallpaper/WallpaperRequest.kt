package com.anthonyla.paperize.service.wallpaper

import android.content.Context
import android.content.Intent
import androidx.work.Data
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants

/**
 * A wallpaper action for [WallpaperChangeService], or for [WallpaperRequestWorker] when Android
 * won't start the service.
 */
sealed interface WallpaperRequest {
    /**
     * Move [screen] on to its next image. [keepSchedule] leaves the automatic countdown alone.
     * [followMode] (tile and shortcut) changes the live wallpaper instead while in live mode.
     * [automatic] (set times, day/night switch, screen off, unlock) happens only while changing is
     * on and the battery conditions allow it (plan 6.1); manual changes always happen.
     */
    data class Change(
        val screen: ScreenType,
        val keepSchedule: Boolean = false,
        val followMode: Boolean = false,
        val automatic: Boolean = false
    ) : WallpaperRequest

    /** Put the chosen image on [screen] (static mode only). */
    data class ApplySpecific(val wallpaperId: String, val screen: ScreenType) : WallpaperRequest

    /** Re-render the current image of [screen] with the latest effects. */
    data class Reapply(val screen: ScreenType) : WallpaperRequest
}

private const val KEY_ACTION = "action"
private const val KEY_SCREEN = Constants.EXTRA_SCREEN_TYPE
private const val KEY_WALLPAPER_ID = Constants.EXTRA_WALLPAPER_ID
private const val KEY_KEEP_SCHEDULE = "com.anthonyla.paperize.EXTRA_KEEP_SCHEDULE"
private const val KEY_REPORT = "com.anthonyla.paperize.EXTRA_REPORT"
private const val KEY_AUTOMATIC = "com.anthonyla.paperize.EXTRA_AUTOMATIC"

private fun WallpaperRequest.action(): String = when (this) {
    is WallpaperRequest.Change ->
        if (followMode) WallpaperChangeService.ACTION_CHANGE_WALLPAPER_AUTO else WallpaperChangeService.ACTION_CHANGE_WALLPAPER
    is WallpaperRequest.ApplySpecific -> WallpaperChangeService.ACTION_APPLY_SPECIFIC_WALLPAPER
    is WallpaperRequest.Reapply -> WallpaperChangeService.ACTION_REAPPLY_EFFECTS
}

private fun WallpaperRequest.screen(): ScreenType = when (this) {
    is WallpaperRequest.Change -> screen
    is WallpaperRequest.ApplySpecific -> screen
    is WallpaperRequest.Reapply -> screen
}

/** [report] asks for the outcome to be shown in the app (see [WallpaperChangeEvents]). */
internal fun WallpaperRequest.toIntent(context: Context, report: Boolean): Intent =
    Intent(context, WallpaperChangeService::class.java)
        .setAction(action())
        .putExtra(KEY_SCREEN, screen().name)
        .putExtra(KEY_KEEP_SCHEDULE, (this as? WallpaperRequest.Change)?.keepSchedule == true)
        .putExtra(KEY_AUTOMATIC, (this as? WallpaperRequest.Change)?.automatic == true)
        .putExtra(KEY_WALLPAPER_ID, (this as? WallpaperRequest.ApplySpecific)?.wallpaperId)
        .putExtra(KEY_REPORT, report)

internal fun Intent.isReported(): Boolean = getBooleanExtra(KEY_REPORT, false)

internal fun Intent.toWallpaperRequest(): WallpaperRequest? = wallpaperRequest(
    action = action,
    screenName = getStringExtra(KEY_SCREEN),
    keepSchedule = getBooleanExtra(KEY_KEEP_SCHEDULE, false),
    automatic = getBooleanExtra(KEY_AUTOMATIC, false),
    wallpaperId = getStringExtra(KEY_WALLPAPER_ID)
)

internal fun WallpaperRequest.toData(report: Boolean): Data = Data.Builder()
    .putString(KEY_ACTION, action())
    .putString(KEY_SCREEN, screen().name)
    .putBoolean(KEY_KEEP_SCHEDULE, (this as? WallpaperRequest.Change)?.keepSchedule == true)
    .putBoolean(KEY_AUTOMATIC, (this as? WallpaperRequest.Change)?.automatic == true)
    .putString(KEY_WALLPAPER_ID, (this as? WallpaperRequest.ApplySpecific)?.wallpaperId)
    .putBoolean(KEY_REPORT, report)
    .build()

internal fun Data.isReported(): Boolean = getBoolean(KEY_REPORT, false)

internal fun Data.toWallpaperRequest(): WallpaperRequest? = wallpaperRequest(
    action = getString(KEY_ACTION),
    screenName = getString(KEY_SCREEN),
    keepSchedule = getBoolean(KEY_KEEP_SCHEDULE, false),
    automatic = getBoolean(KEY_AUTOMATIC, false),
    wallpaperId = getString(KEY_WALLPAPER_ID)
)

/** Requests without a screen change both static screens, as the tile and shortcut always have. */
internal fun wallpaperRequest(
    action: String?,
    screenName: String?,
    keepSchedule: Boolean,
    wallpaperId: String?,
    automatic: Boolean = false
): WallpaperRequest? {
    val screen = screenName?.let(ScreenType::fromString) ?: ScreenType.BOTH
    return when (action) {
        WallpaperChangeService.ACTION_CHANGE_WALLPAPER -> WallpaperRequest.Change(screen, keepSchedule, automatic = automatic)
        WallpaperChangeService.ACTION_CHANGE_WALLPAPER_AUTO -> WallpaperRequest.Change(screen, followMode = true)
        WallpaperChangeService.ACTION_APPLY_SPECIFIC_WALLPAPER -> WallpaperRequest.ApplySpecific(wallpaperId.orEmpty(), screen)
        WallpaperChangeService.ACTION_REAPPLY_EFFECTS -> WallpaperRequest.Reapply(screen)
        else -> null
    }
}
