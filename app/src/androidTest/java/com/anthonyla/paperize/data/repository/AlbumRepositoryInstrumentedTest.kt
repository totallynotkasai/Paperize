package com.anthonyla.paperize.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.data.database.PaperizeDatabase
import com.anthonyla.paperize.data.database.entities.AlbumEntity
import com.anthonyla.paperize.data.mapper.toEntity
import com.anthonyla.paperize.domain.model.Folder
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.source.DocumentSource
import com.anthonyla.paperize.domain.source.SourceFolder
import com.anthonyla.paperize.domain.source.SourceImage
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumRepositoryInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, PaperizeDatabase::class.java).build()
    private val wallpapers = WallpaperRepositoryImpl(db)
    private val albums = AlbumRepositoryImpl(com.anthonyla.paperize.data.source.AndroidDocumentSource(context), db)

    @After fun close() = db.close()

    @Test fun cancellingAChunkedFolderImportRollsBackAllRows() = runBlocking {
        db.albumDao().insertAlbum(AlbumEntity("album", "Album", null))
        val folder = Folder.empty("folder", "album").copy(
            wallpapers = (0..599).map { Wallpaper.empty("image-$it", "album").copy(folderId = "folder", uri = "content://image-$it") }
        )
        try {
            albums.addFolderToAlbum("album", folder) { saved, _ ->
                if (saved >= 500) throw CancellationException("User cancelled")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(0, db.wallpaperDao().getWallpaperCountByAlbum("album"))
        assertNull(db.folderDao().getFolderWithWallpapers("folder").first())
        assertNull(db.albumDao().getAlbumById("album")?.coverUri)
    }

    @Test fun importingImagesUpdatesCoverAndRotationQueueTogether() = runBlocking {
        db.albumDao().insertAlbum(AlbumEntity("album", "Album", null))
        val first = Wallpaper.empty("first", "album").copy(uri = "content://first")
        db.wallpaperDao().insertWallpaper(first.toEntity())
        db.wallpaperQueueDao().rebuildQueue("album", ScreenType.HOME, listOf("first"))
        db.folderDao().insertFolder(Folder.empty("folder", "album").toEntity())
        val second = first.copy(id = "second", uri = "content://second", folderId = "folder")

        assertEquals(Result.Success(1), albums.addWallpapersToAlbum("album", listOf(second)))
        assertTrue(db.wallpaperQueueDao().getQueueItems("album", ScreenType.HOME).isEmpty())
        assertEquals(2, db.wallpaperDao().getWallpaperCountByAlbum("album"))
        assertEquals("content://second", db.folderDao().getFolderById("folder")?.coverUri)
    }

    @Test fun removingCoverUsesOnlyWallpapersFromThatAlbum() = runBlocking {
        db.albumDao().insertAlbum(AlbumEntity("one", "One", "content://shared"))
        db.albumDao().insertAlbum(AlbumEntity("two", "Two", "content://shared"))
        val cover = Wallpaper.empty("cover", "one").copy(uri = "content://shared")
        val replacement = cover.copy(id = "replacement", uri = "content://replacement", displayOrder = 1)
        db.wallpaperDao().insertWallpapers(listOf(cover, replacement, cover.copy(id = "other", albumId = "two")).map { it.toEntity() })

        assertEquals(Result.Success(Unit), albums.removeWallpapersFromAlbum("one", listOf("cover", "other")))
        assertEquals("content://replacement", db.albumDao().getAlbumById("one")?.coverUri)
        assertEquals("content://shared", db.albumDao().getAlbumById("two")?.coverUri)
        assertNotNull(db.wallpaperDao().getWallpaperById("other"))
    }

    @Test fun coverRefreshUsesDisplayOrderAndClearsEmptyFolders() = runBlocking {
        db.albumDao().insertAlbum(AlbumEntity("album", "Album", null))
        db.folderDao().insertFolder(Folder.empty("folder", "album").toEntity())
        val later = Wallpaper.empty("later", "album").copy(folderId = "folder", uri = "content://later", displayOrder = 9)
        val first = later.copy(id = "first", uri = "content://first", displayOrder = 1)
        val direct = later.copy(id = "direct", folderId = null, uri = "content://direct", displayOrder = 99)
        db.wallpaperDao().insertWallpapers(listOf(later, first, direct).map { it.toEntity() })

        db.folderDao().refreshFolderCovers("album")
        assertEquals("content://first", db.folderDao().getFolderById("folder")?.coverUri)
        assertEquals("content://direct", db.wallpaperDao().getAlbumCoverUri("album"))

        db.wallpaperDao().deleteWallpapersByFolder("folder")
        db.folderDao().refreshFolderCovers("album")
        assertNull(db.folderDao().getFolderById("folder")?.coverUri)
    }

    @Test fun concurrentImportsDeduplicateInsideTheTransaction() = runBlocking {
        db.albumDao().insertAlbum(AlbumEntity("album", "Album", null))
        val image = Wallpaper.empty("one", "album").copy(uri = "content://same")
        val first = async(Dispatchers.IO) { albums.addWallpapersToAlbum("album", listOf(image)).getOrThrow() }
        val second = async(Dispatchers.IO) { albums.addWallpapersToAlbum("album", listOf(image.copy(id = "two"))).getOrThrow() }
        assertEquals(1, first.await() + second.await())
        assertEquals(1, db.wallpaperDao().getWallpaperCountByAlbum("album"))

        db.folderDao().insertFolder(Folder.empty("folder", "album").toEntity())
        assertEquals(Result.Success(1), albums.addWallpapersToAlbum("album", listOf(image.copy(id = "folder-image", folderId = "folder"))))
        db.folderDao().deleteFolderById("folder")
        assertEquals(Result.Success(0), albums.addWallpapersToAlbum("album", listOf(image.copy(id = "stale", folderId = "folder"))))
    }

    @Test fun reorderPersistsNestedImagesWithoutOverwritingMetadata() = runBlocking {
        db.albumDao().insertAlbum(AlbumEntity("album", "Album", null))
        val first = Wallpaper.empty("first", "album").copy(uri = "content://first", folderId = "folder")
        val second = first.copy(id = "second", uri = "content://second")
        val folder = Folder.empty("folder", "album").copy(wallpapers = listOf(first, second))
        albums.addFolderToAlbum("album", folder).getOrThrow()
        wallpapers.ensureWallpaperQueue("album", ScreenType.HOME, false).getOrThrow()
        db.wallpaperDao().updateWallpaper(first.copy(fileName = "new-name.jpg").toEntity())

        albums.reorderAlbum("album", listOf(folder.copy(wallpapers = listOf(second, first))), emptyList()).getOrThrow()
        assertEquals(listOf("second", "first"), db.wallpaperDao().getOrderedWallpaperIdsByAlbum("album"))
        assertEquals("new-name.jpg", db.wallpaperDao().getWallpaperById("first")?.fileName)
        assertEquals("content://second", db.folderDao().getFolderById("folder")?.coverUri)
        assertTrue(db.wallpaperQueueDao().getQueueItems("album", ScreenType.HOME).isEmpty())
    }

    @Test fun failedReorderRollsBackEarlierRowsAndPreservesQueue() = runBlocking {
        db.albumDao().insertAlbum(AlbumEntity("album", "Album", null))
        val first = Wallpaper.empty("first", "album").copy(uri = "content://first")
        val second = first.copy(id = "second", uri = "content://second")
        albums.addWallpapersToAlbum("album", listOf(first, second)).getOrThrow()
        wallpapers.ensureWallpaperQueue("album", ScreenType.HOME, false).getOrThrow()
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_order BEFORE UPDATE OF displayOrder ON wallpapers
            WHEN NEW.id = 'first' BEGIN SELECT RAISE(ABORT, 'test write failure'); END
        """)
        assertTrue(albums.reorderAlbum("album", emptyList(), listOf(second, first)) is Result.Error)
        assertEquals(listOf("first", "second"), db.wallpaperDao().getOrderedWallpaperIdsByAlbum("album"))
        assertEquals(listOf("first", "second"), db.wallpaperQueueDao().getQueueItems("album", ScreenType.HOME).map { it.wallpaperId })
    }

    @Test fun concurrentQueueCreationKeepsBothScreensInSyncAndDoesNotRefillConsumedItems() = runBlocking {
        db.albumDao().insertAlbum(AlbumEntity("album", "Album", null))
        albums.addWallpapersToAlbum("album", (1..8).map {
            Wallpaper.empty("image-$it", "album").copy(uri = "content://image-$it")
        }).getOrThrow()
        val home = async(Dispatchers.IO) { wallpapers.ensureWallpaperQueue("album", ScreenType.HOME, true).getOrThrow() }
        val lock = async(Dispatchers.IO) { wallpapers.ensureWallpaperQueue("album", ScreenType.LOCK, true).getOrThrow() }
        home.await(); lock.await()
        val expected = db.wallpaperQueueDao().getQueueItems("album", ScreenType.HOME).map { it.wallpaperId }
        assertEquals(8, expected.toSet().size)
        assertEquals(expected, db.wallpaperQueueDao().getQueueItems("album", ScreenType.LOCK).map { it.wallpaperId })
        wallpapers.getAndDequeueWallpaper("album", ScreenType.HOME)
        wallpapers.ensureWallpaperQueue("album", ScreenType.HOME, true).getOrThrow()
        assertEquals(expected.drop(1), db.wallpaperQueueDao().getQueueItems("album", ScreenType.HOME).map { it.wallpaperId })
    }

    /** Records grant changes instead of touching the system's real grants. */
    private class FakeDocumentSource(val grants: MutableSet<String> = mutableSetOf()) : DocumentSource {
        val released = mutableListOf<String>()
        override suspend fun retainReadPermission(uri: String) { grants += uri }
        override suspend fun releaseReadPermission(uri: String) { released += uri; grants -= uri }
        override suspend fun persistedReadGrants(): Set<String> = grants.toSet()
        override suspend fun readImage(uri: String) = SourceImage(uri, uri.substringAfterLast('/'), 0L)
        override suspend fun readFolder(uri: String, onProgress: (Int) -> Unit) = SourceFolder("folder", emptyList())
        override suspend fun isMissing(uri: String, isTree: Boolean) = false
    }

    private fun image(id: String, folderId: String? = null, album: String = "album") =
        Wallpaper.empty(id, album).copy(uri = "content://$id", folderId = folderId)

    @Test fun newFilesJoinTheEndOfTheirOwnGroup() = runBlocking {
        db.albumDao().insertAlbum(AlbumEntity("album", "Album", null))
        albums.addWallpapersToAlbum("album", listOf(image("d1"), image("d2"))).getOrThrow()
        albums.addFolderToAlbum("album", Folder.empty("f1", "album").copy(uri = "content://tree1", wallpapers = listOf(image("f1a"), image("f1b")))).getOrThrow()
        albums.addFolderToAlbum("album", Folder.empty("f2", "album").copy(uri = "content://tree2", wallpapers = listOf(image("f2a")))).getOrThrow()

        albums.addWallpapersToAlbum("album", listOf(image("d3"), image("f1c", folderId = "f1"))).getOrThrow()

        assertEquals(
            listOf("d1", "d2", "d3", "f1a", "f1b", "f1c", "f2a"),
            db.wallpaperDao().getOrderedWallpaperIdsByAlbum("album")
        )
    }

    @Test fun deletingReleasesOnlyGrantsNoAlbumStillUses() = runBlocking {
        val documents = FakeDocumentSource()
        val tracked = AlbumRepositoryImpl(documents, db)
        db.albumDao().insertAlbum(AlbumEntity("one", "One", null))
        db.albumDao().insertAlbum(AlbumEntity("two", "Two", null))
        tracked.addWallpapersToAlbum("one", listOf(image("shared", album = "one"), image("only-one", album = "one"))).getOrThrow()
        tracked.addWallpapersToAlbum("two", listOf(image("shared", album = "two").copy(id = "shared-2"))).getOrThrow()
        tracked.addFolderToAlbum("one", Folder.empty("tree-one", "one").copy(uri = "content://tree", wallpapers = listOf(image("in-tree", "tree-one", "one")))).getOrThrow()
        tracked.addFolderToAlbum("two", Folder.empty("tree-two", "two").copy(uri = "content://tree")).getOrThrow()

        tracked.removeWallpapersFromAlbum("one", listOf("only-one")).getOrThrow()
        assertEquals(listOf("content://only-one"), documents.released)

        tracked.deleteAlbum("one").getOrThrow()
        // Album two still uses the shared image and the shared folder.
        assertEquals(listOf("content://only-one"), documents.released)

        tracked.removeFolderFromAlbum("two", "tree-two").getOrThrow()
        tracked.deleteAlbum("two").getOrThrow()
        assertEquals(listOf("content://only-one", "content://tree", "content://shared"), documents.released)
    }

    @Test fun lostGrantsAreFlaggedAndSkippedUntilAccessReturns() = runBlocking {
        val documents = FakeDocumentSource(mutableSetOf("content://direct", "content://tree"))
        val tracked = AlbumRepositoryImpl(documents, db)
        db.albumDao().insertAlbum(AlbumEntity("album", "Album", null))
        tracked.addWallpapersToAlbum("album", listOf(image("direct"), image("other"))).getOrThrow()
        tracked.addFolderToAlbum("album", Folder.empty("folder", "album").copy(uri = "content://tree", wallpapers = listOf(image("in-folder")))).getOrThrow()
        wallpapers.ensureWallpaperQueue("album", ScreenType.HOME, false).getOrThrow()

        // "other" never had a grant; the folder's grant covers its image.
        assertEquals(1, tracked.syncAccess("album").getOrThrow())
        assertTrue(db.wallpaperDao().getWallpaperById("other")!!.accessLost)
        assertFalse(db.wallpaperDao().getWallpaperById("in-folder")!!.accessLost)

        documents.grants -= "content://tree"
        assertEquals(1, tracked.syncAccess("album").getOrThrow())
        assertTrue(db.wallpaperDao().getWallpaperById("in-folder")!!.accessLost)
        assertEquals("direct", wallpapers.getAndDequeueWallpaper("album", ScreenType.HOME)?.id)
        // Only unreadable images remain queued, so the queue reports empty and is rebuilt.
        assertNull(wallpapers.getAndDequeueWallpaper("album", ScreenType.HOME))
        assertEquals(3, wallpapers.countWallpapers("album"))

        documents.grants += setOf("content://tree", "content://other")
        assertEquals(2, tracked.syncAccess("album").getOrThrow())
        assertEquals("other", wallpapers.getAndDequeueWallpaper("album", ScreenType.HOME)?.id)
    }

    @Test fun anEmptyGrantListIsNotTrustedWhileTheLibraryHasEntries() = runBlocking {
        val tracked = AlbumRepositoryImpl(FakeDocumentSource(), db)
        db.albumDao().insertAlbum(AlbumEntity("album", "Album", null))
        tracked.addWallpapersToAlbum("album", listOf(image("direct"))).getOrThrow()
        assertEquals(0, tracked.syncAccess("album").getOrThrow())
        assertFalse(db.wallpaperDao().getWallpaperById("direct")!!.accessLost)
    }
}
