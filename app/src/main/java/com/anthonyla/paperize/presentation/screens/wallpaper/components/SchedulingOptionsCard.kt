package com.anthonyla.paperize.presentation.screens.wallpaper.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.presentation.theme.AppSpacing

/** What the scheduling options card lists as turned on, in the order the screen shows them. */
internal fun schedulingOptionsSummary(settings: ScheduleSettings, mode: WallpaperMode): List<Int> = buildList {
    if (mode == WallpaperMode.STATIC) {
        if (settings.changeOnScreenOff) add(R.string.summary_screen_off)
        if (settings.changeOnUnlock) add(R.string.summary_unlock)
    }
    if (settings.screensWithNightAlbum(mode).isNotEmpty()) add(R.string.summary_night_albums)
    if (settings.onlyWhileCharging) add(R.string.summary_while_charging)
    if (settings.pauseInBatterySaver) add(R.string.summary_battery_saver)
}

/**
 * Opens the scheduling options (plan 6.1, 6.2 and the night albums of 6.4); says which are on, or
 * what is there while none are.
 */
@Composable
fun SchedulingOptionsCard(
    settings: ScheduleSettings,
    mode: WallpaperMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = schedulingOptionsSummary(settings, mode).map { stringResource(it) }
    val separator = stringResource(R.string.summary_separator)
    val description = summary.joinToString(separator).ifEmpty {
        stringResource(if (mode == WallpaperMode.STATIC) R.string.scheduling_options_none else R.string.scheduling_options_none_live)
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(AppSpacing.large), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.scheduling_options_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summary.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
