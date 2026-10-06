package com.anthonyla.paperize.presentation.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.core.util.isPaperizeLiveWallpaperActive
import com.anthonyla.paperize.domain.model.AlbumSummary
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.usecase.CreateAlbumUseCase
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeEvents
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeRequests
import com.anthonyla.paperize.service.schedule.ScheduleEvents
import com.anthonyla.paperize.service.worker.WallpaperScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val albumRepository: AlbumRepository,
    private val createAlbumUseCase: CreateAlbumUseCase,
    private val settingsRepository: SettingsRepository,
    private val wallpaperScheduler: WallpaperScheduler,
    private val wallpaperRepository: com.anthonyla.paperize.domain.repository.WallpaperRepository,
    private val changeRequests: WallpaperChangeRequests,
    changeEvents: WallpaperChangeEvents,
    private val scheduleEvents: ScheduleEvents
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val settingsMutex = Mutex()

    // Keep settings writes and their scheduling side effects ordered during rapid UI changes.
    private fun launchSettingsUpdate(action: suspend () -> Unit) = viewModelScope.launch {
        settingsMutex.withLock { action() }
    }

    val albums: StateFlow<List<AlbumSummary>> = albumRepository.getAlbumSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val scheduleSettings: StateFlow<ScheduleSettings> = settingsRepository.getScheduleSettingsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = ScheduleSettings()
        )

    val appSettings = settingsRepository.getAppSettingsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = com.anthonyla.paperize.domain.model.AppSettings()
        )

    val wallpaperMode = settingsRepository.getWallpaperModeFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = null
        )

    /** "Change wallpaper now" is busy until its result arrives. */
    val changeInProgress: StateFlow<Boolean> = changeEvents.pending
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS), false)

    /**
     * URI of the wallpaper Paperize last applied for the home / lock screen, for the in-app preview.
     * Reading the source URI (rather than WallpaperManager.getDrawable) avoids the storage permission
     * the preview would otherwise need. Each screen records its own current image.
     * A turned-off screen keeps its album but shows no preview.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentHomeWallpaperUri: StateFlow<String?> = scheduleSettings
        .map { it.albumFor(ScreenType.HOME) }
        .distinctUntilChanged()
        .flatMapLatest { albumId -> currentWallpaperUriFlow(albumId, ScreenType.HOME) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentLockWallpaperUri: StateFlow<String?> = scheduleSettings
        .map { it.albumFor(ScreenType.LOCK) }
        .distinctUntilChanged()
        .flatMapLatest { albumId -> currentWallpaperUriFlow(albumId, ScreenType.LOCK) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS), null)

    /** The image the live wallpaper last put on screen; recorded by its engine. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentLiveWallpaperUri: StateFlow<String?> = scheduleSettings
        .map { it.liveAlbumId }
        .distinctUntilChanged()
        .flatMapLatest { albumId ->
            if (albumId == null) flowOf(null)
            else wallpaperRepository.getCurrentWallpaperFlow(albumId, ScreenType.LIVE).map { it?.uri }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS), null)

    private fun currentWallpaperUriFlow(albumId: String?, screenType: ScreenType): Flow<String?> =
        if (albumId == null) flowOf(null)
        else wallpaperRepository.getCurrentWallpaperFlow(albumId, screenType).map { it?.uri }

    private val _showLiveWallpaperPrompt = MutableStateFlow(false)
    val showLiveWallpaperPrompt: StateFlow<Boolean> = _showLiveWallpaperPrompt

    fun dismissLiveWallpaperPrompt() {
        _showLiveWallpaperPrompt.value = false
    }

    private val _liveWallpaperNotSet = MutableStateFlow(false)

    /**
     * Live mode has an album, but Paperize is the live wallpaper on neither screen (it was never
     * set, or something else replaced it). The album and settings are kept; a banner offers to set
     * it again.
     */
    val liveWallpaperNotSet: StateFlow<Boolean> = _liveWallpaperNotSet.asStateFlow()

    /** Call whenever the screen resumes, e.g. on return from the system wallpaper picker. */
    fun checkLiveWallpaperStatus() {
        viewModelScope.launch {
            val notSet = settingsRepository.getWallpaperMode() == WallpaperMode.LIVE &&
                settingsRepository.getScheduleSettings().liveAlbumId != null &&
                !isPaperizeLiveWallpaperActive(context)
            if (notSet != _liveWallpaperNotSet.value) {
                Log.d(TAG, "Paperize live wallpaper ${if (notSet) "is not set" else "is set"}")
            }
            _liveWallpaperNotSet.value = notSet
        }
    }

    suspend fun createAlbum(name: String) = createAlbumUseCase(name)

    fun selectHomeAlbum(album: AlbumSummary?) = selectAlbum(album, ScreenType.HOME, settingsRepository::updateHomeAlbumId)

    fun selectLockAlbum(album: AlbumSummary?) = selectAlbum(album, ScreenType.LOCK, settingsRepository::updateLockAlbumId)

    fun selectLiveAlbum(album: AlbumSummary?) = selectAlbum(album, ScreenType.LIVE, settingsRepository::updateLiveAlbumId)

    private fun selectAlbum(
        album: AlbumSummary?,
        screen: ScreenType,
        updateSelection: suspend (String?) -> Unit
    ) {
        launchSettingsUpdate {
            val before = settingsRepository.getScheduleSettings()
            updateSelection(album?.id)
            // A screen with a night album may start rotating, so put the right album in use first.
            scheduleEvents.syncNightAlbums()
            val mode = settingsRepository.getWallpaperMode()
            var updated = settingsRepository.getScheduleSettings()
            if (album == null && updated.activeScreens(mode).isEmpty()) {
                settingsRepository.updateEnableChanger(false)
                updated = updated.copy(enableChanger = false)
            }
            wallpaperScheduler.updateSchedules(updated, mode)
            if (mode == WallpaperMode.LIVE) {
                checkLiveWallpaperStatus()
            } else if (album != null && updated.enableChanger && updated.albumFor(screen) == album.id) {
                // Only the screen whose album was picked changes. A screen that wasn't rotating yet
                // keeps the countdown its new job just started; otherwise the change restarts it.
                val newlyRotating = screen !in before.rotatingStaticScreens()
                changeWallpaperNow(screen, keepSchedule = newlyRotating)
            }
        }
    }

    fun toggleWallpaperChanger(enabled: Boolean) {
        launchSettingsUpdate {
            settingsRepository.updateEnableChanger(enabled)
            scheduleEvents.syncNightAlbums()
            val updated = settingsRepository.getScheduleSettings()
            val mode = settingsRepository.getWallpaperMode()
            // New jobs count a full interval from now, so the first change happens here instead.
            wallpaperScheduler.updateSchedules(updated, mode)
            if (enabled && updated.hasRequiredAlbums(mode)) {
                if (mode == WallpaperMode.STATIC) changeStaticScreensNow(updated.rotatingStaticScreens())
                promptForLiveWallpaper(mode)
            }
        }
    }

    private fun promptForLiveWallpaper(mode: WallpaperMode) {
        if (mode == WallpaperMode.LIVE && !isPaperizeLiveWallpaperActive(context)) {
            _showLiveWallpaperPrompt.value = true
        }
    }

    /**
     * Save an edit at once. [deferRender] (slider levels) waits briefly before re-rendering the
     * static wallpaper, so a run of edits renders once; [flushPendingRender] renders straight away.
     */
    fun updateScheduleSettings(settings: ScheduleSettings, deferRender: Boolean = false) {
        launchSettingsUpdate {
            lateinit var currentSettings: ScheduleSettings
            var validated = settingsRepository.updateScheduleSettings { current ->
                currentSettings = current
                // Album selection and pause/resume have their own actions. An edit made from an
                // older draft must not overwrite changes those actions made since. Turning a
                // screen off keeps its album, ready for when it is turned on again.
                settings.copy(
                    enableChanger = current.enableChanger,
                    homeAlbumId = current.homeAlbumId,
                    lockAlbumId = current.lockAlbumId,
                    liveAlbumId = current.liveAlbumId,
                    homeNightAlbumId = current.homeNightAlbumId,
                    lockNightAlbumId = current.lockNightAlbumId,
                    liveNightAlbumId = current.liveNightAlbumId,
                    nightActive = current.nightActive
                ).validate()
            }
            // New night hours, trigger or screens may put the other albums in use now (plan 6.4).
            if (validated.hasSchedulingChanges(currentSettings) && scheduleEvents.syncNightAlbums()) {
                validated = settingsRepository.getScheduleSettings()
            }
            if (currentSettings.shuffleEnabled != validated.shuffleEnabled) {
                wallpaperRepository.clearAllQueues()
            }

            val mode = settingsRepository.getWallpaperMode()
            if (validated.hasSchedulingChanges(currentSettings)) {
                wallpaperScheduler.updateSchedules(validated, mode)
            }
            if (mode != WallpaperMode.STATIC) return@launchSettingsUpdate

            // A screen turned on with changing on shows its first image now; its new job already
            // counts down from now, so the change keeps that countdown.
            val newlyRotating = if (validated.enableChanger) {
                validated.rotatingStaticScreens() - currentSettings.rotatingStaticScreens()
            } else emptySet()
            changeStaticScreensNow(newlyRotating)

            // Screens that switched between their day and night albums change now, like picking an album.
            val switched = if (validated.enableChanger) switchedScreens(currentSettings, validated) else emptySet()
            changeStaticScreensNow(switched, keepSchedule = false)

            // Effects apply while paused too; the screens changed above already use them. A screen
            // whose album has its own effects doesn't show the screen's effects, so editing those
            // doesn't set its wallpaper again.
            val toRender = validated.rotatingStaticScreens().filter { screen ->
                val ownEffects = validated.albumFor(screen)?.let { albumRepository.getAlbumEffects(it) } != null
                validated.hasDisplayChanges(currentSettings, screen, albumHasOwnEffects = ownEffects)
            } - newlyRotating - switched
            if (deferRender) {
                deferRender(toRender)
            } else {
                pendingRenderScreens -= toRender.toSet()
                reapplyEffectsNow(toRender)
            }
        }
    }

    private val pendingRenderScreens = mutableSetOf<ScreenType>()
    private var pendingRenderJob: Job? = null

    private fun deferRender(screens: Collection<ScreenType>) {
        if (screens.isEmpty()) return
        pendingRenderScreens += screens
        pendingRenderJob?.cancel()
        pendingRenderJob = viewModelScope.launch {
            delay(Constants.SETTINGS_DEBOUNCE_MS)
            pendingRenderJob = null
            renderPending()
        }
    }

    /** Render waiting effect edits now; call when the user leaves the screen. */
    fun flushPendingRender() {
        pendingRenderJob?.cancel()
        pendingRenderJob = null
        renderPending()
    }

    private fun renderPending() {
        val screens = pendingRenderScreens.toSet()
        pendingRenderScreens.clear()
        reapplyEffectsNow(screens)
    }

    override fun onCleared() = flushPendingRender()

    /**
     * Change [screens] now; both static screens go in one request, which changes Home first.
     * [keepSchedule] leaves the countdown alone (a newly rotating screen's job just started one).
     */
    private fun changeStaticScreensNow(screens: Set<ScreenType>, keepSchedule: Boolean = true) {
        when (screens.size) {
            0 -> Unit
            1 -> changeWallpaperNow(screens.single(), keepSchedule)
            else -> changeWallpaperNow(ScreenType.BOTH, keepSchedule)
        }
    }

    /** Static screens that rotated before and after but now use another album: day and night switched. */
    private fun switchedScreens(before: ScheduleSettings, after: ScheduleSettings): Set<ScreenType> =
        (after.rotatingStaticScreens() intersect before.rotatingStaticScreens())
            .filterTo(mutableSetOf()) { after.albumFor(it) != before.albumFor(it) }

    /**
     * A night album for [screen] (plan 6.4), or null for none. If it is in use now (it is night), the
     * screen changes to it at once, like picking an album; the live wallpaper follows by itself.
     */
    fun selectNightAlbum(screen: ScreenType, album: AlbumSummary?) {
        launchSettingsUpdate {
            val before = settingsRepository.getScheduleSettings()
            settingsRepository.updateNightAlbumId(screen, album?.id)
            scheduleEvents.syncNightAlbums()
            val updated = settingsRepository.getScheduleSettings()
            val mode = settingsRepository.getWallpaperMode()
            wallpaperScheduler.updateSchedules(updated, mode)
            if (mode == WallpaperMode.STATIC && updated.enableChanger) {
                changeStaticScreensNow(switchedScreens(before, updated), keepSchedule = false)
            }
        }
    }

    /** [keepSchedule] leaves the automatic countdown alone instead of restarting it. */
    fun changeWallpaperNow(screenType: ScreenType, keepSchedule: Boolean = false) =
        changeRequests.change(screenType, keepSchedule)

    /**
     * "Change wallpaper now": every configured destination in one request, so the screen gets one
     * result to show. BOTH changes each rotating static screen from its own queue and restarts
     * each one's countdown; LIVE is routed through the service to reload the renderer.
     */
    fun changeWallpaperNowForActiveScreens() {
        val mode = wallpaperMode.value ?: return
        val screen = when (mode) {
            WallpaperMode.LIVE -> ScreenType.LIVE.takeIf { scheduleSettings.value.liveAlbumId != null }
            WallpaperMode.STATIC -> scheduleSettings.value.rotatingStaticScreens().let { screens ->
                if (screens.size > 1) ScreenType.BOTH else screens.singleOrNull()
            }
        } ?: return
        changeRequests.change(screen, report = true)
    }

    private fun reapplyEffectsNow(screens: Collection<ScreenType>) {
        when (screens.size) {
            0 -> Unit
            1 -> changeRequests.reapplyEffects(screens.single())
            else -> changeRequests.reapplyEffects(ScreenType.BOTH)
        }
    }
}
