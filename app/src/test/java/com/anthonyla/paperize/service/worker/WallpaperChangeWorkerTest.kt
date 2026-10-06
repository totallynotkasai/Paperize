package com.anthonyla.paperize.service.worker

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.service.WallpaperChangeLock
import com.anthonyla.paperize.service.WallpaperNotifier
import com.anthonyla.paperize.service.schedule.ChangeBlock
import com.anthonyla.paperize.service.schedule.ChangeConditions
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeOutcome
import com.anthonyla.paperize.service.wallpaper.WallpaperController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The periodic job behind interval changes (plans 1.8, 2.1 and 6.1). */
class WallpaperChangeWorkerTest {
    private val controller = mockk<WallpaperController>()
    private val settings = mockk<SettingsRepository>()
    private val scheduler = mockk<WallpaperScheduler>(relaxed = true)
    private val notifier = mockk<WallpaperNotifier>(relaxed = true)
    private val conditions = mockk<ChangeConditions> { every { automaticChangeBlock(any()) } returns null }
    private var mode = WallpaperMode.STATIC
    private var current = ScheduleSettings(enableChanger = true, homeEnabled = true, homeAlbumId = "album")

    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        coEvery { settings.getScheduleSettings() } answers { current }
        coEvery { settings.getWallpaperMode() } answers { mode }
        coEvery { controller.change(any(), any()) } returns WallpaperChangeOutcome(changed = true)
    }

    @After fun tearDown() = unmockkAll()

    private fun worker(screen: ScreenType?, attempt: Int = 0): WallpaperChangeWorker {
        val params = mockk<WorkerParameters>(relaxed = true) {
            every { inputData } returns Data.Builder().putString(Constants.EXTRA_SCREEN_TYPE, screen?.name).build()
            every { runAttemptCount } returns attempt
        }
        return WallpaperChangeWorker(
            mockk<Context>(relaxed = true), params, controller, settings, WallpaperChangeLock(), scheduler, notifier, conditions
        )
    }

    @Test fun `a scheduled screen changes and the job carries on`() = runTest {
        assertEquals(Result.success(), worker(ScreenType.HOME).doWork())
        coVerify(exactly = 1) { controller.change(ScreenType.HOME, current) }
        coVerify(exactly = 0) { scheduler.updateSchedules(any(), any(), any()) }
        verify(exactly = 0) { notifier.showEmptyAlbum() }
    }

    @Test fun `older jobs without a screen change the home screen`() = runTest {
        worker(screen = null).doWork()
        coVerify { controller.change(ScreenType.HOME, current) }
    }

    @Test fun `the shared job changes both screens`() = runTest {
        current = current.copy(lockEnabled = true, lockAlbumId = "album")
        worker(ScreenType.BOTH).doWork()
        coVerify(exactly = 1) { controller.change(ScreenType.BOTH, current) }
    }

    @Test fun `a job the settings no longer call for changes nothing and cancels itself`() = runTest {
        listOf(
            current.copy(enableChanger = false),
            // Turned off, it keeps its album but isn't scheduled (plan 2.1).
            current.copy(homeEnabled = false),
            // Set times use alarms instead (plan 6.4).
            current.copy(scheduleType = ScheduleType.TIMES)
        ).forEach { settings ->
            current = settings
            assertEquals(Result.success(), worker(ScreenType.HOME).doWork())
        }
        coVerify(exactly = 0) { controller.change(any(), any()) }
        coVerify(exactly = 3) { scheduler.updateSchedules(any(), WallpaperMode.STATIC, onlyIfNotScheduled = true) }
    }

    @Test fun `a static job does nothing in live mode`() = runTest {
        mode = WallpaperMode.LIVE
        current = current.copy(liveAlbumId = "live")
        worker(ScreenType.HOME).doWork()
        coVerify(exactly = 0) { controller.change(any(), any()) }
        coVerify { scheduler.updateSchedules(current, WallpaperMode.LIVE, onlyIfNotScheduled = true) }
    }

    @Test fun `battery saver skips this turn but keeps the job`() = runTest {
        every { conditions.automaticChangeBlock(any()) } returns ChangeBlock.BATTERY_SAVER
        assertEquals(Result.success(), worker(ScreenType.HOME).doWork())
        coVerify(exactly = 0) { controller.change(any(), any()) }
        coVerify(exactly = 0) { scheduler.updateSchedules(any(), any(), any()) }
    }

    @Test fun `an empty album is reported once and its job stops`() = runTest {
        coEvery { controller.change(any(), any()) } returns WallpaperChangeOutcome(emptyAlbum = true)
        assertEquals(Result.success(), worker(ScreenType.HOME).doWork())
        verify(exactly = 1) { notifier.showEmptyAlbum() }
        coVerify(exactly = 1) { scheduler.updateSchedules(any(), any(), onlyIfNotScheduled = true) }
    }

    @Test fun `a failure is retried quietly until the last attempt, then notified`() = runTest {
        coEvery { controller.change(any(), any()) } throws IllegalStateException("decoder broke")
        (0 until Constants.MAX_WORK_RETRY_ATTEMPTS).forEach { attempt ->
            assertEquals(Result.retry(), worker(ScreenType.HOME, attempt).doWork())
        }
        verify(exactly = 0) { notifier.showChangeFailed(any()) }

        assertEquals(Result.failure(), worker(ScreenType.HOME, Constants.MAX_WORK_RETRY_ATTEMPTS).doWork())
        verify(exactly = 1) { notifier.showChangeFailed("decoder broke") }
    }

    @Test fun `a cancelled job stays cancelled instead of counting as a failure`() = runTest {
        coEvery { controller.change(any(), any()) } throws CancellationException("stopped")
        try {
            worker(ScreenType.HOME).doWork()
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
        }
        verify(exactly = 0) { notifier.showChangeFailed(any()) }
    }

    @Test fun `the job waits for a change already in progress`() = runTest {
        val lock = WallpaperChangeLock()
        lock.mutex.lock()
        val params = mockk<WorkerParameters>(relaxed = true) {
            every { inputData } returns Data.Builder().putString(Constants.EXTRA_SCREEN_TYPE, "HOME").build()
        }
        val worker = WallpaperChangeWorker(
            mockk(relaxed = true), params, controller, settings, lock, scheduler, notifier, conditions
        )
        val job = backgroundScope.async { worker.doWork() }
        testScheduler.runCurrent()
        coVerify(exactly = 0) { controller.change(any(), any()) }
        lock.mutex.unlock()
        assertEquals(Result.success(), job.await())
        coVerify(exactly = 1) { controller.change(ScreenType.HOME, current) }
    }
}
