package com.anthonyla.paperize.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anthonyla.paperize.core.FavoritesMode

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val coverUri: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),

    /** Per-album effects as JSON (schema v5); null means the screen's own effects apply. */
    val effects: String? = null,

    @ColumnInfo(defaultValue = "MARKER_ONLY")
    val favoritesMode: FavoritesMode = FavoritesMode.MARKER_ONLY
)
