package com.anthonyla.paperize.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.anthonyla.paperize.domain.model.AppSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val settings = mockk<SettingsRepository>()
    private val stored = MutableStateFlow(AppSettings(firstLaunch = true))
    private val store = ViewModelStore()

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { settings.getAppSettingsFlow() } returns stored
        coEvery { settings.updateFirstLaunch(any()) } answers { stored.value = stored.value.copy(firstLaunch = firstArg()) }
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        MainViewModel(settings, saved).also { store.put(it.hashCode().toString(), it) }

    @Test fun `the splash screen waits for settings`() = runTest {
        val viewModel = viewModel()
        assertFalse(viewModel.isReady)
        advanceUntilIdle()
        assertTrue(viewModel.isReady)
    }

    @Test fun `finishing onboarding keeps the start screen it began with`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(true, viewModel.startsWithOnboarding.value)
        viewModel.finishOnboarding()
        advanceUntilIdle()
        assertFalse(stored.value.firstLaunch)
        assertEquals(true, viewModel.startsWithOnboarding.value)
    }

    @Test fun `a saved decision survives re-creation`() = runTest {
        val viewModel = viewModel(SavedStateHandle(mapOf("starts_with_onboarding" to false)))
        advanceUntilIdle()
        assertEquals(false, viewModel.startsWithOnboarding.value)
    }
}
