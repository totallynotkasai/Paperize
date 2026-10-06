package com.anthonyla.paperize.data.repository

import androidx.room.withTransaction
import com.anthonyla.paperize.core.FavoritesMode
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.util.QueueBuilder
import com.anthonyla.paperize.data.database.PaperizeDatabase
import com.anthonyla.paperize.data.database.entities.WallpaperCurrentEntity
import com.anthonyla.paperize.data.mapper.toDomainModel
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class WallpaperRepositoryImpl @Inject constructor(
    private val database: PaperizeDatabase
) : WallpaperRepository {

    private val albumDao = database.albumDao()
    private val wallpaperDao = database.wallpaperDao()
    private val wallpaperQueueDao = database.wallpaperQueueDao()
    private val wallpaperCurrentDao = database.wallpaperCurrentDao()

    override suspend fun getWallpaperById(wallpaperId: String): Wallpaper? =
        wallpaperDao.getWallpaperById(wallpaperId)?.toDomainModel()

    override suspend fun countWallpapers(albumId: String): Int =
        wallpaperDao.getWallpaperCountByAlbum(albumId)

    override suspend fun countIncludedWallpapers(albumId: String): Int =
        wallpaperDao.getIncludedCountByAlbum(albumId)

    override suspend fun getNextWallpaperInQueue(albumId: String, screenType: ScreenType): Wallpaper? =
        wallpaperQueueDao.getNextWallpaperInQueue(albumId, screenType)?.toDomainModel()

    override suspend fun getAndDequeueWallpaper(albumId: String, screenType: ScreenType, avoidId: String?): Wallpaper? =
        wallpaperQueueDao.getAndDequeueWallpaper(albumId, screenType, avoidId)?.toDomainModel()

    override suspend fun removeWallpaperFromQueue(
        albumId: String,
        screenType: ScreenType,
        wallpaperId: String
    ) {
        wallpaperQueueDao.deleteFirstQueueItem(albumId, screenType, wallpaperId)
    }

    override suspend fun restoreWallpaperToQueueFront(
        albumId: String,
        screenType: ScreenType,
        wallpaperId: String
    ) {
        wallpaperQueueDao.restoreQueueItem(albumId, screenType, wallpaperId)
    }

    override suspend fun ensureWallpaperQueue(
        albumId: String,
        screenType: ScreenType,
        shuffle: Boolean,
        startHalfway: Boolean,
        avoidId: String?
    ): Result<Unit> = Result.runCatching {
        database.withTransaction {
            // Recheck inside the transaction: another caller may already have filled it.
            if (wallpaperQueueDao.getNextWallpaperInQueue(albumId, screenType, avoidId) != null) return@withTransaction
            // Each screen gets its own order, so screens sharing an album don't move in step.
            val ids = if (shuffle) {
                val rotating = wallpaperDao.getWallpaperIdsByAlbum(albumId)
                if (albumDao.getFavoritesMode(albumId) == FavoritesMode.SHOW_MORE_OFTEN) {
                    QueueBuilder.weightedShuffle(rotating, wallpaperDao.getEligibleFavoriteIds(albumId).toHashSet())
                } else {
                    rotating.shuffled()
                }
            } else {
                wallpaperDao.getOrderedWallpaperIdsByAlbum(albumId)
                    .let { if (startHalfway) QueueBuilder.startingHalfway(it) else it }
            }
            wallpaperQueueDao.rebuildQueue(albumId, screenType, ids)
        }
    }

    override suspend fun addToQueues(
        albumId: String,
        wallpaperIds: Collection<String>,
        shuffle: Boolean
    ): Result<Unit> = Result.runCatching {
        if (wallpaperIds.isEmpty()) return@runCatching
        database.withTransaction { mergeIntoQueues(albumId, wallpaperIds, shuffle) }
    }

    /** Call within a transaction. */
    private suspend fun mergeIntoQueues(albumId: String, wallpaperIds: Collection<String>, shuffle: Boolean) {
        val rotation = wallpaperDao.getRotationOrder(albumId)
        val added = wallpaperIds.toHashSet()
        for (screen in ScreenType.entries) {
            val queue = wallpaperQueueDao.getQueueItems(albumId, screen).map { it.wallpaperId }
            // No queue yet: the next change builds a full one that already includes them.
            if (queue.isEmpty()) continue
            val queued = queue.toHashSet()
            val newIds = rotation.filter { it in added && it !in queued }
            if (newIds.isEmpty()) continue
            wallpaperQueueDao.rebuildQueue(albumId, screen, QueueBuilder.mergeNew(queue, newIds, rotation, shuffle))
        }
    }

    override suspend fun favoritesChanged(
        albumId: String,
        wallpaperIds: Collection<String>,
        favorite: Boolean,
        shuffle: Boolean
    ): Result<Unit> = Result.runCatching {
        if (wallpaperIds.isEmpty()) return@runCatching
        database.withTransaction {
            when (albumDao.getFavoritesMode(albumId)) {
                FavoritesMode.SHOW_MORE_OFTEN -> if (shuffle) {
                    for (screen in ScreenType.entries) {
                        val queue = wallpaperQueueDao.getQueueItems(albumId, screen).map { it.wallpaperId }
                        if (queue.isEmpty()) continue
                        val updated = if (favorite) QueueBuilder.addExtraTurns(queue, wallpaperIds)
                            else QueueBuilder.removeExtraTurns(queue, wallpaperIds)
                        if (updated != queue) wallpaperQueueDao.rebuildQueue(albumId, screen, updated)
                    }
                }
                // Images that stopped being favourites are passed over while they wait in the queue.
                FavoritesMode.FAVORITES_ONLY -> if (favorite) mergeIntoQueues(albumId, wallpaperIds, shuffle)
                else -> Unit
            }
        }
    }

    override suspend fun rotatesFavoritesOnly(albumId: String): Boolean = wallpaperDao.rotatesFavoritesOnly(albumId)

    override suspend fun clearQueues(albumId: String): Result<Unit> = Result.runCatching {
        wallpaperQueueDao.clearAllQueues(albumId)
    }

    override suspend fun clearAllQueues(): Result<Unit> = Result.runCatching {
        wallpaperQueueDao.deleteAllQueueItems()
    }

    override suspend fun getCurrentWallpaper(albumId: String, screenType: ScreenType): Wallpaper? =
        wallpaperCurrentDao.getCurrentWallpaper(albumId, screenType)?.toDomainModel()

    override fun getCurrentWallpaperFlow(albumId: String, screenType: ScreenType): Flow<Wallpaper?> =
        wallpaperCurrentDao.getCurrentWallpaperFlow(albumId, screenType).map { it?.toDomainModel() }

    override suspend fun setCurrentWallpaper(albumId: String, screenType: ScreenType, wallpaperId: String) {
        wallpaperCurrentDao.upsertCurrentWallpaper(
            WallpaperCurrentEntity(albumId = albumId, screenType = screenType, wallpaperId = wallpaperId)
        )
    }

}
