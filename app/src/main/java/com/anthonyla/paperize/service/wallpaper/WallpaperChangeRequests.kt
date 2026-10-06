package com.anthonyla.paperize.service.wallpaper

import android.content.Context
import android.util.Log
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Kind
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Outcome
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * The one way to ask for a wallpaper change: from the app, the Quick Settings tile, the launcher
 * shortcut or the widgets. Android 12+ refuses to start a foreground service from the background,
 * so a refused start runs the same request as a background job instead of crashing or being lost.
 */
class WallpaperChangeRequests @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val events: WallpaperChangeEvents
) {
    /**
     * [keepSchedule] leaves the automatic countdown alone instead of restarting it. [report]
     * shows the outcome on the visible screen (see [WallpaperChangeEvents]).
     */
    fun change(screen: ScreenType, keepSchedule: Boolean = false, report: Boolean = false) =
        send(WallpaperRequest.Change(screen, keepSchedule), report)

    /**
     * Tile, shortcut and widgets: [screen] (by default every turned-on static screen), or the live
     * wallpaper in live mode.
     */
    fun changeConfigured(screen: ScreenType = ScreenType.BOTH) =
        send(WallpaperRequest.Change(screen, followMode = true), report = false)

    /** Put a chosen image on [screen]; the outcome is shown on the visible screen. */
    fun applySpecific(wallpaperId: String, screen: ScreenType) =
        send(WallpaperRequest.ApplySpecific(wallpaperId, screen), report = true)

    /** Re-render the current image of [screen] with the latest effects. */
    fun reapplyEffects(screen: ScreenType) = send(WallpaperRequest.Reapply(screen), report = false)

    private fun send(request: WallpaperRequest, report: Boolean) {
        if (report) events.begin()
        try {
            context.startForegroundService(request.toIntent(context, report))
            return
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the app is in the background.
            Log.w(TAG, "Android refused the wallpaper service; running $request as a background job", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not start the wallpaper service; running $request as a background job", e)
        }
        try {
            WallpaperRequestWorker.enqueue(context, request, report)
        } catch (e: Exception) {
            Log.e(TAG, "Could not run $request", e)
            if (report) {
                val kind = if (request is WallpaperRequest.ApplySpecific) Kind.SET_CHOSEN else Kind.CHANGE
                events.publish(WallpaperChangeResult(kind, Outcome.FAILED, e.localizedMessage))
                events.end()
            }
        }
    }

    /** For entry points Hilt can't inject, such as the shortcut's plain Activity. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Provider {
        fun wallpaperChangeRequests(): WallpaperChangeRequests
    }

    companion object {
        private const val TAG = "WallpaperChangeRequests"

        fun from(context: Context): WallpaperChangeRequests =
            EntryPointAccessors.fromApplication(context.applicationContext, Provider::class.java)
                .wallpaperChangeRequests()
    }
}
