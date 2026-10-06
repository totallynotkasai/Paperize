package com.anthonyla.paperize.presentation.screens.wallpaper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.AlbumSummary
import com.anthonyla.paperize.domain.model.AppSettings
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.presentation.common.components.SettingSwitchItem
import com.anthonyla.paperize.presentation.screens.wallpaper.components.AlbumSelectionBottomSheet
import com.anthonyla.paperize.presentation.screens.wallpaper.components.AutoPanSetting
import com.anthonyla.paperize.presentation.screens.wallpaper.components.CurrentLiveWallpaperPreview
import com.anthonyla.paperize.presentation.screens.wallpaper.components.CurrentWallpaperPreview
import com.anthonyla.paperize.presentation.screens.wallpaper.components.LiveWallpaperBanner
import com.anthonyla.paperize.presentation.screens.wallpaper.components.SettingSwitchWithSlider
import com.anthonyla.paperize.presentation.screens.wallpaper.components.disabledUnless
import com.anthonyla.paperize.presentation.screens.wallpaper.components.TimeIntervalPicker
import com.anthonyla.paperize.presentation.theme.AppSpacing

private enum class AlbumSelectionContext {
    HOME, LOCK, LIVE
}

@Composable
fun WallpaperScreen(
    albums: List<AlbumSummary>,
    persistedScheduleSettings: ScheduleSettings,
    appSettings: AppSettings,
    wallpaperMode: WallpaperMode,
    onToggleChanger: (Boolean) -> Unit,
    onSelectHomeAlbum: (AlbumSummary?) -> Unit,
    onSelectLockAlbum: (AlbumSummary?) -> Unit,
    onSelectLiveAlbum: (AlbumSummary?) -> Unit,
    onUpdateScheduleSettings: (ScheduleSettings) -> Unit,
    onUpdateSettingsDeferRender: (ScheduleSettings) -> Unit,
    onChangeWallpaperNow: () -> Unit,
    homeWallpaperUri: String?,
    lockWallpaperUri: String?,
    modifier: Modifier = Modifier,
    liveWallpaperUri: String? = null,
    liveWallpaperNotSet: Boolean = false,
    changeInProgress: Boolean = false
) {
    var albumSelectionContext by rememberSaveable { mutableStateOf<AlbumSelectionContext?>(null) }
    var showEmptyAlbumWarning by rememberSaveable { mutableStateOf(false) }
    var scheduleSettings by remember { mutableStateOf(persistedScheduleSettings) }

    // Every edit is saved at once; this local copy only keeps the controls responsive until the
    // saved value comes back.
    LaunchedEffect(persistedScheduleSettings) {
        scheduleSettings = persistedScheduleSettings
    }

    /** Slider levels: saved at once, while re-rendering the static wallpaper waits for more edits. */
    fun updateSettingsDeferRender(newSettings: ScheduleSettings) {
        scheduleSettings = newSettings
        onUpdateSettingsDeferRender(newSettings)
    }

    fun updateSettingsImmediate(newSettings: ScheduleSettings) {
        scheduleSettings = newSettings
        onUpdateScheduleSettings(newSettings)
    }

    val homeEnabled = scheduleSettings.homeEnabled
    val lockEnabled = scheduleSettings.lockEnabled
    // Static effects belong to a screen; with neither turned on there is nothing to apply them to.
    val effectsEnabled = wallpaperMode == WallpaperMode.LIVE || homeEnabled || lockEnabled

    val primaryEffects = when {
        wallpaperMode == WallpaperMode.LIVE -> scheduleSettings.liveEffects
        homeEnabled -> scheduleSettings.homeEffects
        else -> scheduleSettings.lockEffects
    }
    val bothEnabled = wallpaperMode == WallpaperMode.STATIC && homeEnabled && lockEnabled

    fun updateEffects(
        home: (WallpaperEffects) -> WallpaperEffects,
        lock: (WallpaperEffects) -> WallpaperEffects = home,
        debounced: Boolean = false
    ) {
        val updated = if (wallpaperMode == WallpaperMode.LIVE) {
            scheduleSettings.copy(liveEffects = home(scheduleSettings.liveEffects))
        } else {
            scheduleSettings.copy(
                homeEffects = if (homeEnabled) home(scheduleSettings.homeEffects) else scheduleSettings.homeEffects,
                lockEffects = if (lockEnabled) lock(scheduleSettings.lockEffects) else scheduleSettings.lockEffects
            )
        }
        if (debounced) updateSettingsDeferRender(updated) else updateSettingsImmediate(updated)
    }

    val scalingOptions = listOf(
        ScalingType.FILL to stringResource(R.string.fill),
        ScalingType.FIT to stringResource(R.string.fit),
        ScalingType.STRETCH to stringResource(R.string.stretch),
        ScalingType.NONE to stringResource(R.string.none)
    )
    // Albums shown right now that use their own effects instead of these (plan 5.3).
    val albumsWithOwnEffects = remember(albums, scheduleSettings, wallpaperMode) {
        val shown = if (wallpaperMode == WallpaperMode.LIVE) listOfNotNull(scheduleSettings.liveAlbumId)
            else listOfNotNull(scheduleSettings.albumFor(ScreenType.HOME), scheduleSettings.albumFor(ScreenType.LOCK))
        shown.distinct().mapNotNull { id -> albums.find { it.id == id && it.hasCustomEffects }?.name }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = AppSpacing.small, end = AppSpacing.small, bottom = AppSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        if (wallpaperMode == WallpaperMode.STATIC) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                ScreenToggleCard(
                    title = stringResource(R.string.lock), icon = Icons.Default.Lock, enabled = lockEnabled,
                    onClick = { updateSettingsImmediate(scheduleSettings.copy(lockEnabled = !lockEnabled)) },
                    modifier = Modifier.weight(1f)
                )
                ScreenToggleCard(
                    title = stringResource(R.string.home), icon = Icons.Default.Home, enabled = homeEnabled,
                    onClick = { updateSettingsImmediate(scheduleSettings.copy(homeEnabled = !homeEnabled)) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (lockEnabled) AlbumSelector(
                albumId = scheduleSettings.lockAlbumId, albums = albums,
                label = stringResource(R.string.lock_album_label),
                onClick = { albumSelectionContext = AlbumSelectionContext.LOCK }
            )
            if (homeEnabled) AlbumSelector(
                albumId = scheduleSettings.homeAlbumId, albums = albums,
                label = stringResource(R.string.home_album_label),
                onClick = { albumSelectionContext = AlbumSelectionContext.HOME }
            )
        } else {
            if (liveWallpaperNotSet) LiveWallpaperBanner()
            AlbumSelector(
                albumId = scheduleSettings.liveAlbumId, albums = albums,
                label = stringResource(R.string.currently_selected_album),
                onClick = { albumSelectionContext = AlbumSelectionContext.LIVE }
            )
        }
        if (wallpaperMode == WallpaperMode.STATIC && scheduleSettings.enableChanger && homeEnabled && lockEnabled) {
            SettingSwitchItem(
                title = stringResource(R.string.individual_scheduling),
                description = stringResource(R.string.show_interval_sliders),
                checked = scheduleSettings.separateSchedules,
                onCheckedChange = { enabled ->
                    updateSettingsImmediate(scheduleSettings.copy(separateSchedules = enabled))
                }
            )
        }

        val hasAlbumSelected = scheduleSettings.activeScreens(wallpaperMode).isNotEmpty()
        val allRequiredAlbumsSelected = scheduleSettings.hasRequiredAlbums(wallpaperMode)

        if (allRequiredAlbumsSelected) {
            SettingSwitchItem(
                title = stringResource(R.string.wallpaper_changer),
                description = stringResource(R.string.wallpaper_changer_description),
                checked = scheduleSettings.enableChanger,
                onCheckedChange = onToggleChanger
            )
        }
        if (hasAlbumSelected) {
            if (wallpaperMode == WallpaperMode.STATIC) {
                if (!scheduleSettings.separateSchedules || !homeEnabled || !lockEnabled) {
                    TimeIntervalPicker(
                        title = stringResource(R.string.interval_text),
                        minutes = scheduleSettings.homeIntervalMinutes,
                        onMinutesChange = { minutes ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    homeIntervalMinutes = minutes,
                                    lockIntervalMinutes = minutes
                                )
                            )
                        }
                    )
                } else {
                    TimeIntervalPicker(
                        title = stringResource(R.string.lock_screen_btn),
                        minutes = scheduleSettings.lockIntervalMinutes,
                        onMinutesChange = { minutes ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(lockIntervalMinutes = minutes)
                            )
                        }
                    )
                    TimeIntervalPicker(
                        title = stringResource(R.string.home_screen_btn),
                        minutes = scheduleSettings.homeIntervalMinutes,
                        onMinutesChange = { minutes ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(homeIntervalMinutes = minutes)
                            )
                        }
                    )
                }
            } else {
                TimeIntervalPicker(
                    title = stringResource(R.string.interval_text),
                    minutes = scheduleSettings.liveIntervalMinutes,
                    minimumMinutes = Constants.MIN_LIVE_INTERVAL_MINUTES,
                    onMinutesChange = { minutes ->
                        updateSettingsImmediate(
                            scheduleSettings.copy(liveIntervalMinutes = minutes)
                        )
                    }
                )
                Text(
                    text = stringResource(R.string.live_short_interval_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.large)
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))

        if (wallpaperMode == WallpaperMode.STATIC) {
            CurrentWallpaperPreview(
                homeWallpaperUri = homeWallpaperUri,
                lockWallpaperUri = lockWallpaperUri,
                animate = appSettings.animate
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))
        } else if (scheduleSettings.liveAlbumId != null) {
            CurrentLiveWallpaperPreview(
                wallpaperUri = liveWallpaperUri,
                animate = appSettings.animate
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))
        }
        Text(
            text = stringResource(R.string.wallpaper_effects_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = AppSpacing.large, vertical = AppSpacing.small),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!effectsEnabled) {
            Text(
                text = stringResource(R.string.effects_need_a_screen),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppSpacing.large)
            )
        }
        albumsWithOwnEffects.forEach { name ->
            Text(
                text = stringResource(R.string.album_uses_own_effects, name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppSpacing.large)
            )
        }
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                Text(
                    text = stringResource(R.string.scaling),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.disabledUnless(effectsEnabled),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                when {
                    wallpaperMode == WallpaperMode.LIVE -> ScalingChoices(
                        options = scalingOptions,
                        selected = scheduleSettings.liveScalingType,
                        enabled = true,
                        onSelect = { updateSettingsImmediate(scheduleSettings.copy(liveScalingType = it)) }
                    )
                    // Each static screen keeps its own scaling (plan 5.4); with both on, each gets a row.
                    bothEnabled -> {
                        ScreenLabel(stringResource(R.string.home))
                        ScalingChoices(
                            options = scalingOptions,
                            selected = scheduleSettings.homeScalingType,
                            enabled = true,
                            onSelect = { updateSettingsImmediate(scheduleSettings.copy(homeScalingType = it)) }
                        )
                        ScreenLabel(stringResource(R.string.lock))
                        ScalingChoices(
                            options = scalingOptions,
                            selected = scheduleSettings.lockScalingType,
                            enabled = true,
                            onSelect = { updateSettingsImmediate(scheduleSettings.copy(lockScalingType = it)) }
                        )
                    }
                    lockEnabled -> ScalingChoices(
                        options = scalingOptions,
                        selected = scheduleSettings.lockScalingType,
                        enabled = true,
                        onSelect = { updateSettingsImmediate(scheduleSettings.copy(lockScalingType = it)) }
                    )
                    else -> ScalingChoices(
                        options = scalingOptions,
                        selected = scheduleSettings.homeScalingType,
                        enabled = effectsEnabled,
                        onSelect = { updateSettingsImmediate(scheduleSettings.copy(homeScalingType = it)) }
                    )
                }
                // Launchers scroll only the home screen, and only Fill keeps the image's overflow.
                if (showsHorizontalScrolling(wallpaperMode, scheduleSettings)) {
                    InlineSwitchRow(
                        title = stringResource(R.string.horizontal_wallpaper_scrolling),
                        description = stringResource(R.string.horizontal_wallpaper_scrolling_description),
                        checked = scheduleSettings.homeScrollingEnabled,
                        onCheckedChange = { enabled ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(homeScrollingEnabled = enabled)
                            )
                        }
                    )
                }
            }
        }

        if (hasAlbumSelected) {
            Button(
                onClick = onChangeWallpaperNow,
                enabled = !changeInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        PaddingValues(
                            horizontal = AppSpacing.small,
                            vertical = AppSpacing.extraSmall
                        )
                    )
            ) {
                Text(text = stringResource(if (changeInProgress) R.string.changing_wallpaper else R.string.change_wallpaper_now))
            }
        }
        SettingSwitchItem(
            title = stringResource(R.string.shuffle),
            description = stringResource(R.string.randomly_shuffle_the_wallpapers),
            checked = scheduleSettings.shuffleEnabled,
            onCheckedChange = { enabled ->
                updateSettingsImmediate(scheduleSettings.copy(shuffleEnabled = enabled))
            }
        )

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Text(
                    text = stringResource(R.string.visual_effects),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.disabledUnless(effectsEnabled),
                    modifier = Modifier.padding(bottom = AppSpacing.small),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                SettingSwitchWithSlider(
                    title = R.string.change_brightness,
                    description = R.string.change_the_image_brightness,
                    checked = primaryEffects.enableDarken,
                    onCheckedChange = { enabled -> updateEffects({ it.copy(enableDarken = enabled) }) },
                    homeChecked = scheduleSettings.homeEffects.enableDarken,
                    lockChecked = scheduleSettings.lockEffects.enableDarken,
                    onHomeCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(homeEffects = scheduleSettings.homeEffects.copy(enableDarken = enabled)))
                    },
                    onLockCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(lockEffects = scheduleSettings.lockEffects.copy(enableDarken = enabled)))
                    },
                    bothEnabled = bothEnabled,
                    enabled = effectsEnabled,
                    homePercentage = primaryEffects.darkenPercentage,
                    lockPercentage = scheduleSettings.lockEffects.darkenPercentage,
                    onPercentageChange = { home, lock ->
                        updateEffects({ it.copy(darkenPercentage = home) }, { it.copy(darkenPercentage = lock) }, debounced = true)
                    }
                )

                SettingSwitchWithSlider(
                    title = R.string.change_blur,
                    description = R.string.add_blur_to_the_image,
                    checked = primaryEffects.enableBlur,
                    onCheckedChange = { enabled -> updateEffects({ it.copy(enableBlur = enabled) }) },
                    homeChecked = scheduleSettings.homeEffects.enableBlur,
                    lockChecked = scheduleSettings.lockEffects.enableBlur,
                    onHomeCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(homeEffects = scheduleSettings.homeEffects.copy(enableBlur = enabled)))
                    },
                    onLockCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(lockEffects = scheduleSettings.lockEffects.copy(enableBlur = enabled)))
                    },
                    bothEnabled = bothEnabled,
                    enabled = effectsEnabled,
                    homePercentage = primaryEffects.blurPercentage,
                    lockPercentage = scheduleSettings.lockEffects.blurPercentage,
                    onPercentageChange = { home, lock ->
                        updateEffects({ it.copy(blurPercentage = home) }, { it.copy(blurPercentage = lock) }, debounced = true)
                    }
                )

                SettingSwitchWithSlider(
                    title = R.string.change_vignette,
                    description = R.string.darken_the_edges_of_the_image,
                    checked = primaryEffects.enableVignette,
                    onCheckedChange = { enabled -> updateEffects({ it.copy(enableVignette = enabled) }) },
                    homeChecked = scheduleSettings.homeEffects.enableVignette,
                    lockChecked = scheduleSettings.lockEffects.enableVignette,
                    onHomeCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(homeEffects = scheduleSettings.homeEffects.copy(enableVignette = enabled)))
                    },
                    onLockCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(lockEffects = scheduleSettings.lockEffects.copy(enableVignette = enabled)))
                    },
                    bothEnabled = bothEnabled,
                    enabled = effectsEnabled,
                    homePercentage = primaryEffects.vignettePercentage,
                    lockPercentage = scheduleSettings.lockEffects.vignettePercentage,
                    onPercentageChange = { home, lock ->
                        updateEffects({ it.copy(vignettePercentage = home) }, { it.copy(vignettePercentage = lock) }, debounced = true)
                    }
                )

                SettingSwitchWithSlider(
                    title = R.string.gray_filter,
                    description = R.string.make_the_colors_grayscale,
                    checked = primaryEffects.enableGrayscale,
                    onCheckedChange = { enabled -> updateEffects({ it.copy(enableGrayscale = enabled) }) },
                    homeChecked = scheduleSettings.homeEffects.enableGrayscale,
                    lockChecked = scheduleSettings.lockEffects.enableGrayscale,
                    onHomeCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(homeEffects = scheduleSettings.homeEffects.copy(enableGrayscale = enabled)))
                    },
                    onLockCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(lockEffects = scheduleSettings.lockEffects.copy(enableGrayscale = enabled)))
                    },
                    bothEnabled = bothEnabled,
                    enabled = effectsEnabled,
                    homePercentage = primaryEffects.grayscalePercentage,
                    lockPercentage = scheduleSettings.lockEffects.grayscalePercentage,
                    onPercentageChange = { home, lock ->
                        updateEffects({ it.copy(grayscalePercentage = home) }, { it.copy(grayscalePercentage = lock) }, debounced = true)
                    }
                )
                SettingSwitchItem(
                    title = stringResource(R.string.adaptive_brightness),
                    description = stringResource(R.string.adjust_brightness_based_on_mode),
                    checked = scheduleSettings.adaptiveBrightness,
                    onCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(adaptiveBrightness = enabled))
                    },
                    enabled = effectsEnabled
                )
            }
        }
        if (wallpaperMode == WallpaperMode.LIVE) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    Text(
                        text = stringResource(R.string.interactive_effects),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SettingSwitchItem(
                        title = stringResource(R.string.double_tap_to_change),
                        description = stringResource(R.string.double_tap_wallpaper_to_change_it),
                        checked = scheduleSettings.liveEffects.enableDoubleTap,
                        onCheckedChange = { enabled ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableDoubleTap = enabled)
                                )
                            )
                        }
                    )
                    SettingSwitchItem(
                        title = stringResource(R.string.change_on_screen_off),
                        description = stringResource(R.string.change_wallpaper_when_screen_turns_off),
                        checked = scheduleSettings.liveEffects.enableChangeOnScreenOff,
                        onCheckedChange = { enabled ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableChangeOnScreenOff = enabled)
                                )
                            )
                        }
                    )
                    SettingSwitchWithSlider(
                        title = R.string.parallax_effect,
                        description = R.string.wallpaper_moves_with_screen_scroll,
                        checked = scheduleSettings.liveEffects.enableParallax,
                        onCheckedChange = { enabled ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableParallax = enabled)
                                )
                            )
                        },
                        bothEnabled = false, // Never separate in Live Mode
                        homePercentage = scheduleSettings.liveEffects.parallaxIntensity,
                        lockPercentage = 0,
                        onPercentageChange = { homePercent, _ ->
                            updateSettingsDeferRender(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(parallaxIntensity = homePercent)
                                )
                            )
                        }
                    )
                    AutoPanSetting(
                        checked = scheduleSettings.liveEffects.enableAutoPan,
                        sweepSeconds = scheduleSettings.liveEffects.autoPanSweepSeconds,
                        available = scheduleSettings.liveScalingType.canCutOff,
                        onCheckedChange = { enabled ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableAutoPan = enabled)
                                )
                            )
                        },
                        onSweepSecondsChange = { seconds ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(autoPanSweepSeconds = seconds)
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    albumSelectionContext?.let { selection ->
        val selectedId = when (selection) {
            AlbumSelectionContext.HOME -> scheduleSettings.homeAlbumId
            AlbumSelectionContext.LOCK -> scheduleSettings.lockAlbumId
            AlbumSelectionContext.LIVE -> scheduleSettings.liveAlbumId
        }
        val selectAlbum = when (selection) {
            AlbumSelectionContext.HOME -> onSelectHomeAlbum
            AlbumSelectionContext.LOCK -> onSelectLockAlbum
            AlbumSelectionContext.LIVE -> onSelectLiveAlbum
        }
        AlbumSelectionBottomSheet(
            albums = albums,
            selectedAlbumId = selectedId,
            onAlbumSelect = { album ->
                when {
                    album.id == selectedId -> selectAlbum(null)
                    album.wallpaperCount == 0 -> showEmptyAlbumWarning = true
                    else -> selectAlbum(album)
                }
            },
            onDismiss = { albumSelectionContext = null }
        )
    }

    if (showEmptyAlbumWarning) {
        AlertDialog(
            onDismissRequest = { showEmptyAlbumWarning = false },
            title = {
                Text(
                    text = stringResource(R.string.empty_album),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.empty_album_message),
                    maxLines = Constants.DIALOG_MESSAGE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                TextButton(onClick = { showEmptyAlbumWarning = false }) {
                    Text(
                        text = stringResource(R.string.ok),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}

/**
 * Horizontal scrolling only does something for the home screen with Fill scaling (see
 * usesLauncherManagedScrolling), so the switch is offered only then.
 */
internal fun showsHorizontalScrolling(mode: WallpaperMode, settings: ScheduleSettings): Boolean =
    mode == WallpaperMode.STATIC && settings.homeEnabled && settings.homeScalingType == ScalingType.FILL

/** Fill / Fit / Stretch / None for one screen. */
@Composable
private fun ScalingChoices(
    options: List<Pair<ScalingType, String>>,
    selected: ScalingType,
    enabled: Boolean,
    onSelect: (ScalingType) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (scalingType, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onSelect(scalingType) },
                selected = scalingType == selected,
                enabled = enabled
            ) {
                Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Names the screen the scaling row below it belongs to. */
@Composable
private fun ScreenLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() }
    )
}

/** A switch row inside a card that already has its own padding; the whole row toggles. */
@Composable
private fun InlineSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = AppSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ScreenToggleCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Announced as a switch ("Lock, on"), so the visible Enabled/Disabled line isn't read twice.
    Card(
        modifier = modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(enabled)
        },
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            val contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(icon, contentDescription = null, tint = contentColor)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (enabled) contentColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(if (enabled) R.string.enabled else R.string.disabled),
                style = MaterialTheme.typography.bodySmall, color = contentColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun AlbumSelector(albumId: String?, albums: List<AlbumSummary>, label: String, onClick: () -> Unit) {
    val album = albums.find { it.id == albumId }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(AppSpacing.large), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(album?.name ?: stringResource(if (albumId == null) R.string.no_album_selected else R.string.loading_placeholder),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
