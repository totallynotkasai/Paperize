package com.anthonyla.paperize.service.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.anthonyla.paperize.core.Result as CoreResult
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.usecase.FolderRefreshResult
import com.anthonyla.paperize.domain.usecase.RefreshFolderUseCase
import com.anthonyla.paperize.testing.emptyAlbum
import com.anthonyla.paperize.testing.emptyAlbumSummary
import com.anthonyla.paperize.testing.emptyFolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The album refresh: daily at 3 AM, and on opening the app at most every few hours (plan 2.8). */
class AlbumRefreshWorkerTest {
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val history = mockk<SharedPreferences>(relaxed = true) { every { edit() } returns editor }
    private val context = mockk<Context>(relaxed = true) {
        every { getSharedPreferences(any(), any()) } returns history
    }
    private val albums = mockk<AlbumRepository>()
    private val refreshFolder = mockk<RefreshFolderUseCase>()

    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        coEvery { albums.syncAccess(any()) } returns CoreResult.Success(0)
        coEvery { albums.pruneMissingEntries(any()) } returns CoreResult.Success(0)
    }

    @After fun tearDown() = unmockkAll()

    private fun worker(fromForeground: Boolean = false) = AlbumRefreshWorker(
        context,
        mockk<WorkerParameters>(relaxed = true) {
            every { inputData } returns Data.Builder().putBoolean(AlbumRefreshWorker.KEY_FROM_FOREGROUND, fromForeground).build()
        },
        albums,
        refreshFolder
    )

    private fun lastRefreshed(millisAgo: Long) {
        every { history.getLong(any(), any()) } returns System.currentTimeMillis() - millisAgo
    }

    private fun twoAlbums() {
        every { albums.getAlbumSummaries() } returns flowOf(listOf(emptyAlbumSummary("a"), emptyAlbumSummary("b")))
        every { albums.getAlbumById("a") } returns flowOf(emptyAlbum("a").copy(folders = listOf(emptyFolder("f1", "a"), emptyFolder("f2", "a"))))
        every { albums.getAlbumById("b") } returns flowOf(emptyAlbum("b"))
    }

    @Test fun `every album is checked, pruned and its folders rescanned`() = runTest {
        twoAlbums()
        coEvery { refreshFolder(any()) } returns CoreResult.Success(FolderRefreshResult(added = 1, removed = 0))
        assertEquals(Result.success(), worker().doWork())
        listOf("a", "b").forEach { id ->
            coVerify(exactly = 1) { albums.syncAccess(id) }
            coVerify(exactly = 1) { albums.pruneMissingEntries(id) }
        }
        coVerify(exactly = 1) { refreshFolder("f1") }
        coVerify(exactly = 1) { refreshFolder("f2") }
        verify { editor.putLong(any(), any()) }
        verify { editor.apply() }
    }

    @Test fun `opening the app soon after a refresh skips it`() = runTest {
        lastRefreshed(millisAgo = 60_000)
        assertEquals(Result.success(), worker(fromForeground = true).doWork())
        verify(exactly = 0) { albums.getAlbumSummaries() }
    }

    @Test fun `opening the app after a few hours refreshes, and the 3 AM run always does`() = runTest {
        every { albums.getAlbumSummaries() } returns flowOf(emptyList())
        lastRefreshed(millisAgo = Constants.FOREGROUND_REFRESH_MIN_INTERVAL_MS + 1)
        worker(fromForeground = true).doWork()
        lastRefreshed(millisAgo = 60_000)
        worker(fromForeground = false).doWork()
        verify(exactly = 2) { albums.getAlbumSummaries() }
    }

    @Test fun `a clock set back doesn't block refreshes`() = runTest {
        every { albums.getAlbumSummaries() } returns flowOf(emptyList())
        // The last refresh appears to be in the future.
        lastRefreshed(millisAgo = -60_000)
        worker(fromForeground = true).doWork()
        verify(exactly = 1) { albums.getAlbumSummaries() }
    }

    @Test fun `one album or folder failing doesn't stop the others`() = runTest {
        twoAlbums()
        coEvery { albums.pruneMissingEntries("a") } returns CoreResult.Error(IllegalStateException("provider gone"))
        coEvery { refreshFolder("f1") } returns CoreResult.Error(IllegalStateException("scan failed"))
        coEvery { refreshFolder("f2") } returns CoreResult.Success(FolderRefreshResult(added = 0, removed = 2))
        assertEquals(Result.success(), worker().doWork())
        coVerify(exactly = 1) { refreshFolder("f2") }
        coVerify(exactly = 1) { albums.pruneMissingEntries("b") }
    }

    @Test fun `an unexpected error fails this run without recording it as done`() = runTest {
        every { albums.getAlbumSummaries() } returns flowOf(listOf(emptyAlbumSummary("a")))
        coEvery { albums.syncAccess("a") } throws IllegalStateException("database closed")
        assertEquals(Result.failure(), worker().doWork())
        verify(exactly = 0) { editor.putLong(any(), any()) }
    }
}
