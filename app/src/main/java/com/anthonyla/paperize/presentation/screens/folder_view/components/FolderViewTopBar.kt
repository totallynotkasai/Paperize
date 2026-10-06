package com.anthonyla.paperize.presentation.screens.folder_view.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.anthonyla.paperize.R
import com.anthonyla.paperize.presentation.screens.album_view.SelectionMarks
import com.anthonyla.paperize.presentation.screens.album_view.components.MarkToggles

/**
 * The folder's top bar: Refresh and Sort, or while selecting, Select all and the favourite and
 * exclude toggles. A folder's images can't be removed one by one (the next refresh would bring
 * them back), so there is no Delete; excluding keeps them out of rotation instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderViewTopBar(
    title: String,
    onBackClick: () -> Unit,
    onSortClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    selectedCount: Int = 0,
    allSelected: Boolean = false,
    selectionMarks: SelectionMarks = SelectionMarks(),
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onFavoriteChange: (Boolean) -> Unit = {},
    onExcludedChange: (Boolean) -> Unit = {}
) {
    if (selectedCount > 0) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.selected_count, selectedCount),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_clear_selection))
                }
            },
            actions = {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = stringResource(R.string.content_desc_select_all)
                    )
                }
                MarkToggles(selectionMarks, onFavoriteChange, onExcludedChange)
            },
            modifier = modifier
        )
        return
    }
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        actions = {
            IconButton(onClick = onRefreshClick, enabled = !isRefreshing) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_folder))
            }
            IconButton(onClick = onSortClick) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
            }
        },
        modifier = modifier
    )
}
