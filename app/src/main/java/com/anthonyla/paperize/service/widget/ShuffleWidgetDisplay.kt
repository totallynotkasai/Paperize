package com.anthonyla.paperize.service.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScreenType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the Shuffle widgets look and speak: their views on the launcher, and their short messages. */
@Singleton
class ShuffleWidgetDisplay @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val manager: AppWidgetManager? get() = AppWidgetManager.getInstance(context)

    /** Draw [screen]'s widgets, the given ones or every placed one, as usable or greyed out. */
    fun show(screen: ScreenType, ready: Boolean, widgetIds: IntArray? = null) {
        val manager = manager ?: return
        val ids = widgetIds ?: manager.getAppWidgetIds(ComponentName(context, providerFor(screen)))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, views(screen, ready))
    }

    suspend fun toast(@StringRes message: Int) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** One small icon button, or icon and name once resized to two cells or more. */
    private fun views(screen: ScreenType, ready: Boolean): RemoteViews = RemoteViews(
        mapOf(
            SizeF(COMPACT_MIN_WIDTH_DP, MIN_HEIGHT_DP) to layout(screen, ready, wide = false),
            SizeF(WIDE_MIN_WIDTH_DP, MIN_HEIGHT_DP) to layout(screen, ready, wide = true)
        )
    )

    @VisibleForTesting
    internal fun layout(screen: ScreenType, ready: Boolean, wide: Boolean) =
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

    companion object {
        /** Below this width (about two cells) the widget is a bare icon button. */
        private const val COMPACT_MIN_WIDTH_DP = 40f
        private const val WIDE_MIN_WIDTH_DP = 100f
        private const val MIN_HEIGHT_DP = 40f

        @VisibleForTesting internal const val OPAQUE = 255
        /** Material's 38% for disabled content. */
        @VisibleForTesting internal const val UNAVAILABLE_ALPHA = 97

        private fun providerFor(screen: ScreenType): Class<out ShuffleWidgetProvider> = when (screen) {
            ScreenType.HOME -> ShuffleHomeWidget::class.java
            ScreenType.LOCK -> ShuffleLockWidget::class.java
            else -> ShuffleBothWidget::class.java
        }

        @StringRes
        internal fun nameFor(screen: ScreenType): Int = when (screen) {
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
