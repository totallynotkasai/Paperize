package com.anthonyla.paperize.service.wallpaper

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.anthonyla.paperize.core.ScreenType
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The background job that runs a request Android wouldn't start the service for (plan 3.12). */
class WallpaperRequestWorkerTest {
    private val handler = mockk<WallpaperRequestHandler>(relaxed = true)

    private fun worker(input: Data) = WallpaperRequestWorker(
        mockk<Context>(relaxed = true),
        mockk<WorkerParameters>(relaxed = true) { every { inputData } returns input },
        handler
    )

    @Test fun `every kind of request reaches the handler as it was sent`() = runTest {
        val requests = listOf(
            WallpaperRequest.Change(ScreenType.LOCK, keepSchedule = true) to true,
            WallpaperRequest.Change(ScreenType.BOTH, followMode = true) to false,
            WallpaperRequest.Change(ScreenType.HOME, automatic = true) to false,
            WallpaperRequest.ApplySpecific("image", ScreenType.HOME) to true,
            WallpaperRequest.Reapply(ScreenType.LOCK) to false
        )
        requests.forEach { (request, report) ->
            // The handler turns problems into a result or a notification, so the job never retries.
            assertEquals(Result.success(), worker(request.toData(report)).doWork())
            coVerify(exactly = 1) { handler.handle(request, report) }
        }
    }

    @Test fun `unreadable input fails without doing anything`() = runTest {
        assertEquals(Result.failure(), worker(Data.EMPTY).doWork())
        assertEquals(Result.failure(), worker(Data.Builder().putString("action", "something else").build()).doWork())
        coVerify(exactly = 0) { handler.handle(any(), any()) }
    }
}
