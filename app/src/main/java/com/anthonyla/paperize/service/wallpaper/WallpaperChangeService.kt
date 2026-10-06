package com.anthonyla.paperize.service.wallpaper

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import com.anthonyla.paperize.presentation.MainActivity
import com.anthonyla.paperize.service.WallpaperChangeLock
import com.anthonyla.paperize.service.WallpaperNotifier
import com.anthonyla.paperize.service.worker.WallpaperScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

@AndroidEntryPoint
class WallpaperChangeService : Service() {

    @Inject lateinit var wallpaperController: WallpaperController
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var wallpaperChangeLock: WallpaperChangeLock
    @Inject lateinit var wallpaperScheduler: WallpaperScheduler
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var notifier: WallpaperNotifier

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, createNotification())
        }

        val screenType = intent?.getStringExtra(EXTRA_SCREEN_TYPE)
            ?.let(ScreenType::fromString)
            ?: ScreenType.BOTH
        when (intent?.action) {
            ACTION_CHANGE_WALLPAPER -> handleChangeWallpaper(
                screenType, startId, resetSchedule = !intent.getBooleanExtra(EXTRA_KEEP_SCHEDULE, false)
            )
            ACTION_CHANGE_WALLPAPER_AUTO ->
                handleChangeWallpaper(screenType, startId, respectWallpaperMode = true)
            ACTION_APPLY_SPECIFIC_WALLPAPER -> handleApplySpecificWallpaper(
                wallpaperId = intent.getStringExtra(EXTRA_WALLPAPER_ID),
                screenType = screenType,
                startId = startId
            )
            ACTION_REAPPLY_EFFECTS -> handleReapplyEffects(screenType, startId)
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * [resetSchedule] restarts the countdown of the jobs covering [screenType]. A change that puts
     * a newly turned-on screen's first image up skips it: its job was just set up with a countdown.
     */
    private fun handleChangeWallpaper(
        screenType: ScreenType,
        startId: Int,
        respectWallpaperMode: Boolean = false,
        resetSchedule: Boolean = true
    ) {
        serviceScope.launch {
            wallpaperChangeLock.mutex.withLock {
                try {
                    val wallpaperMode = settingsRepository.getWallpaperMode()
                    val effectiveScreenType =
                        if (
                            respectWallpaperMode &&
                            wallpaperMode == WallpaperMode.LIVE
                        ) {
                            ScreenType.LIVE
                        } else {
                            screenType
                        }
                    val settings = settingsRepository.getScheduleSettings()
                    val outcome = wallpaperController.change(effectiveScreenType, settings)
                    if (outcome.emptyAlbum) notifier.showEmptyAlbum()
                    if (outcome.changed && resetSchedule) {
                        wallpaperScheduler.resetAfterManualChange(
                            effectiveScreenType,
                            settings,
                            wallpaperMode
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error changing wallpaper", e)
                    notifier.showChangeFailed(e.localizedMessage)
                } finally {
                    stopSelf(startId)
                }
            }
        }
    }

    private fun handleApplySpecificWallpaper(
        wallpaperId: String?,
        screenType: ScreenType,
        startId: Int
    ) {
        serviceScope.launch {
            wallpaperChangeLock.mutex.withLock {
                try {
                    require(!wallpaperId.isNullOrBlank()) { getString(R.string.wallpaper_not_found) }
                    require(screenType != ScreenType.LIVE) { getString(R.string.static_mode_required) }
                    val wallpaperMode = settingsRepository.getWallpaperMode()
                    require(wallpaperMode == WallpaperMode.STATIC) {
                        getString(R.string.static_mode_required)
                    }
                    val wallpaper = wallpaperRepository.getWallpaperById(wallpaperId)
                        ?: error(getString(R.string.wallpaper_not_found))
                    val settings = settingsRepository.getScheduleSettings()

                    wallpaperController.applySpecific(
                        albumId = wallpaper.albumId,
                        wallpaperId = wallpaper.id,
                        screen = screenType,
                        settings = settings
                    )
                    wallpaperScheduler.resetAfterManualChange(
                        screenType = screenType,
                        settings = settings,
                        wallpaperMode = wallpaperMode
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error applying selected wallpaper", e)
                    notifier.showChangeFailed(e.localizedMessage)
                } finally {
                    stopSelf(startId)
                }
            }
        }
    }

    private fun handleReapplyEffects(screenType: ScreenType, startId: Int) {
        serviceScope.launch {
            wallpaperChangeLock.mutex.withLock {
                try {
                    val settings = settingsRepository.getScheduleSettings()
                    val outcome = wallpaperController.reapply(screenType, settings)
                    if (outcome.emptyAlbum) notifier.showEmptyAlbum()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error reapplying effects", e)
                    notifier.showChangeFailed(e.localizedMessage)
                } finally {
                    stopSelf(startId)
                }
            }
        }
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
        const val EXTRA_SCREEN_TYPE = Constants.EXTRA_SCREEN_TYPE
        const val EXTRA_WALLPAPER_ID = Constants.EXTRA_WALLPAPER_ID
        const val EXTRA_KEEP_SCHEDULE = "com.anthonyla.paperize.EXTRA_KEEP_SCHEDULE"
    }
}
