package com.anthonyla.paperize.domain.repository

import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.domain.model.Wallpaper
import kotlinx.coroutines.flow.Flow

interface WallpaperRepository {
    suspend fun getWallpaperById(wallpaperId: String): Wallpaper?

    /** Every image in the album, including ones that cannot rotate right now. */
    suspend fun countWallpapers(albumId: String): Int

    suspend fun getNextWallpaperInQueue(albumId: String, screenType: ScreenType): Wallpaper?

    /**
     * Atomically get and remove next wallpaper from queue
     * Prevents race conditions when multiple wallpaper changes happen simultaneously.
     * [avoidId] is passed over and keeps its place in the queue.
     */
    suspend fun getAndDequeueWallpaper(albumId: String, screenType: ScreenType, avoidId: String? = null): Wallpaper?

    suspend fun removeWallpaperFromQueue(
        albumId: String,
        screenType: ScreenType,
        wallpaperId: String
    )

    /**
     * Restore a prepared wallpaper to the front when applying it did not succeed.
     */
    suspend fun restoreWallpaperToQueueFront(
        albumId: String,
        screenType: ScreenType,
        wallpaperId: String
    )

    /**
     * Start a new round once nothing but [avoidId] is left to rotate; preserve a queue already
     * filled by another caller. Each screen shuffles on its own. [startHalfway] starts a sequential
     * round half-way through the album (a lock screen sharing the home screen's album).
     */
    suspend fun ensureWallpaperQueue(
        albumId: String,
        screenType: ScreenType,
        shuffle: Boolean = false,
        startHalfway: Boolean = false,
        avoidId: String? = null
    ): Result<Unit>

    /**
     * Add newly imported images to every queue of the album that is part-way through its round,
     * so the progress made so far survives. [wallpaperIds] not in the album are ignored.
     */
    suspend fun addToQueues(albumId: String, wallpaperIds: Collection<String>, shuffle: Boolean): Result<Unit>

    /**
     * Clear all queues for all albums
     * Used when shuffle setting changes to force rebuild with new mode
     */
    suspend fun clearAllQueues(): Result<Unit>

    suspend fun getCurrentWallpaper(albumId: String, screenType: ScreenType): Wallpaper?

    fun getCurrentWallpaperFlow(albumId: String, screenType: ScreenType): Flow<Wallpaper?>

    suspend fun setCurrentWallpaper(albumId: String, screenType: ScreenType, wallpaperId: String)

}
