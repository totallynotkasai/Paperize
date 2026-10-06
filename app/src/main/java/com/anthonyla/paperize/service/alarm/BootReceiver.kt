package com.anthonyla.paperize.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.service.schedule.ScheduleEvents
import com.anthonyla.paperize.service.schedule.ScreenTriggers
import com.anthonyla.paperize.service.worker.WallpaperScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Puts schedules back after a restart or an app update (which clear alarms and stop services), and
 * re-arms set times when the clock or time zone changes.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var wallpaperScheduler: WallpaperScheduler

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var scheduleEvents: ScheduleEvents

    @Inject
    lateinit var screenTriggers: ScreenTriggers

    companion object {
        private const val TAG = "BootReceiver"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val restarted = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, ACTION_QUICKBOOT_POWERON, Intent.ACTION_MY_PACKAGE_REPLACED -> true
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> false
            else -> return
        }
        Log.d(TAG, "${intent.action}: bringing schedules up to date")

        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (restarted) {
                    wallpaperScheduler.updateSchedules(
                        settingsRepository.getScheduleSettings(),
                        settingsRepository.getWallpaperMode(),
                        onlyIfNotScheduled = true
                    )
                    // Android allows starting the screen-off / unlock listener at these moments.
                    screenTriggers.sync()
                }
                // Catches up a set time or day/night switch missed while off, and arms the next one.
                scheduleEvents.onTimeEvent()
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling wallpaper changes", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
