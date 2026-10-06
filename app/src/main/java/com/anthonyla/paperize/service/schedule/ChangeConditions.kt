package com.anthonyla.paperize.service.schedule

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import com.anthonyla.paperize.core.util.isSystemDarkTheme
import com.anthonyla.paperize.domain.model.ScheduleSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Why an automatic change has to wait (plan 6.1). */
enum class ChangeBlock { NOT_CHARGING, BATTERY_SAVER }

/**
 * Why an automatic change (interval, set time, day/night switch, screen off or unlock) must not
 * happen now, or null if it may. Manual changes (button, tile, widgets, shortcut, double-tap)
 * never ask. [charging] and [powerSave] are asked only when their setting is on.
 */
fun automaticChangeBlock(settings: ScheduleSettings, charging: () -> Boolean, powerSave: () -> Boolean): ChangeBlock? = when {
    settings.onlyWhileCharging && !charging() -> ChangeBlock.NOT_CHARGING
    settings.pauseInBatterySaver && powerSave() -> ChangeBlock.BATTERY_SAVER
    else -> null
}

/** The phone's charging, battery-saver and dark-theme state, read when needed. */
@Singleton
class ChangeConditions @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun isCharging(): Boolean = context.getSystemService(BatteryManager::class.java)?.isCharging ?: false

    fun isPowerSaveMode(): Boolean = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode ?: false

    /** The dark theme as the system has it now; the app's own theme choice doesn't change this. */
    fun isDarkTheme(): Boolean = isSystemDarkTheme(context)

    fun automaticChangeBlock(settings: ScheduleSettings): ChangeBlock? =
        automaticChangeBlock(settings, ::isCharging, ::isPowerSaveMode)
}
