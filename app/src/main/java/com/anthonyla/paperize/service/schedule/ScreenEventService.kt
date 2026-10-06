package com.anthonyla.paperize.service.schedule

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.presentation.MainActivity
import com.anthonyla.paperize.service.wallpaper.WallpaperRequest
import com.anthonyla.paperize.service.wallpaper.WallpaperRequestHandler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class ScreenEvent { SCREEN_OFF, UNLOCK }

/**
 * The static screens a screen-off or unlock should change now: those the event's target names
 * (see [ScheduleSettings.triggerScreens]), leaving out any that changed less than the minimum gap
 * ago, by any means. Whether changing is on and the battery allows it is checked when it runs.
 */
internal fun screensForEvent(
    event: ScreenEvent,
    settings: ScheduleSettings,
    mode: WallpaperMode,
    now: Long,
    lastChangedAt: (ScreenType) -> Long
): Set<ScreenType> {
    if (!settings.listensForScreenEvents(mode)) return emptySet()
    val target = when (event) {
        ScreenEvent.SCREEN_OFF -> settings.screenOffTarget.takeIf { settings.changeOnScreenOff }
        ScreenEvent.UNLOCK -> settings.unlockTarget.takeIf { settings.changeOnUnlock }
    } ?: return emptySet()
    val gapMillis = settings.triggerGapMinutes * 60_000L
    return settings.triggerScreens(target).filterTo(mutableSetOf()) { screen ->
        val last = lastChangedAt(screen)
        // A change "in the future" means the clock went back; don't let it block changes for hours.
        now - last >= gapMillis || last > now
    }
}

/**
 * Static mode's screen-off and unlock changes (plan 6.2). Android tells only running apps about
 * these events, so this foreground service listens while either is turned on. Android requires a
 * notification. Its channel asks for minimum importance, but Android raises a foreground service's
 * channel to low (silent, with a status-bar icon), so the app offers a one-tap way to turn the
 * channel off; the service keeps running without it (decision D).
 */
@AndroidEntryPoint
class ScreenEventService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var handler: WallpaperRequestHandler
    @Inject lateinit var scheduleState: ScheduleState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** One event at a time, so a screen-off change is recorded before an unlock right after it checks the gap. */
    private val eventMutex = Mutex()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenEvent(ScreenEvent.SCREEN_OFF)
                Intent.ACTION_USER_PRESENT -> onScreenEvent(ScreenEvent.UNLOCK)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Both are protected broadcasts that only the system can send, so exporting is safe, and it is
        // needed: SystemUI sends USER_PRESENT under an ordinary app UID, which Android doesn't deliver
        // to a not-exported receiver (found on HyperOS; SCREEN_OFF comes from the system server).
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(Constants.LISTENER_NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Constants.LISTENER_NOTIFICATION_ID, notification())
            }
        } catch (e: Exception) {
            // e.g. a restart Android allowed but not into the foreground. Opening Paperize or
            // restarting the phone starts the listener again.
            Log.w(TAG, "Could not listen in the foreground", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        running = true
        Log.d(TAG, "Listening for screen off and unlock")
        return START_STICKY
    }

    private fun onScreenEvent(event: ScreenEvent) {
        // With the screen off the CPU may sleep at any moment; keep it awake until the change is done.
        val wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { acquire(Constants.TRIGGER_WAKE_LOCK_TIMEOUT_MS) }
        scope.launch {
            try {
                eventMutex.withLock { handle(event) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not handle $event", e)
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
        }
    }

    private suspend fun handle(event: ScreenEvent) {
        val settings = settingsRepository.getScheduleSettings()
        val mode = settingsRepository.getWallpaperMode()
        val screens = screensForEvent(event, settings, mode, System.currentTimeMillis(), scheduleState::lastChangedAt)
        val screen = requestScreenFor(screens) ?: return
        Log.d(TAG, "$event: changing $screen")
        // An automatic change: skipped while paused or held back by the battery settings (plan 6.1).
        // Like double-tap on the live wallpaper (plan 2.11), it restarts the interval countdown.
        handler.handle(WallpaperRequest.Change(screen, automatic = true), report = false)
    }

    private fun notification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent().setClassName(packageName, MainActivity::class.java.name),
            PendingIntent.FLAG_IMMUTABLE
        )
        val hide = PendingIntent.getActivity(
            this, 1,
            listenerChannelSettingsIntent(this),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.LISTENER_CHANNEL_ID)
            .setContentTitle(getString(R.string.listener_notification_title))
            .setContentText(getString(R.string.listener_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.hide_notification), hide)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenEventService"
        private const val WAKE_LOCK_TAG = "Paperize:ScreenEvent"

        /** Whether the listener is in the foreground in this process. */
        @Volatile
        var running = false
            private set
    }
}

/** Android's settings for the listener's notification channel, where it can be turned off. */
fun listenerChannelSettingsIntent(context: Context): Intent =
    Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, Constants.LISTENER_CHANNEL_ID)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
