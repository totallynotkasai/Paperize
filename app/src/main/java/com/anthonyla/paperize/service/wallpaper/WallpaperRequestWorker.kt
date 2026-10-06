package com.anthonyla.paperize.service.wallpaper

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
    }
}
