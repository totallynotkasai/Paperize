package com.anthonyla.paperize.service.wallpaper

import android.content.Context
import android.util.Log
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.util.isPaperizeLiveWallpaperActive
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import com.anthonyla.paperize.service.WallpaperChangeLock
import com.anthonyla.paperize.service.WallpaperNotifier
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Kind
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Outcome
import com.anthonyla.paperize.service.worker.WallpaperScheduler
import com.anthonyla.paperize.testing.emptyWallpaper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperRequestHandlerTest {
    private val context = mockk<Context>(relaxed = true)
    private val controller = mockk<WallpaperController>()
    private val settings = mockk<SettingsRepository>()
    private val scheduler = mockk<WallpaperScheduler>(relaxed = true)
    private val wallpapers = mockk<WallpaperRepository>()
    private val notifier = mockk<WallpaperNotifier>(relaxed = true)
    private val events = WallpaperChangeEvents()
    private val handler = WallpaperRequestHandler(
        context, controller, settings, WallpaperChangeLock(), scheduler, wallpapers, notifier, events
    )
    private val scheduled = ScheduleSettings(enableChanger = true, homeEnabled = true, homeAlbumId = "album")

    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        mockkStatic("com.anthonyla.paperize.core.util.WallpaperUtilKt")
        every { isPaperizeLiveWallpaperActive(any()) } returns true
        coEvery { settings.getWallpaperMode() } returns WallpaperMode.STATIC
        coEvery { settings.getScheduleSettings() } returns scheduled
        coEvery { controller.change(any(), any()) } returns WallpaperChangeOutcome(changed = true)
    }

    @After fun tearDown() = unmockkAll()

    /** A screen showing results, as the visible Wallpaper tab or image viewer does. */
    private fun TestScope.visibleScreen(): List<WallpaperChangeResult> =
        mutableListOf<WallpaperChangeResult>().also { shown ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.results.collect(shown::add) }
        }

    @Test fun `a reported change is shown on the visible screen and restarts the countdown`() = runTest {
        val shown = visibleScreen()
        events.begin()
        handler.handle(WallpaperRequest.Change(ScreenType.HOME), report = true)
        assertEquals(listOf(WallpaperChangeResult(Kind.CHANGE, Outcome.CHANGED)), shown)
        assertEquals(0, events.pending.value)
        coVerify { scheduler.resetAfterManualChange(ScreenType.HOME, scheduled, WallpaperMode.STATIC) }
        verify(exactly = 0) { notifier.showChangeFailed(any()) }
    }

    @Test fun `with no screen visible a failure becomes a notification`() = runTest {
        coEvery { controller.change(any(), any()) } throws IllegalStateException("boom")
        events.begin()
        handler.handle(WallpaperRequest.Change(ScreenType.HOME), report = true)
        verify { notifier.showChangeFailed("boom") }
        assertEquals(0, events.pending.value)
    }

    @Test fun `a visible screen shows the failure instead of a notification`() = runTest {
        val shown = visibleScreen()
        coEvery { controller.change(any(), any()) } throws IllegalStateException("boom")
        events.begin()
        handler.handle(WallpaperRequest.Change(ScreenType.HOME), report = true)
        assertEquals(listOf(WallpaperChangeResult(Kind.CHANGE, Outcome.FAILED, "boom")), shown)
        verify(exactly = 0) { notifier.showChangeFailed(any()) }
    }

    @Test fun `unreported requests notify problems as before`() = runTest {
        visibleScreen()
        coEvery { controller.change(any(), any()) } returns WallpaperChangeOutcome(emptyAlbum = true)
        handler.handle(WallpaperRequest.Change(ScreenType.HOME), report = false)
        verify { notifier.showEmptyAlbum() }
    }

    @Test fun `keeping the schedule leaves the countdown alone`() = runTest {
        handler.handle(WallpaperRequest.Change(ScreenType.HOME, keepSchedule = true), report = false)
        coVerify(exactly = 0) { scheduler.resetAfterManualChange(any(), any(), any()) }
    }

    @Test fun `tile and shortcut change the live wallpaper in live mode`() = runTest {
        coEvery { settings.getWallpaperMode() } returns WallpaperMode.LIVE
        handler.handle(WallpaperRequest.Change(ScreenType.BOTH, followMode = true), report = false)
        coVerify { controller.change(ScreenType.LIVE, scheduled) }
    }

    @Test fun `a live change while Paperize isn't the live wallpaper says nothing changed`() = runTest {
        val shown = visibleScreen()
        every { isPaperizeLiveWallpaperActive(any()) } returns false
        handler.handle(WallpaperRequest.Change(ScreenType.LIVE), report = true)
        assertEquals(Outcome.LIVE_NOT_SET, shown.single().outcome)
    }

    @Test fun `setting a chosen image is reported as set`() = runTest {
        val shown = visibleScreen()
        coEvery { wallpapers.getWallpaperById("chosen") } returns emptyWallpaper("chosen", "album")
        coEvery { controller.applySpecific(any(), any(), any(), any()) } returns Unit
        handler.handle(WallpaperRequest.ApplySpecific("chosen", ScreenType.LOCK), report = true)
        assertEquals(listOf(WallpaperChangeResult(Kind.SET_CHOSEN, Outcome.CHANGED)), shown)
        coVerify { controller.applySpecific("album", "chosen", ScreenType.LOCK, scheduled) }
        coVerify { scheduler.resetAfterManualChange(ScreenType.LOCK, scheduled, WallpaperMode.STATIC) }
    }
}
