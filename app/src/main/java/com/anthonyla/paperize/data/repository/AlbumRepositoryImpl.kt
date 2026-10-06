package com.anthonyla.paperize.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.anthonyla.paperize.core.FavoritesMode
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.core.util.generateId
import com.anthonyla.paperize.data.database.PaperizeDatabase
import com.anthonyla.paperize.data.mapper.decodeAlbumEffects
import com.anthonyla.paperize.data.mapper.encodeAlbumEffects
import com.anthonyla.paperize.data.mapper.toDomainModel
import com.anthonyla.paperize.data.mapper.toEntity
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.model.AlbumSummary
import com.anthonyla.paperize.domain.model.Folder
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.source.DocumentSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val documents: DocumentSource,
    private val database: PaperizeDatabase
) : AlbumRepository {
    private val albumDao = database.albumDao()
    private val wallpaperDao = database.wallpaperDao()
    private val folderDao = database.folderDao()

    companion object {
        private const val TAG = "AlbumRepository"
        private const val WALLPAPER_BATCH_SIZE = 500
    }

    override fun getAlbumSummaries(): Flow<List<AlbumSummary>> =
        albumDao.getAlbumSummaries().map { it.map { summary -> summary.toDomainModel() } }

    override fun getAlbumById(albumId: String): Flow<Album?> =
        albumDao.getAlbumWithDetails(albumId).map { it?.toDomainModel() }

    override fun getFolderById(folderId: String): Flow<Folder?> =
        folderDao.getFolderWithWallpapers(folderId).map { it?.toDomainModel() }

    override suspend fun createAlbum(name: String, coverUri: String?): Result<Album> = Result.runCatching {
        val now = System.currentTimeMillis()
        val album = Album(id = generateId(), name = name, coverUri = coverUri, createdAt = now, modifiedAt = now)
        albumDao.insertAlbum(album.toEntity())
        album
    }

    override suspend fun deleteAlbum(albumId: String): Result<Unit> = Result.runCatching {
        val (imageUris, folderUris) = database.withTransaction {
            val uris = wallpaperDao.getDirectUris(albumId) to folderDao.getFoldersByAlbum(albumId).map { it.uri }
            albumDao.deleteAlbumById(albumId)
            uris
        }
        releaseUnusedGrants(imageUris, folderUris)
    }

    override suspend fun addWallpapersToAlbum(
        albumId: String,
        wallpapers: List<Wallpaper>,
        onProgress: (saved: Int, total: Int) -> Unit
    ): Result<Int> = Result.runCatching {
        database.withTransaction {
            checkNotNull(albumDao.getAlbumById(albumId))
            insertNewWallpapers(albumId, wallpapers, onProgress)
        }
    }

    override suspend fun addFolderToAlbum(
        albumId: String,
        folder: Folder,
        onProgress: (saved: Int, total: Int) -> Unit
    ): Result<Boolean> = Result.runCatching {
        database.withTransaction {
            checkNotNull(albumDao.getAlbumById(albumId))
            if (folderDao.containsUri(albumId, folder.uri)) return@withTransaction false
            folderDao.insertFolder(folder.toEntity().copy(
                albumId = albumId, displayOrder = folderDao.getMaxOrder(albumId) + 1, coverUri = null
            ))
            insertNewWallpapers(albumId, folder.wallpapers.map { it.copy(folderId = folder.id) }, onProgress)
            albumDao.updateAlbumModifiedTime(albumId, System.currentTimeMillis())
            true
        }
    }

    private suspend fun insertNewWallpapers(
        albumId: String,
        wallpapers: List<Wallpaper>,
        onProgress: (Int, Int) -> Unit
    ): Int {
        val additions = wallpapers.groupBy { it.folderId }.flatMap { (folderId, images) ->
            // A folder can be removed while its provider scan is in flight.
            if (folderId != null && folderDao.getFolderById(folderId)?.albumId != albumId) {
                emptyList()
            } else {
                val existing = wallpaperDao.getUrisInCollection(albumId, folderId).toHashSet()
                // New files join the end of their own group (direct images or one folder), so the
                // rotation keeps direct images first and then each folder in its saved order.
                var nextOrder = wallpaperDao.getMaxOrderInCollection(albumId, folderId) + 1
                images.filter { existing.add(it.uri) }.map {
                    it.copy(albumId = albumId, displayOrder = nextOrder++)
                }
            }
        }
        if (additions.isEmpty()) return 0
        insertWallpapersChunked(additions, onProgress)
        additions.mapNotNull { it.folderId }.toSet().forEach { folderId ->
            folderDao.updateFolderCover(folderId, wallpaperDao.getFolderCoverUri(folderId))
        }
        // Rotation queues are kept: the caller adds the new images to the rounds in progress.
        albumDao.updateAlbumModifiedTime(albumId, System.currentTimeMillis())
        updateAlbumCoverIfNeeded(albumId)
        return additions.size
    }

    /** Report chunk progress within the caller's transaction; cancellation rolls back every chunk. */
    private suspend fun insertWallpapersChunked(
        wallpapers: List<Wallpaper>,
        onProgress: (saved: Int, total: Int) -> Unit
    ) {
        val total = wallpapers.size
        var saved = 0
        wallpapers.chunked(WALLPAPER_BATCH_SIZE).forEach { chunk ->
            wallpaperDao.insertWallpapers(chunk.map { it.toEntity() })
            saved += chunk.size
            onProgress(saved, total)
        }
    }

    override suspend fun reorderAlbum(
        albumId: String,
        folders: List<Folder>,
        wallpapers: List<Wallpaper>
    ): Result<Unit> = Result.runCatching {
        database.withTransaction {
            folders.forEachIndexed { index, folder ->
                folderDao.updateFolderOrder(albumId, folder.id, index)
            }
            // Rotation follows direct images, then each folder in the saved order.
            (wallpapers.asSequence() + folders.asSequence().flatMap { it.wallpapers })
                .forEachIndexed { index, wallpaper ->
                    wallpaperDao.updateAlbumWallpaperOrder(albumId, wallpaper.id, index)
                }
            folderDao.refreshFolderCovers(albumId)
            updateAlbumCoverIfNeeded(albumId)
            albumDao.updateAlbumModifiedTime(albumId, System.currentTimeMillis())
            database.wallpaperQueueDao().clearAllQueues(albumId)
        }
    }

    override suspend fun removeWallpapersFromAlbum(
        albumId: String,
        wallpaperIds: List<String>
    ): Result<Unit> = Result.runCatching {
        if (wallpaperIds.isEmpty()) return@runCatching
        val imageUris = database.withTransaction {
            val uris = wallpaperIds.chunked(WALLPAPER_BATCH_SIZE).flatMap { wallpaperDao.getDirectUris(albumId, it) }
            wallpaperIds.chunked(WALLPAPER_BATCH_SIZE).forEach {
                wallpaperDao.deleteAlbumWallpapers(albumId, it)
            }
            albumDao.updateAlbumModifiedTime(albumId, System.currentTimeMillis())
            folderDao.refreshFolderCovers(albumId)
            updateAlbumCoverIfNeeded(albumId)
            uris
        }
        releaseUnusedGrants(imageUris, emptyList())
    }

    override suspend fun removeFolderFromAlbum(albumId: String, folderId: String): Result<Unit> = Result.runCatching {
        val folderUri = database.withTransaction {
            val uri = folderDao.getFolderById(folderId)?.takeIf { it.albumId == albumId }?.uri
            folderDao.deleteAlbumFolder(albumId, folderId)
            albumDao.updateAlbumModifiedTime(albumId, System.currentTimeMillis())
            updateAlbumCoverIfNeeded(albumId)
            uri
        }
        releaseUnusedGrants(emptyList(), listOfNotNull(folderUri))
    }

    override suspend fun deleteAllAlbums(): Result<Unit> = Result.runCatching {
        albumDao.deleteAllAlbums()
        // Nothing references any file now, so every grant this app holds is unused.
        withContext(NonCancellable) {
            documents.persistedReadGrants().forEach { releaseGrant(it) }
        }
    }

    override suspend fun pruneMissingEntries(albumId: String): Result<Int> = Result.runCatching {
        val folders = folderDao.getFoldersByAlbum(albumId)
        val missingFolders = folders.filter { documents.isMissing(it.uri, isTree = true) }.map { it.id }.toSet()
        val missingImages = mutableListOf<Wallpaper>()
        var afterId: String? = null
        while (true) {
            val batch = wallpaperDao.getWallpapersByAlbumPage(albumId, WALLPAPER_BATCH_SIZE, afterId)
            if (batch.isEmpty()) break
            // Only directly added images need a query each; a folder's refresh compares its
            // images with one scan of the folder instead.
            batch.filter { it.folderId == null && documents.isMissing(it.uri) }
                .mapTo(missingImages) { it.toDomainModel() }
            afterId = batch.last().id
        }
        val removed = database.withTransaction {
            var removed = missingFolders.sumOf { folderDao.deleteAlbumFolder(albumId, it) }
            missingImages.map { it.id }.chunked(WALLPAPER_BATCH_SIZE).forEach {
                removed += wallpaperDao.deleteAlbumWallpapers(albumId, it)
            }
            if (removed > 0) {
                folderDao.refreshFolderCovers(albumId)
                updateAlbumCoverIfNeeded(albumId)
                albumDao.updateAlbumModifiedTime(albumId, System.currentTimeMillis())
            }
            removed
        }
        releaseUnusedGrants(
            imageUris = missingImages.filter { it.folderId == null }.map { it.uri },
            folderUris = folders.filter { it.id in missingFolders }.map { it.uri }
        )
        removed
    }

    override suspend fun removeFolderImagesNotIn(folderId: String, foundUris: Set<String>): Result<Int> =
        Result.runCatching {
            database.withTransaction {
                val albumId = folderDao.getFolderById(folderId)?.albumId ?: return@withTransaction 0
                val gone = wallpaperDao.getFolderUris(folderId).filter { it.uri !in foundUris }.map { it.id }
                if (gone.isEmpty()) return@withTransaction 0
                // Folder images are covered by the folder's grant, so there is none to release.
                val removed = gone.chunked(WALLPAPER_BATCH_SIZE).sumOf { wallpaperDao.deleteAlbumWallpapers(albumId, it) }
                folderDao.refreshFolderCovers(albumId)
                updateAlbumCoverIfNeeded(albumId)
                albumDao.updateAlbumModifiedTime(albumId, System.currentTimeMillis())
                removed
            }
        }

    override suspend fun syncAccess(albumId: String): Result<Int> = Result.runCatching {
        val grants = documents.persistedReadGrants()
        database.withTransaction {
            val folders = folderDao.getFoldersByAlbum(albumId)
            val images = wallpaperDao.getDirectAccess(albumId)
            // An app that still has library entries never legitimately holds no grants at all;
            // treat that as a failed read rather than marking every image unreadable.
            if (grants.isEmpty() && (folders.isNotEmpty() || images.isNotEmpty())) {
                Log.w(TAG, "No persisted grants reported; keeping the current access flags")
                return@withTransaction 0
            }
            var changed = folders.sumOf { wallpaperDao.setFolderAccessLost(it.id, it.uri !in grants) }
            val (lost, readable) = images.partition { it.uri !in grants }
            lost.map { it.id }.chunked(WALLPAPER_BATCH_SIZE).forEach { changed += wallpaperDao.setAccessLost(it, true) }
            readable.map { it.id }.chunked(WALLPAPER_BATCH_SIZE).forEach { changed += wallpaperDao.setAccessLost(it, false) }
            changed
        }
    }

    override suspend fun relinkWallpaper(wallpaperId: String, uri: String): Result<Unit> = Result.runCatching {
        wallpaperDao.relink(wallpaperId, uri)
    }

    override suspend fun isAlbumNameTaken(name: String, exceptAlbumId: String?): Boolean {
        val wanted = name.trim()
        return albumDao.getAlbumNames().any { it.id != exceptAlbumId && it.name.trim().equals(wanted, ignoreCase = true) }
    }

    override suspend fun renameAlbum(albumId: String, name: String): Result<Boolean> = Result.runCatching {
        val newName = name.trim()
        require(newName.isNotEmpty()) { "Album name cannot be empty" }
        database.withTransaction {
            if (isAlbumNameTaken(newName, exceptAlbumId = albumId)) return@withTransaction false
            check(albumDao.renameAlbum(albumId, newName) == 1) { "Album $albumId not found" }
            true
        }
    }

    override suspend fun setFavorite(
        albumId: String,
        wallpaperIds: Collection<String>,
        favorite: Boolean
    ): Result<List<String>> = Result.runCatching {
        database.withTransaction {
            wallpaperIds.distinct().chunked(WALLPAPER_BATCH_SIZE).flatMap { chunk ->
                wallpaperDao.getIdsByFavorite(albumId, chunk, !favorite).also { changed ->
                    if (changed.isNotEmpty()) wallpaperDao.setFavorite(albumId, changed, favorite)
                }
            }
        }
    }

    override suspend fun setExcluded(
        albumId: String,
        wallpaperIds: Collection<String>,
        excluded: Boolean
    ): Result<List<String>> = Result.runCatching {
        database.withTransaction {
            wallpaperIds.distinct().chunked(WALLPAPER_BATCH_SIZE).flatMap { chunk ->
                wallpaperDao.getIdsByExcluded(albumId, chunk, !excluded).also { changed ->
                    if (changed.isNotEmpty()) wallpaperDao.setExcluded(albumId, changed, excluded)
                }
            }
        }
    }

    override suspend fun setFavoritesMode(albumId: String, mode: FavoritesMode): Result<Boolean> = Result.runCatching {
        database.withTransaction {
            val changed = albumDao.setFavoritesMode(albumId, mode) > 0
            // Which images rotate, and how often, depends on the mode: every screen starts a new
            // round, as it does when the album is reordered.
            if (changed) database.wallpaperQueueDao().clearAllQueues(albumId)
            changed
        }
    }

    override suspend fun getAlbumEffects(albumId: String): WallpaperEffects? =
        decodeAlbumEffects(albumDao.getEffects(albumId))

    override fun getAlbumEffectsFlow(albumId: String): Flow<WallpaperEffects?> =
        albumDao.getEffectsFlow(albumId).distinctUntilChanged().map { decodeAlbumEffects(it) }

    override suspend fun setAlbumEffects(albumId: String, effects: WallpaperEffects?): Result<Unit> = Result.runCatching {
        albumDao.setEffects(albumId, encodeAlbumEffects(effects?.validate()))
    }

    /**
     * Give back grants that no album uses any more. Android lets an app keep only a limited number
     * of persisted grants, so leaked ones eventually cost access to images that are still in use.
     */
    private suspend fun releaseUnusedGrants(imageUris: Collection<String>, folderUris: Collection<String>) =
        withContext(NonCancellable) {
            imageUris.toSet().filter { wallpaperDao.countDirectReferences(it) == 0 }.forEach { releaseGrant(it) }
            folderUris.toSet().filter { folderDao.countReferences(it) == 0 }.forEach { releaseGrant(it) }
        }

    private suspend fun releaseGrant(uri: String) {
        try {
            documents.releaseReadPermission(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not release the grant for $uri", e)
        }
    }

    /** Call within a transaction; direct wallpapers take priority over folder images. */
    private suspend fun updateAlbumCoverIfNeeded(albumId: String) {
        val album = albumDao.getAlbumById(albumId) ?: return
        val newCoverUri = wallpaperDao.getAlbumCoverUri(albumId)

        if (album.coverUri != newCoverUri) {
            albumDao.updateAlbumCover(albumId, newCoverUri, System.currentTimeMillis())
        }
    }
}
