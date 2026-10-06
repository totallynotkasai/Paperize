package com.anthonyla.paperize.core

/** How an album treats its favourite images (plan 5.2). Stored per album since schema v5. */
enum class FavoritesMode {
    /** Favourites are only a marker for filtering the album view. */
    MARKER_ONLY,

    /** Favourites appear more often in shuffle. */
    SHOW_MORE_OFTEN,

    /** Only favourites rotate; falls back to every image when the album has none. */
    FAVORITES_ONLY;

    companion object {
        fun fromString(value: String?): FavoritesMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: MARKER_ONLY
    }
}
