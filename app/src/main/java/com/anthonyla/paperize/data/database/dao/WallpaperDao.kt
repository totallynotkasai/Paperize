package com.anthonyla.paperize.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anthonyla.paperize.data.database.entities.WallpaperEntity

/** A directly added image and whether it was last seen without a covering grant. */
data class DirectWallpaperAccess(val id: String, val uri: String, val accessLost: Boolean)

data class WallpaperUri(val id: String, val uri: String)

@Dao
interface WallpaperDao {
    @Query("DELETE FROM wallpapers WHERE albumId = :albumId AND id IN (:ids)")
    suspend fun deleteAlbumWallpapers(albumId: String, ids: List<String>): Int

    @Query("SELECT uri FROM wallpapers WHERE albumId = :albumId AND folderId IS :folderId")
    suspend fun getUrisInCollection(albumId: String, folderId: String?): List<String>

    /** The highest order among direct images ([folderId] null) or within one folder. */
    @Query("SELECT COALESCE(MAX(displayOrder), -1) FROM wallpapers WHERE albumId = :albumId AND folderId IS :folderId")
    suspend fun getMaxOrderInCollection(albumId: String, folderId: String?): Int

    @Query("SELECT * FROM wallpapers WHERE albumId = :albumId AND (:afterId IS NULL OR id > :afterId) ORDER BY id LIMIT :limit")
    suspend fun getWallpapersByAlbumPage(albumId: String, limit: Int, afterId: String?): List<WallpaperEntity>

    @Query("""
        SELECT w.uri FROM wallpapers w LEFT JOIN folders f ON f.id = w.folderId
        WHERE w.albumId = :albumId ORDER BY $ROTATION_ORDER LIMIT 1
    """)
    suspend fun getAlbumCoverUri(albumId: String): String?

    @Query("SELECT uri FROM wallpapers WHERE folderId = :folderId ORDER BY displayOrder, id LIMIT 1")
    suspend fun getFolderCoverUri(folderId: String): String?

    @Query("SELECT * FROM wallpapers WHERE id = :wallpaperId")
    suspend fun getWallpaperById(wallpaperId: String): WallpaperEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpaper(wallpaper: WallpaperEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpapers(wallpapers: List<WallpaperEntity>)

    @Update
    suspend fun updateWallpaper(wallpaper: WallpaperEntity)

    @Query("UPDATE wallpapers SET displayOrder = :order WHERE id = :wallpaperId AND albumId = :albumId")
    suspend fun updateAlbumWallpaperOrder(albumId: String, wallpaperId: String, order: Int)

    @Query("DELETE FROM wallpapers WHERE folderId = :folderId")
    suspend fun deleteWallpapersByFolder(folderId: String)

    @Query("SELECT COUNT(*) FROM wallpapers WHERE albumId = :albumId")
    suspend fun getWallpaperCountByAlbum(albumId: String): Int

    @Query("SELECT COUNT(*) FROM wallpapers WHERE albumId = :albumId AND excluded = 0")
    suspend fun getIncludedCountByAlbum(albumId: String): Int

    /** Images a new round is built from (see [ROTATES]). */
    @Query("SELECT w.id FROM wallpapers w WHERE w.albumId = :albumId AND $ROTATES")
    suspend fun getWallpaperIdsByAlbum(albumId: String): List<String>

    @Query("""
        SELECT w.id FROM wallpapers w LEFT JOIN folders f ON f.id = w.folderId
        WHERE w.albumId = :albumId AND $ROTATES ORDER BY $ROTATION_ORDER
    """)
    suspend fun getOrderedWallpaperIdsByAlbum(albumId: String): List<String>

    @Query("SELECT w.id FROM wallpapers w WHERE w.albumId = :albumId AND $ELIGIBLE AND w.favorite = 1")
    suspend fun getEligibleFavoriteIds(albumId: String): List<String>

    @Query("SELECT $FAVORITES_ONLY_ACTIVE")
    suspend fun rotatesFavoritesOnly(albumId: String): Boolean

    @Query("SELECT id FROM wallpapers WHERE albumId = :albumId AND id IN (:ids) AND favorite = :favorite")
    suspend fun getIdsByFavorite(albumId: String, ids: List<String>, favorite: Boolean): List<String>

    @Query("SELECT id FROM wallpapers WHERE albumId = :albumId AND id IN (:ids) AND excluded = :excluded")
    suspend fun getIdsByExcluded(albumId: String, ids: List<String>, excluded: Boolean): List<String>

    @Query("UPDATE wallpapers SET favorite = :favorite WHERE albumId = :albumId AND id IN (:ids) AND favorite != :favorite")
    suspend fun setFavorite(albumId: String, ids: List<String>, favorite: Boolean): Int

    @Query("UPDATE wallpapers SET excluded = :excluded WHERE albumId = :albumId AND id IN (:ids) AND excluded != :excluded")
    suspend fun setExcluded(albumId: String, ids: List<String>, excluded: Boolean): Int

    /** Every image in rotation order, including ones that can't rotate right now. */
    @Query("""
        SELECT w.id FROM wallpapers w LEFT JOIN folders f ON f.id = w.folderId
        WHERE w.albumId = :albumId ORDER BY $ROTATION_ORDER
    """)
    suspend fun getRotationOrder(albumId: String): List<String>

    @Query("SELECT id, uri FROM wallpapers WHERE folderId = :folderId")
    suspend fun getFolderUris(folderId: String): List<WallpaperUri>

    /** Directly added images hold their own grant; folder images use their folder's grant. */
    @Query("SELECT uri FROM wallpapers WHERE albumId = :albumId AND folderId IS NULL")
    suspend fun getDirectUris(albumId: String): List<String>

    @Query("SELECT uri FROM wallpapers WHERE albumId = :albumId AND folderId IS NULL AND id IN (:ids)")
    suspend fun getDirectUris(albumId: String, ids: List<String>): List<String>

    /** Counts every album's direct images, because albums can share one granted file. */
    @Query("SELECT COUNT(*) FROM wallpapers WHERE uri = :uri AND folderId IS NULL")
    suspend fun countDirectReferences(uri: String): Int

    @Query("SELECT id, uri, accessLost FROM wallpapers WHERE albumId = :albumId AND folderId IS NULL")
    suspend fun getDirectAccess(albumId: String): List<DirectWallpaperAccess>

    @Query("UPDATE wallpapers SET accessLost = :lost WHERE id IN (:ids) AND accessLost != :lost")
    suspend fun setAccessLost(ids: List<String>, lost: Boolean): Int

    @Query("UPDATE wallpapers SET accessLost = :lost WHERE folderId = :folderId AND accessLost != :lost")
    suspend fun setFolderAccessLost(folderId: String, lost: Boolean): Int

    @Query("UPDATE wallpapers SET uri = :uri, accessLost = 0 WHERE id = :wallpaperId")
    suspend fun relink(wallpaperId: String, uri: String)
}
