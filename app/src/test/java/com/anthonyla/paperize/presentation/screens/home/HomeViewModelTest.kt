package com.anthonyla.paperize.presentation.screens.home

import com.anthonyla.paperize.testing.emptyAlbumSummary
import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.AppSettings
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeEvents
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeRequests
import com.anthonyla.paperize.service.schedule.ScheduleEvents
import com.anthonyla.paperize.service.worker.WallpaperScheduler
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val settings = mockk<SettingsRepository>()
    private val albums = mockk<AlbumRepository>()
    private val wallpapers = mockk<WallpaperRepository>(relaxed = true)
    private val scheduler = mockk<WallpaperScheduler>(relaxed = true)
    private val requests = mockk<WallpaperChangeRequests>(relaxed = true)
    private val scheduleEvents = mockk<ScheduleEvents>(relaxed = true)
    private val stored = MutableStateFlow(ScheduleSettings(homeEnabled = true, homeAlbumId = "old"))
    private val store = ViewModelStore()
    private lateinit var viewModel: HomeViewModel

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { albums.getAlbumSummaries() } returns flowOf(emptyList())
        coEvery { albums.getAlbumEffects(any()) } returns null
        every { settings.getScheduleSettingsFlow() } returns stored
        every { settings.getAppSettingsFlow() } returns flowOf(AppSettings())
        every { settings.getWallpaperModeFlow() } returns flowOf(WallpaperMode.STATIC)
        coEvery { settings.getWallpaperMode() } returns WallpaperMode.STATIC
        coEvery { settings.getScheduleSettings() } answers { stored.value }
        coEvery { settings.updateEnableChanger(any()) } answers { stored.value = stored.value.copy(enableChanger = firstArg()) }
        coEvery { settings.updateHomeAlbumId(any()) } answers { stored.value = stored.value.copy(homeAlbumId = firstArg()) }
        coEvery { settings.updateLockAlbumId(any()) } answers { stored.value = stored.value.copy(lockAlbumId = firstArg()) }
        coEvery { settings.updateScheduleSettings(any<(ScheduleSettings) -> ScheduleSettings>()) } answers {
            stored.value = firstArg<(ScheduleSettings) -> ScheduleSettings>()(stored.value)
            stored.value
        }
        every { wallpapers.getCurrentWallpaperFlow(any(), any()) } returns flowOf(null)
        viewModel = HomeViewModel(mockk<Context>(), albums, mockk(), settings, scheduler, wallpapers, requests, WallpaperChangeEvents(), scheduleEvents)
        store.put("home", viewModel)
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test fun `delayed effect edits preserve a newer pause and album selection`() = runTest {
        stored.value = stored.value.copy(enableChanger = true)
        val draft = stored.value.copy(homeEffects = stored.value.homeEffects.copy(blurPercentage = 60))
        viewModel.updateScheduleSettings(draft, deferRender = true)
        viewModel.toggleWallpaperChanger(false)
        runCurrent()
        viewModel.selectHomeAlbum(emptyAlbumSummary("new"))
        advanceUntilIdle()

        assertFalse(stored.value.enableChanger)
        assertEquals("new", stored.value.homeAlbumId)
        assertEquals(60, stored.value.homeEffects.blurPercentage)
    }

    @Test fun `turning a screen off keeps its album and stops only its rotation`() = runTest {
        stored.value = stored.value.copy(enableChanger = true, lockEnabled = true, lockAlbumId = "lock")
        viewModel.updateScheduleSettings(stored.value.copy(homeEnabled = false))
        advanceUntilIdle()
        assertEquals("old", stored.value.homeAlbumId)
        assertEquals("lock", stored.value.lockAlbumId)
        assertNull(stored.value.albumFor(ScreenType.HOME))
        coVerify { scheduler.updateSchedules(match { it.activeScreens(WallpaperMode.STATIC) == setOf(ScreenType.LOCK) }, any(), any()) }
        verify(exactly = 0) { requests.change(any(), any()) }
    }

    @Test fun `turning a screen back on shows its first image without restarting the new countdown`() = runTest {
        stored.value = stored.value.copy(enableChanger = true, homeEnabled = false, lockEnabled = true, lockAlbumId = "lock")
        viewModel.updateScheduleSettings(stored.value.copy(homeEnabled = true))
        advanceUntilIdle()
        verify(exactly = 1) { requests.change(ScreenType.HOME, keepSchedule = true) }
        verify(exactly = 0) { requests.change(ScreenType.LOCK, any()) }
    }

    @Test fun `picking an album for a new screen changes only that screen and keeps its countdown`() = runTest {
        stored.value = stored.value.copy(enableChanger = true, lockEnabled = true)
        viewModel.selectLockAlbum(emptyAlbumSummary("old"))
        advanceUntilIdle()
        coVerifyOrder {
            scheduler.updateSchedules(match { it.lockAlbumId == "old" }, WallpaperMode.STATIC, false)
            requests.change(ScreenType.LOCK, keepSchedule = true)
        }
        verify(exactly = 0) { requests.change(ScreenType.HOME, any()) }
        verify(exactly = 0) { requests.change(ScreenType.BOTH, any()) }
    }

    @Test fun `picking another album for a rotating screen restarts its countdown`() = runTest {
        stored.value = stored.value.copy(enableChanger = true)
        viewModel.selectHomeAlbum(emptyAlbumSummary("new"))
        advanceUntilIdle()
        verify(exactly = 1) { requests.change(ScreenType.HOME, keepSchedule = false) }
    }

    @Test fun `turning changing on applies the first images itself`() = runTest {
        stored.value = stored.value.copy(lockEnabled = true, lockAlbumId = "lock")
        viewModel.toggleWallpaperChanger(true)
        advanceUntilIdle()
        coVerify { scheduler.updateSchedules(match { it.enableChanger }, WallpaperMode.STATIC, false) }
        verify(exactly = 1) { requests.change(ScreenType.BOTH, keepSchedule = true) }
    }

    @Test fun `effects re-render the current image while paused, only for the edited screen`() = runTest {
        stored.value = stored.value.copy(lockEnabled = true, lockAlbumId = "lock")
        viewModel.updateScheduleSettings(stored.value.copy(lockEffects = stored.value.lockEffects.copy(enableBlur = true)))
        advanceUntilIdle()
        verify(exactly = 1) { requests.reapplyEffects(ScreenType.LOCK) }
        verify(exactly = 0) { requests.change(any(), any()) }
    }

    @Test fun `a screen whose album has its own effects ignores edits to the screen's effects`() = runTest {
        coEvery { albums.getAlbumEffects("old") } returns WallpaperEffects(enableGrayscale = true)
        viewModel.updateScheduleSettings(stored.value.copy(homeEffects = stored.value.homeEffects.copy(enableBlur = true)))
        advanceUntilIdle()
        verify(exactly = 0) { requests.reapplyEffects(any()) }
        // Scaling still applies to that album.
        viewModel.updateScheduleSettings(stored.value.copy(homeScalingType = ScalingType.FIT))
        advanceUntilIdle()
        verify(exactly = 1) { requests.reapplyEffects(ScreenType.HOME) }
    }

    @Test fun `slider levels are saved at once and render once after the edits settle`() = runTest {
        val first = stored.value.copy(homeEffects = stored.value.homeEffects.copy(enableDarken = true, darkenPercentage = 20))
        viewModel.updateScheduleSettings(first, deferRender = true)
        runCurrent()
        assertEquals(20, stored.value.homeEffects.darkenPercentage)
        viewModel.updateScheduleSettings(first.copy(homeEffects = first.homeEffects.copy(darkenPercentage = 40)), deferRender = true)
        runCurrent()
        assertEquals(40, stored.value.homeEffects.darkenPercentage)
        verify(exactly = 0) { requests.reapplyEffects(any()) }
        advanceTimeBy(Constants.SETTINGS_DEBOUNCE_MS + 1)
        verify(exactly = 1) { requests.reapplyEffects(ScreenType.HOME) }
    }

    @Test fun `leaving the screen renders waiting effect edits straight away`() = runTest {
        viewModel.updateScheduleSettings(stored.value.copy(homeEffects = stored.value.homeEffects.copy(enableBlur = true)), deferRender = true)
        runCurrent()
        viewModel.flushPendingRender()
        verify(exactly = 1) { requests.reapplyEffects(ScreenType.HOME) }
        advanceUntilIdle()
        verify(exactly = 1) { requests.reapplyEffects(any()) }
    }

    @Test fun `no effect is rendered when no screen is turned on`() = runTest {
        stored.value = stored.value.copy(homeEnabled = false)
        viewModel.updateScheduleSettings(stored.value.copy(homeEffects = stored.value.homeEffects.copy(enableBlur = true)))
        advanceUntilIdle()
        verify(exactly = 0) { requests.reapplyEffects(any()) }
    }

    @Test fun `rapid edits keep scheduling side effects in persistence order`() = runTest {
        val firstSchedule = CompletableDeferred<Unit>()
        val scheduled = mutableListOf<Boolean>()
        coEvery { scheduler.updateSchedules(any(), any(), any()) } coAnswers {
            val snapshot = firstArg<ScheduleSettings>()
            if (!snapshot.enableChanger) firstSchedule.await()
            scheduled.add(snapshot.enableChanger)
        }
        viewModel.updateScheduleSettings(stored.value.copy(homeIntervalMinutes = 45))
        runCurrent()
        viewModel.toggleWallpaperChanger(true)
        runCurrent()
        assertFalse(stored.value.enableChanger)
        firstSchedule.complete(Unit)
        advanceUntilIdle()
        assertTrue(stored.value.enableChanger)
        assertEquals(listOf(false, true), scheduled)
    }

    @Test fun `effect edits do not restart preview subscriptions but album changes do`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.currentHomeWallpaperUri.collect() }
        runCurrent()
        stored.value = stored.value.copy(homeEffects = stored.value.homeEffects.copy(blurPercentage = 60))
        runCurrent()
        verify(exactly = 1) { wallpapers.getCurrentWallpaperFlow("old", ScreenType.HOME) }

        stored.value = stored.value.copy(homeAlbumId = "new")
        runCurrent()
        verify(exactly = 1) { wallpapers.getCurrentWallpaperFlow("new", ScreenType.HOME) }
        // Each screen records its own current image; nothing is ever recorded for "both".
        verify(exactly = 0) { wallpapers.getCurrentWallpaperFlow(any(), ScreenType.BOTH) }
    }

    @Test fun `change now sends one reported request covering every rotating screen`() = runTest {
        // The screen collects these; their values only follow the settings while collected.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.wallpaperMode.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.scheduleSettings.collect() }
        stored.value = stored.value.copy(enableChanger = true, lockEnabled = true, lockAlbumId = "lock", separateSchedules = true)
        runCurrent()
        viewModel.changeWallpaperNowForActiveScreens()
        verify(exactly = 1) { requests.change(ScreenType.BOTH, keepSchedule = false, report = true) }

        stored.value = stored.value.copy(lockEnabled = false)
        runCurrent()
        viewModel.changeWallpaperNowForActiveScreens()
        verify(exactly = 1) { requests.change(ScreenType.HOME, keepSchedule = false, report = true) }
    }

    @Test fun `change now does nothing while no screen can rotate`() = runTest {
        // The screen collects these; their values only follow the settings while collected.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.wallpaperMode.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.scheduleSettings.collect() }
        stored.value = stored.value.copy(homeEnabled = false)
        runCurrent()
        viewModel.changeWallpaperNowForActiveScreens()
        verify(exactly = 0) { requests.change(any(), any(), any()) }
    }

    @Test fun `change now is busy until its result arrives`() = runTest {
        val events = WallpaperChangeEvents()
        val busyViewModel = HomeViewModel(mockk<Context>(), albums, mockk(), settings, scheduler, wallpapers, requests, events, scheduleEvents)
        store.put("busy", busyViewModel)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { busyViewModel.changeInProgress.collect() }
        assertFalse(busyViewModel.changeInProgress.value)
        events.begin()
        runCurrent()
        assertTrue(busyViewModel.changeInProgress.value)
        events.end()
        runCurrent()
        assertFalse(busyViewModel.changeInProgress.value)
    }

    /** The night albums come into use when [syncNightAlbums][ScheduleEvents.syncNightAlbums] runs while [night]. */
    private fun nightIs(night: () -> Boolean) {
        coEvery { scheduleEvents.syncNightAlbums(any()) } answers {
            val changed = stored.value.nightActive != night()
            stored.value = stored.value.copy(nightActive = night())
            changed
        }
        coEvery { settings.updateNightAlbumId(any(), any()) } answers {
            stored.value = when (firstArg<ScreenType>()) {
                ScreenType.HOME -> stored.value.copy(homeNightAlbumId = secondArg())
                ScreenType.LOCK -> stored.value.copy(lockNightAlbumId = secondArg())
                else -> stored.value.copy(liveNightAlbumId = secondArg())
            }
        }
    }

    @Test fun `picking a night album at night changes that screen to it at once`() = runTest {
        nightIs { true }
        stored.value = stored.value.copy(enableChanger = true, lockEnabled = true, lockAlbumId = "lock")
        viewModel.selectNightAlbum(ScreenType.HOME, emptyAlbumSummary("night"))
        advanceUntilIdle()
        assertEquals("night", stored.value.albumFor(ScreenType.HOME))
        verify(exactly = 1) { requests.change(ScreenType.HOME, keepSchedule = false) }
        verify(exactly = 0) { requests.change(ScreenType.LOCK, any()) }
        coVerify { scheduler.updateSchedules(match { it.homeNightAlbumId == "night" }, WallpaperMode.STATIC, false) }
    }

    @Test fun `picking a night album by day changes nothing yet`() = runTest {
        nightIs { false }
        stored.value = stored.value.copy(enableChanger = true)
        viewModel.selectNightAlbum(ScreenType.HOME, emptyAlbumSummary("night"))
        advanceUntilIdle()
        assertEquals("night", stored.value.homeNightAlbumId)
        verify(exactly = 0) { requests.change(any(), any()) }
    }

    @Test fun `new night hours that start the night change the screens with a night album`() = runTest {
        var night = false
        nightIs { night }
        stored.value = stored.value.copy(enableChanger = true, homeNightAlbumId = "night")
        night = true
        viewModel.updateScheduleSettings(stored.value.copy(nightStartMinutes = 6 * 60))
        advanceUntilIdle()
        assertTrue(stored.value.nightActive)
        verify(exactly = 1) { requests.change(ScreenType.HOME, keepSchedule = false) }
        // Changing the screen already draws it with the current effects; no separate re-render.
        verify(exactly = 0) { requests.reapplyEffects(any()) }
    }

    @Test fun `edits from an older draft keep the night albums and which ones are in use`() = runTest {
        nightIs { true }
        val draft = stored.value
        stored.value = stored.value.copy(homeNightAlbumId = "night", nightActive = true)
        viewModel.updateScheduleSettings(draft.copy(onlyWhileCharging = true))
        advanceUntilIdle()
        assertEquals("night", stored.value.homeNightAlbumId)
        assertTrue(stored.value.nightActive)
        assertTrue(stored.value.onlyWhileCharging)
    }
}
