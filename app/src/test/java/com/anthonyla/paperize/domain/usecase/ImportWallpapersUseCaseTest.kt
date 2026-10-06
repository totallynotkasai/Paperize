package com.anthonyla.paperize.domain.usecase

import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.core.WallpaperSourceType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.model.Folder
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

class ImportWallpapersUseCaseTest {
    private val documents = mockk<DocumentSource>()
    private val repository = mockk<AlbumRepository>()
    private val addToRotation = mockk<AddToRotationUseCase>(relaxed = true)
    private val useCase = ImportWallpapersUseCase(documents, repository, addToRotation)

    @Before fun setUp() {
        coEvery { documents.persistedReadGrants() } returns emptySet()
        coEvery { documents.releaseReadPermission(any()) } just Runs
        coEvery { repository.syncAccess(any()) } returns Result.Success(0)
    }

    @Test fun `image imports retain permission once and preserve metadata`() = runTest {
        coEvery { documents.retainReadPermission("image") } just Runs
        coEvery { documents.readImage("image") } returns SourceImage("image", "photo.jpg", 42L)
        coEvery { repository.addWallpapersToAlbum(any(), any(), any()) } returns Result.Success(1)
        val result = useCase.addImages("album", listOf("image", "image")) { _, _ -> }
        assertEquals(1, result.added)
        assertFalse(result.alreadyInAlbum)
        assertEquals(1, result.grantsInUse)
        coVerify(exactly = 1) { documents.retainReadPermission("image") }
        val saved = slot<List<Wallpaper>>()
        coVerify { repository.addWallpapersToAlbum("album", capture(saved), any()) }
        saved.captured.single().let { image ->
            assertTrue(image.uri == "image" && image.dateModified == 42L && image.folderId == null)
        }
        // New images join the rotation rounds already in progress instead of restarting them.
        coVerify(exactly = 1) { addToRotation("album", listOf(saved.captured.single().id)) }
    }

    @Test fun `images already in the album leave the rotation alone`() = runTest {
        coEvery { documents.retainReadPermission("image") } just Runs
        coEvery { documents.readImage("image") } returns SourceImage("image", "photo.jpg", 42L)
        coEvery { repository.addWallpapersToAlbum(any(), any(), any()) } returns Result.Success(0)
        assertTrue(useCase.addImages("album", listOf("image")) { _, _ -> }.alreadyInAlbum)
        coVerify(exactly = 0) { addToRotation(any(), any()) }
    }

    @Test fun `permission failure does not save an inaccessible image`() = runTest {
        coEvery { documents.readImage("blocked") } returns SourceImage("blocked", "photo.png", 1L)
        coEvery { documents.retainReadPermission("blocked") } throws SecurityException("No grant")
        try {
            useCase.addImages("album", listOf("blocked")) { _, _ -> }
            fail("Expected permission error")
        } catch (_: SecurityException) { }
        coVerify(exactly = 0) { repository.addWallpapersToAlbum(any(), any(), any()) }
    }

    @Test fun `unsupported formats are skipped without taking a grant and are counted`() = runTest {
        coEvery { documents.readImage("tiff") } returns SourceImage("tiff", "scan.tiff", 1L, "image/tiff")
        coEvery { documents.readImage("svg") } returns SourceImage("svg", "logo.svg", 1L, "image/svg+xml")
        coEvery { documents.retainReadPermission("svg") } just Runs
        coEvery { repository.addWallpapersToAlbum(any(), any(), any()) } returns Result.Success(1)

        val result = useCase.addImages("album", listOf("tiff", "svg")) { _, _ -> }

        assertEquals(1, result.added)
        assertEquals(1, result.skippedUnsupported)
        coVerify(exactly = 0) { documents.retainReadPermission("tiff") }
        coVerify { repository.addWallpapersToAlbum("album", match { it.single().uri == "svg" }, any()) }
    }

    @Test fun `a selection with no supported image saves nothing`() = runTest {
        coEvery { documents.readImage("tiff") } returns SourceImage("tiff", "scan.tif", 1L)
        val result = useCase.addImages("album", listOf("tiff")) { _, _ -> }
        assertEquals(ImportResult(added = 0, skippedUnsupported = 1), result)
        coVerify(exactly = 0) { repository.addWallpapersToAlbum(any(), any(), any()) }
    }

    @Test fun `imports stop before Android's grant limit and take no grants`() = runTest {
        val held = (1 until Constants.MAX_PERSISTED_URI_GRANTS).map { "held-$it" }.toSet()
        coEvery { documents.persistedReadGrants() } returns held
        coEvery { documents.readImage(any()) } answers { SourceImage(firstArg(), "${firstArg<String>()}.jpg", 1L) }

        try {
            useCase.addImages("album", listOf("new-1", "new-2", "held-1")) { _, _ -> }
            fail("Expected the grant limit to stop the import")
        } catch (e: GrantLimitException) {
            assertEquals(2, e.needed)
            assertEquals(1, e.available)
        }
        coVerify(exactly = 0) { documents.retainReadPermission(any()) }
    }

    @Test fun `imports near the grant limit report how many grants are in use`() = runTest {
        val held = (1..Constants.PERSISTED_URI_GRANT_WARNING).map { "held-$it" }.toSet()
        coEvery { documents.persistedReadGrants() } returns held
        coEvery { documents.readImage("image") } returns SourceImage("image", "photo.jpg", 1L)
        coEvery { documents.retainReadPermission("image") } just Runs
        coEvery { repository.addWallpapersToAlbum(any(), any(), any()) } returns Result.Success(1)

        val result = useCase.addImages("album", listOf("image")) { _, _ -> }

        assertEquals(Constants.PERSISTED_URI_GRANT_WARNING + 1, result.grantsInUse)
        assertTrue(result.nearGrantLimit)
    }

    @Test fun `a failed save gives back only the grants it took`() = runTest {
        coEvery { documents.persistedReadGrants() } returns setOf("old")
        coEvery { documents.readImage(any()) } answers { SourceImage(firstArg(), "${firstArg<String>()}.png", 1L) }
        coEvery { documents.retainReadPermission(any()) } just Runs
        coEvery { repository.addWallpapersToAlbum(any(), any(), any()) } returns Result.Error(IllegalStateException("Disk full"))

        try {
            useCase.addImages("album", listOf("old", "new")) { _, _ -> }
            fail("Expected the save to fail")
        } catch (_: IllegalStateException) { }
        coVerify(exactly = 1) { documents.releaseReadPermission("new") }
        coVerify(exactly = 0) { documents.releaseReadPermission("old") }
    }

    @Test fun `folder import keeps metadata and assigns membership`() = runTest {
        coEvery { documents.retainReadPermission("tree") } just Runs
        every { repository.getAlbumById("album") } returns flowOf(Album.empty("album"))
        coEvery { documents.readFolder("tree", any()) } returns
            SourceFolder("Photos", listOf(SourceImage("image", "photo.png", 42L)), skippedUnsupported = 2)
        coEvery { repository.addFolderToAlbum(any(), any(), any()) } returns Result.Success(true)
        val result = useCase.addFolder("album", "tree", {}, { _, _ -> })
        assertEquals(1, result.added)
        assertEquals(2, result.skippedUnsupported)
        val saved = slot<Folder>()
        coVerify { repository.addFolderToAlbum("album", capture(saved), any()) }
        val folder = saved.captured
        assertTrue(folder.name == "Photos" && folder.wallpapers.single().let {
            it.folderId == folder.id && it.albumId == "album" && it.sourceType == WallpaperSourceType.FOLDER &&
                it.dateModified == 42L && it.fileName == "photo.png"
        })
        coVerify(exactly = 1) { addToRotation("album", folder.wallpapers.map { it.id }) }
    }

    @Test fun `existing folder restores access without rescanning`() = runTest {
        coEvery { documents.retainReadPermission("tree") } just Runs
        every { repository.getAlbumById("album") } returns flowOf(Album.empty("album").copy(
            folders = listOf(Folder.empty().copy(uri = "tree"))
        ))
        val result = useCase.addFolder("album", "tree", {}, { _, _ -> })
        assertTrue(result.alreadyInAlbum)
        coVerify { documents.retainReadPermission("tree") }
        coVerify { repository.syncAccess("album") }
        coVerify(exactly = 0) { documents.readFolder(any(), any()) }
        coVerify(exactly = 0) { documents.releaseReadPermission(any()) }
    }

    @Test fun `failed and cancelled scans do not save a partial folder or keep its grant`() = runTest {
        coEvery { documents.retainReadPermission("tree") } just Runs
        every { repository.getAlbumById("album") } returns flowOf(Album.empty("album"))
        for (failure in listOf(java.io.IOException("Unavailable"), CancellationException("Cancelled"))) {
            coEvery { documents.readFolder("tree", any()) } throws failure
            try {
                useCase.addFolder("album", "tree", {}, { _, _ -> })
                fail("Expected scan failure")
            } catch (actual: Exception) { assertSame(failure, actual) }
        }
        coVerify(exactly = 0) { repository.addFolderToAlbum(any(), any(), any()) }
        coVerify(exactly = 2) { documents.releaseReadPermission("tree") }
    }

    @Test fun `restoring access matches by uri, then by name, and relinks moved files`() = runTest {
        val lostSame = Wallpaper.empty("same", "album").copy(uri = "content://same", fileName = "a.jpg", accessLost = true)
        val lostMoved = Wallpaper.empty("moved", "album").copy(uri = "content://old", fileName = "b.jpg", accessLost = true)
        val readable = Wallpaper.empty("fine", "album").copy(uri = "content://fine", fileName = "c.jpg")
        every { repository.getAlbumById("album") } returns
            flowOf(Album.empty("album").copy(wallpapers = listOf(lostSame, lostMoved, readable)))
        coEvery { documents.readImage("content://new") } returns SourceImage("content://new", "b.jpg", 1L)
        coEvery { documents.readImage("content://other") } returns SourceImage("content://other", "zzz.jpg", 1L)
        coEvery { documents.retainReadPermission(any()) } just Runs
        coEvery { repository.relinkWallpaper(any(), any()) } returns Result.Success(Unit)

        val restored = useCase.restoreImageAccess(
            "album", listOf("content://same", "content://new", "content://other", "content://fine")
        )

        assertEquals(2, restored)
        coVerify { documents.retainReadPermission("content://same") }
        coVerify { documents.retainReadPermission("content://new") }
        coVerify(exactly = 0) { documents.retainReadPermission("content://other") }
        coVerify(exactly = 0) { documents.retainReadPermission("content://fine") }
        coVerify(exactly = 1) { repository.relinkWallpaper("moved", "content://new") }
        coVerify { repository.syncAccess("album") }
    }

    @Test fun `restoring a folder only grants folders already in the album`() = runTest {
        every { repository.getAlbumById("album") } returns flowOf(Album.empty("album").copy(
            folders = listOf(Folder.empty("folder", "album").copy(uri = "tree"))
        ))
        coEvery { documents.retainReadPermission("tree") } just Runs

        assertFalse(useCase.restoreFolderAccess("album", "elsewhere"))
        assertTrue(useCase.restoreFolderAccess("album", "tree"))
        coVerify(exactly = 0) { documents.retainReadPermission("elsewhere") }
        coVerify(exactly = 1) { repository.syncAccess("album") }
    }
}
