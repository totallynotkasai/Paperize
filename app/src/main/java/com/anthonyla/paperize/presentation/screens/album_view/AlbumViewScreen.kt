package com.anthonyla.paperize.presentation.screens.album_view

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.anthonyla.paperize.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.Folder
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.presentation.common.components.AddAlbumAnimatedFab
import com.anthonyla.paperize.presentation.common.components.EmptyCollection
import com.anthonyla.paperize.presentation.common.util.UiText
import com.anthonyla.paperize.presentation.common.util.asString
import com.anthonyla.paperize.presentation.screens.album_view.components.AccessBanner
import com.anthonyla.paperize.presentation.screens.album_view.components.AlbumViewTopBar
import com.anthonyla.paperize.presentation.screens.album_view.components.FolderItem
import com.anthonyla.paperize.presentation.screens.album_view.components.GrantNoticeDialog
import com.anthonyla.paperize.presentation.screens.album_view.components.ImportProgressCard
import com.anthonyla.paperize.presentation.screens.album_view.components.ImportProgressDialog
import com.anthonyla.paperize.presentation.screens.album_view.components.SortBottomSheet
import com.anthonyla.paperize.presentation.screens.album_view.components.SortOption
import com.anthonyla.paperize.presentation.screens.album_view.components.WallpaperItem
import com.anthonyla.paperize.presentation.theme.AppGrid
import com.anthonyla.paperize.presentation.theme.AppSpacing

@Composable
fun AlbumViewScreen(
    onBackClick: () -> Unit,
    onNavigateToFolder: (String) -> Unit,
    onNavigateToWallpaperView: (String, String, String) -> Unit,
    onNavigateToReorder: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewViewModel = hiltViewModel()
) {
    val lazyListState = rememberLazyGridState()

    val album by viewModel.album.collectAsStateWithLifecycle()
    val folders = album?.folders.orEmpty()
    val wallpapers = album?.wallpapers.orEmpty()

    val selectedWallpapers by viewModel.selectedWallpapers.collectAsStateWithLifecycle()
    val selectedFolders by viewModel.selectedFolders.collectAsStateWithLifecycle()
    val isSelectionMode = selectedWallpapers.isNotEmpty() || selectedFolders.isNotEmpty()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val grantNotice by viewModel.grantNotice.collectAsStateWithLifecycle()
    val accessIssues by viewModel.accessIssues.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val albumDeleted by viewModel.albumDeleted.collectAsStateWithLifecycle()
    var showDeleteAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoveUnavailableDialog by rememberSaveable { mutableStateOf(false) }
    // Hiding the import dialog leaves a progress card in the grid; the next import shows it again.
    var importDialogHidden by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(importProgress is ImportProgress.Idle) {
        if (importProgress is ImportProgress.Idle) importDialogHidden = false
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = message?.asString()
    LaunchedEffect(messageText) {
        messageText?.let {
            if (message == UiText.Resource(R.string.delete_album_failed)) showDeleteAlbumDialog = false
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(albumDeleted) {
        if (albumDeleted) onBackClick()
    }
    val selectedCount = selectedWallpapers.size + selectedFolders.size

    val totalItemsCount = wallpapers.size + folders.size
    val allSelected = selectedCount == totalItemsCount && totalItemsCount > 0

    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteSelectedDialog by rememberSaveable { mutableStateOf(false) }
    var sortOption by rememberSaveable { mutableStateOf(SortOption.ROTATION) }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    val sortedFolders = remember(folders, sortOption) {
        when (sortOption) {
            SortOption.ROTATION -> folders.sortedWith(compareBy<Folder> { it.displayOrder }.thenBy { it.id })
            SortOption.NAME_ASC -> folders.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> folders.sortedByDescending { it.name.lowercase() }
            SortOption.DATE_ADDED_ASC -> folders.sortedBy { it.addedAt }
            SortOption.DATE_ADDED_DESC -> folders.sortedByDescending { it.addedAt }
            SortOption.DATE_MODIFIED_ASC -> folders.sortedBy { it.dateModified }
            SortOption.DATE_MODIFIED_DESC -> folders.sortedByDescending { it.dateModified }
        }
    }

    val sortedWallpapers = remember(wallpapers, sortOption) {
        wallpapers.sortedWith(sortOption.wallpaperComparator)
    }

    val unavailableFolderIds = remember(accessIssues) { accessIssues.unavailableFolders.map { it.id }.toSet() }

    val commonItemModifier = remember {
        Modifier
            .fillMaxWidth()
            .aspectRatio(Constants.WALLPAPER_ASPECT_RATIO)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        viewModel.addWallpapers(uris.map { it.toString() })
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.addFolder(it.toString()) }
    }

    val restoreImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        viewModel.restoreImageAccess(uris.map { it.toString() })
    }

    val restoreFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.restoreFolderAccess(it.toString()) }
    }

    val grantAccess: () -> Unit = {
        val folder = accessIssues.unavailableFolders.firstOrNull()
        if (folder != null) {
            restoreFolderLauncher.launch(folder.pickerStartLocation())
        } else {
            restoreImagesLauncher.launch(arrayOf("image/*"))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AlbumViewTopBar(
                title = album?.name ?: "",
                isSelectionMode = isSelectionMode,
                selectedCount = selectedCount,
                allSelected = allSelected,
                onBackClick = onBackClick,
                onSortClick = { showSortSheet = true },
                onReorderClick = onNavigateToReorder,
                onDeleteAlbum = { showDeleteAlbumDialog = true },
                onSelectAll = { if (allSelected) viewModel.clearSelection() else viewModel.selectAll() },
                onDeleteSelected = { if (!isDeleting) showDeleteSelectedDialog = true },
                onClearSelection = { viewModel.clearSelection() }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode && totalItemsCount > 0) {
                AddAlbumAnimatedFab(
                    isLoading = importProgress !is ImportProgress.Idle,
                    onImageClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                    onFolderClick = { folderPickerLauncher.launch(null) }
                )
            }
        }
    ) { paddingValues ->
        if (album != null && totalItemsCount == 0) {
            EmptyCollection(
                title = stringResource(R.string.folder_empty_title),
                hint = stringResource(R.string.album_empty_hint),
                modifier = modifier.padding(paddingValues)
            ) {
                if (importDialogHidden && importProgress !is ImportProgress.Idle) {
                    ImportProgressCard(
                        progress = importProgress,
                        onShow = { importDialogHidden = false },
                        onCancel = viewModel::cancelImport
                    )
                } else {
                    FilledTonalButton(onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }) {
                        Text(stringResource(R.string.add_wallpapers))
                    }
                    TextButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Text(stringResource(R.string.add_folder))
                    }
                }
            }
            return@Scaffold
        }
        val itemPlacement = tween<androidx.compose.ui.unit.IntOffset>(
            durationMillis = Constants.ANIMATION_DURATION_LONG_MS,
            easing = FastOutSlowInEasing
        )
        val folderItems: LazyGridScope.() -> Unit = {
            items(
                items = sortedFolders,
                key = { folder -> "folder-${folder.id}" }
            ) { folder ->
                FolderItem(
                    folder = folder,
                    isSelected = folder.id in selectedFolders,
                    isSelectionMode = isSelectionMode,
                    onClick = {
                        if (isSelectionMode) {
                            viewModel.toggleFolderSelection(folder.id)
                        } else {
                            onNavigateToFolder(folder.id)
                        }
                    },
                    onLongClick = {
                        viewModel.toggleFolderSelection(folder.id)
                    },
                    unavailable = folder.id in unavailableFolderIds,
                    modifier = commonItemModifier.animateItem(placementSpec = itemPlacement)
                )
            }
        }
        val wallpaperItems: LazyGridScope.() -> Unit = {
            items(
                items = sortedWallpapers,
                key = { wallpaper -> "wallpaper-${wallpaper.id}" }
            ) { wallpaper: Wallpaper ->
                WallpaperItem(
                    wallpaperUri = wallpaper.uri,
                    wallpaperName = wallpaper.displayFileName,
                    isSelected = wallpaper.id in selectedWallpapers,
                    isSelectionMode = isSelectionMode,
                    onClick = {
                        if (isSelectionMode) {
                            viewModel.toggleWallpaperSelection(wallpaper.id)
                        } else {
                            onNavigateToWallpaperView(
                                wallpaper.id,
                                wallpaper.uri,
                                wallpaper.fileName
                            )
                        }
                    },
                    onLongClick = {
                        viewModel.toggleWallpaperSelection(wallpaper.id)
                    },
                    unavailable = wallpaper.accessLost,
                    modifier = commonItemModifier.animateItem(placementSpec = itemPlacement)
                )
            }
        }
        LazyVerticalGrid(
            state = lazyListState,
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            columns = GridCells.Adaptive(AppGrid.itemMinSize),
            contentPadding = PaddingValues(AppSpacing.gridPadding),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.gridSpacing),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.gridSpacing)
        ) {
            if (importDialogHidden && importProgress !is ImportProgress.Idle) {
                item(key = "import-progress", span = { GridItemSpan(maxLineSpan) }) {
                    ImportProgressCard(
                        progress = importProgress,
                        onShow = { importDialogHidden = false },
                        onCancel = viewModel::cancelImport
                    )
                }
            }
            if (accessIssues.unavailableImages > 0 && !isSelectionMode) {
                item(key = "access-banner", span = { GridItemSpan(maxLineSpan) }) {
                    AccessBanner(
                        issues = accessIssues,
                        onGrantAccess = grantAccess,
                        onRemove = { showRemoveUnavailableDialog = true }
                    )
                }
            }
            // Rotation order lists images in the order they change in: direct images first,
            // then each folder in turn.
            if (sortOption == SortOption.ROTATION) {
                wallpaperItems()
                folderItems()
            } else {
                folderItems()
                wallpaperItems()
            }
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            selectedOption = sortOption,
            onSortSelected = { sortOption = it },
            onDismiss = { showSortSheet = false }
        )
    }

    if (!importDialogHidden) {
        ImportProgressDialog(
            progress = importProgress,
            onCancel = viewModel::cancelImport,
            onHide = { importDialogHidden = true }
        )
    }

    grantNotice?.let { notice ->
        GrantNoticeDialog(
            notice = notice,
            onAddFolder = { folderPickerLauncher.launch(null) },
            onDismiss = viewModel::dismissGrantNotice
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.remove_selected_title, selectedCount)) },
            text = { Text(stringResource(R.string.remove_selected_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedDialog = false
                    viewModel.deleteSelected()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRemoveUnavailableDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveUnavailableDialog = false },
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.remove_unavailable_title, accessIssues.unavailableImages, accessIssues.unavailableImages
                    )
                )
            },
            text = { Text(stringResource(R.string.remove_selected_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveUnavailableDialog = false
                    viewModel.removeUnavailable()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveUnavailableDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteAlbumDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteAlbumDialog = false },
            title = { Text(stringResource(R.string.delete_album_question)) },
            text = { Text(stringResource(R.string.are_you_sure_you_want_to_delete_this)) },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        viewModel.deleteAlbum()
                    }
                ) { Text(stringResource(if (isDeleting) R.string.deleting_album else R.string.confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !isDeleting, onClick = { showDeleteAlbumDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Opens the folder picker at the folder that needs access again, when the provider allows it. */
private fun Folder.pickerStartLocation(): Uri? = try {
    val tree = uri.toUri()
    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
} catch (_: IllegalArgumentException) {
    null
}
