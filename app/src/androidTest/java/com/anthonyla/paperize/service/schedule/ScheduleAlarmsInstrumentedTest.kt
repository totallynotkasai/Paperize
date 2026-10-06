package com.anthonyla.paperize.service.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.anthonyla.paperize.core.ScheduleType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.data.datastore.PreferencesManager
import com.anthonyla.paperize.domain.model.ScheduleSettings
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Arms and cancels Paperize Debug's real set-time alarm and dark-theme jobs, then puts back what
 * its own settings call for. Changes no wallpaper and no setting.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleAlarmsInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val state = ScheduleState(context)
    private val alarms = TimeOfDayAlarms(context, state)
    private val checks = DarkThemeChecks(context)
    private val workManager = WorkManager.getInstance(context)
    private var savedCheck = 0L
    private var savedTimes: String? = null

    private val times = ScheduleSettings(
        enableChanger = true, homeEnabled = true, homeAlbumId = "album",
        scheduleType = ScheduleType.TIMES, changeTimes = listOf(7 * 60, 19 * 60)
    )

    @Before fun saveState() {
        savedCheck = state.lastTimedCheck
        savedTimes = state.setTimesInUse
    }

    @After fun restore() = runBlocking {
        state.lastTimedCheck = savedCheck
        state.setTimesInUse = savedTimes
        val prefs = PreferencesManager(context)
        val settings = prefs.getScheduleSettings()
        val mode = prefs.getWallpaperMode()
        alarms.sync(settings, mode)
        checks.sync(settings, mode)
    }

    @Test fun setTimesArmAnAlarmThatGoesWhenTheyStop() {
        alarms.sync(times, WallpaperMode.STATIC)
        assertTrue(alarms.isArmed())
        alarms.sync(times.copy(scheduleType = ScheduleType.INTERVAL), WallpaperMode.STATIC)
        assertFalse(alarms.isArmed())
    }

    @Test fun onlyANewListOfTimesStartsAfresh() {
        val morning = ZonedDateTime.now().withHour(8)
        alarms.sync(times, WallpaperMode.STATIC, morning)
        val firstCheck = state.lastTimedCheck
        // The same list again (as after a restart) keeps the last check, so missed times catch up.
        alarms.sync(times, WallpaperMode.STATIC, morning.plusHours(2))
        assertEquals(firstCheck, state.lastTimedCheck)
        alarms.sync(times.copy(changeTimes = listOf(9 * 60)), WallpaperMode.STATIC, morning.plusHours(3))
        assertNotEquals(firstCheck, state.lastTimedCheck)
        alarms.cancel()
    }

    @Test fun darkThemeChecksWatchTheSettingAndCheckEveryQuarterHour() {
        val adaptive = times.copy(adaptiveBrightness = true)
        checks.sync(adaptive, WallpaperMode.STATIC)
        assertTrue(checks.isWatchingSettings())
        val periodic = workManager.getWorkInfosForUniqueWork(Constants.WORK_NAME_DARK_MODE_CHECK).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, periodic.state)

        checks.sync(times, WallpaperMode.STATIC)
        assertFalse(checks.isWatchingSettings())
        assertTrue(
            workManager.getWorkInfosForUniqueWork(Constants.WORK_NAME_DARK_MODE_CHECK).get().all { it.state == WorkInfo.State.CANCELLED }
        )
    }
}
