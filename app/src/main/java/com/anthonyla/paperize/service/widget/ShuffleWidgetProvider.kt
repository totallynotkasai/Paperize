package com.anthonyla.paperize.service.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anthonyla.paperize.core.ScreenType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A home-screen button for the next wallpaper on [screen]; see [ShuffleWidgets]. */
abstract class ShuffleWidgetProvider(private val screen: ScreenType) : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SHUFFLE) {
            runAsync { ShuffleWidgets.from(context).shuffle(screen) }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runAsync { ShuffleWidgets.from(context).refresh(screen, appWidgetIds) }
    }

    /** Settings are read from disk, so the work runs after onReceive returns. */
    private fun runAsync(work: suspend () -> Unit) {
        val pending: BroadcastReceiver.PendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                work()
            } catch (e: Exception) {
                Log.e(TAG, "Shuffle widget for $screen failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ShuffleWidgetProvider"
        const val ACTION_SHUFFLE = "com.anthonyla.paperize.ACTION_SHUFFLE_WIDGET"
    }
}

class ShuffleHomeWidget : ShuffleWidgetProvider(ScreenType.HOME)

class ShuffleLockWidget : ShuffleWidgetProvider(ScreenType.LOCK)

class ShuffleBothWidget : ShuffleWidgetProvider(ScreenType.BOTH)
