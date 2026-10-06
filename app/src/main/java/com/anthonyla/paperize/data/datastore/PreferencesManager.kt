package com.anthonyla.paperize.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.core.constants.PreferenceKeys
import com.anthonyla.paperize.domain.model.AppSettings
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.domain.model.validChangeTimes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.PREFERENCES_NAME
)

/** "420,1140" → [420, 1140]; anything unreadable is dropped. */
internal fun parseChangeTimes(stored: String): List<Int> =
    validChangeTimes(stored.split(',').mapNotNull { it.trim().toIntOrNull() })

@Singleton
class PreferencesManager @Inject constructor(
    context: Context
) {
    private val dataStore = context.dataStore

    suspend fun getAppSettings(): AppSettings = getAppSettingsFlow().first()

    fun getAppSettingsFlow(): Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            darkMode = prefs[booleanPreferencesKey(PreferenceKeys.DARK_MODE)],
            dynamicTheming = prefs[booleanPreferencesKey(PreferenceKeys.DYNAMIC_THEMING)] ?: false,
            animate = prefs[booleanPreferencesKey(PreferenceKeys.ANIMATE)] ?: true,
            firstLaunch = prefs[booleanPreferencesKey(PreferenceKeys.FIRST_LAUNCH)] ?: true
        )
    }

    suspend fun getWallpaperMode(): WallpaperMode = getWallpaperModeFlow().first()

    fun getWallpaperModeFlow(): Flow<WallpaperMode> = dataStore.data.map { prefs ->
        val modeString = prefs[stringPreferencesKey(PreferenceKeys.WALLPAPER_MODE)]
        WallpaperMode.fromString(modeString)
    }

    /** The caller must reset library and schedule data when switching modes. */
    suspend fun updateWallpaperMode(mode: WallpaperMode) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(PreferenceKeys.WALLPAPER_MODE)] = mode.name
        }
    }

    suspend fun getScheduleSettings(): ScheduleSettings = getScheduleSettingsFlow().first()

    fun getScheduleSettingsFlow(): Flow<ScheduleSettings> = dataStore.data.map(::scheduleSettings)

    private fun scheduleSettings(prefs: Preferences): ScheduleSettings = ScheduleSettings(
        enableChanger = prefs[booleanPreferencesKey(PreferenceKeys.ENABLE_CHANGER)] ?: false,
        separateSchedules = prefs[booleanPreferencesKey(PreferenceKeys.SEPARATE_SCHEDULES)] ?: false,
        shuffleEnabled = prefs[booleanPreferencesKey(PreferenceKeys.SHUFFLE_ENABLED)] ?: false,
        homeEnabled = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLED)] ?: false,
        lockEnabled = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLED)] ?: false,
        homeAlbumId = prefs[stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID)],
        lockAlbumId = prefs[stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID)],
        liveAlbumId = prefs[stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID)],
        homeIntervalMinutes = prefs[intPreferencesKey(PreferenceKeys.HOME_INTERVAL_MINUTES)]
            ?: Constants.DEFAULT_INTERVAL_MINUTES,
        lockIntervalMinutes = prefs[intPreferencesKey(PreferenceKeys.LOCK_INTERVAL_MINUTES)]
            ?: Constants.DEFAULT_INTERVAL_MINUTES,
        liveIntervalMinutes = prefs[intPreferencesKey(PreferenceKeys.LIVE_INTERVAL_MINUTES)]
            ?: Constants.DEFAULT_INTERVAL_MINUTES,
        homeScalingType = ScalingType.fromString(prefs[stringPreferencesKey(PreferenceKeys.HOME_SCALING_TYPE)]),
        lockScalingType = ScalingType.fromString(prefs[stringPreferencesKey(PreferenceKeys.LOCK_SCALING_TYPE)]),
        liveScalingType = ScalingType.fromString(prefs[stringPreferencesKey(PreferenceKeys.LIVE_SCALING_TYPE)]),
        homeScrollingEnabled = prefs[booleanPreferencesKey(PreferenceKeys.HOME_SCROLLING_ENABLED)] ?: false,
        homeEffects = WallpaperEffects(
            enableBlur = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_BLUR)] ?: false,
            blurPercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_BLUR)] ?: 0,
            enableDarken = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DARKEN)] ?: false,
            darkenPercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_DARKEN)] ?: 0,
            enableVignette = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_VIGNETTE)] ?: false,
            vignettePercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_VIGNETTE)] ?: 0,
            enableGrayscale = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_GRAYSCALE)] ?: false,
            grayscalePercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_GRAYSCALE)] ?: 0,
            enableDoubleTap = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DOUBLE_TAP)] ?: false,
            enableParallax = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_PARALLAX)] ?: false,
            parallaxIntensity = prefs[intPreferencesKey(PreferenceKeys.HOME_PARALLAX_INTENSITY)] ?: Constants.DEFAULT_PARALLAX_INTENSITY
        ),
        lockEffects = WallpaperEffects(
            enableBlur = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_BLUR)] ?: false,
            blurPercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_BLUR)] ?: 0,
            enableDarken = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DARKEN)] ?: false,
            darkenPercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_DARKEN)] ?: 0,
            enableVignette = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_VIGNETTE)] ?: false,
            vignettePercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_VIGNETTE)] ?: 0,
            enableGrayscale = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_GRAYSCALE)] ?: false,
            grayscalePercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_GRAYSCALE)] ?: 0,
            enableDoubleTap = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DOUBLE_TAP)] ?: false,
            enableParallax = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_PARALLAX)] ?: false,
            parallaxIntensity = prefs[intPreferencesKey(PreferenceKeys.LOCK_PARALLAX_INTENSITY)] ?: Constants.DEFAULT_PARALLAX_INTENSITY
        ),
        liveEffects = WallpaperEffects(
            enableBlur = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_BLUR)] ?: false,
            blurPercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_BLUR)] ?: 0,
            enableDarken = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DARKEN)] ?: false,
            darkenPercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_DARKEN)] ?: 0,
            enableVignette = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_VIGNETTE)] ?: false,
            vignettePercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_VIGNETTE)] ?: 0,
            enableGrayscale = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_GRAYSCALE)] ?: false,
            grayscalePercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_GRAYSCALE)] ?: 0,
            enableDoubleTap = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DOUBLE_TAP)] ?: false,
            enableChangeOnScreenOff = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_CHANGE_ON_SCREEN_OFF)] ?: false,
            enableParallax = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_PARALLAX)] ?: false,
            parallaxIntensity = prefs[intPreferencesKey(PreferenceKeys.LIVE_PARALLAX_INTENSITY)] ?: Constants.DEFAULT_PARALLAX_INTENSITY,
            enableAutoPan = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_AUTO_PAN)] ?: false,
            autoPanSweepSeconds = prefs[intPreferencesKey(PreferenceKeys.LIVE_AUTO_PAN_SWEEP_SECONDS)]
                ?: Constants.DEFAULT_AUTO_PAN_SWEEP_SECONDS
        ),
        adaptiveBrightness = prefs[booleanPreferencesKey(PreferenceKeys.ADAPTIVE_BRIGHTNESS)] ?: false,
        onlyWhileCharging = prefs[booleanPreferencesKey(PreferenceKeys.ONLY_WHILE_CHARGING)] ?: false,
        pauseInBatterySaver = prefs[booleanPreferencesKey(PreferenceKeys.PAUSE_IN_BATTERY_SAVER)] ?: false,
        changeOnScreenOff = prefs[booleanPreferencesKey(PreferenceKeys.CHANGE_ON_SCREEN_OFF)] ?: false,
        screenOffTarget = prefs[stringPreferencesKey(PreferenceKeys.SCREEN_OFF_TARGET)]
            ?.let(ScreenType::fromString) ?: ScreenType.BOTH,
        changeOnUnlock = prefs[booleanPreferencesKey(PreferenceKeys.CHANGE_ON_UNLOCK)] ?: false,
        unlockTarget = prefs[stringPreferencesKey(PreferenceKeys.UNLOCK_TARGET)]
            ?.let(ScreenType::fromString) ?: ScreenType.HOME,
        triggerGapMinutes = prefs[intPreferencesKey(PreferenceKeys.TRIGGER_GAP_MINUTES)]
            ?: Constants.DEFAULT_TRIGGER_GAP_MINUTES,
        scheduleType = ScheduleType.fromString(prefs[stringPreferencesKey(PreferenceKeys.SCHEDULE_TYPE)]),
        changeTimes = prefs[stringPreferencesKey(PreferenceKeys.CHANGE_TIMES)]
            ?.let(::parseChangeTimes) ?: Constants.DEFAULT_CHANGE_TIMES,
        homeNightAlbumId = prefs[stringPreferencesKey(PreferenceKeys.HOME_NIGHT_ALBUM_ID)],
        lockNightAlbumId = prefs[stringPreferencesKey(PreferenceKeys.LOCK_NIGHT_ALBUM_ID)],
        liveNightAlbumId = prefs[stringPreferencesKey(PreferenceKeys.LIVE_NIGHT_ALBUM_ID)],
        nightTrigger = NightTrigger.fromString(prefs[stringPreferencesKey(PreferenceKeys.NIGHT_TRIGGER)]),
        nightStartMinutes = prefs[intPreferencesKey(PreferenceKeys.NIGHT_START_MINUTES)]
            ?: Constants.DEFAULT_NIGHT_START_MINUTES,
        dayStartMinutes = prefs[intPreferencesKey(PreferenceKeys.DAY_START_MINUTES)]
            ?: Constants.DEFAULT_DAY_START_MINUTES,
        nightActive = prefs[booleanPreferencesKey(PreferenceKeys.NIGHT_ACTIVE)] ?: false
    )

    suspend fun updateScheduleSettings(settings: ScheduleSettings) {
        updateScheduleSettings { settings }
    }

    suspend fun updateScheduleSettings(transform: (ScheduleSettings) -> ScheduleSettings): ScheduleSettings {
        val updated = dataStore.edit { prefs ->
            val settings = transform(scheduleSettings(prefs))
            prefs[booleanPreferencesKey(PreferenceKeys.ENABLE_CHANGER)] = settings.enableChanger
            prefs[booleanPreferencesKey(PreferenceKeys.SEPARATE_SCHEDULES)] = settings.separateSchedules
            prefs[booleanPreferencesKey(PreferenceKeys.SHUFFLE_ENABLED)] = settings.shuffleEnabled
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLED)] = settings.homeEnabled
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLED)] = settings.lockEnabled
            if (settings.homeAlbumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID)] = settings.homeAlbumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID))
            }
            if (settings.lockAlbumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID)] = settings.lockAlbumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID))
            }
            if (settings.liveAlbumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID)] = settings.liveAlbumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID))
            }
            prefs[intPreferencesKey(PreferenceKeys.HOME_INTERVAL_MINUTES)] = settings.homeIntervalMinutes
            prefs[intPreferencesKey(PreferenceKeys.LOCK_INTERVAL_MINUTES)] = settings.lockIntervalMinutes
            prefs[intPreferencesKey(PreferenceKeys.LIVE_INTERVAL_MINUTES)] = settings.liveIntervalMinutes
            prefs[stringPreferencesKey(PreferenceKeys.HOME_SCALING_TYPE)] = settings.homeScalingType.name
            prefs[stringPreferencesKey(PreferenceKeys.LOCK_SCALING_TYPE)] = settings.lockScalingType.name
            prefs[stringPreferencesKey(PreferenceKeys.LIVE_SCALING_TYPE)] = settings.liveScalingType.name
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_SCROLLING_ENABLED)] = settings.homeScrollingEnabled

            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_BLUR)] = settings.homeEffects.enableBlur
            prefs[intPreferencesKey(PreferenceKeys.HOME_BLUR)] = settings.homeEffects.blurPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DARKEN)] = settings.homeEffects.enableDarken
            prefs[intPreferencesKey(PreferenceKeys.HOME_DARKEN)] = settings.homeEffects.darkenPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_VIGNETTE)] = settings.homeEffects.enableVignette
            prefs[intPreferencesKey(PreferenceKeys.HOME_VIGNETTE)] = settings.homeEffects.vignettePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_GRAYSCALE)] = settings.homeEffects.enableGrayscale
            prefs[intPreferencesKey(PreferenceKeys.HOME_GRAYSCALE)] = settings.homeEffects.grayscalePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DOUBLE_TAP)] = settings.homeEffects.enableDoubleTap
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_PARALLAX)] = settings.homeEffects.enableParallax
            prefs[intPreferencesKey(PreferenceKeys.HOME_PARALLAX_INTENSITY)] = settings.homeEffects.parallaxIntensity

            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_BLUR)] = settings.lockEffects.enableBlur
            prefs[intPreferencesKey(PreferenceKeys.LOCK_BLUR)] = settings.lockEffects.blurPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DARKEN)] = settings.lockEffects.enableDarken
            prefs[intPreferencesKey(PreferenceKeys.LOCK_DARKEN)] = settings.lockEffects.darkenPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_VIGNETTE)] = settings.lockEffects.enableVignette
            prefs[intPreferencesKey(PreferenceKeys.LOCK_VIGNETTE)] = settings.lockEffects.vignettePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_GRAYSCALE)] = settings.lockEffects.enableGrayscale
            prefs[intPreferencesKey(PreferenceKeys.LOCK_GRAYSCALE)] = settings.lockEffects.grayscalePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DOUBLE_TAP)] = settings.lockEffects.enableDoubleTap
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_PARALLAX)] = settings.lockEffects.enableParallax
            prefs[intPreferencesKey(PreferenceKeys.LOCK_PARALLAX_INTENSITY)] = settings.lockEffects.parallaxIntensity

            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_BLUR)] = settings.liveEffects.enableBlur
            prefs[intPreferencesKey(PreferenceKeys.LIVE_BLUR)] = settings.liveEffects.blurPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DARKEN)] = settings.liveEffects.enableDarken
            prefs[intPreferencesKey(PreferenceKeys.LIVE_DARKEN)] = settings.liveEffects.darkenPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_VIGNETTE)] = settings.liveEffects.enableVignette
            prefs[intPreferencesKey(PreferenceKeys.LIVE_VIGNETTE)] = settings.liveEffects.vignettePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_GRAYSCALE)] = settings.liveEffects.enableGrayscale
            prefs[intPreferencesKey(PreferenceKeys.LIVE_GRAYSCALE)] = settings.liveEffects.grayscalePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DOUBLE_TAP)] = settings.liveEffects.enableDoubleTap
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_CHANGE_ON_SCREEN_OFF)] = settings.liveEffects.enableChangeOnScreenOff
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_PARALLAX)] = settings.liveEffects.enableParallax
            prefs[intPreferencesKey(PreferenceKeys.LIVE_PARALLAX_INTENSITY)] = settings.liveEffects.parallaxIntensity
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_AUTO_PAN)] = settings.liveEffects.enableAutoPan
            prefs[intPreferencesKey(PreferenceKeys.LIVE_AUTO_PAN_SWEEP_SECONDS)] = settings.liveEffects.autoPanSweepSeconds

            prefs[booleanPreferencesKey(PreferenceKeys.ADAPTIVE_BRIGHTNESS)] = settings.adaptiveBrightness

            prefs[booleanPreferencesKey(PreferenceKeys.ONLY_WHILE_CHARGING)] = settings.onlyWhileCharging
            prefs[booleanPreferencesKey(PreferenceKeys.PAUSE_IN_BATTERY_SAVER)] = settings.pauseInBatterySaver
            prefs[booleanPreferencesKey(PreferenceKeys.CHANGE_ON_SCREEN_OFF)] = settings.changeOnScreenOff
            prefs[stringPreferencesKey(PreferenceKeys.SCREEN_OFF_TARGET)] = settings.screenOffTarget.name
            prefs[booleanPreferencesKey(PreferenceKeys.CHANGE_ON_UNLOCK)] = settings.changeOnUnlock
            prefs[stringPreferencesKey(PreferenceKeys.UNLOCK_TARGET)] = settings.unlockTarget.name
            prefs[intPreferencesKey(PreferenceKeys.TRIGGER_GAP_MINUTES)] = settings.triggerGapMinutes
            prefs[stringPreferencesKey(PreferenceKeys.SCHEDULE_TYPE)] = settings.scheduleType.name
            prefs[stringPreferencesKey(PreferenceKeys.CHANGE_TIMES)] = settings.changeTimes.joinToString(",")
            prefs.putOrRemove(PreferenceKeys.HOME_NIGHT_ALBUM_ID, settings.homeNightAlbumId)
            prefs.putOrRemove(PreferenceKeys.LOCK_NIGHT_ALBUM_ID, settings.lockNightAlbumId)
            prefs.putOrRemove(PreferenceKeys.LIVE_NIGHT_ALBUM_ID, settings.liveNightAlbumId)
            prefs[stringPreferencesKey(PreferenceKeys.NIGHT_TRIGGER)] = settings.nightTrigger.name
            prefs[intPreferencesKey(PreferenceKeys.NIGHT_START_MINUTES)] = settings.nightStartMinutes
            prefs[intPreferencesKey(PreferenceKeys.DAY_START_MINUTES)] = settings.dayStartMinutes
            prefs[booleanPreferencesKey(PreferenceKeys.NIGHT_ACTIVE)] = settings.nightActive
        }
        return scheduleSettings(updated)
    }

    private fun MutablePreferences.putOrRemove(key: String, value: String?) {
        if (value != null) this[stringPreferencesKey(key)] = value else remove(stringPreferencesKey(key))
    }

    /** A night album for [screen] (plan 6.4); null removes it. */
    suspend fun updateNightAlbumId(screen: ScreenType, albumId: String?) {
        val key = when (screen) {
            ScreenType.HOME -> PreferenceKeys.HOME_NIGHT_ALBUM_ID
            ScreenType.LOCK -> PreferenceKeys.LOCK_NIGHT_ALBUM_ID
            ScreenType.LIVE -> PreferenceKeys.LIVE_NIGHT_ALBUM_ID
            ScreenType.BOTH -> return
        }
        dataStore.edit { prefs -> prefs.putOrRemove(key, albumId) }
    }

    /** Switch between the day and night albums; returns whether the stored value changed. */
    suspend fun updateNightActive(active: Boolean): Boolean {
        var changed = false
        dataStore.edit { prefs ->
            val key = booleanPreferencesKey(PreferenceKeys.NIGHT_ACTIVE)
            changed = (prefs[key] ?: false) != active
            if (changed) prefs[key] = active
        }
        return changed
    }

    suspend fun updateHomeAlbumId(albumId: String?) {
        dataStore.edit { prefs ->
            if (albumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID)] = albumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID))
            }
        }
    }

    suspend fun updateLockAlbumId(albumId: String?) {
        dataStore.edit { prefs ->
            if (albumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID)] = albumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID))
            }
        }
    }

    suspend fun updateLiveAlbumId(albumId: String?) {
        dataStore.edit { prefs ->
            if (albumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID)] = albumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID))
            }
        }
    }

    suspend fun clearAlbumSelectionsIfMatches(albumId: String): Boolean {
        var wasCleared = false
        dataStore.edit { prefs ->
            val targets = listOf(
                PreferenceKeys.HOME_ALBUM_ID, PreferenceKeys.LOCK_ALBUM_ID, PreferenceKeys.LIVE_ALBUM_ID,
                PreferenceKeys.HOME_NIGHT_ALBUM_ID, PreferenceKeys.LOCK_NIGHT_ALBUM_ID, PreferenceKeys.LIVE_NIGHT_ALBUM_ID
            ).map(::stringPreferencesKey).filter { prefs[it] == albumId }
            targets.forEach { prefs.remove(it) }
            wasCleared = targets.isNotEmpty()
        }
        return wasCleared
    }

    suspend fun clearEmptyAlbumSelection(albumId: String, screen: ScreenType) {
        dataStore.edit { prefs ->
            // The empty album may be the screen's night album (plan 6.4).
            val targets = when (screen) {
                ScreenType.HOME -> listOf(PreferenceKeys.HOME_ALBUM_ID, PreferenceKeys.HOME_NIGHT_ALBUM_ID)
                ScreenType.LOCK -> listOf(PreferenceKeys.LOCK_ALBUM_ID, PreferenceKeys.LOCK_NIGHT_ALBUM_ID)
                ScreenType.BOTH -> listOf(
                    PreferenceKeys.HOME_ALBUM_ID, PreferenceKeys.LOCK_ALBUM_ID,
                    PreferenceKeys.HOME_NIGHT_ALBUM_ID, PreferenceKeys.LOCK_NIGHT_ALBUM_ID
                )
                ScreenType.LIVE -> listOf(PreferenceKeys.LIVE_ALBUM_ID, PreferenceKeys.LIVE_NIGHT_ALBUM_ID)
            }.map(::stringPreferencesKey).filter { prefs[it] == albumId }
            if (targets.isEmpty()) return@edit
            targets.forEach { prefs.remove(it) }
            val active = if (WallpaperMode.fromString(prefs[stringPreferencesKey(PreferenceKeys.WALLPAPER_MODE)]) == WallpaperMode.LIVE) {
                prefs[stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID)] != null
            } else {
                (prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLED)] == true && prefs[stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID)] != null) ||
                    (prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLED)] == true && prefs[stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID)] != null)
            }
            if (!active) prefs[booleanPreferencesKey(PreferenceKeys.ENABLE_CHANGER)] = false
        }
    }

    /** [dark] null removes the choice, so the app follows the system setting. */
    suspend fun updateDarkMode(dark: Boolean?) {
        dataStore.edit { prefs ->
            val key = booleanPreferencesKey(PreferenceKeys.DARK_MODE)
            if (dark == null) prefs.remove(key) else prefs[key] = dark
        }
    }

    suspend fun updateDynamicTheming(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.DYNAMIC_THEMING)] = enabled
        }
    }

    suspend fun updateAnimate(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.ANIMATE)] = enabled
        }
    }

    suspend fun updateFirstLaunch(isFirstLaunch: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.FIRST_LAUNCH)] = isFirstLaunch
        }
    }

    suspend fun updateEnableChanger(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.ENABLE_CHANGER)] = enabled
        }
    }

    suspend fun clearScheduleSettings() = updateScheduleSettings(ScheduleSettings())

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
