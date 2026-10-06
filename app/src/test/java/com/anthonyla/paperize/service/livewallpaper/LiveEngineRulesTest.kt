package com.anthonyla.paperize.service.livewallpaper

import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.service.livewallpaper.renderer.LiveSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live wallpaper engines' decisions (plans 1.5–1.7, 2.11, 6.1, 6.4). */
class LiveEngineRulesTest {

    /** A stand-in for an engine: what the system says about it. */
    private class Engine(val name: String, val preview: Boolean = false, var drawsHome: Boolean = false) {
        override fun toString() = name
    }

    private fun roster() = EngineRoster<Engine>(isPreview = { it.preview }, drawsHomeScreen = { it.drawsHome })

    // Which engine leads.

    @Test fun `a preview never leads, so it never takes images from the queue`() {
        val roster = roster()
        val preview = Engine("preview", preview = true)
        assertFalse(roster.attach(preview))
        assertNull(roster.leader)

        val real = Engine("real")
        assertTrue(roster.attach(real))
        assertSame(real, roster.leader)
        assertEquals(listOf(preview), roster.followers())
    }

    @Test fun `the first real engine leads until it goes`() {
        val roster = roster()
        val first = Engine("first")
        val second = Engine("second")
        roster.attach(first)
        assertFalse("a second engine doesn't take over", roster.attach(second))
        assertSame(first, roster.leader)

        assertTrue(roster.detach(first))
        assertSame(second, roster.leader)
        assertTrue(roster.detach(second))
        assertNull(roster.leader)
    }

    @Test fun `the engine drawing the home screen leads when home and lock have their own`() {
        val roster = roster()
        val lock = Engine("lock")
        val home = Engine("home", drawsHome = true)
        roster.attach(lock)
        assertTrue(roster.attach(home))
        assertSame(home, roster.leader)

        // The system moves the home screen to the other engine (Android 14+ flag change).
        home.drawsHome = false
        lock.drawsHome = true
        assertTrue(roster.elect())
        assertSame(lock, roster.leader)
        assertFalse("nothing changed, so no new election result", roster.elect())
    }

    @Test fun `followers are every engine but the leader, in creation order`() {
        val roster = roster()
        val engines = listOf(Engine("a"), Engine("b", preview = true), Engine("c"))
        engines.forEach { roster.attach(it) }
        assertEquals(engines, roster.engines)
        assertEquals(listOf(engines[1], engines[2]), roster.followers())
    }

    // What each engine shows and records.

    @Test fun `only the leader takes the pending selection, others only look`() {
        LiveSelection.entries.forEach { pending ->
            assertEquals(pending, liveSelectionFor(preview = false, leader = true, pending = pending))
            assertEquals(LiveSelection.PEEK, liveSelectionFor(preview = false, leader = false, pending = pending))
            assertEquals(LiveSelection.PEEK, liveSelectionFor(preview = true, leader = false, pending = pending))
        }
    }

    @Test fun `the leader records an image once it is shown, and only once`() {
        assertTrue(recordsShownImage(leader = true, selection = LiveSelection.ADVANCE, recordedId = "a", shownId = "b"))
        assertTrue(recordsShownImage(leader = true, selection = LiveSelection.RESUME, recordedId = null, shownId = "a"))
        // Showing the recorded image again (a restart) writes nothing.
        assertFalse(recordsShownImage(leader = true, selection = LiveSelection.RESUME, recordedId = "a", shownId = "a"))
        // A peeked image wasn't taken from the queue, and followers never record.
        assertFalse(recordsShownImage(leader = true, selection = LiveSelection.PEEK, recordedId = null, shownId = "a"))
        assertFalse(recordsShownImage(leader = false, selection = LiveSelection.ADVANCE, recordedId = null, shownId = "a"))
    }

    // The short-interval timer.

    private val shortInterval = ScheduleSettings(enableChanger = true, liveAlbumId = "live", liveIntervalMinutes = 5)

    @Test fun `the short timer runs on the visible leader while changing is on`() {
        assertTrue(runsLiveTimer(visible = true, leader = true, mode = WallpaperMode.LIVE, settings = shortInterval))
        assertFalse(runsLiveTimer(visible = false, leader = true, mode = WallpaperMode.LIVE, settings = shortInterval))
        assertFalse(runsLiveTimer(visible = true, leader = false, mode = WallpaperMode.LIVE, settings = shortInterval))
        assertFalse(runsLiveTimer(visible = true, leader = true, mode = WallpaperMode.STATIC, settings = shortInterval))
        assertFalse(runsLiveTimer(true, true, WallpaperMode.LIVE, shortInterval.copy(enableChanger = false)))
        assertFalse(runsLiveTimer(true, true, WallpaperMode.LIVE, shortInterval.copy(liveAlbumId = null)))
    }

    @Test fun `intervals of 15 minutes or more and set times leave changing to background jobs`() {
        assertFalse(runsLiveTimer(true, true, WallpaperMode.LIVE, shortInterval.copy(liveIntervalMinutes = 15)))
        assertFalse(runsLiveTimer(true, true, WallpaperMode.LIVE, shortInterval.copy(scheduleType = ScheduleType.TIMES)))
        assertTrue(runsLiveTimer(true, true, WallpaperMode.LIVE, shortInterval.copy(liveIntervalMinutes = 1)))
    }

    // Screen off and double-tap.

    private val interactive = ScheduleSettings(
        enableChanger = true, liveAlbumId = "live",
        liveEffects = WallpaperEffects(enableDoubleTap = true, enableChangeOnScreenOff = true)
    )

    @Test fun `screen off changes the wallpaper once, on the leader, when automatic changes may happen`() {
        assertTrue(changesOnScreenOff(interactive, leader = true, automaticChangeAllowed = true))
        assertFalse(changesOnScreenOff(interactive, leader = false, automaticChangeAllowed = true))
        // Paused, or held back by the battery settings (plan 6.1).
        assertFalse(changesOnScreenOff(interactive, leader = true, automaticChangeAllowed = false))
        val off = interactive.copy(liveEffects = WallpaperEffects(enableChangeOnScreenOff = false))
        assertFalse(changesOnScreenOff(off, leader = true, automaticChangeAllowed = true))
    }

    @Test fun `a double-tap is a manual change, so it works while paused`() {
        assertTrue(changesOnDoubleTap(interactive, preview = false, hasLeader = true))
        assertTrue(changesOnDoubleTap(interactive.copy(enableChanger = false), preview = false, hasLeader = true))
        assertFalse(changesOnDoubleTap(interactive, preview = true, hasLeader = true))
        assertFalse(changesOnDoubleTap(interactive, preview = false, hasLeader = false))
        val off = interactive.copy(liveEffects = WallpaperEffects(enableDoubleTap = false))
        assertFalse(changesOnDoubleTap(off, preview = false, hasLeader = true))
    }

    // Settings updates.

    private val live = ScheduleSettings(enableChanger = true, liveAlbumId = "day")
    private val allowed = { _: ScheduleSettings -> true }
    private val notAllowed = { _: ScheduleSettings -> false }

    @Test fun `the first update only records the album the engine started on`() {
        val tracker = LiveSettingsTracker()
        val first = tracker.update(live, WallpaperMode.LIVE, allowed)
        assertEquals(LiveAlbumChange.NONE, first.album)
        assertTrue(first.live)
        assertTrue("the timer starts with the first settings", first.restartTimer)
        assertFalse(first.reloadForScaling)
    }

    @Test fun `picking another album shows its next image`() {
        val tracker = LiveSettingsTracker()
        tracker.update(live, WallpaperMode.LIVE, allowed)
        // Picking an album is a manual change: it happens even while automatic ones may not.
        val update = tracker.update(live.copy(liveAlbumId = "other"), WallpaperMode.LIVE, notAllowed)
        assertEquals(LiveAlbumChange.RELOAD, update.album)
        assertTrue(update.restartTimer)
    }

    @Test fun `a day-night switch changes the image only when automatic changes may happen`() {
        val withNight = live.copy(liveNightAlbumId = "night")
        val tracker = LiveSettingsTracker()
        tracker.update(withNight, WallpaperMode.LIVE, allowed)
        assertEquals(LiveAlbumChange.RELOAD, tracker.update(withNight.copy(nightActive = true), WallpaperMode.LIVE, allowed).album)

        // Paused or held back: the image stays; the album in use still switches back by day.
        assertEquals(LiveAlbumChange.HELD_BACK, tracker.update(withNight, WallpaperMode.LIVE, notAllowed).album)
        // Nothing switched, so nothing is asked.
        assertEquals(LiveAlbumChange.NONE, tracker.update(withNight, WallpaperMode.LIVE) { error("not asked") }.album)
    }

    @Test fun `picking a night album while it is night counts as picking an album`() {
        val tracker = LiveSettingsTracker()
        tracker.update(live.copy(nightActive = true), WallpaperMode.LIVE, allowed)
        val update = tracker.update(live.copy(nightActive = true, liveNightAlbumId = "night"), WallpaperMode.LIVE, notAllowed)
        assertEquals(LiveAlbumChange.RELOAD, update.album)
    }

    @Test fun `new scaling reloads the same image instead of moving on`() {
        val tracker = LiveSettingsTracker()
        tracker.update(live, WallpaperMode.LIVE, allowed)
        val update = tracker.update(live.copy(liveScalingType = ScalingType.FIT), WallpaperMode.LIVE, allowed)
        assertTrue(update.reloadForScaling)
        assertEquals(LiveAlbumChange.NONE, update.album)
        assertFalse("scaling doesn't touch the timer", update.restartTimer)

        // With a new album at the same time, the new album's image is loaded instead.
        val both = tracker.update(live.copy(liveAlbumId = "other", liveScalingType = ScalingType.FILL), WallpaperMode.LIVE, allowed)
        assertFalse(both.reloadForScaling)
        assertEquals(LiveAlbumChange.RELOAD, both.album)
    }

    @Test fun `effect edits neither restart the timer nor reload`() {
        val tracker = LiveSettingsTracker()
        tracker.update(live, WallpaperMode.LIVE, allowed)
        val update = tracker.update(live.copy(liveEffects = WallpaperEffects(enableBlur = true)), WallpaperMode.LIVE, allowed)
        assertEquals(LiveSettingsUpdate(restartTimer = false, live = true), update)
    }

    @Test fun `the timer restarts when anything it depends on changes`() {
        val tracker = LiveSettingsTracker()
        tracker.update(live, WallpaperMode.LIVE, allowed)
        listOf(
            live.copy(enableChanger = false),
            live.copy(enableChanger = false, liveIntervalMinutes = 5),
            live.copy(enableChanger = false, liveIntervalMinutes = 5, scheduleType = ScheduleType.TIMES)
        ).forEach { settings ->
            assertTrue(settings.toString(), tracker.update(settings, WallpaperMode.LIVE, allowed).restartTimer)
        }
    }

    @Test fun `outside live mode the engine only follows the timer settings`() {
        val tracker = LiveSettingsTracker()
        tracker.update(live, WallpaperMode.LIVE, allowed)
        val update = tracker.update(live.copy(liveAlbumId = "other"), WallpaperMode.STATIC, allowed)
        assertEquals(LiveSettingsUpdate(restartTimer = true), update)
        assertEquals(WallpaperMode.STATIC, tracker.mode)
        assertEquals("other", tracker.settings.liveAlbumId)

        // Back in live mode the engine shows the album now picked.
        assertEquals(LiveAlbumChange.RELOAD, tracker.update(tracker.settings, WallpaperMode.LIVE, allowed).album)
    }
}
