package com.anthonyla.paperize.service.wallpaper

import com.anthonyla.paperize.core.ScreenType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the change service does with each start (plans 3.4 and 3.12). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeServiceStartsTest {
    private val handled = mutableListOf<Pair<WallpaperRequest, Boolean>>()
    private val handedOver = mutableListOf<Pair<WallpaperRequest, Boolean>>()
    private val stopped = mutableListOf<Int>()
    private var reportsEnded = 0
    private var gate: CompletableDeferred<Unit>? = null

    private fun TestScope.starts(scope: CoroutineScope = backgroundScope) = ChangeServiceStarts(
        scope = scope,
        handle = { request, report ->
            gate?.await()
            handled += request to report
        },
        handOver = { request, report -> handedOver += request to report },
        endReport = { reportsEnded++ },
        stop = { stopped += it }
    )

    private val change = WallpaperRequest.Change(ScreenType.HOME)

    @Test fun `a request runs in the foreground and then stops its own start`() = runTest {
        starts().start(change, report = true, startId = 4, inForeground = true)
        runCurrent()
        assertEquals(listOf(change to true), handled)
        assertEquals(listOf(4), stopped)
        // The handler ends the reported request itself.
        assertEquals(0, reportsEnded)
    }

    @Test fun `overlapping requests each stop only their own start`() = runTest {
        val starts = starts()
        gate = CompletableDeferred()
        starts.start(change, report = false, startId = 1, inForeground = true)
        starts.start(WallpaperRequest.Reapply(ScreenType.LOCK), report = false, startId = 2, inForeground = true)
        runCurrent()
        assertEquals(emptyList<Int>(), stopped)
        gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2), stopped)
    }

    @Test fun `a refused foreground start hands the request to a background job`() = runTest {
        val apply = WallpaperRequest.ApplySpecific("image", ScreenType.LOCK)
        starts().start(apply, report = true, startId = 7, inForeground = false)
        runCurrent()
        assertEquals(listOf(apply to true), handedOver)
        assertEquals(emptyList<Pair<WallpaperRequest, Boolean>>(), handled)
        assertEquals(listOf(7), stopped)
        // The background job reports and ends it.
        assertEquals(0, reportsEnded)
    }

    @Test fun `an unknown request ends its report and stops, whether or not it runs in the foreground`() = runTest {
        val starts = starts()
        starts.start(null, report = true, startId = 1, inForeground = true)
        starts.start(null, report = true, startId = 2, inForeground = false)
        starts.start(null, report = false, startId = 3, inForeground = true)
        runCurrent()
        assertEquals(2, reportsEnded)
        assertEquals(listOf(1, 2, 3), stopped)
        assertEquals(emptyList<Pair<WallpaperRequest, Boolean>>(), handled + handedOver)
    }

    @Test fun `a request cut short by the service ending still stops its start`() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        gate = CompletableDeferred()
        starts(scope).start(change, report = false, startId = 9, inForeground = true)
        runCurrent()
        scope.cancel()
        runCurrent()
        assertEquals(listOf(9), stopped)
        assertEquals(emptyList<Pair<WallpaperRequest, Boolean>>(), handled)
    }
}
