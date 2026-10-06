package com.anthonyla.paperize.service.wallpaper

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Runs wallpaper requests from the app, tile and shortcut; see [WallpaperChangeRequests]. */
@AndroidEntryPoint
class WallpaperChangeService : Service() {

    @Inject lateinit var handler: WallpaperRequestHandler
    @Inject lateinit var events: WallpaperChangeEvents

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Created on the first start, once Hilt has injected the service. */
    private val starts by lazy {
        ChangeServiceStarts(
            scope = serviceScope,
            handle = handler::handle,
            handOver = { request, report -> WallpaperRequestWorker.enqueue(this, request, report) },
            endReport = events::end,
            stop = ::stopSelf
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inForeground = try {
            startForeground(
                Constants.NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
            true
        } catch (e: IllegalStateException) {
            // Android can still refuse once started, e.g. when the data-sync time allowance is
            // used up. The request goes to a background job instead of crashing.
            Log.w(TAG, "Could not run in the foreground; handing over to a background job", e)
            false
        }
        val request = intent?.toWallpaperRequest()
        if (request == null) Log.w(TAG, "Unknown action: ${intent?.action}")
        starts.start(request, report = intent?.isReported() == true, startId = startId, inForeground = inForeground)
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent().setClassName(packageName, MainActivity::class.java.name),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.changing_wallpaper))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WallpaperChangeService"
        const val ACTION_CHANGE_WALLPAPER = Constants.ACTION_CHANGE_WALLPAPER
        const val ACTION_CHANGE_WALLPAPER_AUTO =
            "com.anthonyla.paperize.ACTION_CHANGE_WALLPAPER_AUTO"
        const val ACTION_APPLY_SPECIFIC_WALLPAPER = Constants.ACTION_APPLY_SPECIFIC_WALLPAPER
        const val ACTION_REAPPLY_EFFECTS = Constants.ACTION_REAPPLY_EFFECTS
    }
}

/**
 * What [WallpaperChangeService] does with each start, kept apart from Android so it can be tested.
 * Each start stops only itself ([stop] takes its start ID), so requests that overlap all finish.
 * A reported request is always ended exactly once, whichever way it goes, so the app never keeps
 * waiting for it: by the handler, by the background job it is handed to, or here.
 */
internal class ChangeServiceStarts(
    private val scope: CoroutineScope,
    private val handle: suspend (WallpaperRequest, Boolean) -> Unit,
    private val handOver: (WallpaperRequest, Boolean) -> Unit,
    private val endReport: () -> Unit,
    private val stop: (Int) -> Unit
) {
    /** [inForeground]: whether Android let the service run in the foreground for this start. */
    fun start(request: WallpaperRequest?, report: Boolean, startId: Int, inForeground: Boolean) {
        when {
            request == null -> {
                if (report) endReport()
                stop(startId)
            }
            !inForeground -> {
                handOver(request, report)
                stop(startId)
            }
            else -> scope.launch {
                try {
                    handle(request, report)
                } finally {
                    stop(startId)
                }
            }
        }
    }
}
