package com.anthonyla.paperize.data.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.data.database.entities.WallpaperEntity
import com.anthonyla.paperize.data.database.entities.WallpaperQueueEntity

/** A queued image and the queue row it came from; a favourite can be queued twice in a round. */
data class QueuedWallpaper(
    @Embedded val wallpaper: WallpaperEntity,
    val queueId: Long
)

@Dao
interface WallpaperQueueDao {
    /**
     * Images that can't rotate right now (unreadable, excluded, or not a favourite while the album
     * rotates favourites only) stay queued but are passed over, so they resume their place. Once
     * only those remain, this returns null and the caller rebuilds the queue.
     * [avoidId] (the image on the other screen) is passed over the same way and keeps its place.
     */
    @Query("""
        SELECT w.*, wq.id AS queueId FROM wallpapers w
        INNER JOIN wallpaper_queue wq ON w.id = wq.wallpaperId
        WHERE wq.albumId = :albumId AND wq.screenType = :screenType
        AND $ROTATES
        AND (:avoidId IS NULL OR w.id != :avoidId)
        ORDER BY wq.queuePosition ASC, wq.id ASC
        LIMIT 1
    """)
    suspend fun getNextQueued(albumId: String, screenType: ScreenType, avoidId: String?): QueuedWallpaper?

    suspend fun getNextWallpaperInQueue(albumId: String, screenType: ScreenType, avoidId: String?): WallpaperEntity? =
        getNextQueued(albumId, screenType, avoidId)?.wallpaper

    suspend fun getNextWallpaperInQueue(albumId: String, screenType: ScreenType): WallpaperEntity? =
        getNextWallpaperInQueue(albumId, screenType, null)

    /** Takes one queue row, so a favourite queued twice keeps its other place. */
    @Transaction
    suspend fun getAndDequeueWallpaper(albumId: String, screenType: ScreenType, avoidId: String?): WallpaperEntity? {
        val next = getNextQueued(albumId, screenType, avoidId) ?: return null
        deleteQueueRow(next.queueId)
        return next.wallpaper
    }

    @Query("SELECT * FROM wallpaper_queue WHERE albumId = :albumId AND screenType = :screenType ORDER BY queuePosition ASC, id ASC")
    suspend fun getQueueItems(albumId: String, screenType: ScreenType): List<WallpaperQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<WallpaperQueueEntity>)

    @Query("DELETE FROM wallpaper_queue WHERE id = :queueId")
    suspend fun deleteQueueRow(queueId: Long)

    /** Remove the first queued copy of [wallpaperId]; a second copy of a favourite stays. */
    @Query("""
        DELETE FROM wallpaper_queue WHERE id = (
            SELECT id FROM wallpaper_queue
            WHERE albumId = :albumId AND screenType = :screenType AND wallpaperId = :wallpaperId
            ORDER BY queuePosition ASC, id ASC LIMIT 1
        )
    """)
    suspend fun deleteFirstQueueItem(albumId: String, screenType: ScreenType, wallpaperId: String)

    @Query("""
        SELECT MIN(queuePosition) FROM wallpaper_queue
        WHERE albumId = :albumId AND screenType = :screenType
    """)
    suspend fun getFirstQueuePosition(albumId: String, screenType: ScreenType): Int?

    /**
     * Put back an image that was taken from the queue but not shown. Other copies of it stay where
     * they are, so a favourite keeps its extra place in the round.
     */
    @Transaction
    suspend fun restoreQueueItem(
        albumId: String,
        screenType: ScreenType,
        wallpaperId: String
    ) {
        val firstPosition = getFirstQueuePosition(albumId, screenType) ?: 0
        insertQueueItems(
            listOf(
                WallpaperQueueEntity(
                    albumId = albumId,
                    wallpaperId = wallpaperId,
                    screenType = screenType,
                    queuePosition = firstPosition - 1
                )
            )
        )
    }

    @Query("DELETE FROM wallpaper_queue WHERE albumId = :albumId AND screenType = :screenType")
    suspend fun clearQueue(albumId: String, screenType: ScreenType)

    @Query("DELETE FROM wallpaper_queue WHERE albumId = :albumId")
    suspend fun clearAllQueues(albumId: String)

    @Query("DELETE FROM wallpaper_queue")
    suspend fun deleteAllQueueItems()

    @Transaction
    suspend fun rebuildQueue(
        albumId: String,
        screenType: ScreenType,
        wallpaperIds: List<String>
    ) {
        clearQueue(albumId, screenType)
        val items = wallpaperIds.mapIndexed { index, wallpaperId ->
            WallpaperQueueEntity(
                albumId = albumId,
                wallpaperId = wallpaperId,
                screenType = screenType,
                queuePosition = index
            )
        }
        insertQueueItems(items)
    }
}
