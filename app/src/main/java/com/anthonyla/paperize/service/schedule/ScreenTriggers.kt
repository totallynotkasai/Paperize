package com.anthonyla.paperize.service.schedule

import android.content.Context
import android.content.Intent
import android.util.Log
import com.anthonyla.paperize.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Starts and stops [ScreenEventService] as the settings call for it (plan 6.2). Android lets an app
 * start a foreground service only from the foreground and a few moments such as a restart, so a
 * start from the background may be refused; opening Paperize or restarting the phone tries again.
 */
@Singleton
class ScreenTriggers @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** While the app's process runs, follow the settings; every settings change happens there. */
    fun keepInStep() {
        scope.launch {
            combine(settingsRepository.getScheduleSettingsFlow(), settingsRepository.getWallpaperModeFlow()) { settings, mode ->
                settings.listensForScreenEvents(mode)
            }
                .distinctUntilChanged()
                .catch { e -> Log.e(TAG, "Could not follow the settings for screen events", e) }
                .collect { listen -> if (listen) start() else stop() }
        }
    }

    /** Start or stop the listener for the current settings, e.g. when Paperize comes to the foreground. */
    suspend fun sync() {
        val listen = settingsRepository.getScheduleSettings().listensForScreenEvents(settingsRepository.getWallpaperMode())
        if (listen) start() else stop()
    }

    private fun start() {
        if (ScreenEventService.running) return
        try {
            context.startForegroundService(Intent(context, ScreenEventService::class.java))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: Paperize is in the background.
            Log.w(TAG, "Android won't start the screen-event listener from the background now: ${e.message}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not start the screen-event listener", e)
        }
    }

    private fun stop() {
        context.stopService(Intent(context, ScreenEventService::class.java))
    }

    private companion object {
        const val TAG = "ScreenTriggers"
    }
}
