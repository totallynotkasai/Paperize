package com.anthonyla.paperize.presentation.screens.album_view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.presentation.screens.album_view.AccessIssues
import com.anthonyla.paperize.presentation.screens.album_view.GrantNotice
import com.anthonyla.paperize.presentation.theme.AppSpacing

/** How strongly an image or folder that can't be opened is dimmed in the grid. */
internal const val UNAVAILABLE_ALPHA = 0.4f

/** Marks a grid item whose file Paperize can no longer read. */
@Composable
internal fun UnavailableBadge(contentDescription: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.BrokenImage,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** "Needs attention" banner for images whose file grant was lost, with ways to fix it. */
@Composable
fun AccessBanner(
    issues: AccessIssues,
    onGrantAccess: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(start = AppSpacing.large, top = AppSpacing.large, end = AppSpacing.small),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Text(
                    text = stringResource(R.string.access_lost_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.access_lost_message, issues.unavailableImages, issues.unavailableImages
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = AppSpacing.small)
            )
            Text(
                text = issues.unavailableFolders.firstOrNull()
                    ?.let { stringResource(R.string.access_lost_folder_hint, it.displayName) }
                    ?: stringResource(R.string.access_lost_images_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = AppSpacing.small)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_unavailable)) }
                TextButton(onClick = onGrantAccess) { Text(stringResource(R.string.grant_access)) }
            }
        }
    }
}

/** Explains Android's limit on kept file grants and suggests adding a folder instead. */
@Composable
fun GrantNoticeDialog(
    notice: GrantNotice,
    onAddFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = {
            Text(
                stringResource(
                    if (notice is GrantNotice.LimitReached) R.string.grant_limit_title else R.string.grant_warning_title
                )
            )
        },
        text = {
            Text(
                when (notice) {
                    is GrantNotice.LimitReached -> pluralStringResource(
                        R.plurals.grant_limit_message, notice.available,
                        Constants.MAX_PERSISTED_URI_GRANTS, notice.needed, notice.available
                    )
                    is GrantNotice.NearLimit -> stringResource(
                        R.string.grant_warning_message, notice.inUse, Constants.MAX_PERSISTED_URI_GRANTS
                    )
                }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onAddFolder()
            }) { Text(stringResource(R.string.add_folder)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (notice is GrantNotice.LimitReached) R.string.cancel else R.string.ok))
            }
        }
    )
}
