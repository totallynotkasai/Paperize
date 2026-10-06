package com.anthonyla.paperize.presentation.screens.album_view.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anthonyla.paperize.R
import com.anthonyla.paperize.presentation.screens.album_view.ImportProgress
import com.anthonyla.paperize.presentation.theme.AppSpacing

/**
 * Import progress. Imports carry on if the album is left, so [onHide] tucks the dialog away into
 * an [ImportProgressCard] instead of blocking the screen.
 */
@Composable
fun ImportProgressDialog(progress: ImportProgress, onCancel: () -> Unit, onHide: () -> Unit) {
    if (progress is ImportProgress.Idle) return

    Dialog(
        onDismissRequest = onHide,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(progress.titleRes()),
                    style = MaterialTheme.typography.titleMedium
                )
                ImportProgressContent(progress)
                Text(
                    text = stringResource(R.string.import_continues_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = onHide) {
                        Text(stringResource(R.string.import_hide))
                    }
                }
            }
        }
    }
}

/** The import in progress, shown in the album grid once its dialog is hidden; tap to open it again. */
@Composable
fun ImportProgressCard(progress: ImportProgress, onShow: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    if (progress is ImportProgress.Idle) return
    Card(
        onClick = onShow,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(progress.titleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
            ImportProgressContent(progress)
        }
    }
}

@Composable
private fun ImportProgressContent(progress: ImportProgress) {
    when (progress) {
        is ImportProgress.Scanning -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.import_scanning, progress.found),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        is ImportProgress.Saving -> {
            Text(
                text = stringResource(R.string.import_saving, progress.saved, progress.total),
                style = MaterialTheme.typography.bodyMedium
            )
            val fraction by animateFloatAsState(
                targetValue = if (progress.total > 0) progress.saved.toFloat() / progress.total else 0f,
                label = "importProgress"
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }

        ImportProgress.Idle -> Unit
    }
}

private fun ImportProgress.titleRes(): Int = when (this) {
    is ImportProgress.Scanning -> R.string.import_adding_folder
    else -> R.string.import_adding_wallpapers
}
