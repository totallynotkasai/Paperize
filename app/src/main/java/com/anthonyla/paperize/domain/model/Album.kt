package com.anthonyla.paperize.domain.model

import com.anthonyla.paperize.core.FavoritesMode

data class Album(
    val id: String,
    val name: String,
    val coverUri: String?,
    val wallpapers: List<Wallpaper> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    /** Overrides the Home, Lock and Live effects wherever this album is shown (plan 5.3). */
    val effects: WallpaperEffects? = null,
    val favoritesMode: FavoritesMode = FavoritesMode.MARKER_ONLY
)
