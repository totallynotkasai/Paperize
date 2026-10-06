package com.anthonyla.paperize.service.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Asks for a folder rescan whenever Paperize enters the foreground. The worker skips it when the
 * albums were refreshed within the last few hours.
 *
 * Unique work with [ExistingWorkPolicy.KEEP] coalesces rapid foreground transitions and avoids
 * running a second scan while an existing one is still active.
 */
object AlbumRefreshScheduler {
    internal const val UNIQUE_WORK_NAME = "album_refresh_on_foreground"
    internal const val WORK_TAG = "foreground_album_refresh"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<AlbumRefreshWorker>()
            .setInputData(workDataOf(AlbumRefreshWorker.KEY_FROM_FOREGROUND to true))
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
