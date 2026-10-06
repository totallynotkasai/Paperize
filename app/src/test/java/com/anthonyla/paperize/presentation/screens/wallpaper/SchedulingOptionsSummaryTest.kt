package com.anthonyla.paperize.presentation.screens.wallpaper

import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.presentation.screens.wallpaper.components.schedulingOptionsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingOptionsSummaryTest {
    private val everything = ScheduleSettings(
        homeEnabled = true, homeAlbumId = "day", homeNightAlbumId = "night",
        liveAlbumId = "day", liveNightAlbumId = "night",
        changeOnScreenOff = true, changeOnUnlock = true,
        onlyWhileCharging = true, pauseInBatterySaver = true
    )

    @Test fun `the card lists what is on, in the order the screen shows it`() {
        assertEquals(
            listOf(
                R.string.summary_screen_off, R.string.summary_unlock, R.string.summary_night_albums,
                R.string.summary_while_charging, R.string.summary_battery_saver
            ),
            schedulingOptionsSummary(everything, WallpaperMode.STATIC)
        )
        assertTrue(schedulingOptionsSummary(ScheduleSettings(), WallpaperMode.STATIC).isEmpty())
    }

    @Test fun `live mode has no screen-off or unlock option of its own here`() {
        assertEquals(
            listOf(R.string.summary_night_albums, R.string.summary_while_charging, R.string.summary_battery_saver),
            schedulingOptionsSummary(everything, WallpaperMode.LIVE)
        )
    }

    @Test fun `a night album on a turned-off screen isn't listed`() {
        assertTrue(schedulingOptionsSummary(ScheduleSettings(homeAlbumId = "day", homeNightAlbumId = "night"), WallpaperMode.STATIC).isEmpty())
    }
}
