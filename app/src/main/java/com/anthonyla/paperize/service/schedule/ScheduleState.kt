package com.anthonyla.paperize.service.schedule

import android.content.Context
import androidx.core.content.edit
import com.anthonyla.paperize.core.ScreenType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small bookkeeping for Phase 6, kept apart from the user's settings: when each static screen last
 * changed (for the screen-off / unlock gap), which theme each was last drawn for (to redraw
 * adaptive brightness when the theme switches), and when set times were last checked.
 */
@Singleton
class ScheduleState @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** When [screen] last got a new image, in epoch milliseconds; 0 if never. */
    fun lastChangedAt(screen: ScreenType): Long = prefs.getLong(KEY_CHANGED + screen.name, 0L)

    fun recordChanged(screens: Collection<ScreenType>, at: Long = System.currentTimeMillis()) =
        prefs.edit { screens.forEach { putLong(KEY_CHANGED + it.name, at) } }

    /** Whether [screen] was last drawn with adaptive brightness for the dark theme; null if never. */
    fun renderedDark(screen: ScreenType): Boolean? {
        val key = KEY_RENDERED_DARK + screen.name
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    fun recordRendered(screens: Collection<ScreenType>, dark: Boolean) =
        prefs.edit { screens.forEach { putBoolean(KEY_RENDERED_DARK + it.name, dark) } }

    /** When set times were last checked, so a check only acts on times that came round since. */
    var lastTimedCheck: Long
        get() = prefs.getLong(KEY_LAST_TIMED_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_TIMED_CHECK, value) }

    /** The set times in use at the last check ("420,1140"), or null while none are; a new list starts afresh. */
    var setTimesInUse: String?
        get() = prefs.getString(KEY_SET_TIMES_IN_USE, null)
        set(value) = prefs.edit { putString(KEY_SET_TIMES_IN_USE, value) }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val PREFS_NAME = "schedule_state"
        const val KEY_CHANGED = "changed_at_"
        const val KEY_RENDERED_DARK = "rendered_dark_"
        const val KEY_LAST_TIMED_CHECK = "last_timed_check"
        const val KEY_SET_TIMES_IN_USE = "set_times_in_use"
    }
}

/** HOME, LOCK, or both for BOTH; LIVE has no static screen. */
internal fun ScreenType.staticScreens(): List<ScreenType> = when (this) {
    ScreenType.BOTH -> listOf(ScreenType.HOME, ScreenType.LOCK)
    ScreenType.LIVE -> emptyList()
    else -> listOf(this)
}

/** One request for [screens]: BOTH for both static screens. */
internal fun requestScreenFor(screens: Set<ScreenType>): ScreenType? = when {
    screens.isEmpty() -> null
    ScreenType.LIVE in screens -> ScreenType.LIVE
    screens.containsAll(listOf(ScreenType.HOME, ScreenType.LOCK)) || ScreenType.BOTH in screens -> ScreenType.BOTH
    else -> screens.single()
}
