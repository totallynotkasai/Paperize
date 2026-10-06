package com.anthonyla.paperize.presentation.screens.wallpaper.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.presentation.theme.AppSpacing
import java.util.Calendar

/** A minute of the day (e.g. 1140) in the phone's own 12- or 24-hour style ("19:00", "7:00 PM"). */
fun formatMinuteOfDay(context: Context, minuteOfDay: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / Constants.MINUTES_PER_HOUR)
        set(Calendar.MINUTE, minuteOfDay % Constants.MINUTES_PER_HOUR)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

/** "Interval" or "Set times" (plan 6.4), above the interval boxes or the list of times. */
@Composable
fun ScheduleTypeChoice(
    selected: ScheduleType,
    onSelect: (ScheduleType) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        ScheduleType.INTERVAL to stringResource(R.string.schedule_type_interval),
        ScheduleType.TIMES to stringResource(R.string.schedule_type_times)
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.large, vertical = AppSpacing.extraSmall),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        Text(
            text = stringResource(R.string.schedule_type_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = { onSelect(type) },
                    selected = type == selected
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * The set times as chips: tap one to change it, its cross to remove it (one always stays), or
 * "Add time" for another, up to [Constants.MAX_CHANGE_TIMES].
 */
@Composable
fun ChangeTimesCard(
    times: List<Int>,
    onTimesChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // The time being edited: its index, or -1 for a new one; null while no picker is open.
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
            .fillMaxWidth()
            .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text(
                text = stringResource(R.string.change_times_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W500
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)
            ) {
                times.forEachIndexed { index, minutes ->
                    val label = formatMinuteOfDay(context, minutes)
                    InputChip(
                        selected = false,
                        onClick = { editing = index },
                        label = { Text(label) },
                        trailingIcon = if (times.size > 1) {
                            {
                                IconButton(
                                    onClick = { onTimesChange(times.filterIndexed { i, _ -> i != index }) },
                                    modifier = Modifier.size(InputChipDefaults.AvatarSize)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.remove_time, label),
                                        modifier = Modifier.size(InputChipDefaults.IconSize)
                                    )
                                }
                            }
                        } else null
                    )
                }
                if (times.size < Constants.MAX_CHANGE_TIMES) {
                    AssistChip(
                        onClick = { editing = -1 },
                        label = { Text(stringResource(R.string.add_time)) },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                        }
                    )
                }
            }
            Text(
                text = stringResource(R.string.change_times_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    editing?.let { index ->
        TimeOfDayDialog(
            title = stringResource(if (index < 0) R.string.add_time else R.string.change_time),
            initialMinutes = times.getOrNull(index) ?: Constants.DEFAULT_CHANGE_TIMES.first(),
            onConfirm = { minutes ->
                editing = null
                onTimesChange(if (index < 0) times + minutes else times.mapIndexed { i, old -> if (i == index) minutes else old })
            },
            onDismiss = { editing = null }
        )
    }
}

/** Pick a time of day, in the phone's 12- or 24-hour style, on the clock or by typing it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayDialog(
    title: String,
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinutes / Constants.MINUTES_PER_HOUR,
        initialMinute = initialMinutes % Constants.MINUTES_PER_HOUR,
        is24Hour = DateFormat.is24HourFormat(context)
    )
    var typing by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f))
                IconButton(onClick = { typing = !typing }) {
                    Icon(
                        if (typing) Icons.Default.Schedule else Icons.Default.Keyboard,
                        contentDescription = stringResource(if (typing) R.string.time_picker_use_dial else R.string.time_picker_use_keyboard)
                    )
                }
            }
        },
        text = { if (typing) TimeInput(state = state) else TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * Constants.MINUTES_PER_HOUR + state.minute) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
