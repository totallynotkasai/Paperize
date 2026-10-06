package com.anthonyla.paperize.service.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.service.WallpaperChangeLock
import com.anthonyla.paperize.service.WallpaperNotifier
import com.anthonyla.paperize.service.schedule.ChangeConditions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.anthonyla.paperize.service.wallpaper.WallpaperController
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

@HiltWorker
class WallpaperChangeWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val wallpaperController: WallpaperController,
    private val settingsRepository: SettingsRepository,
    private val wallpaperChangeLock: WallpaperChangeLock,
    private val wallpaperScheduler: WallpaperScheduler,
    private val notifier: WallpaperNotifier,
    private val conditions: ChangeConditions
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val screenType = inputData.getString(Constants.EXTRA_SCREEN_TYPE)
            ?.let(ScreenType::fromString)
            ?: ScreenType.HOME
        return try {
            Log.d(TAG, "Starting wallpaper change for $screenType")
            val outcome = wallpaperChangeLock.mutex.withLock {
                val settings = settingsRepository.getScheduleSettings()
                val block = conditions.automaticChangeBlock(settings)
                when {
                    screenType !in scheduledScreens(settings, settingsRepository.getWallpaperMode()) -> null
                    // Battery saver is checked here; "Only while charging" is also a job constraint (plan 6.1).
                    block != null -> {
                        Log.d(TAG, "Skipping this change for $screenType: $block")
                        WallpaperChangeOutcome()
                    }
                    else -> wallpaperController.change(screenType, settings)
                }
            }
            when {
                outcome == null -> {
                    // Settings no longer schedule this screen, e.g. after an empty album turned
                    // changing off. Running on would do nothing at every interval.
                    Log.d(TAG, "$screenType is no longer scheduled; cancelling its job")
                    cancelUnscheduledWork()
                }
                outcome.emptyAlbum -> {
                    notifier.showEmptyAlbum()
                    cancelUnscheduledWork()
                }
                outcome.changed -> Log.d(TAG, "Wallpaper change completed successfully for $screenType")
                else -> Unit
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error changing wallpaper", e)
            if (runAttemptCount < Constants.MAX_WORK_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                // Same notice as a manual change, once the retries are used up.
                notifier.showChangeFailed(e.localizedMessage)
                Result.failure()
            }
        }
    }

    /** Cancel jobs the current settings no longer call for, including possibly this one. */
    private suspend fun cancelUnscheduledWork() {
        wallpaperScheduler.updateSchedules(
            settingsRepository.getScheduleSettings(),
            settingsRepository.getWallpaperMode(),
            onlyIfNotScheduled = true
        )
    }

    private companion object {
        const val TAG = "WallpaperChangeWorker"
    }
}
