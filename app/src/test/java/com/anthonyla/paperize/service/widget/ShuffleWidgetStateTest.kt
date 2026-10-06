package com.anthonyla.paperize.service.widget

import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleWidgetStateTest {
    private val homeOnly = ScheduleSettings(homeEnabled = true, homeAlbumId = "home")
    private val bothScreens = homeOnly.copy(lockEnabled = true, lockAlbumId = "lock")
    private val live = ScheduleSettings(liveAlbumId = "live")

    @Test fun `each static widget needs its own screen turned on with an album`() {
        assertNull(setUpProblem(ScreenType.HOME, homeOnly, WallpaperMode.STATIC))
        assertEquals(ShuffleProblem.LOCK_NOT_SET_UP, setUpProblem(ScreenType.LOCK, homeOnly, WallpaperMode.STATIC))
        assertNull(setUpProblem(ScreenType.LOCK, bothScreens, WallpaperMode.STATIC))
        // A screen still waiting for its album isn't set up.
        assertEquals(
            ShuffleProblem.LOCK_NOT_SET_UP,
            setUpProblem(ScreenType.LOCK, homeOnly.copy(lockEnabled = true), WallpaperMode.STATIC)
        )
    }

    @Test fun `a turned-off screen keeps its album but its widget greys out`() {
        val homeOff = bothScreens.copy(homeEnabled = false)
        assertEquals(ShuffleProblem.HOME_NOT_SET_UP, setUpProblem(ScreenType.HOME, homeOff, WallpaperMode.STATIC))
        assertFalse(isShuffleReady(ScreenType.HOME, homeOff, WallpaperMode.STATIC))
        assertTrue(isShuffleReady(ScreenType.LOCK, homeOff, WallpaperMode.STATIC))
    }

    @Test fun `Shuffle Both works while at least one screen can change`() {
        assertNull(setUpProblem(ScreenType.BOTH, homeOnly, WallpaperMode.STATIC))
        assertNull(setUpProblem(ScreenType.BOTH, bothScreens.copy(homeEnabled = false), WallpaperMode.STATIC))
        assertEquals(ShuffleProblem.NO_SCREEN_SET_UP, setUpProblem(ScreenType.BOTH, ScheduleSettings(), WallpaperMode.STATIC))
    }

    @Test fun `in live mode every widget follows the live album`() {
        SHUFFLE_TARGETS.forEach { screen ->
            assertNull(setUpProblem(screen, live, WallpaperMode.LIVE))
            // Static albums don't count in live mode.
            assertEquals(ShuffleProblem.NO_LIVE_ALBUM, setUpProblem(screen, bothScreens, WallpaperMode.LIVE))
        }
    }

    @Test fun `a live tap needs Paperize to be the live wallpaper`() {
        var asked = false
        assertNull(shuffleProblem(ScreenType.HOME, live, WallpaperMode.LIVE) { asked = true; true })
        assertTrue(asked)
        assertEquals(ShuffleProblem.LIVE_NOT_SET, shuffleProblem(ScreenType.LOCK, live, WallpaperMode.LIVE) { false })
        // A missing album is the first thing to fix.
        assertEquals(
            ShuffleProblem.NO_LIVE_ALBUM,
            shuffleProblem(ScreenType.LOCK, ScheduleSettings(), WallpaperMode.LIVE) { false }
        )
    }

    @Test fun `static taps never ask about the live wallpaper`() {
        assertNull(shuffleProblem(ScreenType.HOME, homeOnly, WallpaperMode.STATIC) { error("not asked in static mode") })
        assertEquals(
            ShuffleProblem.LOCK_NOT_SET_UP,
            shuffleProblem(ScreenType.LOCK, homeOnly, WallpaperMode.STATIC) { error("not asked in static mode") }
        )
    }

    @Test fun `only a change limited to the lock screen is confirmed with a message`() {
        assertTrue(changesOnlyLockScreen(ScreenType.LOCK, bothScreens, WallpaperMode.STATIC))
        assertFalse(changesOnlyLockScreen(ScreenType.HOME, bothScreens, WallpaperMode.STATIC))
        assertFalse(changesOnlyLockScreen(ScreenType.BOTH, bothScreens, WallpaperMode.STATIC))
        // Shuffle Both with only Lock turned on changes just the lock screen.
        assertTrue(changesOnlyLockScreen(ScreenType.BOTH, bothScreens.copy(homeEnabled = false), WallpaperMode.STATIC))
        // Live changes show on the home screen.
        assertFalse(changesOnlyLockScreen(ScreenType.LOCK, live, WallpaperMode.LIVE))
    }
}
