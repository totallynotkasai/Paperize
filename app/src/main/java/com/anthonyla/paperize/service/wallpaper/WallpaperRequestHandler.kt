package com.anthonyla.paperize.service.wallpaper

import android.content.Context
import android.util.Log
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.util.isPaperizeLiveWallpaperActive
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import com.anthonyla.paperize.service.WallpaperChangeLock
import com.anthonyla.paperize.service.WallpaperNotifier
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Kind
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Outcome
import com.anthonyla.paperize.service.worker.WallpaperScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Carries out a [WallpaperRequest] for the foreground service or its fallback background job, so
 * both behave the same. Problems become notifications unless a visible screen asked to report them.
 */
class WallpaperRequestHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wallpaperController: WallpaperController,
    private val settingsRepository: SettingsRepository,
    private val wallpaperChangeLock: WallpaperChangeLock,
    private val wallpaperScheduler: WallpaperScheduler,
    private val wallpaperRepository: WallpaperRepository,
    private val notifier: WallpaperNotifier,
    private val events: WallpaperChangeEvents
) {
    /** [report] requests were counted by [WallpaperChangeEvents.begin]; this ends them. */
    suspend fun handle(request: WallpaperRequest, report: Boolean) {
        try {
            val result = try {
                wallpaperChangeLock.mutex.withLock { perform(request) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not carry out $request", e)
                WallpaperChangeResult(request.kind(), Outcome.FAILED, e.localizedMessage)
            }
            deliver(result, report)
        } finally {
            if (report) events.end()
        }
    }

    private suspend fun perform(request: WallpaperRequest): WallpaperChangeResult = when (request) {
        is WallpaperRequest.Change -> {
            val mode = settingsRepository.getWallpaperMode()
            val screen = if (request.followMode && mode == WallpaperMode.LIVE) ScreenType.LIVE else request.screen
            val settings = settingsRepository.getScheduleSettings()
            val outcome = wallpaperController.change(screen, settings)
            if (outcome.changed && !request.keepSchedule) {
                wallpaperScheduler.resetAfterManualChange(screen, settings, mode)
            }
            result(Kind.CHANGE, outcome, screen)
        }
        is WallpaperRequest.ApplySpecific -> {
            require(request.wallpaperId.isNotBlank()) { context.getString(R.string.wallpaper_not_found) }
            require(request.screen != ScreenType.LIVE) { context.getString(R.string.static_mode_required) }
            val mode = settingsRepository.getWallpaperMode()
            require(mode == WallpaperMode.STATIC) { context.getString(R.string.static_mode_required) }
            val wallpaper = wallpaperRepository.getWallpaperById(request.wallpaperId)
                ?: error(context.getString(R.string.wallpaper_not_found))
            val settings = settingsRepository.getScheduleSettings()
            wallpaperController.applySpecific(wallpaper.albumId, wallpaper.id, request.screen, settings)
            wallpaperScheduler.resetAfterManualChange(request.screen, settings, mode)
            WallpaperChangeResult(Kind.SET_CHOSEN, Outcome.CHANGED)
        }
        is WallpaperRequest.Reapply -> {
            val outcome = wallpaperController.reapply(request.screen, settingsRepository.getScheduleSettings())
            result(Kind.CHANGE, outcome, request.screen)
        }
    }

    private fun result(kind: Kind, outcome: WallpaperChangeOutcome, screen: ScreenType) = WallpaperChangeResult(
        kind,
        when {
            outcome.emptyAlbum -> Outcome.EMPTY_ALBUM
            !outcome.changed -> Outcome.NOTHING_TO_CHANGE
            // The live engine changes on its own; nothing shows unless it is the live wallpaper.
            screen == ScreenType.LIVE && !isPaperizeLiveWallpaperActive(context) -> Outcome.LIVE_NOT_SET
            else -> Outcome.CHANGED
        }
    )

    private fun deliver(result: WallpaperChangeResult, report: Boolean) {
        if (report && events.publish(result)) return
        when (result.outcome) {
            Outcome.EMPTY_ALBUM -> notifier.showEmptyAlbum()
            Outcome.FAILED -> notifier.showChangeFailed(result.message)
            else -> Unit
        }
    }

    private fun WallpaperRequest.kind() = if (this is WallpaperRequest.ApplySpecific) Kind.SET_CHOSEN else Kind.CHANGE

    private companion object {
        const val TAG = "WallpaperRequestHandler"
    }
}
