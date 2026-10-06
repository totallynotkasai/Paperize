package com.anthonyla.paperize.presentation

import com.anthonyla.paperize.testing.emptyAlbumSummary
import com.anthonyla.paperize.testing.emptyAlbum
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import com.anthonyla.paperize.presentation.screens.library.components.AlbumItem
import java.io.File
import androidx.compose.runtime.mutableIntStateOf
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.presentation.screens.wallpaper.components.TimeIntervalPicker
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.anthonyla.paperize.R
import com.anthonyla.paperize.presentation.common.theme.PaperizeTheme
import com.anthonyla.paperize.presentation.screens.album_view.components.SortBottomSheet
import com.anthonyla.paperize.presentation.screens.album_view.components.SortOption
import com.anthonyla.paperize.presentation.screens.album_view.components.WallpaperItem
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.presentation.screens.library.LibraryScreen
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.semantics.SemanticsActions
import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.AppSettings
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.presentation.screens.wallpaper.WallpaperScreen

class AlbumUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun effectSlidersRouteDraftChangesToEnabledScreensAndLiveMode() {
        val settings = mutableStateOf(ScheduleSettings(
            homeEnabled = true, lockEnabled = true,
            homeEffects = WallpaperEffects(enableBlur = true, blurPercentage = 45),
            lockEffects = WallpaperEffects(enableBlur = true, blurPercentage = 65),
            liveEffects = WallpaperEffects(enableBlur = true, blurPercentage = 80)
        ))
        val mode = mutableStateOf(WallpaperMode.STATIC)
        var latest = settings.value
        compose.setContent {
            PaperizeTheme(false, false) {
                WallpaperScreen(
                    albums = emptyList(), persistedScheduleSettings = settings.value,
                    appSettings = AppSettings(), wallpaperMode = mode.value,
                    onToggleChanger = {}, onSelectHomeAlbum = {}, onSelectLockAlbum = {}, onSelectLiveAlbum = {},
                    onUpdateScheduleSettings = { latest = it },
                    onUpdateSettingsDeferRender = { latest = it },
                    onChangeWallpaperNow = {}, homeWallpaperUri = null, lockWallpaperUri = null
                )
            }
        }
        fun slide(index: Int, percentage: Float) {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[index]
                .performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(percentage) }
        }
        slide(0, 20f)
        slide(1, 75f)
        compose.runOnIdle {
            assertEquals(20, latest.homeEffects.blurPercentage)
            assertEquals(75, latest.lockEffects.blurPercentage)
            settings.value = latest.copy(homeEnabled = false)
        }
        slide(0, 55f)
        compose.runOnIdle {
            assertEquals(20, latest.homeEffects.blurPercentage)
            assertEquals(55, latest.lockEffects.blurPercentage)
            settings.value = latest
            mode.value = WallpaperMode.LIVE
        }
        slide(0, 40f)
        compose.runOnIdle {
            assertEquals(20, latest.homeEffects.blurPercentage)
            assertEquals(55, latest.lockEffects.blurPercentage)
            assertEquals(40, latest.liveEffects.blurPercentage)
        }
    }

    @Test fun failedAlbumCreationKeepsNameAndAllowsRetryAfterPendingSave() {
        val pending = CompletableDeferred<Result<Album>>()
        var attempts = 0
        compose.setContent {
            PaperizeTheme(false, false) {
                LibraryScreen(emptyList(), {}, { name ->
                    assertEquals("Mountains", name)
                    attempts++
                    if (attempts == 1) pending.await()
                    else Result.Success(emptyAlbum(id = "album", name = name))
                })
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.add_album)).performClick()
        compose.onNodeWithText(context.getString(R.string.album_name)).performTextInput("Mountains")
        compose.onNodeWithText(context.getString(R.string.save)).performClick().assertIsNotEnabled()
        compose.runOnIdle { pending.complete(Result.Error(IllegalStateException("Storage unavailable"))) }
        compose.onNodeWithText(context.getString(R.string.album_create_failed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.album_name)).assertTextContains("Mountains")
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        compose.onNodeWithText(context.getString(R.string.album_name)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, attempts) }
    }

    @Test fun sortSheetExposesCurrentSelectionAndAppliesNewChoice() {
        var chosen: SortOption? = null
        var dismissed = false
        compose.setContent {
            PaperizeTheme(false, false) {
                SortBottomSheet(SortOption.DATE_ADDED_DESC, { chosen = it }, { dismissed = true })
            }
        }
        compose.onNodeWithText(context.getString(R.string.sort_date_added_desc)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.sort_name_asc)).performClick()
        compose.runOnIdle { assertEquals(SortOption.NAME_ASC, chosen); assertTrue(dismissed) }
    }

    @Test fun wallpaperThumbnailsExposeNameAndSelection() {
        compose.setContent {
            PaperizeTheme(false, false) {
                WallpaperItem("content://test/image", "Mountains.jpg", true, true, {}, {}, Modifier.size(160.dp))
            }
        }
        compose.onNodeWithContentDescription("Mountains.jpg")
            .assertIsSelected().assertHasClickAction()
    }
    @Test fun albumCoverLoadsThroughCoilAndFallsBackForMissingFiles() {
        val file = File.createTempFile("album-cover", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = mutableStateOf<String?>(file.toURI().toString())
        try {
            compose.setContent {
                PaperizeTheme(false, false) {
                    AlbumItem(emptyAlbumSummary("album").copy(name = "Cover", coverUri = uri.value), {}, Modifier.size(180.dp))
                }
            }
            fun centerColor(): Int {
                // The card is labelled by its name; its centre lies inside the cover.
                val pixels = compose.onNodeWithText("Cover").captureToImage().toPixelMap()
                return pixels[pixels.width / 2, pixels.height / 2].toArgb()
            }
            compose.waitUntil(5_000) { centerColor() == Color.RED }
            compose.runOnIdle { uri.value = File(context.cacheDir, "missing-cover.png").toURI().toString() }
            compose.waitUntil(5_000) { centerColor() != Color.RED }
            compose.onNodeWithText("Cover").assertIsDisplayed()
        } finally {
            file.delete()
        }
    }

    @Test fun intervalEditsCommitOnDoneAndAreNeverResetWhileTyping() {
        val minutes = mutableIntStateOf(60)
        val changes = mutableListOf<Int>()
        compose.setContent {
            PaperizeTheme(false, false) {
                TimeIntervalPicker("Interval", minutes.intValue, { changes.add(it) })
            }
        }
        val hours = compose.onNodeWithText(context.getString(R.string.hours_txt))
        val mins = compose.onNodeWithText(context.getString(R.string.mins))
        hours.performClick()
        hours.performTextReplacement("0")
        // Moving to the next box is still the same edit.
        mins.performClick()
        mins.performTextReplacement("1")
        compose.mainClock.advanceTimeBy(5_000)
        compose.runOnIdle { assertTrue(changes.isEmpty()) }

        // A value saved elsewhere arrives while typing: the boxes keep what was typed.
        compose.runOnIdle { minutes.intValue = 120 }
        mins.assertTextContains("1")
        hours.assertTextContains("0")

        // Done commits once, clamped to the minimum, and the boxes then show the saved value.
        mins.performImeAction()
        compose.runOnIdle { assertEquals(listOf(Constants.MIN_INTERVAL_MINUTES), changes) }
        compose.runOnIdle { minutes.intValue = Constants.MIN_INTERVAL_MINUTES }
        mins.assertTextContains(Constants.MIN_INTERVAL_MINUTES.toString())
        hours.assertTextContains("0")
        compose.runOnIdle { assertEquals(1, changes.size) }
    }

    @Test fun effectControlsAreDisabledWhileNoScreenIsOn() {
        val settings = ScheduleSettings(lockEffects = WallpaperEffects(enableBlur = true, blurPercentage = 30))
        var updates = 0
        compose.setContent {
            PaperizeTheme(false, false) {
                WallpaperScreen(
                    albums = emptyList(), persistedScheduleSettings = settings,
                    appSettings = AppSettings(), wallpaperMode = WallpaperMode.STATIC,
                    onToggleChanger = {}, onSelectHomeAlbum = {}, onSelectLockAlbum = {}, onSelectLiveAlbum = {},
                    onUpdateScheduleSettings = { updates++ },
                    onUpdateSettingsDeferRender = { updates++ },
                    onChangeWallpaperNow = {}, homeWallpaperUri = null, lockWallpaperUri = null
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.effects_need_a_screen)).assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
            .performScrollTo()
            .assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.fit)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, updates) }
    }

    @Test fun horizontalScrollingIsOfferedOnlyForHomeWithFill() {
        val settings = mutableStateOf(ScheduleSettings(homeEnabled = true, homeScalingType = ScalingType.FILL))
        compose.setContent {
            PaperizeTheme(false, false) {
                WallpaperScreen(
                    albums = emptyList(), persistedScheduleSettings = settings.value,
                    appSettings = AppSettings(), wallpaperMode = WallpaperMode.STATIC,
                    onToggleChanger = {}, onSelectHomeAlbum = {}, onSelectLockAlbum = {}, onSelectLiveAlbum = {},
                    onUpdateScheduleSettings = {}, onUpdateSettingsDeferRender = {},
                    onChangeWallpaperNow = {}, homeWallpaperUri = null, lockWallpaperUri = null
                )
            }
        }
        val scrolling = context.getString(R.string.horizontal_wallpaper_scrolling)
        compose.onNodeWithText(scrolling).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { settings.value = settings.value.copy(homeScalingType = ScalingType.FIT, lockScalingType = ScalingType.FIT) }
        compose.onNodeWithText(scrolling).assertDoesNotExist()
        compose.runOnIdle { settings.value = ScheduleSettings(lockEnabled = true) }
        compose.onNodeWithText(scrolling).assertDoesNotExist()
    }

    @Test fun screenCardsAnnounceAsSwitches() {
        compose.setContent {
            PaperizeTheme(false, false) {
                WallpaperScreen(
                    albums = emptyList(), persistedScheduleSettings = ScheduleSettings(homeEnabled = true),
                    appSettings = AppSettings(), wallpaperMode = WallpaperMode.STATIC,
                    onToggleChanger = {}, onSelectHomeAlbum = {}, onSelectLockAlbum = {}, onSelectLiveAlbum = {},
                    onUpdateScheduleSettings = {}, onUpdateSettingsDeferRender = {},
                    onChangeWallpaperNow = {}, homeWallpaperUri = null, lockWallpaperUri = null
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.home)).assertIsOn()
        compose.onNodeWithText(context.getString(R.string.lock)).assertIsOff()
        // The state is announced once, by the switch, not again by the visible "Enabled" line.
        compose.onAllNodesWithText(context.getString(R.string.enabled)).assertCountEquals(0)
    }

}
