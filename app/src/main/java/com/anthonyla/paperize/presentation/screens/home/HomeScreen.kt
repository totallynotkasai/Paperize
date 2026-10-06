package com.anthonyla.paperize.presentation.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.anthonyla.paperize.R
import com.anthonyla.paperize.presentation.screens.wallpaper.components.openLiveWallpaperPicker
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.presentation.screens.home.components.HomeTopBar
import com.anthonyla.paperize.presentation.screens.home.components.getTabItems
import com.anthonyla.paperize.presentation.screens.library.LibraryScreen
import com.anthonyla.paperize.presentation.screens.wallpaper.WallpaperScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val scheduleSettings by viewModel.scheduleSettings.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val wallpaperMode by viewModel.wallpaperMode.collectAsStateWithLifecycle()
    val showLiveWallpaperPrompt by viewModel.showLiveWallpaperPrompt.collectAsStateWithLifecycle()
    val currentHomeWallpaperUri by viewModel.currentHomeWallpaperUri.collectAsStateWithLifecycle()
    val currentLockWallpaperUri by viewModel.currentLockWallpaperUri.collectAsStateWithLifecycle()
    val currentLiveWallpaperUri by viewModel.currentLiveWallpaperUri.collectAsStateWithLifecycle()
    val liveWallpaperNotSet by viewModel.liveWallpaperNotSet.collectAsStateWithLifecycle()
    val changeInProgress by viewModel.changeInProgress.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Returning from the system wallpaper picker (or anywhere else) re-checks the live wallpaper.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.checkLiveWallpaperStatus() }
    // Effect edits are already saved; render the last ones before the app leaves the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.flushPendingRender() }

    val tabItems = getTabItems(
        wallpaperTitle = stringResource(R.string.wallpaper),
        libraryTitle = stringResource(R.string.library)
    )

    val pagerState = rememberPagerState { tabItems.size }

    Scaffold(
        topBar = {
            HomeTopBar(
                onSettingsClick = onNavigateToSettings
            )
        }
    ) { paddingValues ->

            Column(modifier = modifier.padding(paddingValues)) {
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage
                ) {
                    tabItems.forEachIndexed { index, item ->
                        Tab(
                            selected = (index == pagerState.currentPage),
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(text = item.title) },
                            icon = {
                                Icon(
                                    imageVector = if (index == pagerState.currentPage) item.filledIcon else item.unfilledIcon,
                                    // The tab text already names it.
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1
                ) { index ->
                    when (index) {
                        0 -> {
                            if (wallpaperMode == null) {
                                Box(modifier = Modifier.fillMaxSize())
                            } else {
                                WallpaperScreen(
                                    albums = albums,
                                    persistedScheduleSettings = scheduleSettings,
                                    appSettings = appSettings,
                                    wallpaperMode = wallpaperMode!!,
                                    onToggleChanger = { viewModel.toggleWallpaperChanger(it) },
                                    onSelectHomeAlbum = { album -> viewModel.selectHomeAlbum(album) },
                                    onSelectLockAlbum = { album -> viewModel.selectLockAlbum(album) },
                                    onSelectLiveAlbum = { album -> viewModel.selectLiveAlbum(album) },
                                    onUpdateScheduleSettings = { viewModel.updateScheduleSettings(it) },
                                    onUpdateSettingsDeferRender = {
                                        viewModel.updateScheduleSettings(it, deferRender = true)
                                    },
                                    onChangeWallpaperNow = {
                                        viewModel.changeWallpaperNowForActiveScreens()
                                    },
                                    homeWallpaperUri = currentHomeWallpaperUri,
                                    lockWallpaperUri = currentLockWallpaperUri,
                                    liveWallpaperUri = currentLiveWallpaperUri,
                                    liveWallpaperNotSet = liveWallpaperNotSet,
                                    changeInProgress = changeInProgress
                                )
                            }
                        }
                        else -> LibraryScreen(
                            albums = albums,
                            onViewAlbum = onNavigateToAlbum,
                            onCreateAlbum = { name -> viewModel.createAlbum(name) }
                        )
                    }
                }
            }
        }

    if (showLiveWallpaperPrompt && wallpaperMode == WallpaperMode.LIVE) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLiveWallpaperPrompt() },
            title = {
                Text(
                    text = stringResource(R.string.select_live_wallpaper),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.select_live_wallpaper_instruction),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.open_wallpaper_picker_instruction_newline),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissLiveWallpaperPrompt()
                        openLiveWallpaperPicker(context)
                    }
                ) {
                    Text(stringResource(R.string.open_wallpaper_picker))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLiveWallpaperPrompt() }) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }
}
