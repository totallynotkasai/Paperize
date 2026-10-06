package com.anthonyla.paperize.presentation.screens.album_view
import com.anthonyla.paperize.core.constants.Constants

import android.util.Log
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.usecase.GrantLimitException
import com.anthonyla.paperize.domain.usecase.ImportResult
import com.anthonyla.paperize.domain.usecase.ImportWallpapersUseCase
import com.anthonyla.paperize.domain.usecase.DeleteAlbumUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.model.Folder
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.presentation.common.navigation.AlbumRoute
import com.anthonyla.paperize.presentation.common.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Images the album can no longer read, and the folders they belong to. */
data class AccessIssues(
    val unavailableImages: Int = 0,
    val unavailableFolders: List<Folder> = emptyList()
)

/** Android's cap on persisted file grants, reached or nearly reached by an import. */
sealed interface GrantNotice {
    data class LimitReached(val needed: Int, val available: Int) : GrantNotice
    data class NearLimit(val inUse: Int) : GrantNotice
}

@HiltViewModel
class AlbumViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val importWallpapersUseCase: ImportWallpapersUseCase,
    private val deleteAlbumUseCase: DeleteAlbumUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "AlbumViewViewModel"
    }

    private val albumId = savedStateHandle.toRoute<AlbumRoute>().albumId

    val album: StateFlow<Album?> = albumRepository.getAlbumById(albumId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = null
        )

    val accessIssues: StateFlow<AccessIssues> = album
        .map { album ->
            if (album == null) return@map AccessIssues()
            val folders = album.folders.filter { folder -> folder.wallpapers.any { it.accessLost } }
            AccessIssues(
                unavailableImages = album.wallpapers.count { it.accessLost } +
                    folders.sumOf { folder -> folder.wallpapers.count { it.accessLost } },
                unavailableFolders = folders
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS), AccessIssues())

    private val _selectedWallpapers = MutableStateFlow<Set<String>>(emptySet())
    val selectedWallpapers: StateFlow<Set<String>> = _selectedWallpapers.asStateFlow()

    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolders: StateFlow<Set<String>> = _selectedFolders.asStateFlow()

    private val _importProgress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message = _message.asStateFlow()
    private val _grantNotice = MutableStateFlow<GrantNotice?>(null)
    val grantNotice = _grantNotice.asStateFlow()
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()
    private val _albumDeleted = MutableStateFlow(false)
    val albumDeleted = _albumDeleted.asStateFlow()
    private var importJob: Job? = null

    init {
        // Grants can disappear while the app is closed; check before showing the album.
        viewModelScope.launch {
            (albumRepository.syncAccess(albumId) as? Result.Error)?.let {
                Log.e(TAG, "Could not check file access", it.exception)
            }
        }
    }

    fun dismissMessage() { _message.value = null }

    fun dismissGrantNotice() { _grantNotice.value = null }

    fun addWallpapers(uris: List<String>) {
        if (uris.isEmpty()) return
        import(ImportProgress.Saving(0, uris.size)) {
            importWallpapersUseCase.addImages(albumId, uris) { saved, total ->
                _importProgress.value = ImportProgress.Saving(saved, total)
            }
        }
    }

    fun addFolder(uri: String) {
        import(ImportProgress.Scanning(0)) {
            importWallpapersUseCase.addFolder(
                albumId, uri,
                onScanning = { _importProgress.value = ImportProgress.Scanning(it) },
                onSaving = { saved, total -> _importProgress.value = ImportProgress.Saving(saved, total) }
            )
        }
    }

    private fun import(initialProgress: ImportProgress, block: suspend () -> ImportResult) {
        if (importJob?.isCompleted == false || _isDeleting.value) return
        _message.value = null
        _importProgress.value = initialProgress
        importJob = viewModelScope.launch {
            try {
                val result = block()
                _message.value = importMessage(result)
                if (result.nearGrantLimit) _grantNotice.value = GrantNotice.NearLimit(result.grantsInUse)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GrantLimitException) {
                _grantNotice.value = GrantNotice.LimitReached(e.needed, e.available)
            } catch (e: SecurityException) {
                Log.e(TAG, "Import permission failed", e)
                _message.value = UiText.Resource(R.string.import_permission_error)
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _message.value = UiText.Resource(R.string.import_failed)
            }
        }.also { job ->
            job.invokeOnCompletion { _importProgress.value = ImportProgress.Idle }
        }
    }

    private fun importMessage(result: ImportResult): UiText? = when {
        result.skippedUnsupported > 0 && result.added == 0 && !result.alreadyInAlbum ->
            UiText.Resource(R.string.import_none_supported)
        result.skippedUnsupported > 0 ->
            UiText.Plural(R.plurals.import_skipped_unsupported, result.skippedUnsupported)
        result.alreadyInAlbum -> UiText.Resource(R.string.import_already_added)
        else -> null
    }

    fun cancelImport() { importJob?.cancel() }

    /** [uri] is the folder the user picked to grant access again. */
    fun restoreFolderAccess(uri: String) = restoreAccess {
        if (importWallpapersUseCase.restoreFolderAccess(albumId, uri)) {
            UiText.Resource(R.string.access_restored_folder)
        } else {
            UiText.Resource(R.string.access_restore_no_match)
        }
    }

    /** [uris] are the images the user picked to grant access again. */
    fun restoreImageAccess(uris: List<String>) {
        if (uris.isEmpty()) return
        restoreAccess {
            val restored = importWallpapersUseCase.restoreImageAccess(albumId, uris)
            if (restored > 0) UiText.Plural(R.plurals.access_restored_images, restored)
            else UiText.Resource(R.string.access_restore_no_match)
        }
    }

    private fun restoreAccess(block: suspend () -> UiText) {
        _message.value = null
        viewModelScope.launch {
            _message.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: GrantLimitException) {
                _grantNotice.value = GrantNotice.LimitReached(e.needed, e.available)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Could not restore access", e)
                UiText.Resource(R.string.import_permission_error)
            }
        }
    }

    fun deleteAlbum() {
        if (_isDeleting.value || importJob?.isCompleted == false) return
        _message.value = null
        _isDeleting.value = true
        viewModelScope.launch {
            try {
                deleteAlbumUseCase(albumId)
                    .getOrThrow()
                _albumDeleted.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting album", e)
                _message.value = UiText.Resource(R.string.delete_album_failed)
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun toggleWallpaperSelection(wallpaperId: String) {
        if (_isDeleting.value) return
        _selectedWallpapers.value = if (wallpaperId in _selectedWallpapers.value) {
            _selectedWallpapers.value - wallpaperId
        } else {
            _selectedWallpapers.value + wallpaperId
        }
    }

    fun toggleFolderSelection(folderId: String) {
        if (_isDeleting.value) return
        _selectedFolders.value = if (folderId in _selectedFolders.value) {
            _selectedFolders.value - folderId
        } else {
            _selectedFolders.value + folderId
        }
    }

    fun selectAll() {
        if (_isDeleting.value) return
        _selectedWallpapers.value = album.value?.wallpapers.orEmpty().map { it.id }.toSet()
        _selectedFolders.value = album.value?.folders.orEmpty().map { it.id }.toSet()
    }

    fun clearSelection() {
        if (_isDeleting.value) return
        _selectedWallpapers.value = emptySet()
        _selectedFolders.value = emptySet()
    }

    fun deleteSelected() = removeItems(_selectedWallpapers.value.toList(), _selectedFolders.value.toList())

    /** Remove every image the album can no longer read, including folders that lost access. */
    fun removeUnavailable() {
        val album = album.value ?: return
        removeItems(
            wallpaperIds = album.wallpapers.filter { it.accessLost }.map { it.id },
            folderIds = accessIssues.value.unavailableFolders.map { it.id }
        )
    }

    private fun removeItems(wallpaperIds: List<String>, folderIds: List<String>) {
        if (_isDeleting.value || importJob?.isCompleted == false) return
        _message.value = null
        if (wallpaperIds.isEmpty() && folderIds.isEmpty()) return
        _isDeleting.value = true
        viewModelScope.launch {
            try {
                if (wallpaperIds.isNotEmpty()) {
                    when (albumRepository.removeWallpapersFromAlbum(albumId, wallpaperIds)) {
                        is Result.Success -> _selectedWallpapers.value -= wallpaperIds.toSet()
                        else -> _message.value = UiText.Resource(R.string.delete_items_failed)
                    }
                }
                folderIds.forEach { folderId ->
                    when (albumRepository.removeFolderFromAlbum(albumId, folderId)) {
                        is Result.Success -> _selectedFolders.value -= folderId
                        else -> _message.value = UiText.Resource(R.string.delete_items_failed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error removing selected items", e)
                _message.value = UiText.Resource(R.string.delete_items_failed)
            } finally {
                _isDeleting.value = false
            }
        }
    }
}
