package com.anthonyla.paperize.presentation.screens.wallpaper.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.presentation.theme.AppSpacing
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Live auto-pan: a switch, and while it is on a speed slider from slow (left) to fast (right).
 * [available] is false under Fit or Stretch, which never cut anything off; the switch is then
 * greyed out and says which scaling it needs. The speed is saved once the slider is let go.
 */
@Composable
fun AutoPanSetting(
    checked: Boolean,
    sweepSeconds: Int,
    available: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onSweepSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val stops = Constants.AUTO_PAN_SWEEP_STEPS_SECONDS
    var position by remember(sweepSeconds) { mutableFloatStateOf(autoPanStopIndex(sweepSeconds).toFloat()) }
    val shownSeconds = stops[position.roundToInt().coerceIn(stops.indices)]

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall)),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, enabled = available, role = Role.Switch, onValueChange = onCheckedChange),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.large),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)
                ) {
                    Text(
                        text = stringResource(R.string.auto_pan),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.disabledUnless(available),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(if (available) R.string.auto_pan_description else R.string.auto_pan_needs_fill_or_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = checked, onCheckedChange = null, enabled = available)
            }

            if (checked && available) {
                val speedLabel = sweepLabel(shownSeconds)
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = stringResource(R.string.auto_pan_speed),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = speedLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = AppSpacing.medium)
                        )
                    }
                    Slider(
                        value = position,
                        onValueChange = { position = it },
                        onValueChangeFinished = { onSweepSecondsChange(stops[position.roundToInt().coerceIn(stops.indices)]) },
                        valueRange = 0f..stops.lastIndex.toFloat(),
                        steps = stops.size - 2,
                        // Read out as "1 minute per sweep", not as a percentage of the track.
                        modifier = Modifier.semantics { stateDescription = speedLabel }
                    )
                }
            }
        }
    }
}

@Composable
private fun sweepLabel(seconds: Int): String =
    if (seconds % 60 == 0) {
        pluralStringResource(R.plurals.auto_pan_sweep_minutes, seconds / 60, seconds / 60)
    } else {
        pluralStringResource(R.plurals.auto_pan_sweep_seconds, seconds, seconds)
    }

/** The slider stop closest to [sweepSeconds], so a value from elsewhere still lands on a stop. */
internal fun autoPanStopIndex(sweepSeconds: Int): Int =
    Constants.AUTO_PAN_SWEEP_STEPS_SECONDS.indices.minBy { abs(Constants.AUTO_PAN_SWEEP_STEPS_SECONDS[it] - sweepSeconds) }
