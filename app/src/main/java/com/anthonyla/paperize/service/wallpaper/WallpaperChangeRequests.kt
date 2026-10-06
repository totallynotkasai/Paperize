package com.anthonyla.paperize.service.wallpaper

import android.content.Context
import android.content.Intent
import android.util.Log
import com.anthonyla.paperize.core.ScreenType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Asks [WallpaperChangeService] to change or re-render wallpapers on the app's behalf. */
class WallpaperChangeRequests @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** [keepSchedule] leaves the automatic countdown alone instead of restarting it. */
    fun change(screen: ScreenType, keepSchedule: Boolean = false) = start(
        Intent(context, WallpaperChangeService::class.java)
            .setAction(WallpaperChangeService.ACTION_CHANGE_WALLPAPER)
            .putExtra(WallpaperChangeService.EXTRA_SCREEN_TYPE, screen.name)
            .putExtra(WallpaperChangeService.EXTRA_KEEP_SCHEDULE, keepSchedule)
    )

    /** Re-render the current image of [screen] with the latest effects. */
    fun reapplyEffects(screen: ScreenType) = start(
        Intent(context, WallpaperChangeService::class.java)
            .setAction(WallpaperChangeService.ACTION_REAPPLY_EFFECTS)
            .putExtra(WallpaperChangeService.EXTRA_SCREEN_TYPE, screen.name)
    )

    private fun start(intent: Intent) {
        try {
            context.startForegroundService(intent)
        } catch (e: IllegalStateException) {
            // Android refuses foreground services once the app is in the background.
            Log.w(TAG, "Could not start the wallpaper service", e)
        }
    }

    private companion object {
        const val TAG = "WallpaperChangeRequests"
    }
}
