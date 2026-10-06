package com.anthonyla.paperize.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.anthonyla.paperize.core.FavoritesMode
import com.anthonyla.paperize.data.database.entities.AlbumEntity
import com.anthonyla.paperize.data.database.entities.AlbumSummaryEntity
import com.anthonyla.paperize.data.database.relations.AlbumWithDetails
import kotlinx.coroutines.flow.Flow

data class AlbumName(val id: String, val name: String)

@Dao
interface AlbumDao {
    @Query("""
        SELECT 
            a.id, 
            a.name, 
            a.coverUri, 
            a.createdAt, 
            a.modifiedAt,
            (SELECT COUNT(*) FROM wallpapers w WHERE w.albumId = a.id) as wallpaperCount,
            (SELECT COUNT(*) FROM folders f WHERE f.albumId = a.id) as folderCount,
            a.effects IS NOT NULL as hasCustomEffects
        FROM albums a
        ORDER BY a.modifiedAt DESC
    """)
    fun getAlbumSummaries(): Flow<List<AlbumSummaryEntity>>

    @Transaction
    @Query("SELECT * FROM albums WHERE id = :albumId")
    fun getAlbumWithDetails(albumId: String): Flow<AlbumWithDetails?>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getAlbumById(albumId: String): AlbumEntity?

    @Query("SELECT id, name FROM albums")
    suspend fun getAlbumNames(): List<AlbumName>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbumById(albumId: String)

    @Query("UPDATE albums SET coverUri = :coverUri, modifiedAt = :modifiedAt WHERE id = :albumId")
    suspend fun updateAlbumCover(albumId: String, coverUri: String?, modifiedAt: Long)

    @Query("UPDATE albums SET modifiedAt = :modifiedAt WHERE id = :albumId")
    suspend fun updateAlbumModifiedTime(albumId: String, modifiedAt: Long)

    /** Leaves modifiedAt alone, so a renamed album keeps its place in the Library. */
    @Query("UPDATE albums SET name = :name WHERE id = :albumId")
    suspend fun renameAlbum(albumId: String, name: String): Int

    @Query("SELECT favoritesMode FROM albums WHERE id = :albumId")
    suspend fun getFavoritesMode(albumId: String): FavoritesMode?

    @Query("UPDATE albums SET favoritesMode = :mode WHERE id = :albumId AND favoritesMode != :mode")
    suspend fun setFavoritesMode(albumId: String, mode: FavoritesMode): Int

    @Query("SELECT effects FROM albums WHERE id = :albumId")
    suspend fun getEffects(albumId: String): String?

    @Query("SELECT effects FROM albums WHERE id = :albumId")
    fun getEffectsFlow(albumId: String): Flow<String?>

    @Query("UPDATE albums SET effects = :effects WHERE id = :albumId")
    suspend fun setEffects(albumId: String, effects: String?)

    @Query("DELETE FROM albums")
    suspend fun deleteAllAlbums()
}
