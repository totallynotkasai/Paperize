package com.anthonyla.paperize.service.widget

import android.content.Context
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.util.isPaperizeLiveWallpaperActive
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeRequests
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** What a tap on a Shuffle widget does, and when placed widgets are redrawn (plan 4.1). */
class ShuffleWidgetsTest {
    private val context = mockk<Context>(relaxed = true)
    private val settingsFlow = MutableStateFlow(ScheduleSettings())
    private val modeFlow = MutableStateFlow(WallpaperMode.STATIC)
    private val settings = mockk<SettingsRepository> {
        every { getScheduleSettingsFlow() } returns settingsFlow
        every { getWallpaperModeFlow() } returns modeFlow
        coEvery { getScheduleSettings() } answers { settingsFlow.value }
        coEvery { getWallpaperMode() } answers { modeFlow.value }
    }
    private val requests = mockk<WallpaperChangeRequests>(relaxed = true)
    private val display = mockk<ShuffleWidgetDisplay>(relaxed = true)
    private val widgets = ShuffleWidgets(context, settings, requests, display)
    private var liveWallpaperActive = true

    private val homeOnly = ScheduleSettings(enableChanger = true, homeEnabled = true, homeAlbumId = "home")
    private val bothScreens = homeOnly.copy(lockEnabled = true, lockAlbumId = "lock")

    @Before fun setUp() {
        mockkStatic("com.anthonyla.paperize.core.util.WallpaperUtilKt")
        every { isPaperizeLiveWallpaperActive(any()) } answers { liveWallpaperActive }
    }

    @After fun tearDown() = unmockkAll()

    @Test fun `a ready widget asks for the next image of its screen and says nothing`() = runTest {
        settingsFlow.value = bothScreens
        widgets.shuffle(ScreenType.HOME)
        verify(exactly = 1) { requests.changeConfigured(ScreenType.HOME) }
        coVerify(exactly = 0) { display.toast(any()) }
    }

    @Test fun `Shuffle Both goes through the same path as the tile`() = runTest {
        settingsFlow.value = bothScreens
        widgets.shuffle(ScreenType.BOTH)
        verify(exactly = 1) { requests.changeConfigured(ScreenType.BOTH) }
        coVerify(exactly = 0) { display.toast(any()) }
    }

    @Test fun `a widget whose screen isn't set up changes nothing, says why and greys out`() = runTest {
        settingsFlow.value = homeOnly
        widgets.shuffle(ScreenType.LOCK)
        verify(exactly = 0) { requests.changeConfigured(any()) }
        coVerify(exactly = 1) { display.toast(R.string.widget_lock_not_set_up) }
        // Redrawn, in case it missed a change made while the app wasn't running.
        verify(exactly = 1) { display.show(ScreenType.LOCK, false) }
    }

    @Test fun `a change that only reaches the lock screen is confirmed`() = runTest {
        settingsFlow.value = bothScreens
        widgets.shuffle(ScreenType.LOCK)
        verify(exactly = 1) { requests.changeConfigured(ScreenType.LOCK) }
        coVerify(exactly = 1) { display.toast(R.string.widget_changing_lock) }

        // Shuffle Both with only Lock turned on changes just the lock screen too.
        settingsFlow.value = bothScreens.copy(homeEnabled = false)
        widgets.shuffle(ScreenType.BOTH)
        coVerify(exactly = 2) { display.toast(R.string.widget_changing_lock) }
    }

    @Test fun `in live mode every widget changes the live wallpaper once it is set`() = runTest {
        modeFlow.value = WallpaperMode.LIVE
        settingsFlow.value = ScheduleSettings(enableChanger = true, liveAlbumId = "live")
        SHUFFLE_TARGETS.forEach { widgets.shuffle(it) }
        SHUFFLE_TARGETS.forEach { verify(exactly = 1) { requests.changeConfigured(it) } }
        // Live changes show on the home screen, so even Shuffle Lock needs no confirmation.
        coVerify(exactly = 0) { display.toast(any()) }

        liveWallpaperActive = false
        widgets.shuffle(ScreenType.HOME)
        coVerify(exactly = 1) { display.toast(R.string.live_wallpaper_not_set_title) }
        verify(exactly = 1) { requests.changeConfigured(ScreenType.HOME) }
        // Still usable: setting the live wallpaper is all it takes.
        verify { display.show(ScreenType.HOME, true) }
    }

    @Test fun `a live widget without a live album says to pick one`() = runTest {
        modeFlow.value = WallpaperMode.LIVE
        settingsFlow.value = bothScreens
        widgets.shuffle(ScreenType.BOTH)
        coVerify { display.toast(R.string.widget_live_no_album) }
        verify(exactly = 0) { requests.changeConfigured(any()) }
    }

    @Test fun `placing widgets draws them for the current settings`() = runTest {
        settingsFlow.value = homeOnly
        val ids = intArrayOf(7, 8)
        widgets.refresh(ScreenType.HOME, ids)
        widgets.refresh(ScreenType.LOCK, ids)
        verify { display.show(ScreenType.HOME, true, ids) }
        verify { display.show(ScreenType.LOCK, false, ids) }
    }

    @Test fun `placed widgets are redrawn only when one becomes usable or stops being usable`() = runTest {
        settingsFlow.value = homeOnly
        val seen = mutableListOf<Map<ScreenType, Boolean>>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { widgets.readiness().take(3).toList(seen) }

        // Changes that leave every widget as it was draw nothing.
        settingsFlow.value = homeOnly.copy(enableChanger = false)
        settingsFlow.value = homeOnly.copy(homeAlbumId = "another")
        // Lock comes into use.
        settingsFlow.value = bothScreens
        // Live mode with no live album greys every widget out.
        modeFlow.value = WallpaperMode.LIVE
        collector.join()

        assertEquals(
            listOf(
                mapOf(ScreenType.HOME to true, ScreenType.LOCK to false, ScreenType.BOTH to true),
                mapOf(ScreenType.HOME to true, ScreenType.LOCK to true, ScreenType.BOTH to true),
                mapOf(ScreenType.HOME to false, ScreenType.LOCK to false, ScreenType.BOTH to false)
            ),
            seen
        )
    }

    @Test fun `every problem has its own message`() {
        with(ShuffleWidgets) {
            assertEquals(
                listOf(
                    R.string.widget_home_not_set_up,
                    R.string.widget_lock_not_set_up,
                    R.string.widget_both_not_set_up,
                    R.string.widget_live_no_album,
                    R.string.live_wallpaper_not_set_title
                ),
                ShuffleProblem.entries.map { it.message() }
            )
        }
    }
}
