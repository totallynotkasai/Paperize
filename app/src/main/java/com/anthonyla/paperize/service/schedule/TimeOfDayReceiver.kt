package com.anthonyla.paperize.service.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The alarm for a set time or clock day/night switch (see [TimeOfDayAlarms]). */
@AndroidEntryPoint
class TimeOfDayReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduleEvents: ScheduleEvents

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimeOfDayAlarms.ACTION_TIME_OF_DAY) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Only reads settings and queues jobs, so it finishes well within a receiver's time.
                scheduleEvents.onTimeEvent()
            } catch (e: Exception) {
                Log.e(TAG, "Could not handle the set time or day/night switch", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "TimeOfDayReceiver"
    }
}
