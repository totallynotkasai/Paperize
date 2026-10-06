package com.anthonyla.paperize.presentation.screens.album_view

import com.anthonyla.paperize.testing.emptyAlbum
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
    private val store = ViewModelStore()
    private lateinit var viewModel: AlbumViewViewModel

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { albums.getAlbumById("album") } returns flowOf(emptyAlbum("album"))
        coEvery { albums.syncAccess("album") } returns Result.Success(0)
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
        return AlbumViewViewModel(savedState, albums, imports, delete, albumImports)
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
