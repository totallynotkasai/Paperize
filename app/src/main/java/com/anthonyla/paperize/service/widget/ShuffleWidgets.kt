package com.anthonyla.paperize.service.widget

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.util.isPaperizeLiveWallpaperActive
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeRequests
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The Shuffle Home, Shuffle Lock and Shuffle Both widgets: what a tap does, and keeping placed
 * widgets in step with the settings. [ShuffleWidgetDisplay] draws them.
 */
@Singleton
class ShuffleWidgets @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val changeRequests: WallpaperChangeRequests,
    private val display: ShuffleWidgetDisplay
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * While the app's process runs, redraw placed widgets when one becomes usable or stops being
     * usable. Every settings change happens in this process, so nothing is missed.
     */
    fun keepInStep() {
        scope.launch {
            readiness()
                .catch { e -> Log.e(TAG, "Could not follow the settings for the widgets", e) }
                .collect { ready -> ready.forEach { (screen, isReady) -> display.show(screen, isReady) } }
        }
    }

    /** Whether each widget can change anything, emitted again only when that changes. */
    internal fun readiness(): Flow<Map<ScreenType, Boolean>> = combine(
        settingsRepository.getScheduleSettingsFlow(),
        settingsRepository.getWallpaperModeFlow()
    ) { settings, mode ->
        SHUFFLE_TARGETS.associateWith { isShuffleReady(it, settings, mode) }
    }.distinctUntilChanged()

    /** Draw the given widgets now, e.g. when they are placed or the launcher restarts. */
    suspend fun refresh(screen: ScreenType, widgetIds: IntArray) {
        val ready = isShuffleReady(screen, settingsRepository.getScheduleSettings(), settingsRepository.getWallpaperMode())
        display.show(screen, ready, widgetIds)
    }

    /**
     * A tap: the next image for [screen] through the same path as the tile and shortcut, so the
     * countdown restarts too. In live mode it changes the live wallpaper. If nothing can change,
     * a short message says why.
     */
    suspend fun shuffle(screen: ScreenType) {
        val settings = settingsRepository.getScheduleSettings()
        val mode = settingsRepository.getWallpaperMode()
        val problem = shuffleProblem(screen, settings, mode) { isPaperizeLiveWallpaperActive(context) }
        if (problem != null) {
            display.toast(problem.message())
            // The widget may have missed a change made while the app's process wasn't running.
            display.show(screen, isShuffleReady(screen, settings, mode))
            return
        }
        changeRequests.changeConfigured(screen)
        if (changesOnlyLockScreen(screen, settings, mode)) display.toast(R.string.widget_changing_lock)
    }

    /** For the widget receivers, which Hilt can't inject through their shared base class. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Provider {
        fun shuffleWidgets(): ShuffleWidgets
    }

    companion object {
        private const val TAG = "ShuffleWidgets"

        fun from(context: Context): ShuffleWidgets =
            EntryPointAccessors.fromApplication(context.applicationContext, Provider::class.java).shuffleWidgets()

        /** Android 12+ cuts toasts off after two lines, so these stay short. */
        @StringRes
        internal fun ShuffleProblem.message(): Int = when (this) {
            ShuffleProblem.HOME_NOT_SET_UP -> R.string.widget_home_not_set_up
            ShuffleProblem.LOCK_NOT_SET_UP -> R.string.widget_lock_not_set_up
            ShuffleProblem.NO_SCREEN_SET_UP -> R.string.widget_both_not_set_up
            ShuffleProblem.NO_LIVE_ALBUM -> R.string.widget_live_no_album
            ShuffleProblem.LIVE_NOT_SET -> R.string.live_wallpaper_not_set_title
        }
    }
}
