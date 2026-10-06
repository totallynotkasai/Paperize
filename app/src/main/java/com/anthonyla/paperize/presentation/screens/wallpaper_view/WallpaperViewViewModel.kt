package com.anthonyla.paperize.presentation.screens.wallpaper_view

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.presentation.common.navigation.WallpaperViewRoute
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeEvents
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WallpaperViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    private val changeRequests: WallpaperChangeRequests,
    changeEvents: WallpaperChangeEvents
) : ViewModel() {
    private val route = savedStateHandle.toRoute<WallpaperViewRoute>()

    val wallpaperMode = settingsRepository.getWallpaperModeFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = WallpaperMode.STATIC
    )

    /** "Set wallpaper" is busy until its result arrives. */
    val applying: StateFlow<Boolean> = changeEvents.pending
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.FLOW_SUBSCRIPTION_TIMEOUT_MS), false)

    fun applyTo(screenType: ScreenType) {
        require(screenType != ScreenType.LIVE)
        changeRequests.applySpecific(route.wallpaperId, screenType)
    }
}
