package com.anthonyla.paperize.service.wallpaper

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs a [WallpaperRequest] when Android won't start [WallpaperChangeService], typically because
 * the request came while Paperize was in the background. Expedited, so it normally runs at once.
 */
@HiltWorker
class WallpaperRequestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val handler: WallpaperRequestHandler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val request = inputData.toWallpaperRequest() ?: return Result.failure()
        handler.handle(request, inputData.isReported())
        // A manual request that failed is reported, not retried later out of the blue.
        return Result.success()
    }

    companion object {
        private const val TAG = "WallpaperRequestWorker"
        const val WORK_TAG = "wallpaper_request"

        fun enqueue(context: Context, request: WallpaperRequest, report: Boolean) {
            Log.d(TAG, "Running $request as a background job")
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<WallpaperRequestWorker>()
                    .setInputData(request.toData(report))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(WORK_TAG)
                    .build()
            )
        }

        /**
         * An automatic change from a set time or the day/night switch (plan 6.4). With "Only while
         * charging" it waits for the charger, like the interval jobs do; otherwise it runs at once.
         * While one for the same screens is still waiting, another adds nothing, so times missed
         * while unplugged come to one change once the phone is plugged in.
         */
        fun enqueueAutomatic(context: Context, request: WallpaperRequest.Change, requiresCharging: Boolean) {
            Log.d(TAG, "Queueing automatic $request (waits for charger: $requiresCharging)")
            val builder = OneTimeWorkRequestBuilder<WallpaperRequestWorker>()
                .setInputData(request.toData(report = false))
                .addTag(WORK_TAG)
            // Expedited work can't wait for a charger.
            if (requiresCharging) {
                builder.setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            } else {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${Constants.WORK_NAME_TIMED_CHANGE}_${request.screen.name.lowercase()}",
                ExistingWorkPolicy.KEEP,
                builder.build()
            )
        }

        /**
         * Redraw [screen]'s current image after the dark theme switched (plan 6.3). Several checks
         * may notice the same switch; while one redraw waits, the others add nothing.
         */
        fun enqueueRedraw(context: Context, screen: ScreenType) {
            Log.d(TAG, "Queueing a theme redraw of $screen")
            WorkManager.getInstance(context).enqueueUniqueWork(
                "theme_redraw_${screen.name.lowercase()}",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WallpaperRequestWorker>()
                    .setInputData(WallpaperRequest.Reapply(screen).toData(report = false))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(WORK_TAG)
                    .build()
            )
        }
    }
}
