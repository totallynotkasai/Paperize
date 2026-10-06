package com.anthonyla.paperize.service.wallpaper

import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Kind
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperChangeEventsTest {
    private var foreground = true
    private var now = 1_000L
    private val events = WallpaperChangeEvents(appInForeground = { foreground }, clock = { now })
    private val changed = WallpaperChangeResult(Kind.CHANGE, Outcome.CHANGED)
    private val failed = WallpaperChangeResult(Kind.CHANGE, Outcome.FAILED, "boom")

    @Test fun `in the background a result is not taken, so the caller notifies`() {
        foreground = false
        assertFalse(events.publish(changed))
    }

    @Test fun `a result waits for a screen, as when a new wallpaper re-creates the activity`() = runTest {
        // Published while no screen is listening (the old activity has gone, the new one isn't up).
        assertTrue(events.publish(changed))
        assertEquals(changed, events.results.first().result)
    }

    @Test fun `a result stays until a screen has shown it in full`() = runTest {
        events.publish(changed)
        val first = events.results.first()
        // The first screen went away part-way; the next one gets the same result again.
        assertEquals(first, events.results.first())
        events.shown(first)
        val seen = mutableListOf<PendingChangeResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.results.collect(seen::add) }
        assertTrue(seen.isEmpty())
    }

    @Test fun `marking an older result shown keeps a newer one`() = runTest {
        events.publish(changed)
        val older = events.results.first()
        events.publish(failed)
        events.shown(older)
        assertEquals(failed, events.results.first().result)
    }

    @Test fun `results nobody showed in time are dropped`() = runTest {
        events.publish(changed)
        now += 10_001L
        val seen = mutableListOf<PendingChangeResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.results.collect(seen::add) }
        assertTrue(seen.isEmpty())
    }

    @Test fun `pending counts running requests and never goes below zero`() {
        events.begin()
        events.begin()
        assertEquals(2, events.pending.value)
        events.end()
        events.end()
        events.end()
        assertEquals(0, events.pending.value)
    }
}
