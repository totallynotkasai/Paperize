package com.anthonyla.paperize.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthonyla.paperize.domain.model.AppSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state for [MainActivity]: the theme settings and which screen the app starts on. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val appSettings: StateFlow<AppSettings?> = settingsRepository.getAppSettingsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Decided once, from the first loaded settings. Finishing onboarding later flips firstLaunch,
     * but the navigation graph must keep its start destination or it is rebuilt. Saved state keeps
     * the decision across activity re-creation and process death.
     */
    val startsWithOnboarding: StateFlow<Boolean?> =
        savedStateHandle.getStateFlow<Boolean?>(KEY_STARTS_WITH_ONBOARDING, null)

    init {
        if (startsWithOnboarding.value == null) {
            viewModelScope.launch {
                savedStateHandle[KEY_STARTS_WITH_ONBOARDING] = settingsRepository.getAppSettingsFlow().first().firstLaunch
            }
        }
    }

    /** The splash screen stays up until the first frame can use the right theme and start screen. */
    val isReady: Boolean
        get() = appSettings.value != null && startsWithOnboarding.value != null

    fun finishOnboarding() {
        viewModelScope.launch { settingsRepository.updateFirstLaunch(false) }
    }

    private companion object {
        const val KEY_STARTS_WITH_ONBOARDING = "starts_with_onboarding"
    }
}
