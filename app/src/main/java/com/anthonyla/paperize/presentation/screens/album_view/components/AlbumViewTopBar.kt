package com.anthonyla.paperize.presentation.screens.album_view.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.anthonyla.paperize.R
import com.anthonyla.paperize.presentation.screens.album_view.SelectionMarks

/**
 * The album's top bar. Normally: Reorder, Sort and the album menu (Album settings, Delete album).
 * While selecting: Select all, the favourite and exclude toggles, and Delete when every selected
 * item can be removed from the album ([canDeleteSelection]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumViewTopBar(
    title: String,
    isSelectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onBackClick: () -> Unit,
    onSortClick: () -> Unit,
    onReorderClick: () -> Unit,
    onAlbumSettings: () -> Unit,
    onDeleteAlbum: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    selectionMarks: SelectionMarks = SelectionMarks(),
    canDeleteSelection: Boolean = true,
    onFavoriteChange: (Boolean) -> Unit = {},
    onExcludedChange: (Boolean) -> Unit = {}
) {
    if (isSelectionMode) {
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
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.content_desc_clear_selection)
                    )
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
                // Images inside folders belong to their folder; they can be excluded, not removed.
                if (canDeleteSelection) {
                    IconButton(onClick = onDeleteSelected) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.content_desc_delete_selected)
                        )
                    }
                }
            }
        )
    } else {
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
                IconButton(onClick = onReorderClick) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = stringResource(R.string.reorder)
                    )
                }
                IconButton(onClick = onSortClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.sort)
                    )
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.album_menu)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.album_settings)) },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onAlbumSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_album_menu)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDeleteAlbum()
                            }
                        )
                    }
                }
            }
        )
    }
}
