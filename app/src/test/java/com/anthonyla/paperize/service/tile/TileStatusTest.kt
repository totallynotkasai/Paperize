package com.anthonyla.paperize.service.tile

import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class TileStatusTest {
    private val homeReady = ScheduleSettings(homeEnabled = true, homeAlbumId = "album")

    @Test fun `the tile is on while automatic changing runs`() {
        assertEquals(TileStatus.ON, tileStatus(homeReady.copy(enableChanger = true), WallpaperMode.STATIC))
    }

    @Test fun `the tile is paused, but still usable, while changing is paused`() {
        assertEquals(TileStatus.PAUSED, tileStatus(homeReady, WallpaperMode.STATIC))
    }

    @Test fun `the tile is unavailable until some screen has an album`() {
        assertEquals(TileStatus.NOT_SET_UP, tileStatus(ScheduleSettings(enableChanger = true), WallpaperMode.STATIC))
        // A turned-off screen keeps its album but can't change.
        assertEquals(TileStatus.NOT_SET_UP, tileStatus(homeReady.copy(homeEnabled = false), WallpaperMode.STATIC))
        assertEquals(TileStatus.NOT_SET_UP, tileStatus(homeReady, WallpaperMode.LIVE))
        assertEquals(
            TileStatus.ON,
            tileStatus(ScheduleSettings(enableChanger = true, liveAlbumId = "live"), WallpaperMode.LIVE)
        )
    }
}
