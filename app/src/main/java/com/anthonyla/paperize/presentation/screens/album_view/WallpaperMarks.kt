package com.anthonyla.paperize.presentation.screens.album_view

import com.anthonyla.paperize.R
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.presentation.common.util.UiText

/** Which images an album or folder view shows (plan 5.2). */
enum class ImageFilter {
    ALL,

    /** Every favourite, including those inside folders. */
    FAVORITES,

    /** Every image excluded from rotation, including those inside folders. */
    EXCLUDED;

    fun matches(wallpaper: Wallpaper): Boolean = when (this) {
        ALL -> true
        FAVORITES -> wallpaper.favorite
        EXCLUDED -> wallpaper.excluded
    }
}

/** Favourite and exclude marks of a selection, for the selection bar's toggles. */
data class SelectionMarks(val images: Int = 0, val allFavorite: Boolean = false, val allExcluded: Boolean = false)

fun Collection<Wallpaper>.selectionMarks() = SelectionMarks(
    images = size,
    allFavorite = isNotEmpty() && all { it.favorite },
    allExcluded = isNotEmpty() && all { it.excluded }
)

/** Images added directly, then each folder's images: every image of the album. */
fun Album.allImages(): List<Wallpaper> = wallpapers + folders.flatMap { it.wallpapers }

/** Images a selection stands for: the chosen images plus every image in the chosen folders. */
fun Album.imagesIn(wallpaperIds: Set<String>, folderIds: Set<String>): List<Wallpaper> =
    wallpapers.filter { it.id in wallpaperIds } + folders.flatMap { folder ->
        if (folder.id in folderIds) folder.wallpapers else folder.wallpapers.filter { it.id in wallpaperIds }
    }

/** The order images rotate in when shuffle is off: direct images, then each folder in turn. */
fun Album.rotationComparator(): Comparator<Wallpaper> {
    val folderOrder = folders.associate { it.id to it.displayOrder }
    return compareBy<Wallpaper> { it.folderId != null }
        .thenBy { folderOrder[it.folderId] ?: 0 }
        .thenBy { it.folderId.orEmpty() }
        .thenBy { it.displayOrder }
        .thenBy { it.id }
}

/** The snackbar after marking [count] images. */
fun markMessage(favorite: Boolean? = null, excluded: Boolean? = null, count: Int): UiText = UiText.Plural(
    when {
        favorite == true -> R.plurals.marked_favorite
        favorite == false -> R.plurals.unmarked_favorite
        excluded == true -> R.plurals.marked_excluded
        else -> R.plurals.unmarked_excluded
    },
    count
)
