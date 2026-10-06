package com.anthonyla.paperize.domain.usecase

import android.util.Log
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MarkWallpapersUseCaseTest {
    private val albums = mockk<AlbumRepository>()
    private val wallpapers = mockk<WallpaperRepository>()
    private val settings = mockk<SettingsRepository>()
    private val useCase = MarkWallpapersUseCase(albums, wallpapers, settings)

    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        coEvery { settings.getScheduleSettings() } returns ScheduleSettings(shuffleEnabled = true)
        coEvery { wallpapers.rotatesFavoritesOnly("album") } returns false
        coEvery { wallpapers.favoritesChanged(any(), any(), any(), any()) } returns Result.Success(Unit)
        coEvery { wallpapers.addToQueues(any(), any(), any()) } returns Result.Success(Unit)
        coEvery { wallpapers.clearQueues(any()) } returns Result.Success(Unit)
    }

    @After fun tearDown() = unmockkAll()

    @Test fun `only images whose mark changed adjust the rounds`() = runTest {
        coEvery { albums.setFavorite("album", listOf("a", "b"), true) } returns Result.Success(listOf("b"))
        assertEquals(Result.Success(1), useCase.setFavorite("album", listOf("a", "b"), true))
        coVerify(exactly = 1) { wallpapers.favoritesChanged("album", listOf("b"), true, true) }
        coVerify(exactly = 0) { wallpapers.clearQueues(any()) }
    }

    @Test fun `nothing changed means the rounds are left alone`() = runTest {
        coEvery { albums.setFavorite("album", listOf("a"), false) } returns Result.Success(emptyList())
        assertEquals(Result.Success(0), useCase.setFavorite("album", listOf("a"), false))
        coVerify(exactly = 0) { wallpapers.favoritesChanged(any(), any(), any(), any()) }
    }

    @Test fun `the first usable favourite in favourites-only mode starts a new round`() = runTest {
        var marked = false
        coEvery { wallpapers.rotatesFavoritesOnly("album") } answers { marked }
        coEvery { albums.setFavorite("album", listOf("a"), true) } answers { marked = true; Result.Success(listOf("a")) }
        useCase.setFavorite("album", listOf("a"), true)
        coVerify(exactly = 1) { wallpapers.clearQueues("album") }
        coVerify(exactly = 0) { wallpapers.favoritesChanged(any(), any(), any(), any()) }
    }

    @Test fun `excluding the last usable favourite also starts a new round`() = runTest {
        var excluded = false
        coEvery { wallpapers.rotatesFavoritesOnly("album") } answers { !excluded }
        coEvery { albums.setExcluded("album", listOf("fav"), true) } answers { excluded = true; Result.Success(listOf("fav")) }
        useCase.setExcluded("album", listOf("fav"), true)
        coVerify(exactly = 1) { wallpapers.clearQueues("album") }
    }

    @Test fun `images included again join the rounds in progress`() = runTest {
        coEvery { albums.setExcluded("album", listOf("a", "b"), false) } returns Result.Success(listOf("a", "b"))
        useCase.setExcluded("album", listOf("a", "b"), false)
        coVerify(exactly = 1) { wallpapers.addToQueues("album", listOf("a", "b"), true) }

        coEvery { albums.setExcluded("album", listOf("c"), true) } returns Result.Success(listOf("c"))
        useCase.setExcluded("album", listOf("c"), true)
        // Excluded images are passed over where they wait, so the rounds need no change.
        coVerify(exactly = 1) { wallpapers.addToQueues(any(), any(), any()) }
    }

    @Test fun `a failure keeping the rounds in line doesn't fail the mark`() = runTest {
        coEvery { albums.setFavorite("album", listOf("a"), true) } returns Result.Success(listOf("a"))
        coEvery { wallpapers.favoritesChanged(any(), any(), any(), any()) } returns Result.Error(IllegalStateException("disk"))
        assertEquals(Result.Success(1), useCase.setFavorite("album", listOf("a"), true))
    }

    @Test fun `a failed mark is reported`() = runTest {
        coEvery { albums.setFavorite("album", listOf("a"), true) } returns Result.Error(IllegalStateException("disk"))
        assert(useCase.setFavorite("album", listOf("a"), true) is Result.Error)
    }
}
