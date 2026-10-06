package com.anthonyla.paperize.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import com.anthonyla.paperize.core.EmptyAlbumException
import com.anthonyla.paperize.core.NoValidWallpaperException
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.domain.model.PreparedWallpaper
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeWallpaperUseCaseTest {

    private val repository = mockk<WallpaperRepository>(relaxed = true)
    private val renderer = mockk<com.anthonyla.paperize.core.util.WallpaperRenderer>()
    private val useCase = ChangeWallpaperUseCase(
        context = mockk<Context>(relaxed = true),
        wallpaperRepository = repository,
        settingsRepository = mockk<SettingsRepository>(relaxed = true),
        renderer = renderer
    )

    @Test
    fun `complete records current then synchronizes exact queue item`() = runTest {
        val prepared = preparedWallpaper()
        coEvery {
            repository.ensureWallpaperQueue(prepared.albumId, ScreenType.LOCK, false)
        } returns Result.Success(Unit)

        useCase.complete(prepared, ScreenType.LOCK)

        coVerifyOrder {
            repository.setCurrentWallpaper(
                prepared.albumId,
                ScreenType.LOCK,
                prepared.wallpaperId
            )
            repository.ensureWallpaperQueue(prepared.albumId, ScreenType.LOCK, false)
            repository.removeWallpaperFromQueue(
                prepared.albumId,
                ScreenType.LOCK,
                prepared.wallpaperId
            )
        }
    }

    @Test
    fun `restore returns rejected item to its original queue`() = runTest {
        val prepared = preparedWallpaper()

        useCase.restore(prepared)

        coVerify(exactly = 1) {
            repository.restoreWallpaperToQueueFront(
                prepared.albumId,
                prepared.screenType,
                prepared.wallpaperId
            )
        }
        coVerify(exactly = 0) {
            repository.setCurrentWallpaper(any(), any(), any())
        }
    }

    @Test
    fun `complete specific records selected item and respects shuffle`() = runTest {
        coEvery {
            repository.ensureWallpaperQueue("album", ScreenType.HOME, true)
        } returns Result.Success(Unit)

        useCase.completeSpecific("album", ScreenType.HOME, "selected", shuffle = true)

        coVerifyOrder {
            repository.setCurrentWallpaper("album", ScreenType.HOME, "selected")
            repository.ensureWallpaperQueue("album", ScreenType.HOME, true)
            repository.removeWallpaperFromQueue("album", ScreenType.HOME, "selected")
        }
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun `cancelled rendering restores the dequeued item without retrying`() = runTest {
        val wallpaper = com.anthonyla.paperize.domain.model.Wallpaper.empty("image", "album")
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.HOME) } returns wallpaper
        coEvery { renderer.render(wallpaper, ScreenType.HOME, any()) } throws kotlinx.coroutines.CancellationException()
        try {
            useCase("album", ScreenType.HOME)
        } finally {
            coVerify(exactly = 1) { repository.getAndDequeueWallpaper("album", ScreenType.HOME) }
            coVerify(exactly = 1) { repository.restoreWallpaperToQueueFront("album", ScreenType.HOME, "image") }
        }
    }

    @Test
    fun `unreadable single image album is not reported as empty`() = runTest {
        val wallpaper = com.anthonyla.paperize.domain.model.Wallpaper.empty("image", "album")
        var dequeues = 0
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.HOME) } answers {
            if (dequeues++ % 2 == 0) null else wallpaper
        }
        coEvery { repository.ensureWallpaperQueue("album", ScreenType.HOME, any()) } returns Result.Success(Unit)
        coEvery { renderer.render(wallpaper, ScreenType.HOME, any()) } returns null

        assertTrue((useCase("album", ScreenType.HOME) as Result.Error).exception is NoValidWallpaperException)
        coVerify(exactly = Constants.MAX_WALLPAPER_LOAD_RETRIES) { renderer.render(wallpaper, ScreenType.HOME, any()) }
    }

    @Test
    fun `empty album stops after rebuilding once`() = runTest {
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.HOME) } returns null
        coEvery { repository.ensureWallpaperQueue("album", ScreenType.HOME, any()) } returns Result.Success(Unit)

        assertTrue((useCase("album", ScreenType.HOME) as Result.Error).exception is EmptyAlbumException)
        coVerify(exactly = 1) { repository.ensureWallpaperQueue("album", ScreenType.HOME, any()) }
        coVerify(exactly = 0) { renderer.render(any(), any(), any()) }
    }

    @Test
    fun `an album whose images all lost access keeps its selection`() = runTest {
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.HOME) } returns null
        coEvery { repository.ensureWallpaperQueue("album", ScreenType.HOME, any()) } returns Result.Success(Unit)
        coEvery { repository.countWallpapers("album") } returns 3

        // Not EmptyAlbumException, which would clear the album selection and turn changing off.
        assertTrue((useCase("album", ScreenType.HOME) as Result.Error).exception is NoValidWallpaperException)
    }

    private val sharedAlbum = ScheduleSettings(homeEnabled = true, lockEnabled = true, homeAlbumId = "album", lockAlbumId = "album")

    private fun useCaseWith(settings: ScheduleSettings) = ChangeWallpaperUseCase(
        context = mockk<Context>(relaxed = true),
        wallpaperRepository = repository,
        settingsRepository = mockk<SettingsRepository> { coEvery { getScheduleSettings() } returns settings },
        renderer = renderer
    )

    @Test
    fun `a screen sharing its album passes over the image the other screen shows`() = runTest {
        val shown = Wallpaper.empty("shown", "album")
        val next = Wallpaper.empty("next", "album")
        val bitmap = mockk<Bitmap>(relaxed = true)
        coEvery { repository.getCurrentWallpaper("album", ScreenType.LOCK) } returns shown
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.HOME, "shown") } returns next
        coEvery { renderer.render(next, ScreenType.HOME, any()) } returns bitmap

        val prepared = (useCaseWith(sharedAlbum)("album", ScreenType.HOME) as Result.Success).data
        assertEquals("next", prepared.wallpaperId)
        coVerify(exactly = 0) { repository.getAndDequeueWallpaper("album", ScreenType.HOME, null) }
    }

    @Test
    fun `the other screen's image is used when nothing else can rotate`() = runTest {
        val shown = Wallpaper.empty("shown", "album")
        val bitmap = mockk<Bitmap>(relaxed = true)
        coEvery { repository.getCurrentWallpaper("album", ScreenType.HOME) } returns shown
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.LOCK, "shown") } returns null
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.LOCK, null) } returns shown
        coEvery { repository.ensureWallpaperQueue(any(), any(), any(), any(), any()) } returns Result.Success(Unit)
        coEvery { renderer.render(shown, ScreenType.LOCK, any()) } returns bitmap

        val prepared = (useCaseWith(sharedAlbum)("album", ScreenType.LOCK) as Result.Success).data
        assertEquals("shown", prepared.wallpaperId)
        // A new round starts first, so the image is only reused when the album has nothing else.
        coVerifyOrder {
            repository.ensureWallpaperQueue("album", ScreenType.LOCK, false, true, "shown")
            repository.getAndDequeueWallpaper("album", ScreenType.LOCK, "shown")
            repository.getAndDequeueWallpaper("album", ScreenType.LOCK, null)
        }
    }

    @Test
    fun `only a lock screen sharing home's album starts half-way, and only in order`() {
        assertTrue(startsHalfway(sharedAlbum, "album", ScreenType.LOCK))
        assertFalse(startsHalfway(sharedAlbum, "album", ScreenType.HOME))
        assertFalse(startsHalfway(sharedAlbum.copy(shuffleEnabled = true), "album", ScreenType.LOCK))
        assertFalse(startsHalfway(sharedAlbum.copy(homeAlbumId = "other"), "album", ScreenType.LOCK))
        // A turned-off home screen keeps its album but no longer shares it.
        assertFalse(startsHalfway(sharedAlbum.copy(homeEnabled = false), "album", ScreenType.LOCK))
        assertFalse(sharesAlbumWithOtherScreen(sharedAlbum, "album", ScreenType.LIVE))
    }

    private fun preparedWallpaper() = PreparedWallpaper(
        bitmap = mockk<Bitmap>(relaxed = true),
        albumId = "album",
        screenType = ScreenType.HOME,
        wallpaperId = "wallpaper",
        shuffle = false
    )
}
