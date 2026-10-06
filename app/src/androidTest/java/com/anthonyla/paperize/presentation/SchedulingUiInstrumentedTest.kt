package com.anthonyla.paperize.presentation

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.NightTrigger
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.AlbumSummary
import com.anthonyla.paperize.domain.model.AppSettings
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.presentation.common.theme.PaperizeTheme
import com.anthonyla.paperize.presentation.screens.scheduling.SchedulingOptionsContent
import com.anthonyla.paperize.presentation.screens.wallpaper.WallpaperScreen
import com.anthonyla.paperize.presentation.screens.wallpaper.components.formatMinuteOfDay
import com.anthonyla.paperize.testing.emptyAlbumSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Phase 6 scheduling controls: set times on the Wallpaper tab, and the Scheduling Options screen. */
class SchedulingUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test fun setTimesReplaceTheIntervalBoxes() {
        val settings = mutableStateOf(ScheduleSettings(
            enableChanger = true, homeEnabled = true, lockEnabled = true, homeAlbumId = "a", lockAlbumId = "b"
        ))
        var latest = settings.value
        var openedOptions = false
        compose.setContent {
            PaperizeTheme(false, false) {
                WallpaperScreen(
                    albums = emptyList(), persistedScheduleSettings = settings.value,
                    appSettings = AppSettings(), wallpaperMode = WallpaperMode.STATIC,
                    onToggleChanger = {}, onSelectHomeAlbum = {}, onSelectLockAlbum = {}, onSelectLiveAlbum = {},
                    onUpdateScheduleSettings = { latest = it }, onUpdateSettingsDeferRender = {},
                    onChangeWallpaperNow = {}, homeWallpaperUri = null, lockWallpaperUri = null,
                    onOpenSchedulingOptions = { openedOptions = true }
                )
            }
        }
        compose.onNodeWithText(text(R.string.schedule_type_interval)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(text(R.string.individual_scheduling)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.schedule_type_times)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ScheduleType.TIMES, latest.scheduleType) }

        // Set times apply to both screens, so the separate intervals go along with the boxes.
        compose.runOnIdle { settings.value = latest }
        compose.onNodeWithText(text(R.string.individual_scheduling)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.interval_text)).assertDoesNotExist()
        val seven = formatMinuteOfDay(context, 7 * 60)
        val nineteen = formatMinuteOfDay(context, 19 * 60)
        compose.onNodeWithText(nineteen).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.remove_time, seven)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(19 * 60), latest.changeTimes) }

        // The last time stays.
        compose.runOnIdle { settings.value = latest }
        compose.onNodeWithContentDescription(text(R.string.remove_time, nineteen)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.add_time)).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText(text(R.string.scheduling_options_title)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(openedOptions) }
    }

    @Test fun schedulingOptionsAppearAsTheyApply() {
        val night = emptyAlbumSummary("night", "Night sky").copy(wallpaperCount = 3)
        val settings = mutableStateOf(ScheduleSettings(enableChanger = true, homeEnabled = true, lockEnabled = true, homeAlbumId = "home"))
        var latest = settings.value
        var picked: Pair<ScreenType, AlbumSummary?>? = null
        compose.setContent {
            PaperizeTheme(false, false) {
                SchedulingOptionsContent(
                    albums = listOf(night), persistedSettings = settings.value, wallpaperMode = WallpaperMode.STATIC,
                    onUpdateSettings = { latest = it }, onSelectNightAlbum = { screen, album -> picked = screen to album }
                )
            }
        }
        val unlock = compose.onNodeWithText(text(R.string.change_on_unlock))
        unlock.performScrollTo().assertIsOff().performClick()
        compose.runOnIdle { assertTrue(latest.changeOnUnlock) }
        unlock.assertIsOn()
        // With one screen rotating there is no target to choose; the gap and the notice appear.
        compose.onNodeWithText(text(R.string.trigger_target_label)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.listener_notice)).performScrollTo().assertIsDisplayed()
        // The gap is a dropdown showing the current choice (15 min to start).
        compose.onNodeWithText(text(R.string.trigger_gap_minutes, 15)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.trigger_gap_hours, 1)).performClick()
        compose.runOnIdle { assertEquals(60, latest.triggerGapMinutes) }

        // Both screens rotating: the unlock target can be chosen.
        compose.runOnIdle { settings.value = latest.copy(lockAlbumId = "lock") }
        compose.onNodeWithText(text(R.string.trigger_target_label)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.trigger_target_both)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ScreenType.BOTH, latest.unlockTarget) }

        // Night albums: the switch's controls wait until one is chosen.
        compose.onNodeWithText(text(R.string.night_trigger_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.night_album_home)).performScrollTo().performClick()
        compose.onNodeWithText("Night sky").performClick()
        compose.runOnIdle { assertEquals(ScreenType.HOME to night, picked) }
        // The album list stays open, as on the Wallpaper tab, until it is closed.
        Espresso.pressBack()
        compose.runOnIdle { settings.value = settings.value.copy(homeNightAlbumId = "night") }
        compose.onNodeWithText(text(R.string.night_trigger_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.night_starts)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.night_trigger_dark_mode)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(NightTrigger.DARK_MODE, latest.nightTrigger) }

        compose.onNodeWithText(text(R.string.only_while_charging)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(latest.onlyWhileCharging) }
    }

    @Test fun liveModeOffersNightAlbumsAndBatteryOnly() {
        compose.setContent {
            PaperizeTheme(false, false) {
                SchedulingOptionsContent(
                    albums = emptyList(), persistedSettings = ScheduleSettings(liveAlbumId = "live"),
                    wallpaperMode = WallpaperMode.LIVE, onUpdateSettings = {}, onSelectNightAlbum = { _, _ -> }
                )
            }
        }
        compose.onNodeWithText(text(R.string.screen_events_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.scheduling_paused_hint)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.night_album_live)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pause_in_battery_saver)).performScrollTo().assertIsDisplayed()
    }
}
