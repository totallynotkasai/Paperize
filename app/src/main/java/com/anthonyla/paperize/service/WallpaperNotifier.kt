package com.anthonyla.paperize.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Problem notifications shared by manual changes and scheduled ones. */
@Singleton
class WallpaperNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val notificationManager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    fun showEmptyAlbum() = show(
        context.getString(R.string.no_wallpapers_in_album),
        context.getString(R.string.wallpaper_changer_disabled_empty_album)
    )

    /** The wallpaper could not be changed; [message] explains why when known. */
    fun showChangeFailed(message: String?) = show(
        context.getString(R.string.app_name),
        message ?: context.getString(R.string.error_no_valid_wallpaper_after_retries)
    )

    private fun show(title: String, message: String) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent().setClassName(context.packageName, MainActivity::class.java.name),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager?.notify(ERROR_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val ERROR_NOTIFICATION_ID = Constants.NOTIFICATION_ID + 1
    }
}
