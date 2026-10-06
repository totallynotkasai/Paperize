package com.anthonyla.paperize.service.wallpaper

import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Kind
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperChangeEventsTest {
    private val events = WallpaperChangeEvents()
    private val changed = WallpaperChangeResult(Kind.CHANGE, Outcome.CHANGED)

    @Test fun `with no visible screen a result is not taken, so the caller can notify`() {
        assertFalse(events.publish(changed))
    }

    @Test fun `a visible screen receives the result`() = runTest {
        val received = mutableListOf<WallpaperChangeResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.results.collect(received::add) }
        assertTrue(events.publish(changed))
        assertEquals(listOf(changed), received)
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
