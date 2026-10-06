package com.anthonyla.paperize.service.schedule

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.ScheduleSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Whether anything depends on the dark theme while Paperize isn't running: static adaptive
 * brightness (plan 6.3; the live wallpaper redraws by itself) or night albums that follow the
 * dark theme (plan 6.4).
 */
internal fun watchesDarkTheme(settings: ScheduleSettings, mode: WallpaperMode): Boolean =
    (mode == WallpaperMode.STATIC && settings.adaptiveBrightness && settings.rotatingStaticScreens().isNotEmpty()) ||
        (settings.nightTrigger == NightTrigger.DARK_MODE && settings.screensWithNightAlbum(mode).isNotEmpty())

/**
 * Notices dark-theme switches while Paperize isn't running (plan 6.3). Switching the theme by hand
 * writes a system setting, which [DarkThemeJobService] watches, so that is noticed within seconds.
 * A schedule (sunset, set hours, bedtime) switches the theme without writing anything, so a light
 * check every 15 minutes catches those. While Paperize runs, the application hears of every switch
 * at once.
 */
@Singleton
class DarkThemeChecks @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun sync(settings: ScheduleSettings, mode: WallpaperMode) {
        if (!watchesDarkTheme(settings, mode)) {
            cancel()
            return
        }
        DarkThemeJobService.watch(context, replace = false)
        workManager.enqueueUniquePeriodicWork(
            Constants.WORK_NAME_DARK_MODE_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DarkThemeCheckWorker>(Constants.DARK_MODE_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .addTag(Constants.WORK_TAG_SCHEDULE_EVENTS)
                .build()
        )
    }

    fun cancel() {
        DarkThemeJobService.stopWatching(context)
        workManager.cancelUniqueWork(Constants.WORK_NAME_DARK_MODE_CHECK)
    }

    /** Whether the theme settings are being watched (for tests). */
    internal fun isWatchingSettings(): Boolean = DarkThemeJobService.isWatching(context)
}

/**
 * Runs when a dark-theme setting changes. A plain JobScheduler job rather than WorkManager work:
 * when the job starts Paperize from cold, WorkManager's start-up clean-up schedules its own jobs
 * again, which stops a content-triggered one that is already running (seen on HyperOS), and the
 * switch would wait for the 15-minute check.
 */
@AndroidEntryPoint
class DarkThemeJobService : JobService() {

    @Inject lateinit var scheduleEvents: ScheduleEvents

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val keepWatching = try {
                scheduleEvents.onDarkThemeMaybeChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not check the dark theme", e)
                true
            }
            // A content trigger fires once, so watch again; scheduling this job's ID ends this run.
            if (keepWatching) watch(applicationContext, replace = true)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DarkThemeJobService"
        /** Above WorkManager's job IDs, which PaperizeApplication keeps at or below [Constants.MAX_WORK_MANAGER_JOB_ID]. */
        internal const val JOB_ID = Constants.MAX_WORK_MANAGER_JOB_ID + 3

        /** Secure settings that change when the dark theme is switched by hand. */
        internal val DARK_THEME_SETTINGS = listOf(
            "ui_night_mode",
            "ui_night_mode_override_on",
            "ui_night_mode_override_off"
        )

        /** HyperOS and MIUI also keep their own switch, in the system settings. */
        internal const val XIAOMI_DARK_MODE_SETTING = "dark_mode_enable"

        private fun jobScheduler(context: Context) = context.getSystemService(JobScheduler::class.java)

        internal fun isWatching(context: Context): Boolean = jobScheduler(context)?.getPendingJob(JOB_ID) != null

        /** Watch the theme settings; without [replace], a job already waiting is kept. */
        internal fun watch(context: Context, replace: Boolean) {
            val scheduler = jobScheduler(context) ?: return
            if (!replace && scheduler.getPendingJob(JOB_ID) != null) return
            val uris = DARK_THEME_SETTINGS.map { Settings.Secure.getUriFor(it) } +
                Settings.System.getUriFor(XIAOMI_DARK_MODE_SETTING)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, DarkThemeJobService::class.java))
                .apply { uris.forEach { addTriggerContentUri(JobInfo.TriggerContentUri(it, 0)) } }
                .setTriggerContentUpdateDelay(TimeUnit.SECONDS.toMillis(1))
                .setTriggerContentMaxDelay(TimeUnit.SECONDS.toMillis(5))
                .build()
            scheduler.schedule(job)
            Log.d(TAG, "Watching the dark-theme settings")
        }

        internal fun stopWatching(context: Context) {
            jobScheduler(context)?.cancel(JOB_ID)
        }
    }
}

/** The 15-minute check for dark-theme switches made by a schedule. */
@HiltWorker
class DarkThemeCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduleEvents: ScheduleEvents
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val keepWatching = try {
            scheduleEvents.onDarkThemeMaybeChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not check the dark theme", e)
            true
        }
        // Nothing depends on the theme any more (the settings changed while this ran).
        if (!keepWatching) WorkManager.getInstance(applicationContext).cancelUniqueWork(Constants.WORK_NAME_DARK_MODE_CHECK)
        return Result.success()
    }

    private companion object {
        const val TAG = "DarkThemeCheckWorker"
    }
}
