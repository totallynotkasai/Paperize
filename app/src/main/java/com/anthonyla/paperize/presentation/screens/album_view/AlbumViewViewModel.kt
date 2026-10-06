package com.anthonyla.paperize.presentation.screens.album_view
import com.anthonyla.paperize.core.constants.Constants

import android.util.Log
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.FavoritesMode
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.usecase.GrantLimitException
import com.anthonyla.paperize.domain.usecase.ImportResult
import com.anthonyla.paperize.domain.usecase.ImportWallpapersUseCase
import com.anthonyla.paperize.domain.usecase.DeleteAlbumUseCase
import com.anthonyla.paperize.domain.usecase.MarkWallpapersUseCase
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.model.Folder
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.presentation.common.navigation.AlbumRoute
import com.anthonyla.paperize.presentation.common.util.UiText
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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

/** How renaming an album ended. */
enum class RenameOutcome { RENAMED, NAME_TAKEN, FAILED }

@HiltViewModel
class AlbumViewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val importWallpapersUseCase: ImportWallpapersUseCase,
    private val deleteAlbumUseCase: DeleteAlbumUseCase,
    private val imports: AlbumImports,
    private val markWallpapers: MarkWallpapersUseCase,
    private val settingsRepository: SettingsRepository,
    private val changeRequests: WallpaperChangeRequests
) : ViewModel() {

    companion object {
        private const val TAG = "AlbumViewViewModel"
        private const val KEY_FILTER = "imageFilter"
    }

    private val albumId = savedStateHandle.toRoute<AlbumRoute>().albumId

    val filter: StateFlow<ImageFilter> = savedStateHandle.getStateFlow(KEY_FILTER, ImageFilter.ALL)

    /** Shows only favourites or excluded images (from folders too); clears the selection. */
    fun setFilter(filter: ImageFilter) {
        if (filter == this.filter.value) return
        clearSelection()
        savedStateHandle[KEY_FILTER] = filter
    }

    val wallpaperMode: StateFlow<WallpaperMode?> = settingsRepository.getWallpaperModeFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS), null)

    val shuffleEnabled: StateFlow<Boolean> = settingsRepository.getScheduleSettingsFlow()
        .map { it.shuffleEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS), false)

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

    /** Whether everything selected (images in selected folders too) is a favourite, or excluded. */
    val selectionMarks: StateFlow<SelectionMarks> = combine(album, _selectedWallpapers, _selectedFolders) { album, images, folders ->
        album?.imagesIn(images, folders)?.selectionMarks() ?: SelectionMarks()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS), SelectionMarks())

    /** Imports run in [AlbumImports], so they carry on if this screen is left. */
    val importProgress: StateFlow<ImportProgress> = imports.state(albumId)
        .map { it.progress }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ImportProgress.Idle)

    private val _message = MutableStateFlow<UiText?>(null)
    val message = _message.asStateFlow()
    private val _grantNotice = MutableStateFlow<GrantNotice?>(null)
    val grantNotice = _grantNotice.asStateFlow()
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()
    private val _albumDeleted = MutableStateFlow(false)
    val albumDeleted = _albumDeleted.asStateFlow()

    init {
        // Grants can disappear while the app is closed; check before showing the album.
        viewModelScope.launch {
            (albumRepository.syncAccess(albumId) as? Result.Error)?.let {
                Log.e(TAG, "Could not check file access", it.exception)
            }
        }
        // An import may have finished while the album was closed; its outcome waits until now.
        viewModelScope.launch {
            imports.state(albumId).mapNotNull { it.outcome }.collect { outcome ->
                showOutcome(outcome)
                imports.consumeOutcome(albumId)
            }
        }
    }

    private fun showOutcome(outcome: ImportOutcome) {
        when (outcome) {
            is ImportOutcome.Finished -> {
                _message.value = importMessage(outcome.result)
                if (outcome.result.nearGrantLimit) _grantNotice.value = GrantNotice.NearLimit(outcome.result.grantsInUse)
            }
            is ImportOutcome.LimitReached -> _grantNotice.value = GrantNotice.LimitReached(outcome.needed, outcome.available)
            is ImportOutcome.Failed -> _message.value = UiText.Resource(
                if (outcome.permissionProblem) R.string.import_permission_error else R.string.import_failed
            )
        }
    }

    private val isImporting: Boolean get() = imports.isRunning(albumId)

    fun dismissMessage() { _message.value = null }

    fun dismissGrantNotice() { _grantNotice.value = null }

    fun addWallpapers(uris: List<String>) {
        if (uris.isEmpty() || _isDeleting.value) return
        _message.value = null
        imports.addImages(albumId, uris)
    }

    fun addFolder(uri: String) {
        if (_isDeleting.value) return
        _message.value = null
        imports.addFolder(albumId, uri)
    }

    private fun importMessage(result: ImportResult): UiText? = when {
        result.skippedUnsupported > 0 && result.added == 0 && !result.alreadyInAlbum ->
            UiText.Resource(R.string.import_none_supported)
        result.skippedUnsupported > 0 ->
            UiText.Plural(R.plurals.import_skipped_unsupported, result.skippedUnsupported)
        result.alreadyInAlbum -> UiText.Resource(R.string.import_already_added)
        else -> null
    }

    fun cancelImport() = imports.cancel(albumId)

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
        if (_isDeleting.value || isImporting) return
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

    /** Select what the grid shows, which depends on the filter. */
    fun selectAll(wallpaperIds: Collection<String>, folderIds: Collection<String>) {
        if (_isDeleting.value) return
        _selectedWallpapers.value = wallpaperIds.toSet()
        _selectedFolders.value = folderIds.toSet()
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

    /** Favourite (or unfavourite) every selected image, including the images of selected folders. */
    fun setSelectionFavorite(favorite: Boolean) = markSelection(favorite = favorite) { ids ->
        markWallpapers.setFavorite(albumId, ids, favorite)
    }

    /** Exclude (or include again) every selected image, including the images of selected folders. */
    fun setSelectionExcluded(excluded: Boolean) = markSelection(excluded = excluded) { ids ->
        markWallpapers.setExcluded(albumId, ids, excluded)
    }

    private fun markSelection(
        favorite: Boolean? = null,
        excluded: Boolean? = null,
        mark: suspend (List<String>) -> Result<Int>
    ) {
        val album = album.value ?: return
        if (_isDeleting.value) return
        val ids = album.imagesIn(_selectedWallpapers.value, _selectedFolders.value).map { it.id }
        if (ids.isEmpty()) return
        _message.value = null
        viewModelScope.launch {
            _message.value = when (val result = mark(ids)) {
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

    /** Returns how renaming went; the dialog stays open unless it worked. */
    suspend fun renameAlbum(name: String): RenameOutcome = when (val result = albumRepository.renameAlbum(albumId, name)) {
        is Result.Success -> if (result.data) RenameOutcome.RENAMED else RenameOutcome.NAME_TAKEN
        is Result.Error -> {
            Log.e(TAG, "Could not rename the album", result.exception)
            RenameOutcome.FAILED
        }
    }

    fun setFavoritesMode(mode: FavoritesMode) {
        viewModelScope.launch {
            (albumRepository.setFavoritesMode(albumId, mode) as? Result.Error)?.let {
                Log.e(TAG, "Could not save the favourites setting", it.exception)
                _message.value = UiText.Resource(R.string.album_settings_failed)
            }
        }
    }

    /**
     * Effects to start "Custom effects" from: those of the screen showing this album, so turning it
     * on changes nothing until an effect is changed.
     */
    suspend fun startingEffects(): WallpaperEffects {
        val settings = settingsRepository.getScheduleSettings()
        val effects = when {
            settingsRepository.getWallpaperMode() == WallpaperMode.LIVE -> settings.liveEffects
            settings.albumFor(ScreenType.HOME) != albumId && settings.albumFor(ScreenType.LOCK) == albumId -> settings.lockEffects
            else -> settings.homeEffects
        }
        return effects.visualOnly()
    }

    /**
     * Save the album's own effects (null: use each screen's own again) and re-render the screens
     * showing this album. [deferRender] (slider levels) waits briefly for further edits, like the
     * Wallpaper tab; the live wallpaper follows the saved effects by itself.
     */
    fun setCustomEffects(effects: WallpaperEffects?, deferRender: Boolean = false) {
        viewModelScope.launch {
            when (val result = albumRepository.setAlbumEffects(albumId, effects)) {
                is Result.Success -> renderScreensShowingAlbum(deferRender)
                is Result.Error -> {
                    Log.e(TAG, "Could not save the album's effects", result.exception)
                    _message.value = UiText.Resource(R.string.album_settings_failed)
                }
            }
        }
    }

    private var pendingRender: ScreenType? = null
    private var pendingRenderJob: Job? = null

    private suspend fun renderScreensShowingAlbum(defer: Boolean) {
        if (settingsRepository.getWallpaperMode() != WallpaperMode.STATIC) return
        val settings = settingsRepository.getScheduleSettings()
        val screens = settings.rotatingStaticScreens().filter { settings.albumFor(it) == albumId }
        val screen = when (screens.size) {
            0 -> return
            1 -> screens.single()
            else -> ScreenType.BOTH
        }
        pendingRenderJob?.cancel()
        pendingRenderJob = null
        if (!defer) {
            pendingRender = null
            changeRequests.reapplyEffects(screen)
            return
        }
        pendingRender = screen
        pendingRenderJob = viewModelScope.launch {
            delay(Constants.SETTINGS_DEBOUNCE_MS)
            pendingRenderJob = null
            flushPendingRender()
        }
    }

    /** Render waiting effect edits now; call when the settings sheet closes or the screen pauses. */
    fun flushPendingRender() {
        pendingRenderJob?.cancel()
        pendingRenderJob = null
        pendingRender?.let {
            pendingRender = null
            changeRequests.reapplyEffects(it)
        }
    }

    override fun onCleared() = flushPendingRender()

    private fun removeItems(wallpaperIds: List<String>, folderIds: List<String>) {
        if (_isDeleting.value || isImporting) return
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
