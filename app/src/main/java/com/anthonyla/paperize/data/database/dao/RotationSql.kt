package com.anthonyla.paperize.data.database.dao

/**
 * SQL shared by the queries that decide which images rotate. Each one reads the wallpapers table
 * as `w` and takes an `:albumId` parameter.
 */

/**
 * Rotation order: images added directly first, then each folder in its saved order. Image order
 * only has to be consistent within its own group, so new files can join the end of their group.
 */
internal const val ROTATION_ORDER = "w.folderId IS NOT NULL, f.displayOrder, f.id, w.displayOrder, w.id"

/** Images that may rotate: still readable and not excluded by the user. */
internal const val ELIGIBLE = "w.accessLost = 0 AND w.excluded = 0"

/**
 * The album is in "Favourites only" mode and at least one favourite can rotate. Without such a
 * favourite every eligible image rotates instead, so the album never runs dry.
 */
internal const val FAVORITES_ONLY_ACTIVE = """EXISTS(
    SELECT 1 FROM albums fa INNER JOIN wallpapers fw ON fw.albumId = fa.id
    WHERE fa.id = :albumId AND fa.favoritesMode = 'FAVORITES_ONLY'
    AND fw.favorite = 1 AND fw.accessLost = 0 AND fw.excluded = 0
)"""

/** Images that rotate now: eligible, and a favourite while "Favourites only" is in effect. */
internal const val ROTATES = "$ELIGIBLE AND (w.favorite = 1 OR NOT $FAVORITES_ONLY_ACTIVE)"
