package com.anthonyla.paperize.presentation.screens.album_view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.anthonyla.paperize.R
import com.anthonyla.paperize.presentation.screens.album_view.ImageFilter
import com.anthonyla.paperize.presentation.theme.AppSpacing

/**
 * "Favourites" and "Excluded" filters for an album or folder (plan 5.2), in a fixed row above the
 * grid. At most one is on; tapping it again shows everything. A filter with nothing to show is
 * left out unless it is on; with neither, there is no row.
 */
@Composable
fun ImageFilterChips(
    filter: ImageFilter,
    favoriteCount: Int,
    excludedCount: Int,
    onFilterChange: (ImageFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val showFavorites = favoriteCount > 0 || filter == ImageFilter.FAVORITES
    val showExcluded = excludedCount > 0 || filter == ImageFilter.EXCLUDED
    if (!showFavorites && !showExcluded) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.gridPadding),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        if (showFavorites) {
            FilterOption(ImageFilter.FAVORITES, R.string.filter_favorites, favoriteCount, Icons.Default.Favorite, filter, onFilterChange)
        }
        if (showExcluded) {
            FilterOption(ImageFilter.EXCLUDED, R.string.filter_excluded, excludedCount, Icons.Default.DoNotDisturbOn, filter, onFilterChange)
        }
    }
}

@Composable
private fun FilterOption(
    option: ImageFilter,
    label: Int,
    count: Int,
    icon: ImageVector,
    current: ImageFilter,
    onFilterChange: (ImageFilter) -> Unit
) {
    val selected = current == option
    FilterChip(
        selected = selected,
        onClick = { onFilterChange(if (selected) ImageFilter.ALL else option) },
        label = { Text(stringResource(R.string.filter_with_count, stringResource(label), count)) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
    )
}

/** Shown in place of the grid when a filter matches nothing. */
@Composable
fun EmptyFilterMessage(filter: ImageFilter, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(if (filter == ImageFilter.FAVORITES) R.string.filter_no_favorites else R.string.filter_no_excluded),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(AppSpacing.large)
    )
}
