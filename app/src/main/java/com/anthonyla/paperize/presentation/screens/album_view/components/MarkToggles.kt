package com.anthonyla.paperize.presentation.screens.album_view.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.anthonyla.paperize.R
import com.anthonyla.paperize.presentation.screens.album_view.SelectionMarks

/**
 * Selection bar toggles for the selected images (plan 5.2). Each shows whether every selected image
 * already has the mark: filled when it does, so a tap takes it away again; outlined otherwise.
 */
@Composable
internal fun MarkToggles(
    marks: SelectionMarks,
    onFavoriteChange: (Boolean) -> Unit,
    onExcludedChange: (Boolean) -> Unit
) {
    val enabled = marks.images > 0
    IconToggleButton(checked = marks.allFavorite, onCheckedChange = onFavoriteChange, enabled = enabled) {
        Icon(
            imageVector = if (marks.allFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(R.string.action_favorite)
        )
    }
    IconToggleButton(checked = marks.allExcluded, onCheckedChange = onExcludedChange, enabled = enabled) {
        Icon(
            imageVector = if (marks.allExcluded) Icons.Filled.DoNotDisturbOn else Icons.Outlined.DoNotDisturbOn,
            contentDescription = stringResource(R.string.action_exclude)
        )
    }
}
