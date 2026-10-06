package com.anthonyla.paperize.service.livewallpaper.renderer

import android.graphics.Bitmap
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LiveWallpaperImageLoaderTest {
    private val repository = mockk<WallpaperRepository>(relaxed = true)
    private val bitmap = mockk<Bitmap>(relaxed = true)
    private val current = Wallpaper.empty("current", "album").copy(uri = "content://current")
    private val next = Wallpaper.empty("next", "album").copy(uri = "content://next")
    private val decoded = mutableListOf<String>()

    private fun loader(
        selection: LiveSelection,
        readable: (Wallpaper) -> Boolean = { true }
    ) = LiveWallpaperImageLoader(repository, "album", shuffle = false, selection = selection, decode = { wallpaper, _, _ ->
        decoded += wallpaper.id
        if (readable(wallpaper)) bitmap else null
    })

    @Test fun `a restarted engine shows the recorded image without advancing`() = runTest {
        coEvery { repository.getCurrentWallpaper("album", ScreenType.LIVE) } returns current
        val loader = loader(LiveSelection.RESUME)

        assertSame(bitmap, loader.load(100, 200))
        assertEquals(current, loader.wallpaper)
        coVerify(exactly = 0) { repository.getAndDequeueWallpaper(any(), any()) }
    }

    @Test fun `resume advances when nothing was recorded or it can't be read`() = runTest {
        coEvery { repository.getCurrentWallpaper("album", ScreenType.LIVE) } returns current
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.LIVE) } returns next
        val loader = loader(LiveSelection.RESUME) { it != current }

        assertSame(bitmap, loader.load(100, 200))
        assertEquals(next, loader.wallpaper)
        assertEquals(listOf("current", "next"), decoded)
    }

    @Test fun `advancing skips unreadable images up to the retry limit`() = runTest {
        var dequeued = 0
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.LIVE) } answers {
            Wallpaper.empty("image-${dequeued++}", "album")
        }
        assertNull(loader(LiveSelection.ADVANCE) { false }.load(100, 200))
        assertEquals(Constants.MAX_WALLPAPER_LOAD_RETRIES, decoded.size)

        decoded.clear()
        dequeued = 0
        val loader = loader(LiveSelection.ADVANCE) { it.id == "image-3" }
        assertSame(bitmap, loader.load(100, 200))
        assertEquals("image-3", loader.wallpaper?.id)
        assertEquals(4, decoded.size)
        coVerify(exactly = 0) { repository.getCurrentWallpaper(any(), any()) }
    }

    @Test fun `an empty queue is rebuilt once before giving up`() = runTest {
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.LIVE) } returns null
        coEvery { repository.ensureWallpaperQueue("album", ScreenType.LIVE, false) } returns Result.Success(Unit)

        assertNull(loader(LiveSelection.ADVANCE).load(100, 200))
        coVerify(exactly = 1) { repository.ensureWallpaperQueue("album", ScreenType.LIVE, false) }
    }

    @Test fun `previews never consume the queue`() = runTest {
        coEvery { repository.getCurrentWallpaper("album", ScreenType.LIVE) } returns null
        coEvery { repository.getNextWallpaperInQueue("album", ScreenType.LIVE) } returns next
        val loader = loader(LiveSelection.PEEK)

        assertSame(bitmap, loader.load(100, 200))
        assertEquals(next, loader.wallpaper)
        coVerify(exactly = 0) { repository.getAndDequeueWallpaper(any(), any()) }
    }

    @Test fun `a surface change redraws the pinned image instead of advancing again`() = runTest {
        coEvery { repository.getAndDequeueWallpaper("album", ScreenType.LIVE) } returnsMany listOf(next, current)
        val loader = loader(LiveSelection.ADVANCE)

        loader.load(100, 200)
        loader.load(200, 100)

        assertEquals(listOf("next", "next"), decoded)
        coVerify(exactly = 1) { repository.getAndDequeueWallpaper("album", ScreenType.LIVE) }
    }
}
