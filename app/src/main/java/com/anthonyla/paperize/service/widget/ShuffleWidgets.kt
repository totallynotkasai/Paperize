package com.anthonyla.paperize.service.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Shuffle Home, Shuffle Lock and Shuffle Both widgets: how they look, what a tap does, and
 * keeping placed widgets in step with the settings.
 */
@Singleton
class ShuffleWidgets @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val changeRequests: WallpaperChangeRequests
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manager: AppWidgetManager? get() = AppWidgetManager.getInstance(context)

    /**
     * While the app's process runs, redraw placed widgets when one becomes usable or stops being
     * usable. Every settings change happens in this process, so nothing is missed.
     */
    fun keepInStep() {
        scope.launch {
            combine(
                settingsRepository.getScheduleSettingsFlow(),
                settingsRepository.getWallpaperModeFlow()
            ) { settings, mode ->
                SHUFFLE_TARGETS.associateWith { isShuffleReady(it, settings, mode) }
            }
                .distinctUntilChanged()
                .catch { e -> Log.e(TAG, "Could not follow the settings for the widgets", e) }
                .collect { ready -> ready.forEach { (screen, isReady) -> show(screen, isReady) } }
        }
    }

    /** Draw the given widgets now, e.g. when they are placed or the launcher restarts. */
    suspend fun refresh(screen: ScreenType, widgetIds: IntArray) {
        val ready = isShuffleReady(screen, settingsRepository.getScheduleSettings(), settingsRepository.getWallpaperMode())
        show(screen, ready, widgetIds)
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
            toast(problem.message())
            // The widget may have missed a change made while the app's process wasn't running.
            show(screen, isShuffleReady(screen, settings, mode))
            return
        }
        changeRequests.changeConfigured(screen)
        if (changesOnlyLockScreen(screen, settings, mode)) toast(R.string.widget_changing_lock)
    }

    private fun show(screen: ScreenType, ready: Boolean, widgetIds: IntArray? = null) {
        val manager = manager ?: return
        val ids = widgetIds ?: manager.getAppWidgetIds(ComponentName(context, providerFor(screen)))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, views(screen, ready))
    }

    /** One small icon button, or icon and name once resized to two cells or more. */
    private fun views(screen: ScreenType, ready: Boolean): RemoteViews = RemoteViews(
        mapOf(
            SizeF(COMPACT_MIN_WIDTH_DP, MIN_HEIGHT_DP) to layout(screen, ready, wide = false),
            SizeF(WIDE_MIN_WIDTH_DP, MIN_HEIGHT_DP) to layout(screen, ready, wide = true)
        )
    )

    private fun layout(screen: ScreenType, ready: Boolean, wide: Boolean) =
        RemoteViews(context.packageName, if (wide) R.layout.widget_shuffle_wide else R.layout.widget_shuffle_compact).apply {
            val name = context.getString(nameFor(screen))
            setImageViewResource(R.id.widget_glyph, glyphFor(screen))
            if (wide) {
                setTextViewText(R.id.widget_label, name)
                setViewVisibility(R.id.widget_status, if (ready) View.GONE else View.VISIBLE)
            }
            setOnClickPendingIntent(android.R.id.background, tapIntent(screen))
            setInt(
                android.R.id.background,
                "setBackgroundResource",
                if (ready) R.drawable.widget_background else R.drawable.widget_background_unavailable
            )
            setInt(R.id.widget_glyph, "setImageAlpha", if (ready) OPAQUE else UNAVAILABLE_ALPHA)
            setViewVisibility(R.id.widget_badge, if (ready) View.VISIBLE else View.GONE)
            setContentDescription(
                android.R.id.background,
                if (ready) name else context.getString(R.string.widget_content_desc_not_set_up, name)
            )
        }

    private fun tapIntent(screen: ScreenType): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, providerFor(screen)).setAction(ShuffleWidgetProvider.ACTION_SHUFFLE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private suspend fun toast(@StringRes message: Int) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** Android 12+ cuts toasts off after two lines, so these stay short. */
    @StringRes
    private fun ShuffleProblem.message(): Int = when (this) {
        ShuffleProblem.HOME_NOT_SET_UP -> R.string.widget_home_not_set_up
        ShuffleProblem.LOCK_NOT_SET_UP -> R.string.widget_lock_not_set_up
        ShuffleProblem.NO_SCREEN_SET_UP -> R.string.widget_both_not_set_up
        ShuffleProblem.NO_LIVE_ALBUM -> R.string.widget_live_no_album
        ShuffleProblem.LIVE_NOT_SET -> R.string.live_wallpaper_not_set_title
    }

    /** For the widget receivers, which Hilt can't inject through their shared base class. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Provider {
        fun shuffleWidgets(): ShuffleWidgets
    }

    companion object {
        private const val TAG = "ShuffleWidgets"

        /** Below this width (about two cells) the widget is a bare icon button. */
        private const val COMPACT_MIN_WIDTH_DP = 40f
        private const val WIDE_MIN_WIDTH_DP = 100f
        private const val MIN_HEIGHT_DP = 40f

        private const val OPAQUE = 255
        /** Material's 38% for disabled content. */
        private const val UNAVAILABLE_ALPHA = 97

        fun from(context: Context): ShuffleWidgets =
            EntryPointAccessors.fromApplication(context.applicationContext, Provider::class.java).shuffleWidgets()

        private fun providerFor(screen: ScreenType): Class<out ShuffleWidgetProvider> = when (screen) {
            ScreenType.HOME -> ShuffleHomeWidget::class.java
            ScreenType.LOCK -> ShuffleLockWidget::class.java
            else -> ShuffleBothWidget::class.java
        }

        @StringRes
        private fun nameFor(screen: ScreenType): Int = when (screen) {
            ScreenType.HOME -> R.string.widget_shuffle_home
            ScreenType.LOCK -> R.string.widget_shuffle_lock
            else -> R.string.widget_shuffle_both
        }

        private fun glyphFor(screen: ScreenType): Int = when (screen) {
            ScreenType.HOME -> R.drawable.ic_widget_home
            ScreenType.LOCK -> R.drawable.ic_widget_lock
            else -> R.drawable.ic_widget_both
        }
    }
}
