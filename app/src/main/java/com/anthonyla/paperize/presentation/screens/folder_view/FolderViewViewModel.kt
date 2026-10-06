package com.anthonyla.paperize.presentation.screens.folder_view
import com.anthonyla.paperize.core.constants.Constants

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anthonyla.paperize.domain.model.Folder
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.presentation.common.navigation.FolderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.usecase.MarkWallpapersUseCase
import com.anthonyla.paperize.domain.usecase.RefreshFolderUseCase
import com.anthonyla.paperize.presentation.common.util.UiText
import com.anthonyla.paperize.presentation.screens.album_view.ImageFilter
import com.anthonyla.paperize.presentation.screens.album_view.SelectionMarks
import com.anthonyla.paperize.presentation.screens.album_view.markMessage
import com.anthonyla.paperize.presentation.screens.album_view.selectionMarks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
class FolderViewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    albumRepository: AlbumRepository,
    private val refreshFolderUseCase: RefreshFolderUseCase,
    private val markWallpapers: MarkWallpapersUseCase
) : ViewModel() {

    private companion object {
        const val TAG = "FolderViewViewModel"
        const val KEY_FILTER = "imageFilter"
    }

    private val folderId = savedStateHandle.toRoute<FolderRoute>().folderId

    val folder: StateFlow<Folder?> = albumRepository.getFolderById(folderId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = null
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _message = MutableStateFlow<UiText?>(null)
    val message = _message.asStateFlow()

    val filter: StateFlow<ImageFilter> = savedStateHandle.getStateFlow(KEY_FILTER, ImageFilter.ALL)

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    val selectionMarks: StateFlow<SelectionMarks> = combine(folder, _selected) { folder, selected ->
        folder?.wallpapers.orEmpty().filter { it.id in selected }.selectionMarks()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS), SelectionMarks())

    fun dismissMessage() { _message.value = null }

    fun setFilter(filter: ImageFilter) {
        if (filter == this.filter.value) return
        clearSelection()
        savedStateHandle[KEY_FILTER] = filter
    }

    fun toggleSelection(wallpaperId: String) {
        _selected.value = if (wallpaperId in _selected.value) _selected.value - wallpaperId else _selected.value + wallpaperId
    }

    fun selectAll(wallpaperIds: Collection<String>) { _selected.value = wallpaperIds.toSet() }

    fun clearSelection() { _selected.value = emptySet() }

    fun setSelectionFavorite(favorite: Boolean) = markSelection(favorite = favorite) { albumId, ids ->
        markWallpapers.setFavorite(albumId, ids, favorite)
    }

    fun setSelectionExcluded(excluded: Boolean) = markSelection(excluded = excluded) { albumId, ids ->
        markWallpapers.setExcluded(albumId, ids, excluded)
    }

    private fun markSelection(
        favorite: Boolean? = null,
        excluded: Boolean? = null,
        mark: suspend (albumId: String, ids: List<String>) -> Result<Int>
    ) {
        val folder = folder.value ?: return
        val ids = folder.wallpapers.filter { it.id in _selected.value }.map { it.id }
        if (ids.isEmpty()) return
        _message.value = null
        viewModelScope.launch {
            _message.value = when (val result = mark(folder.albumId, ids)) {
                is Result.Success -> {
                    clearSelection()
                    markMessage(favorite, excluded, ids.size)
                }
                is Result.Error -> {
                    Log.e(TAG, "Could not mark images", result.exception)
                    UiText.Resource(R.string.mark_failed)
                }
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _message.value = null
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                _message.value = UiText.Resource(
                    when (refreshFolderUseCase(folderId)) {
                        is Result.Success -> R.string.folder_refreshed
                        else -> R.string.folder_refresh_failed
                    }
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
