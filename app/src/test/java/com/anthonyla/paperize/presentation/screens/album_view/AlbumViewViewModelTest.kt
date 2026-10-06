package com.anthonyla.paperize.presentation.screens.album_view

import com.anthonyla.paperize.testing.emptyAlbum
import com.anthonyla.paperize.testing.emptyFolder
import com.anthonyla.paperize.testing.emptyWallpaper
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.usecase.MarkWallpapersUseCase
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeRequests
import kotlinx.coroutines.launch
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.navigation.toRoute
import com.anthonyla.paperize.presentation.common.navigation.AlbumRoute
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.usecase.DeleteAlbumUseCase
import com.anthonyla.paperize.domain.usecase.GrantLimitException
import com.anthonyla.paperize.domain.usecase.ImportResult
import com.anthonyla.paperize.domain.usecase.ImportWallpapersUseCase
import com.anthonyla.paperize.presentation.common.util.UiText
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewViewModelTest {
    private val albums = mockk<AlbumRepository>()
    private val imports = mockk<ImportWallpapersUseCase>()
    private val delete = mockk<DeleteAlbumUseCase>()
    private val marks = mockk<MarkWallpapersUseCase>()
    private val settings = mockk<SettingsRepository>()
    private val requests = mockk<WallpaperChangeRequests>(relaxed = true)
    private val store = ViewModelStore()
    private lateinit var viewModel: AlbumViewViewModel

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { albums.getAlbumById("album") } returns flowOf(emptyAlbum("album"))
        coEvery { albums.syncAccess("album") } returns Result.Success(0)
        every { settings.getWallpaperModeFlow() } returns flowOf(WallpaperMode.STATIC)
        every { settings.getScheduleSettingsFlow() } returns flowOf(ScheduleSettings())
        coEvery { settings.getWallpaperMode() } returns WallpaperMode.STATIC
        coEvery { settings.getScheduleSettings() } returns ScheduleSettings()
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        // Runs imports on the test's main dispatcher, so advanceUntilIdle drives them.
        albumImports = AlbumImports(imports, CoroutineScope(SupervisorJob() + Dispatchers.Main))
        viewModel = albumViewModel()
        store.put("album", viewModel)
    }

    private lateinit var albumImports: AlbumImports

    private fun albumViewModel(): AlbumViewViewModel {
        val savedState = SavedStateHandle()
        every { savedState.toRoute<AlbumRoute>() } returns AlbumRoute("album")
        return AlbumViewViewModel(savedState, albums, imports, delete, albumImports, marks, settings, requests)
    }

    /** One direct image, and a folder holding a favourite and a plain image. */
    private fun albumWithFolder() = emptyAlbum("album").copy(
        wallpapers = listOf(emptyWallpaper("direct", "album")),
        folders = listOf(
            emptyFolder("folder", "album").copy(
                wallpapers = listOf(
                    emptyWallpaper("in-folder-1", "album").copy(folderId = "folder", favorite = true),
                    emptyWallpaper("in-folder-2", "album").copy(folderId = "folder")
                )
            )
        )
    )

    @Test fun `marking a selection includes the images of selected folders`() = runTest {
        every { albums.getAlbumById("album") } returns flowOf(albumWithFolder())
        val vm = albumViewModel().also { store.put("marks", it) }
        backgroundScope.launch { vm.selectionMarks.collect {} }
        vm.toggleWallpaperSelection("direct")
        vm.toggleFolderSelection("folder")
        runCurrent()
        assertEquals(SelectionMarks(images = 3, allFavorite = false, allExcluded = false), vm.selectionMarks.value)

        coEvery { marks.setFavorite("album", any(), true) } returns Result.Success(2)
        vm.setSelectionFavorite(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { marks.setFavorite("album", listOf("direct", "in-folder-1", "in-folder-2"), true) }
        assertTrue(vm.selectedWallpapers.value.isEmpty())
        assertTrue(vm.selectedFolders.value.isEmpty())
        assertEquals(UiText.Plural(R.plurals.marked_favorite, 3), vm.message.value)
    }

    @Test fun `a failed mark keeps the selection and says so`() = runTest {
        every { albums.getAlbumById("album") } returns flowOf(albumWithFolder())
        val vm = albumViewModel().also { store.put("marks", it) }
        backgroundScope.launch { vm.selectionMarks.collect {} }
        vm.toggleWallpaperSelection("in-folder-1")
        runCurrent()
        assertTrue(vm.selectionMarks.value.allFavorite)
        coEvery { marks.setExcluded("album", listOf("in-folder-1"), true) } returns Result.Error(IllegalStateException())
        vm.setSelectionExcluded(true)
        advanceUntilIdle()
        assertEquals(setOf("in-folder-1"), vm.selectedWallpapers.value)
        assertEquals(UiText.Resource(R.string.mark_failed), vm.message.value)
    }

    @Test fun `changing the filter clears the selection`() = runTest {
        viewModel.toggleWallpaperSelection("wallpaper")
        viewModel.setFilter(ImageFilter.FAVORITES)
        assertEquals(ImageFilter.FAVORITES, viewModel.filter.value)
        assertTrue(viewModel.selectedWallpapers.value.isEmpty())
        // Select all takes what the filtered grid shows.
        viewModel.selectAll(listOf("a", "b"), emptyList())
        assertEquals(setOf("a", "b"), viewModel.selectedWallpapers.value)
    }

    @Test fun `renaming reports a taken name and failures`() = runTest {
        coEvery { albums.renameAlbum("album", "Cats") } returns Result.Success(true)
        coEvery { albums.renameAlbum("album", "Dogs") } returns Result.Success(false)
        coEvery { albums.renameAlbum("album", "Birds") } returns Result.Error(IllegalStateException())
        assertEquals(RenameOutcome.RENAMED, viewModel.renameAlbum("Cats"))
        assertEquals(RenameOutcome.NAME_TAKEN, viewModel.renameAlbum("Dogs"))
        assertEquals(RenameOutcome.FAILED, viewModel.renameAlbum("Birds"))
    }

    @Test fun `album effects re-render only the screens showing the album, slider levels after a pause`() = runTest {
        coEvery { settings.getScheduleSettings() } returns
            ScheduleSettings(homeEnabled = true, lockEnabled = true, homeAlbumId = "album", lockAlbumId = "other")
        coEvery { albums.setAlbumEffects("album", any()) } returns Result.Success(Unit)
        viewModel.setCustomEffects(WallpaperEffects(enableBlur = true))
        advanceUntilIdle()
        verify(exactly = 1) { requests.reapplyEffects(ScreenType.HOME) }

        viewModel.setCustomEffects(WallpaperEffects(enableBlur = true, blurPercentage = 40), deferRender = true)
        viewModel.setCustomEffects(WallpaperEffects(enableBlur = true, blurPercentage = 60), deferRender = true)
        runCurrent()
        verify(exactly = 1) { requests.reapplyEffects(ScreenType.HOME) }
        advanceTimeBy(Constants.SETTINGS_DEBOUNCE_MS + 1)
        runCurrent()
        verify(exactly = 2) { requests.reapplyEffects(ScreenType.HOME) }
        verify(exactly = 0) { requests.reapplyEffects(ScreenType.LOCK) }
        verify(exactly = 0) { requests.reapplyEffects(ScreenType.BOTH) }
    }

    @Test fun `a waiting re-render still happens when the album closes`() = runTest {
        coEvery { settings.getScheduleSettings() } returns
            ScheduleSettings(homeEnabled = true, lockEnabled = true, homeAlbumId = "album", lockAlbumId = "album")
        coEvery { albums.setAlbumEffects("album", null) } returns Result.Success(Unit)
        viewModel.setCustomEffects(null, deferRender = true)
        runCurrent()
        verify(exactly = 0) { requests.reapplyEffects(any()) }
        store.clear()
        verify(exactly = 1) { requests.reapplyEffects(ScreenType.BOTH) }
    }

    @Test fun `the live wallpaper follows album effects by itself`() = runTest {
        coEvery { settings.getWallpaperMode() } returns WallpaperMode.LIVE
        coEvery { settings.getScheduleSettings() } returns ScheduleSettings(liveAlbumId = "album")
        coEvery { albums.setAlbumEffects("album", any()) } returns Result.Success(Unit)
        viewModel.setCustomEffects(WallpaperEffects(enableDarken = true))
        advanceUntilIdle()
        verify(exactly = 0) { requests.reapplyEffects(any()) }
    }

    @Test fun `custom effects start from the effects of the screen showing the album`() = runTest {
        coEvery { settings.getScheduleSettings() } returns ScheduleSettings(
            lockEnabled = true, lockAlbumId = "album",
            homeEffects = WallpaperEffects(enableBlur = true),
            lockEffects = WallpaperEffects(enableDarken = true, darkenPercentage = 70, enableDoubleTap = true)
        )
        // Only the visual effects; interactive ones stay with the screen.
        assertEquals(WallpaperEffects(enableDarken = true, darkenPercentage = 70), viewModel.startingEffects())
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test fun `failed import restores idle and shows a retry message`() = runTest {
        coEvery { imports.addImages(any(), any(), any()) } throws IllegalStateException("Disk full")
        viewModel.addWallpapers(listOf("image"))
        advanceUntilIdle()
        assertEquals(ImportProgress.Idle, viewModel.importProgress.value)
        assertEquals(UiText.Resource(R.string.import_failed), viewModel.message.value)
    }

    @Test fun `cancellation restores idle without reporting an error`() = runTest {
        coEvery { imports.addImages(any(), any(), any()) } coAnswers { awaitCancellation() }
        viewModel.addWallpapers(listOf("image"))
        viewModel.addFolder("folder")
        runCurrent()
        viewModel.cancelImport()
        advanceUntilIdle()
        assertEquals(ImportProgress.Idle, viewModel.importProgress.value)
        assertNull(viewModel.message.value)
        coVerify(exactly = 1) { imports.addImages(any(), any(), any()) }
        coVerify(exactly = 0) { imports.addFolder(any(), any(), any(), any()) }
    }

    @Test fun `cancelling before import starts also restores idle`() = runTest {
        viewModel.addWallpapers(listOf("image"))
        viewModel.cancelImport()
        advanceUntilIdle()
        assertEquals(ImportProgress.Idle, viewModel.importProgress.value)
        assertNull(viewModel.message.value)
    }

    @Test fun `navigation waits for successful album deletion and cleanup`() = runTest {
        val completion = CompletableDeferred<Result<Unit>>()
        coEvery { delete("album") } coAnswers { completion.await() }
        viewModel.deleteAlbum()
        runCurrent()
        assertTrue(viewModel.isDeleting.value)
        assertFalse(viewModel.albumDeleted.value)
        viewModel.deleteAlbum()
        completion.complete(Result.Success(Unit))
        advanceUntilIdle()
        assertTrue(viewModel.albumDeleted.value)
        assertFalse(viewModel.isDeleting.value)
        coVerify(exactly = 1) { delete("album") }
        coVerify(exactly = 0) { albums.deleteAlbum(any()) }
    }

    @Test fun `failed deletion keeps the album open`() = runTest {
        coEvery { delete("album") } returns Result.Error(IllegalStateException())
        viewModel.deleteAlbum()
        advanceUntilIdle()
        assertFalse(viewModel.albumDeleted.value)
        assertFalse(viewModel.isDeleting.value)
        assertEquals(UiText.Resource(R.string.delete_album_failed), viewModel.message.value)
    }

    @Test fun `partial removal keeps failed items selected`() = runTest {
        viewModel.toggleWallpaperSelection("wallpaper")
        viewModel.toggleFolderSelection("folder")
        coEvery { albums.removeWallpapersFromAlbum("album", listOf("wallpaper")) } returns Result.Success(Unit)
        coEvery { albums.removeFolderFromAlbum("album", "folder") } returns Result.Error(IllegalStateException())
        viewModel.deleteSelected()
        advanceUntilIdle()
        assertTrue(viewModel.selectedWallpapers.value.isEmpty())
        assertEquals(setOf("folder"), viewModel.selectedFolders.value)
        assertEquals(UiText.Resource(R.string.delete_items_failed), viewModel.message.value)
    }


    @Test fun `opening the album checks file access`() = runTest {
        advanceUntilIdle()
        coVerify(exactly = 1) { albums.syncAccess("album") }
    }

    @Test fun `skipped formats and the grant limit are reported`() = runTest {
        coEvery { imports.addImages(any(), any(), any()) } returns ImportResult(added = 2, skippedUnsupported = 3, grantsInUse = 450)
        viewModel.addWallpapers(listOf("image"))
        advanceUntilIdle()
        assertEquals(UiText.Plural(R.plurals.import_skipped_unsupported, 3), viewModel.message.value)
        assertEquals(GrantNotice.NearLimit(450), viewModel.grantNotice.value)

        viewModel.dismissMessage()
        viewModel.dismissGrantNotice()
        coEvery { imports.addImages(any(), any(), any()) } throws GrantLimitException(needed = 5, available = 2)
        viewModel.addWallpapers(listOf("image"))
        advanceUntilIdle()
        assertNull(viewModel.message.value)
        assertEquals(GrantNotice.LimitReached(needed = 5, available = 2), viewModel.grantNotice.value)
    }

    @Test fun `an import carries on when the album is closed and reports when it is opened again`() = runTest {
        val finish = CompletableDeferred<ImportResult>()
        coEvery { imports.addImages(any(), any(), any()) } coAnswers { finish.await() }
        viewModel.addWallpapers(listOf("image"))
        runCurrent()
        store.clear()  // Leaving the album clears its view model.
        runCurrent()
        assertTrue(albumImports.isRunning("album"))

        finish.complete(ImportResult(added = 1, skippedUnsupported = 2))
        advanceUntilIdle()
        assertFalse(albumImports.isRunning("album"))

        val reopened = albumViewModel()
        store.put("reopened", reopened)
        advanceUntilIdle()
        assertEquals(UiText.Plural(R.plurals.import_skipped_unsupported, 2), reopened.message.value)

        // Shown once: opening the album again later doesn't repeat it.
        val again = albumViewModel()
        store.put("again", again)
        advanceUntilIdle()
        assertNull(again.message.value)
    }

    @Test fun `a reopened album shows an import still in progress`() = runTest {
        coEvery { imports.addImages(any(), any(), any()) } coAnswers {
            thirdArg<(Int, Int) -> Unit>()(1, 4)
            awaitCancellation()
        }
        viewModel.addWallpapers(listOf("a", "b", "c", "d"))
        runCurrent()
        val reopened = albumViewModel()
        store.put("reopened", reopened)
        runCurrent()
        assertEquals(ImportProgress.Saving(1, 4), reopened.importProgress.value)
        reopened.cancelImport()
        advanceUntilIdle()
        assertEquals(ImportProgress.Idle, reopened.importProgress.value)
        assertEquals(ImportProgress.Idle, viewModel.importProgress.value)
    }

    @Test fun `a selection with only unsupported files says so`() = runTest {
        coEvery { imports.addImages(any(), any(), any()) } returns ImportResult(added = 0, skippedUnsupported = 1)
        viewModel.addWallpapers(listOf("image"))
        advanceUntilIdle()
        assertEquals(UiText.Resource(R.string.import_none_supported), viewModel.message.value)
    }
}
