package com.anthonyla.paperize

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration as WorkConfiguration
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.core.util.DataResetManager
import com.anthonyla.paperize.service.schedule.ScheduleEvents
import com.anthonyla.paperize.service.schedule.ScreenTriggers
import com.anthonyla.paperize.service.widget.ShuffleWidgets
import com.anthonyla.paperize.service.worker.AlbumRefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PaperizeApplication : Application(), WorkConfiguration.Provider, DefaultLifecycleObserver {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var shuffleWidgets: ShuffleWidgets

    @Inject
    lateinit var screenTriggers: ScreenTriggers

    @Inject
    lateinit var scheduleEvents: ScheduleEvents

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The system dark theme last seen, to notice when it switches while Paperize runs. */
    private var darkTheme: Boolean? = null

    override fun onCreate() {
        super<Application>.onCreate()

        // Perform one-time data reset for major version upgrades (e.g., v3 -> v4)
        // Must run before any other initialization that accesses DB/preferences
        DataResetManager.performResetIfNeeded(this)

        createNotificationChannels()

        // Placed widgets grey out while their screen can't change, and back again once it can.
        shuffleWidgets.keepInStep()

        // The screen-off / unlock listener runs while those changes are turned on (plan 6.2).
        screenTriggers.keepInStep()

        // Android drops alarms when an app is stopped or updated, so every start re-arms set times
        // and catches up one that was missed (plan 6.4), and a dark-theme switch Paperize missed while
        // it was stopped (plan 6.3).
        appScope.launch {
            runCatchingLogged("re-arm set times") { scheduleEvents.onTimeEvent() }
            runCatchingLogged("follow the dark theme") { scheduleEvents.onDarkThemeMaybeChanged() }
        }

        darkTheme = isDarkTheme(resources.configuration)

        // Process lifecycle distinguishes real background/foreground transitions from activity
        // recreation, so folder-backed albums are refreshed whenever the user returns to the app.
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setWorkerFactory(workerFactory)
            // Leaves room above for DarkThemeJobService's own job.
            .setJobSchedulerJobIdRange(0, Constants.MAX_WORK_MANAGER_JOB_ID)
            .build()

    /**
     * Three channels: the brief "changing wallpaper" notice stays silent, problems make a sound by
     * default, and the screen-off / unlock listener's required notice asks for minimum importance.
     * Android raises a foreground service's channel to low importance (silent, but its icon may show
     * in the status bar), so the app offers to turn that channel off. Android keeps an existing
     * channel's importance, so each needs its own.
     */
    private fun createNotificationChannels() {
        val activity = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_activity),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        val alerts = NotificationChannel(
            Constants.ALERT_CHANNEL_ID,
            getString(R.string.notification_channel_alerts),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_alerts_description)
        }
        val listener = NotificationChannel(
            Constants.LISTENER_CHANNEL_ID,
            getString(R.string.notification_channel_listener),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.notification_channel_listener_description)
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(activity, alerts, listener))
    }

    override fun onStart(owner: LifecycleOwner) {
        AlbumRefreshScheduler.enqueue(this)
        // In the foreground Android allows starting the listener, e.g. after it was refused before.
        appScope.launch { runCatchingLogged("start the screen-event listener") { screenTriggers.sync() } }
    }

    /** While Paperize runs it hears of a dark-theme switch at once (plan 6.3). */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val dark = isDarkTheme(newConfig)
        if (dark == darkTheme) return
        darkTheme = dark
        appScope.launch { runCatchingLogged("follow the dark theme") { scheduleEvents.onDarkThemeMaybeChanged() } }
    }

    private fun isDarkTheme(config: Configuration): Boolean =
        config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private suspend fun runCatchingLogged(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Could not $what", e)
        }
    }

    private companion object {
        const val TAG = "PaperizeApplication"
    }
}
