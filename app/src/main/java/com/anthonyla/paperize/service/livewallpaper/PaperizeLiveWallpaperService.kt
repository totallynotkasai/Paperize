package com.anthonyla.paperize.service.livewallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.model.usesVisibleLiveTimer
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import com.anthonyla.paperize.service.livewallpaper.gl.GLCompatibility
import com.anthonyla.paperize.service.livewallpaper.gl.GLWallpaperService
import com.anthonyla.paperize.service.livewallpaper.renderer.ContentUriImageLoader
import com.anthonyla.paperize.service.livewallpaper.renderer.EmptyImageLoader
import com.anthonyla.paperize.service.livewallpaper.renderer.ImageLoader
import com.anthonyla.paperize.service.livewallpaper.renderer.LiveSelection
import com.anthonyla.paperize.service.livewallpaper.renderer.LiveWallpaperImageLoader
import com.anthonyla.paperize.service.livewallpaper.renderer.PaperizeRenderController
import com.anthonyla.paperize.service.livewallpaper.renderer.PaperizeWallpaperRenderer
import com.anthonyla.paperize.service.worker.WallpaperScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live wallpaper with one queue shared by every engine the system creates.
 *
 * Only the leader engine consumes the queue: the first real (non-preview) engine, preferring the
 * one drawing the home screen. It records each image once it is on screen, so a restarted engine
 * shows the same image again. Previews and other engines show the recorded image and follow the
 * leader. Broadcast receivers belong to the service, so one event advances the queue once.
 */
@AndroidEntryPoint
class PaperizeLiveWallpaperService : GLWallpaperService() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var albumRepository: AlbumRepository
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var wallpaperScheduler: WallpaperScheduler

    companion object {
        private const val TAG = "PaperizeLiveWallpaper"
    }

    /** Engines in creation order. Engine callbacks and receivers all run on the main thread. */
    private val engines = mutableListOf<PaperizeLiveWallpaperEngine>()
    private var leader: PaperizeLiveWallpaperEngine? = null

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_RELOAD_WALLPAPER) {
                Log.d(TAG, "Received reload broadcast")
                leader?.advance()
            }
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) leader?.handleScreenOff()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, reloadReceiver, IntentFilter(Constants.ACTION_RELOAD_WALLPAPER), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Screen-off is a protected system broadcast, so this registration receives it.
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        listOf(reloadReceiver, screenOffReceiver).forEach { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
        super.onDestroy()
    }

    override fun onCreateEngine(): Engine {
        return PaperizeLiveWallpaperEngine()
    }

    private fun attach(engine: PaperizeLiveWallpaperEngine) {
        engines += engine
        electLeader()
    }

    private fun detach(engine: PaperizeLiveWallpaperEngine) {
        engines -= engine
        electLeader()
    }

    private fun electLeader() {
        val candidates = engines.filterNot { it.isPreview }
        val elected = candidates.firstOrNull { it.drawsHomeScreen() } ?: candidates.firstOrNull()
        if (elected === leader) return
        leader = elected
        Log.d(TAG, "Leader engine is now $elected")
        engines.forEach { it.onLeadershipChanged() }
    }

    /** The leader showed a new image; every other engine shows it too. */
    private fun onLeaderShowed(wallpaperId: String) {
        engines.filterNot { it === leader }.forEach { it.follow(wallpaperId) }
    }

    inner class PaperizeLiveWallpaperEngine : GLEngine(),
        PaperizeWallpaperRenderer.Callbacks {

        private lateinit var renderer: PaperizeWallpaperRenderer
        private lateinit var renderController: PaperizeRenderController
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var currentAlbumId: String? = null
        private var settingsObserved = false
        /** The image this engine shows; main thread only. */
        private var shownWallpaper: Wallpaper? = null
        private var recordedWallpaperId: String? = null
        /** Selection for the next load. A new engine resumes the recorded image. */
        private var nextSelection = LiveSelection.RESUME
        private var observedScalingType: ScalingType? = null
        private var hasShownParallaxWarning = false
        private var engineVisible = false
        private var latestSettings = ScheduleSettings()
        private var latestWallpaperMode = WallpaperMode.STATIC
        private var liveIntervalJob: Job? = null

        private val isLeader: Boolean get() = leader === this

        private val gestureDetector = GestureDetector(
            this@PaperizeLiveWallpaperService,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    handleDoubleTap()
                    return true
                }
            }
        )

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            Log.d(TAG, "Engine created (preview=$isPreview)")

            setOffsetNotificationsEnabled(true)

            setTouchEventsEnabled(true)

            renderer = PaperizeWallpaperRenderer(applicationContext, this)
            renderController = PaperizeRenderController(
                queueWallpaper = { loader, skipCrossfade ->
                    // The controller drops loaders that a newer request superseded, so a queued
                    // loader always serves the latest selection.
                    nextSelection = LiveSelection.RESUME
                    renderer.queueWallpaper(loader, skipCrossfade)
                },
                controllerScope = engineScope,
                openCurrentArtwork = ::openArtwork
            )

            setRenderer(renderer)
            requestRender()

            attach(this)
            renderController.reloadCurrentArtwork(immediate = true)

            observeSettings()
        }

        /** Build the loader for the pending selection; it chooses and decodes on the renderer's thread. */
        private suspend fun openArtwork(): ImageLoader {
            val selection = when {
                isPreview || !isLeader -> LiveSelection.PEEK
                else -> nextSelection
            }
            return withContext(Dispatchers.IO) {
                // Live Wallpaper only operates in LIVE mode
                // In STATIC mode, the static wallpaper worker handles HOME/LOCK screens separately
                if (settingsRepository.getWallpaperMode() != WallpaperMode.LIVE) {
                    Log.d(TAG, "App is in STATIC mode, Live Wallpaper not active")
                    return@withContext EmptyImageLoader
                }
                val settings = settingsRepository.getScheduleSettings()
                val albumId = settings.liveAlbumId ?: run {
                    Log.w(TAG, "No live album ID set")
                    return@withContext EmptyImageLoader
                }
                liveLoader(albumId, settings, selection)
            }
        }

        private fun liveLoader(
            albumId: String,
            settings: ScheduleSettings,
            selection: LiveSelection,
            pinned: Wallpaper? = null
        ) = LiveWallpaperImageLoader(
            repository = wallpaperRepository,
            albumId = albumId,
            shuffle = settings.shuffleEnabled,
            selection = selection,
            decode = { wallpaper, width, height ->
                ContentUriImageLoader(applicationContext, wallpaper.uri.toUri(), settings.liveScalingType)
                    .decode(width, height)
            },
            pinned = pinned
        )

        override fun onWallpaperShown(loader: ImageLoader) {
            // Called on the GL thread; engine state lives on the main thread.
            engineScope.launch { handleShown(loader) }
        }

        private fun handleShown(loader: ImageLoader) {
            val wallpaper = (loader as? LiveWallpaperImageLoader)?.wallpaper ?: return
            shownWallpaper = wallpaper
            // Only the leader records, after the image is on screen, so a restart resumes it.
            if (!isLeader || loader.selection == LiveSelection.PEEK || recordedWallpaperId == wallpaper.id) return
            recordedWallpaperId = wallpaper.id
            engineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        wallpaperRepository.setCurrentWallpaper(wallpaper.albumId, ScreenType.LIVE, wallpaper.id)
                    }
                    onLeaderShowed(wallpaper.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Could not record the current live wallpaper", e)
                }
            }
        }

        /** Move to the next image. Only the leader consumes the queue. */
        fun advance() {
            if (!isLeader) return
            nextSelection = LiveSelection.ADVANCE
            renderController.reloadCurrentArtwork(immediate = true)
            restartLiveIntervalTimer()
        }

        /** Show the leader's newly recorded image. */
        fun follow(wallpaperId: String) {
            if (shownWallpaper?.id == wallpaperId) return
            renderController.reloadCurrentArtwork(immediate = true)
        }

        fun onLeadershipChanged() {
            restartLiveIntervalTimer()
        }

        /** Android 14+ can run separate engines for home and lock; the home one leads. */
        fun drawsHomeScreen(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                wallpaperFlags and WallpaperManager.FLAG_SYSTEM != 0

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            electLeader()
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun observeSettings() {
            engineScope.launch {
                combine(
                    settingsRepository.getScheduleSettingsFlow(),
                    settingsRepository.getWallpaperModeFlow()
                ) { settings, mode ->
                    Pair(settings, mode)
                }.flatMapLatest { (settings, mode) ->
                    // The live album's own effects replace the live effects (plan 5.3).
                    val albumEffects = settings.liveAlbumId?.let { albumRepository.getAlbumEffectsFlow(it) } ?: flowOf(null)
                    albumEffects.map { Triple(settings, mode, it) }
                }.catch { e ->
                    Log.e(TAG, "Error observing settings", e)
                }.collect { (settings, mode, albumEffects) ->
                    val timerConfigurationChanged =
                        latestWallpaperMode != mode ||
                            latestSettings.enableChanger != settings.enableChanger ||
                            latestSettings.liveAlbumId != settings.liveAlbumId ||
                            latestSettings.liveIntervalMinutes != settings.liveIntervalMinutes
                    latestSettings = settings
                    latestWallpaperMode = mode
                    if (timerConfigurationChanged) restartLiveIntervalTimer()

                    if (mode != WallpaperMode.LIVE) {
                        return@collect
                    }

                    val albumId = settings.liveAlbumId
                    val effects = settings.liveEffects.withAlbumEffects(albumEffects)
                    val scalingType = settings.liveScalingType

                    // The first value only records the album the engine started with; its initial
                    // load already resumes that album's image.
                    val albumChanged = settingsObserved && albumId != currentAlbumId
                    settingsObserved = true
                    currentAlbumId = albumId

                    renderer.updateEffects(effects)
                    renderer.updateScalingType(scalingType)
                    renderer.updateAdaptiveBrightness(settings.adaptiveBrightness)

                    val scalingChanged =
                        observedScalingType != null && observedScalingType != scalingType
                    observedScalingType = scalingType

                    val shown = shownWallpaper
                    if (scalingChanged && !albumChanged && albumId != null && shown != null) {
                        Log.d(TAG, "Live scaling changed; reloading current wallpaper without advancing")
                        renderer.queueWallpaper(liveLoader(albumId, settings, LiveSelection.RESUME, pinned = shown))
                    }

                    if (effects.enableParallax && !hasShownParallaxWarning && GLCompatibility.shouldWarnAboutParallax()) {
                        hasShownParallaxWarning = true
                        Toast.makeText(
                            applicationContext,
                            R.string.parallax_may_not_work,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    if (albumChanged) {
                        Log.d(TAG, "Album changed to $albumId, reloading")
                        shownWallpaper = null
                        if (isLeader) advance() else renderController.reloadCurrentArtwork()
                    }
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            engineVisible = visible
            renderController.visible = visible
            if (visible) {
                // Re-evaluate draw-time effects such as adaptive brightness after
                // configuration changes while the wallpaper was hidden.
                requestRender()
            }
            restartLiveIntervalTimer()
            super.onVisibilityChanged(visible)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            renderer.setNormalOffsetX(xOffset)
        }

        override fun onTouchEvent(event: MotionEvent) {
            try {
                gestureDetector.onTouchEvent(event)
            } catch (e: Exception) {
                Log.w(TAG, "Error processing touch event", e)
            }
            super.onTouchEvent(event)
        }

        override fun onDestroy() {
            liveIntervalJob?.cancel()
            detach(this)

            renderer.cancelLoading()

            queueEvent {
                renderer.destroy()
            }

            engineScope.cancel()
            super.onDestroy()
        }

        override fun queueEventOnGlThread(event: () -> Unit): Boolean = queueEvent(event)

        private fun handleDoubleTap() {
            if (isPreview) return
            engineScope.launch {
                val settings = settingsRepository.getScheduleSettings()
                val leader = leader
                if (settings.liveEffects.enableDoubleTap && leader != null) {
                    leader.advance()
                    resetBackgroundCountdown(settings)
                }
            }
        }

        fun handleScreenOff() {
            engineScope.launch {
                val settings = settingsRepository.getScheduleSettings()
                if (settings.liveEffects.enableChangeOnScreenOff && isLeader) {
                    Log.d(TAG, "Screen off - changing wallpaper")
                    // Load while the screen is off, bypassing the visibility check.
                    val previous = nextSelection
                    nextSelection = LiveSelection.ADVANCE
                    if (renderController.forceReloadCurrentArtwork()) {
                        resetBackgroundCountdown(settings)
                    } else {
                        nextSelection = previous
                    }
                }
            }
        }

        /**
         * A change made on the wallpaper itself counts as a manual change, like the tile and
         * shortcut: the background job waits a full interval from now instead of following soon.
         */
        private suspend fun resetBackgroundCountdown(settings: ScheduleSettings) {
            try {
                withContext(Dispatchers.IO) {
                    wallpaperScheduler.resetAfterManualChange(ScreenType.LIVE, settings, settingsRepository.getWallpaperMode())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not restart the live wallpaper's background countdown", e)
            }
        }

        private fun restartLiveIntervalTimer() {
            liveIntervalJob?.cancel()
            liveIntervalJob = null

            val intervalMinutes = latestSettings.liveIntervalMinutes
            val shouldRun =
                engineVisible &&
                    isLeader &&
                    latestWallpaperMode == WallpaperMode.LIVE &&
                    latestSettings.enableChanger &&
                    latestSettings.liveAlbumId != null &&
                    usesVisibleLiveTimer(intervalMinutes)
            if (!shouldRun) return

            liveIntervalJob = engineScope.launch {
                val intervalMillis = intervalMinutes.toLong() * 60_000L
                while (isActive) {
                    delay(intervalMillis)
                    Log.d(TAG, "Visible live interval elapsed; changing wallpaper")
                    nextSelection = LiveSelection.ADVANCE
                    renderController.reloadCurrentArtwork(immediate = true)
                }
            }
        }
    }
}
