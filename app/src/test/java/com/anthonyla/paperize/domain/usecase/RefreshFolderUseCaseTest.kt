package com.anthonyla.paperize.domain.usecase

import com.anthonyla.paperize.testing.emptyFolder
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.source.DocumentSource
import com.anthonyla.paperize.domain.source.SourceFolder
import com.anthonyla.paperize.domain.source.SourceImage
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RefreshFolderUseCaseTest {
    private val albums = mockk<AlbumRepository>()
    private val documents = mockk<DocumentSource>()
    private val addToRotation = mockk<AddToRotationUseCase>(relaxed = true)
    private val refresh = RefreshFolderUseCase(albums, documents, addToRotation)

    @Before fun setUp() {
        every { albums.getFolderById("folder") } returns flowOf(emptyFolder("folder", "album").copy(uri = "tree"))
        coEvery { albums.removeFolderImagesNotIn("folder", any()) } returns Result.Success(0)
    }

    @Test fun `refresh assigns membership, reports inserted count and joins the rounds in progress`() = runTest {
        coEvery { documents.readFolder("tree", any()) } returns SourceFolder("Photos", listOf(
            SourceImage("existing", "old.png", 1L), SourceImage("new", "new.png", 2L)
        ))
        val passed = slot<List<Wallpaper>>()
        coEvery { albums.addWallpapersToAlbum("album", capture(passed), any()) } returns Result.Success(1)
        assertEquals(Result.Success(FolderRefreshResult(added = 1, removed = 0)), refresh("folder"))
        assertTrue(passed.captured.size == 2 && passed.captured.all { it.albumId == "album" && it.folderId == "folder" })
        coVerify { addToRotation("album", passed.captured.map { it.id }) }
    }

    @Test fun `files no longer in the folder are removed by comparing with the scan`() = runTest {
        coEvery { documents.readFolder("tree", any()) } returns SourceFolder("Photos", listOf(SourceImage("kept", "kept.png", 1L)))
        coEvery { albums.removeFolderImagesNotIn("folder", setOf("kept")) } returns Result.Success(2)
        coEvery { albums.addWallpapersToAlbum("album", any(), any()) } returns Result.Success(0)
        assertEquals(Result.Success(FolderRefreshResult(added = 0, removed = 2)), refresh("folder"))
        coVerify(exactly = 0) { documents.isMissing(any(), any()) }
        coVerify(exactly = 0) { addToRotation(any(), any()) }
    }

    @Test fun `an empty scan of a folder that has gone keeps its images for the album refresh`() = runTest {
        coEvery { documents.readFolder("tree", any()) } returns SourceFolder("Photos", emptyList())
        coEvery { documents.isMissing("tree", true) } returns true
        coEvery { albums.addWallpapersToAlbum("album", any(), any()) } returns Result.Success(0)
        assertEquals(Result.Success(FolderRefreshResult(added = 0, removed = 0)), refresh("folder"))
        coVerify(exactly = 0) { albums.removeFolderImagesNotIn(any(), any()) }

        // A folder that is still there but now empty loses its images.
        coEvery { documents.isMissing("tree", true) } returns false
        refresh("folder")
        coVerify(exactly = 1) { albums.removeFolderImagesNotIn("folder", emptySet()) }
    }

    @Test fun `failed scan preserves existing data and cancellation propagates`() = runTest {
        coEvery { documents.readFolder("tree", any()) } throws SecurityException()
        assertTrue(refresh("folder") is Result.Error)
        coEvery { documents.readFolder("tree", any()) } throws CancellationException()
        try { refresh("folder"); fail("Expected cancellation") } catch (_: CancellationException) { }
        coVerify(exactly = 0) { albums.addWallpapersToAlbum(any(), any(), any()) }
        coVerify(exactly = 0) { albums.removeFolderImagesNotIn(any(), any()) }
    }
}
