package com.anthonyla.paperize.data.mapper

import android.util.Log
import com.anthonyla.paperize.data.database.entities.AlbumEntity
import com.anthonyla.paperize.data.database.entities.AlbumSummaryEntity
import com.anthonyla.paperize.data.database.relations.AlbumWithDetails
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.model.AlbumSummary
import com.anthonyla.paperize.domain.model.Folder
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.model.WallpaperEffects
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val effectsJson = Json { ignoreUnknownKeys = true }

/** A corrupt or outdated value falls back to the screen's own effects instead of failing the album. */
internal fun decodeAlbumEffects(json: String?): WallpaperEffects? = json?.let {
    try {
        effectsJson.decodeFromString(WallpaperEffects.serializer(), it).validate()
    } catch (e: SerializationException) {
        Log.w("AlbumMapper", "Ignoring unreadable album effects", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w("AlbumMapper", "Ignoring unreadable album effects", e)
        null
    }
}

internal fun encodeAlbumEffects(effects: WallpaperEffects?): String? =
    effects?.let { effectsJson.encodeToString(WallpaperEffects.serializer(), it) }

fun AlbumEntity.toDomainModel(
    wallpapers: List<Wallpaper> = emptyList(),
    folders: List<Folder> = emptyList()
): Album = Album(
    id = id,
    name = name,
    coverUri = coverUri,
    wallpapers = wallpapers,
    folders = folders,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    effects = decodeAlbumEffects(effects),
    favoritesMode = favoritesMode
)

fun Album.toEntity(): AlbumEntity = AlbumEntity(
    id = id,
    name = name,
    coverUri = coverUri,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    effects = encodeAlbumEffects(effects),
    favoritesMode = favoritesMode
)

fun AlbumWithDetails.toDomainModel(): Album {
    val wallpapersByFolder = wallpapers.groupBy { it.folderId }
    
    val domainWallpapers = wallpapersByFolder[null]?.map { it.toDomainModel() } ?: emptyList()

    val domainFolders = folders.map { folderEntity ->
        val folderWallpapers = wallpapersByFolder[folderEntity.id]?.map { it.toDomainModel() } ?: emptyList()
        folderEntity.toDomainModel(folderWallpapers)
    }

    return album.toDomainModel(
        wallpapers = domainWallpapers,
        folders = domainFolders
    )
}

fun AlbumSummaryEntity.toDomainModel(): AlbumSummary =
    AlbumSummary(
        id = id,
        name = name,
        coverUri = coverUri,
        wallpaperCount = wallpaperCount,
        folderCount = folderCount,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        hasCustomEffects = hasCustomEffects
    )
