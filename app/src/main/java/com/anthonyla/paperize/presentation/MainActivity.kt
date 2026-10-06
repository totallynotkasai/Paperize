package com.anthonyla.paperize.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anthonyla.paperize.presentation.common.navigation.HomeRoute
import com.anthonyla.paperize.presentation.common.navigation.NavigationGraph
import com.anthonyla.paperize.presentation.common.navigation.StartupRoute
import com.anthonyla.paperize.presentation.common.theme.PaperizeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Without this the app would show a blank frame in the wrong theme while settings load.
        splashScreen.setKeepOnScreenCondition { !viewModel.isReady }

        setContent {
            val currentSettings by viewModel.appSettings.collectAsStateWithLifecycle()
            val startsWithOnboarding by viewModel.startsWithOnboarding.collectAsStateWithLifecycle()

            PaperizeTheme(
                darkMode = currentSettings?.darkMode,
                dynamicTheming = currentSettings?.dynamicTheming ?: false
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    startsWithOnboarding?.let { onboarding ->
                        NavigationGraph(
                            startDestination = if (onboarding) StartupRoute else HomeRoute,
                            animate = currentSettings?.animate ?: true,
                            onFirstLaunchComplete = viewModel::finishOnboarding
                        )
                    }
                }
            }
        }
    }
}
