package com.anthonyla.paperize.presentation.screens.scheduling

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.AlbumSummary
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.presentation.screens.home.HomeViewModel
import com.anthonyla.paperize.presentation.screens.wallpaper.components.AlbumSelectionBottomSheet
import com.anthonyla.paperize.presentation.screens.wallpaper.components.InlineSwitchRow
import com.anthonyla.paperize.presentation.screens.wallpaper.components.TimeOfDayDialog
import com.anthonyla.paperize.presentation.screens.wallpaper.components.formatMinuteOfDay
import com.anthonyla.paperize.presentation.theme.AppSpacing
import com.anthonyla.paperize.service.schedule.listenerChannelSettingsIntent

/** The two clock times of the day/night switch. */
private enum class NightTime { NIGHT_START, DAY_START }

/**
 * Scheduling options beyond the interval or set times: screen-off and unlock changes (static
 * mode, plan 6.2), night albums (plan 6.4) and the battery conditions (plan 6.1). Shares the
 * Wallpaper tab's view model, so every edit goes through the same saving and scheduling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulingScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val settings by viewModel.scheduleSettings.collectAsStateWithLifecycle()
    val mode by viewModel.wallpaperMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationHidden by remember { mutableStateOf(isListenerNotificationHidden(context)) }
    // Coming back from Android's notification settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationHidden = isListenerNotificationHidden(context) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduling_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        mode?.let { wallpaperMode ->
            SchedulingOptionsContent(
                albums = albums,
                persistedSettings = settings,
                wallpaperMode = wallpaperMode,
                onUpdateSettings = { viewModel.updateScheduleSettings(it) },
                onSelectNightAlbum = viewModel::selectNightAlbum,
                listenerNotificationHidden = notificationHidden,
                onOpenNotificationSettings = { openListenerChannelSettings(context) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun SchedulingOptionsContent(
    albums: List<AlbumSummary>,
    persistedSettings: ScheduleSettings,
    wallpaperMode: WallpaperMode,
    onUpdateSettings: (ScheduleSettings) -> Unit,
    onSelectNightAlbum: (ScreenType, AlbumSummary?) -> Unit,
    modifier: Modifier = Modifier,
    listenerNotificationHidden: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {}
) {
    // Every edit is saved at once; the local copy keeps the controls responsive until it is back.
    var draft by remember { mutableStateOf(persistedSettings) }
    LaunchedEffect(persistedSettings) { draft = persistedSettings }
    fun update(transform: (ScheduleSettings) -> ScheduleSettings) {
        draft = transform(draft)
        onUpdateSettings(draft)
    }

    var nightAlbumScreen by rememberSaveable { mutableStateOf<ScreenType?>(null) }
    var editingTime by rememberSaveable { mutableStateOf<NightTime?>(null) }
    var showEmptyAlbumWarning by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = AppSpacing.small, end = AppSpacing.small, bottom = AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        if (!draft.enableChanger) {
            Text(
                text = stringResource(R.string.scheduling_paused_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppSpacing.large, vertical = AppSpacing.small)
            )
        }
        if (wallpaperMode == WallpaperMode.STATIC) {
            ScreenEventsCard(
                settings = draft,
                onUpdate = ::update,
                notificationHidden = listenerNotificationHidden,
                onOpenNotificationSettings = onOpenNotificationSettings
            )
        }
        DayNightCard(
            settings = draft,
            albums = albums,
            mode = wallpaperMode,
            onPickNightAlbum = { nightAlbumScreen = it },
            onEditTime = { editingTime = it },
            onTriggerChange = { trigger -> update { it.copy(nightTrigger = trigger) } }
        )
        OptionsCard(title = stringResource(R.string.battery_title)) {
            InlineSwitchRow(
                title = stringResource(R.string.only_while_charging),
                description = stringResource(R.string.only_while_charging_description),
                checked = draft.onlyWhileCharging,
                onCheckedChange = { enabled -> update { it.copy(onlyWhileCharging = enabled) } }
            )
            InlineSwitchRow(
                title = stringResource(R.string.pause_in_battery_saver),
                description = stringResource(R.string.pause_in_battery_saver_description),
                checked = draft.pauseInBatterySaver,
                onCheckedChange = { enabled -> update { it.copy(pauseInBatterySaver = enabled) } }
            )
        }
    }

    nightAlbumScreen?.let { screen ->
        val selectedId = nightAlbumId(draft, screen)
        AlbumSelectionBottomSheet(
            albums = albums,
            selectedAlbumId = selectedId,
            onAlbumSelect = { album ->
                when {
                    album.id == selectedId -> onSelectNightAlbum(screen, null)
                    album.wallpaperCount == 0 -> showEmptyAlbumWarning = true
                    else -> onSelectNightAlbum(screen, album)
                }
            },
            onDismiss = { nightAlbumScreen = null }
        )
    }

    editingTime?.let { time ->
        TimeOfDayDialog(
            title = stringResource(if (time == NightTime.NIGHT_START) R.string.night_starts else R.string.day_starts),
            initialMinutes = if (time == NightTime.NIGHT_START) draft.nightStartMinutes else draft.dayStartMinutes,
            onConfirm = { minutes ->
                editingTime = null
                update {
                    if (time == NightTime.NIGHT_START) it.copy(nightStartMinutes = minutes) else it.copy(dayStartMinutes = minutes)
                }
            },
            onDismiss = { editingTime = null }
        )
    }

    if (showEmptyAlbumWarning) {
        AlertDialog(
            onDismissRequest = { showEmptyAlbumWarning = false },
            title = { Text(stringResource(R.string.empty_album)) },
            text = { Text(stringResource(R.string.empty_album_message)) },
            confirmButton = {
                TextButton(onClick = { showEmptyAlbumWarning = false }) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

@Composable
private fun ScreenEventsCard(
    settings: ScheduleSettings,
    onUpdate: ((ScheduleSettings) -> ScheduleSettings) -> Unit,
    notificationHidden: Boolean,
    onOpenNotificationSettings: () -> Unit
) {
    // With one screen rotating, both events change that screen; the choice only matters for two.
    val bothRotate = settings.rotatingStaticScreens().size == 2
    OptionsCard(title = stringResource(R.string.screen_events_title)) {
        InlineSwitchRow(
            title = stringResource(R.string.change_on_screen_off),
            description = stringResource(R.string.change_on_screen_off_static_description),
            checked = settings.changeOnScreenOff,
            onCheckedChange = { enabled -> onUpdate { it.copy(changeOnScreenOff = enabled) } }
        )
        if (settings.changeOnScreenOff && bothRotate) {
            TargetChoice(selected = settings.screenOffTarget) { target -> onUpdate { it.copy(screenOffTarget = target) } }
        }
        InlineSwitchRow(
            title = stringResource(R.string.change_on_unlock),
            description = stringResource(R.string.change_on_unlock_description),
            checked = settings.changeOnUnlock,
            onCheckedChange = { enabled -> onUpdate { it.copy(changeOnUnlock = enabled) } }
        )
        if (settings.changeOnUnlock && bothRotate) {
            TargetChoice(selected = settings.unlockTarget) { target -> onUpdate { it.copy(unlockTarget = target) } }
        }
        if (settings.changeOnScreenOff || settings.changeOnUnlock) {
            HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.extraSmall))
            GapChoice(selected = settings.triggerGapMinutes) { minutes -> onUpdate { it.copy(triggerGapMinutes = minutes) } }
            HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.extraSmall))
            Text(
                text = stringResource(if (notificationHidden) R.string.listener_notice_hidden else R.string.listener_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(if (notificationHidden) R.string.notification_settings else R.string.hide_notification))
            }
        }
    }
}

/** Lock / Home / Both, in the order of the screen cards on the Wallpaper tab. */
@Composable
private fun TargetChoice(selected: ScreenType, onSelect: (ScreenType) -> Unit) {
    val options = listOf(
        ScreenType.LOCK to stringResource(R.string.lock),
        ScreenType.HOME to stringResource(R.string.home),
        ScreenType.BOTH to stringResource(R.string.trigger_target_both)
    )
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)) {
        Text(
            text = stringResource(R.string.trigger_target_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SegmentedChoice(options, selected, onSelect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GapChoice(selected: Int, onSelect: (Int) -> Unit) {
    val options = Constants.TRIGGER_GAP_STEPS_MINUTES.map { minutes ->
        minutes to when {
            minutes == 0 -> stringResource(R.string.trigger_gap_none)
            minutes < Constants.MINUTES_PER_HOUR -> stringResource(R.string.trigger_gap_minutes, minutes)
            else -> stringResource(R.string.trigger_gap_hours, minutes / Constants.MINUTES_PER_HOUR)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)) {
        Text(
            text = stringResource(R.string.trigger_gap_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.trigger_gap_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // A stored value that is no longer a choice shows as the nearest one.
        val shown = options.minBy { (minutes, _) -> kotlin.math.abs(minutes - selected) }
        // A dropdown rather than buttons: five buttons cut their labels short on a phone.
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = shown.second,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (minutes, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onSelect(minutes)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun DayNightCard(
    settings: ScheduleSettings,
    albums: List<AlbumSummary>,
    mode: WallpaperMode,
    onPickNightAlbum: (ScreenType) -> Unit,
    onEditTime: (NightTime) -> Unit,
    onTriggerChange: (NightTrigger) -> Unit
) {
    val context = LocalContext.current
    OptionsCard(title = stringResource(R.string.day_night_title)) {
        Text(
            text = stringResource(R.string.day_night_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val screens = when (mode) {
            WallpaperMode.LIVE -> listOf(ScreenType.LIVE)
            WallpaperMode.STATIC -> listOfNotNull(
                ScreenType.LOCK.takeIf { settings.lockEnabled },
                ScreenType.HOME.takeIf { settings.homeEnabled }
            )
        }
        if (screens.isEmpty()) {
            Text(
                text = stringResource(R.string.day_night_needs_a_screen),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        screens.forEach { screen ->
            NightAlbumRow(
                label = stringResource(
                    when (screen) {
                        ScreenType.HOME -> R.string.night_album_home
                        ScreenType.LOCK -> R.string.night_album_lock
                        else -> R.string.night_album_live
                    }
                ),
                hasDayAlbum = dayAlbumId(settings, screen) != null,
                nightAlbumName = nightAlbumId(settings, screen)?.let { id -> albums.find { it.id == id }?.name },
                onClick = { onPickNightAlbum(screen) }
            )
        }
        if (settings.screensWithNightAlbum(mode).isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.extraSmall))
            Text(
                text = stringResource(R.string.night_trigger_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            SegmentedChoice(
                options = listOf(
                    NightTrigger.CLOCK to stringResource(R.string.night_trigger_clock),
                    NightTrigger.DARK_MODE to stringResource(R.string.night_trigger_dark_mode)
                ),
                selected = settings.nightTrigger,
                onSelect = onTriggerChange
            )
            if (settings.nightTrigger == NightTrigger.CLOCK) {
                TimeRow(stringResource(R.string.night_starts), formatMinuteOfDay(context, settings.nightStartMinutes)) {
                    onEditTime(NightTime.NIGHT_START)
                }
                TimeRow(stringResource(R.string.day_starts), formatMinuteOfDay(context, settings.dayStartMinutes)) {
                    onEditTime(NightTime.DAY_START)
                }
            }
            Text(
                text = nightStatus(context, settings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Which albums are in use now, and until when. */
@Composable
private fun nightStatus(context: Context, settings: ScheduleSettings): String = when {
    settings.nightTrigger == NightTrigger.DARK_MODE ->
        stringResource(if (settings.nightActive) R.string.night_status_night_dark else R.string.night_status_day_dark)
    settings.nightStartMinutes == settings.dayStartMinutes -> stringResource(R.string.night_status_same_times)
    settings.nightActive -> stringResource(R.string.night_status_night_until, formatMinuteOfDay(context, settings.dayStartMinutes))
    else -> stringResource(R.string.night_status_day_until, formatMinuteOfDay(context, settings.nightStartMinutes))
}

/** "Home at Night: Same as day ›"; needs the screen's daytime album first. */
@Composable
private fun NightAlbumRow(label: String, hasDayAlbum: Boolean, nightAlbumName: String?, onClick: () -> Unit) {
    val value = when {
        !hasDayAlbum -> stringResource(R.string.pick_day_album_first)
        else -> nightAlbumName ?: stringResource(R.string.same_as_day)
    }
    val contentColor = if (hasDayAlbum) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasDayAlbum, role = Role.Button, onClick = onClick)
            .padding(vertical = AppSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = contentColor)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasDayAlbum && nightAlbumName != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hasDayAlbum) 1f else 0.38f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (hasDayAlbum) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "Night starts      19:00"; tapping it opens the time picker. */
@Composable
private fun TimeRow(label: String, time: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AppSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(time, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onSelect(value) },
                selected = value == selected,
                icon = {}
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OptionsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            content()
        }
    }
}

private fun dayAlbumId(settings: ScheduleSettings, screen: ScreenType): String? = when (screen) {
    ScreenType.HOME -> settings.homeAlbumId
    ScreenType.LOCK -> settings.lockAlbumId
    ScreenType.LIVE -> settings.liveAlbumId
    ScreenType.BOTH -> null
}

private fun nightAlbumId(settings: ScheduleSettings, screen: ScreenType): String? = when (screen) {
    ScreenType.HOME -> settings.homeNightAlbumId
    ScreenType.LOCK -> settings.lockNightAlbumId
    ScreenType.LIVE -> settings.liveNightAlbumId
    ScreenType.BOTH -> null
}

/** Whether the listener's notification channel, or all of Paperize's notifications, are turned off. */
private fun isListenerNotificationHidden(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return true
    return manager.getNotificationChannelCompat(Constants.LISTENER_CHANNEL_ID)?.importance ==
        NotificationManagerCompat.IMPORTANCE_NONE
}

private fun openListenerChannelSettings(context: Context) {
    try {
        context.startActivity(listenerChannelSettingsIntent(context))
    } catch (_: ActivityNotFoundException) {
    }
}
