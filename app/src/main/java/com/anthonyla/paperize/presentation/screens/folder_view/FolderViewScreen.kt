package com.anthonyla.paperize.presentation.screens.folder_view

import com.anthonyla.paperize.core.constants.Constants

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.anthonyla.paperize.R
import com.anthonyla.paperize.presentation.common.components.EmptyCollection
import com.anthonyla.paperize.presentation.common.util.asString
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.anthonyla.paperize.presentation.screens.album_view.ImageFilter
import com.anthonyla.paperize.presentation.screens.album_view.components.EmptyFilterMessage
import com.anthonyla.paperize.presentation.screens.album_view.components.ImageFilterChips
import com.anthonyla.paperize.presentation.screens.album_view.components.SortBottomSheet
import com.anthonyla.paperize.presentation.screens.album_view.components.SortOption
import com.anthonyla.paperize.presentation.screens.album_view.components.WallpaperItem
import com.anthonyla.paperize.presentation.screens.folder_view.components.FolderViewTopBar
import com.anthonyla.paperize.presentation.theme.AppGrid
import com.anthonyla.paperize.presentation.theme.AppSpacing

@Composable
fun FolderViewScreen(
    onBackClick: () -> Unit,
    onNavigateToWallpaperView: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderViewViewModel = hiltViewModel()
) {
    val lazyListState = rememberLazyGridState()

    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val wallpapers = folder?.wallpapers.orEmpty()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val selectionMarks by viewModel.selectionMarks.collectAsStateWithLifecycle()
    val isSelectionMode = selected.isNotEmpty()
    val snackbarHost = remember { SnackbarHostState() }
    val messageText = message?.asString()
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbarHost.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    BackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }

    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var sortOption by rememberSaveable { mutableStateOf(SortOption.ROTATION) }

    val sortedWallpapers = remember(wallpapers, sortOption, filter) {
        wallpapers.filter(filter::matches).sortedWith(sortOption.wallpaperComparator)
    }
    val favoriteCount = wallpapers.count { it.favorite }
    val excludedCount = wallpapers.count { it.excluded }
    val visibleIds = sortedWallpapers.map { it.id }
    val allSelected = visibleIds.isNotEmpty() && selected.containsAll(visibleIds)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            FolderViewTopBar(
                title = folder?.displayName ?: "",
                onBackClick = onBackClick,
                onSortClick = { showSortSheet = true },
                isRefreshing = isRefreshing,
                onRefreshClick = viewModel::refresh,
                selectedCount = selected.size,
                allSelected = allSelected,
                selectionMarks = selectionMarks,
                onSelectAll = { if (allSelected) viewModel.clearSelection() else viewModel.selectAll(visibleIds) },
                onClearSelection = viewModel::clearSelection,
                onFavoriteChange = viewModel::setSelectionFavorite,
                onExcludedChange = viewModel::setSelectionExcluded
            )
        }
    ) { paddingValues ->
        Box(modifier = modifier.fillMaxSize().padding(paddingValues)) {
            if (folder != null && wallpapers.isEmpty()) {
                EmptyCollection(
                    title = stringResource(R.string.folder_empty_title),
                    hint = stringResource(R.string.folder_empty_hint)
                )
            } else Column(modifier = Modifier.fillMaxSize()) {
                ImageFilterChips(
                    filter = filter,
                    favoriteCount = favoriteCount,
                    excludedCount = excludedCount,
                    onFilterChange = viewModel::setFilter,
                    modifier = Modifier.padding(top = AppSpacing.small)
                )
                LazyVerticalGrid(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    columns = GridCells.Adaptive(AppGrid.itemMinSize),
                    contentPadding = PaddingValues(AppSpacing.gridPadding),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.gridSpacing)
                ) {
                    if (filter != ImageFilter.ALL && sortedWallpapers.isEmpty()) {
                        item(key = "filter-empty", span = { GridItemSpan(maxLineSpan) }) { EmptyFilterMessage(filter) }
                    }
                    items(
                        items = sortedWallpapers,
                        key = { wallpaper -> wallpaper.id }
                    ) { wallpaper ->
                        WallpaperItem(
                            wallpaperUri = wallpaper.uri,
                            wallpaperName = wallpaper.displayFileName,
                            isSelected = wallpaper.id in selected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(wallpaper.id)
                                } else {
                                    onNavigateToWallpaperView(
                                        wallpaper.id,
                                        wallpaper.uri,
                                        wallpaper.fileName
                                    )
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(wallpaper.id) },
                            unavailable = wallpaper.accessLost,
                            favorite = wallpaper.favorite,
                            excluded = wallpaper.excluded,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(Constants.WALLPAPER_ASPECT_RATIO)
                                .animateItem(
                                    placementSpec = tween(
                                        durationMillis = Constants.ANIMATION_DURATION_LONG_MS,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                        )
                    }
                }
            }
            if (isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            selectedOption = sortOption,
            onSortSelected = { sortOption = it },
            onDismiss = { showSortSheet = false }
        )
    }
}
