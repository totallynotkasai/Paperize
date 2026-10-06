package com.anthonyla.paperize.testing

import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.model.AlbumSummary
import com.anthonyla.paperize.domain.model.Folder
import com.anthonyla.paperize.domain.model.Wallpaper

/** Minimal models for tests; shared by unit and device tests (see sharedTest in the build file). */
fun emptyAlbum(id: String = "", name: String = "") = Album(
    id = id,
    name = name,
    coverUri = null
)

fun emptyAlbumSummary(id: String = "", name: String = "") = AlbumSummary(
    id = id,
    name = name,
    coverUri = null,
    wallpaperCount = 0,
    folderCount = 0,
    createdAt = System.currentTimeMillis(),
    modifiedAt = System.currentTimeMillis()
)

fun emptyFolder(id: String = "", albumId: String = "") = Folder(
    id = id,
    albumId = albumId,
    name = "",
    uri = "",
    coverUri = null,
    dateModified = 0L
)

fun emptyWallpaper(id: String = "", albumId: String = "") = Wallpaper(
    id = id,
    albumId = albumId,
    uri = "",
    fileName = "",
    dateModified = 0L
)
