package com.anthonyla.paperize.presentation.screens.wallpaper.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.presentation.theme.AppSpacing

/**
 * Days / hours / minutes boxes. What you type stays as typed until you press Done or leave the
 * boxes (or the screen); then the total is saved, clamped to the allowed range, and the boxes
 * show the saved value.
 */
@Composable
fun TimeIntervalPicker(
    title: String,
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minimumMinutes: Int = Constants.MIN_INTERVAL_MINUTES
) {
    var dayInput by remember { mutableStateOf(daysPart(minutes)) }
    var hourInput by remember { mutableStateOf(hoursPart(minutes)) }
    var minuteInput by remember { mutableStateOf(minutesPart(minutes)) }
    var editing by remember { mutableStateOf(false) }

    // Show the saved value whenever nobody is typing; never overwrite typing in progress.
    LaunchedEffect(minutes, editing) {
        if (!editing) {
            dayInput = daysPart(minutes)
            hourInput = hoursPart(minutes)
            minuteInput = minutesPart(minutes)
        }
    }

    val onChange by rememberUpdatedState(onMinutesChange)
    val commit by rememberUpdatedState {
        val total = (dayInput.toIntOrNull() ?: 0) * Constants.MINUTES_PER_DAY +
            (hourInput.toIntOrNull() ?: 0) * Constants.MINUTES_PER_HOUR + (minuteInput.toIntOrNull() ?: 0)
        val clamped = total.coerceIn(minimumMinutes, Constants.MAX_INTERVAL_MINUTES)
        if (clamped != minutes) onChange(clamped)
    }
    val currentlyEditing by rememberUpdatedState(editing)
    DisposableEffect(Unit) {
        onDispose { if (currentlyEditing) commit() }
    }

    // Moving from one box to the next can briefly leave none focused; only a loss of focus that
    // lasts past the next frame counts as leaving the boxes.
    val focusedBoxes = remember { mutableStateListOf<Int>() }
    val anyFocused = focusedBoxes.isNotEmpty()
    LaunchedEffect(anyFocused) {
        if (anyFocused) {
            editing = true
        } else if (editing) {
            withFrameNanos { }
            commit()
            editing = false
        }
    }
    fun Modifier.trackFocus(box: Int) = onFocusChanged { state ->
        if (state.isFocused) {
            if (box !in focusedBoxes) focusedBoxes += box
        } else {
            focusedBoxes -= box
        }
    }
    val focusManager = LocalFocusManager.current
    val nextField = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    val lastField = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
    val keyboardActions = KeyboardActions(
        onNext = { focusManager.moveFocus(FocusDirection.Next) },
        onDone = { focusManager.clearFocus() }
    )

    androidx.compose.material3.Card(
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W500,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = dayInput,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } && newValue.length <= Constants.MAX_DAYS_INPUT_LENGTH) {
                            dayInput = newValue
                        }
                    },
                    label = { Text(stringResource(R.string.days_txt)) },
                    keyboardOptions = nextField,
                    keyboardActions = keyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f).trackFocus(1),
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                OutlinedTextField(
                    value = hourInput,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } && newValue.length <= Constants.MAX_HOURS_MINUTES_INPUT_LENGTH) {
                            hourInput = newValue
                        }
                    },
                    label = { Text(stringResource(R.string.hours_txt)) },
                    keyboardOptions = nextField,
                    keyboardActions = keyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f).trackFocus(2),
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                OutlinedTextField(
                    value = minuteInput,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } && newValue.length <= Constants.MAX_HOURS_MINUTES_INPUT_LENGTH) {
                            minuteInput = newValue
                        }
                    },
                    label = { Text(stringResource(R.string.mins)) },
                    keyboardOptions = lastField,
                    keyboardActions = keyboardActions,
                    singleLine = true,
                    modifier = Modifier.weight(1f).trackFocus(3),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }

            Text(
                text = "${stringResource(R.string.total_interval)} ${formatIntervalComposable(minutes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun daysPart(minutes: Int) = (minutes / Constants.MINUTES_PER_DAY).toString()
private fun hoursPart(minutes: Int) = (minutes % Constants.MINUTES_PER_DAY / Constants.MINUTES_PER_HOUR).toString()
private fun minutesPart(minutes: Int) = (minutes % Constants.MINUTES_PER_HOUR).toString()

@Composable
private fun formatIntervalComposable(minutes: Int): String {
    val minUnit = stringResource(R.string.time_unit_min)

    return when {
        minutes < Constants.MINUTES_PER_HOUR -> "$minutes $minUnit"
        minutes < Constants.MINUTES_PER_DAY -> {
            val h = minutes / Constants.MINUTES_PER_HOUR
            val remainingMins = minutes % Constants.MINUTES_PER_HOUR
            val hUnit = pluralStringResource(R.plurals.time_unit_hours, h)
            if (remainingMins == 0) "$h $hUnit"
            else "$h $hUnit $remainingMins $minUnit"
        }
        else -> {
            val d = minutes / Constants.MINUTES_PER_DAY
            val remainingMinutes = minutes % Constants.MINUTES_PER_DAY
            val remainingHours = remainingMinutes / Constants.MINUTES_PER_HOUR
            val finalMins = remainingMinutes % Constants.MINUTES_PER_HOUR
            val dUnit = pluralStringResource(R.plurals.time_unit_days, d)
            buildString {
                append("$d $dUnit")
                if (remainingHours > 0) {
                    val hUnit = pluralStringResource(R.plurals.time_unit_hours, remainingHours)
                    append(" $remainingHours $hUnit")
                }
                if (finalMins > 0) append(" $finalMins $minUnit")
            }
        }
    }
}
