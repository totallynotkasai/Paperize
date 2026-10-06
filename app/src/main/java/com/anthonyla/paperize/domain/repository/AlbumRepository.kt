package com.anthonyla.paperize.domain.repository

import com.anthonyla.paperize.core.FavoritesMode
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.model.AlbumSummary
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getAlbumSummaries(): Flow<List<AlbumSummary>>

    fun getAlbumById(albumId: String): Flow<Album?>

    fun getFolderById(folderId: String): Flow<Folder?>

    suspend fun createAlbum(name: String, coverUri: String? = null): Result<Album>

    suspend fun deleteAlbum(albumId: String): Result<Unit>

    /** Returns the number of new images. Progress counts rows written within the transaction. */
    suspend fun addWallpapersToAlbum(
        albumId: String,
        wallpapers: List<Wallpaper>,
        onProgress: (saved: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Int>

    /** Returns false for an existing folder URI. Progress counts images written within the transaction. */
    suspend fun addFolderToAlbum(
        albumId: String,
        folder: Folder,
        onProgress: (saved: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Boolean>

    suspend fun reorderAlbum(albumId: String, folders: List<Folder>, wallpapers: List<Wallpaper>): Result<Unit>

    /** Also releases file grants that no album uses any more; the same applies to the deletes below. */
    suspend fun removeWallpapersFromAlbum(albumId: String, wallpaperIds: List<String>): Result<Unit>

    suspend fun removeFolderFromAlbum(albumId: String, folderId: String): Result<Unit>

    suspend fun deleteAllAlbums(): Result<Unit>

    /**
     * Removes missing folders and missing directly added images, and updates covers in the same
     * transaction. Images inside folders are left to [removeFolderImagesNotIn].
     */
    suspend fun pruneMissingEntries(albumId: String): Result<Int>

    /** Remove the folder's images that a fresh scan of it no longer found. Returns how many. */
    suspend fun removeFolderImagesNotIn(folderId: String, foundUris: Set<String>): Result<Int>

    /**
     * Mark images whose grant is gone (and clear the mark once it is back). Direct images need
     * their own grant; folder images are covered by their folder's grant. Returns rows changed.
     */
    suspend fun syncAccess(albumId: String): Result<Int>

    /** Point an image at a newly granted copy of the same file and mark it readable. */
    suspend fun relinkWallpaper(wallpaperId: String, uri: String): Result<Unit>

    /** Whether an album other than [exceptAlbumId] is called [name], ignoring case and outer spaces. */
    suspend fun isAlbumNameTaken(name: String, exceptAlbumId: String? = null): Boolean

    /** Returns false, and keeps the old name, when another album already has [name]. */
    suspend fun renameAlbum(albumId: String, name: String): Result<Boolean>

    /** Returns the images whose mark actually changed. */
    suspend fun setFavorite(albumId: String, wallpaperIds: Collection<String>, favorite: Boolean): Result<List<String>>

    /** Returns the images whose mark actually changed. Excluded images never rotate. */
    suspend fun setExcluded(albumId: String, wallpaperIds: Collection<String>, excluded: Boolean): Result<List<String>>

    /** A new mode starts a new rotation round for every screen. Returns false if it was already set. */
    suspend fun setFavoritesMode(albumId: String, mode: FavoritesMode): Result<Boolean>

    /** The album's own effects, or null when the screen's effects apply (plan 5.3). */
    suspend fun getAlbumEffects(albumId: String): WallpaperEffects?

    fun getAlbumEffectsFlow(albumId: String): Flow<WallpaperEffects?>

    /** Null goes back to the screen's own effects. */
    suspend fun setAlbumEffects(albumId: String, effects: WallpaperEffects?): Result<Unit>
}
